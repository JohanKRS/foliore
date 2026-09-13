package com.foliore.android.billing

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.android.billingclient.api.*
import com.foliore.android.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

/** App-scoped: one Play connection, including background acknowledgement retries. */
class PlayBillingRepository private constructor(context: Context) {
    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val operations = Mutex()
    private val verifier = PurchaseVerifier(BuildConfig.PLAY_BILLING_PUBLIC_KEY, context.packageName)
    private val store = PurchaseStore(this.context, verifier)
    private val mutableState = MutableStateFlow(BillingState(unlimited = store.read() != null))
    val state = mutableState.asStateFlow()
    private var purchaseInProgress = false
    private var flowTimeout: Job? = null
    private var refreshJob: Job? = null
    private val client = BillingClient.newBuilder(this.context)
        .setListener(::onPurchasesUpdated)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    fun refresh(restore: Boolean = false) {
        if (refreshJob?.isActive == true) return
        refreshJob = scope.launch { refreshPurchases(restore, loadPrice = true) }
    }

    internal suspend fun retryAcknowledgement(): Boolean = refreshPurchases(loadPrice = false)

    private suspend fun refreshPurchases(restore: Boolean = false, loadPrice: Boolean): Boolean =
        withContext(Dispatchers.Main.immediate) { operations.withLock {
            mutableState.update { it.copy(busy = true, message = null) }
            try {
                if (!verifier.isConfigured) throw BillingUnavailable()
                connect()
                val purchases = queryPurchases()
                val relevant = purchases.filter { LibraryLimit.PRODUCT_ID in it.products }
                val acknowledged = processPurchases(relevant)
                if (shouldRevokePurchase(true, hasNetwork(), relevant.isNotEmpty(), purchaseInProgress)) {
                    withContext(Dispatchers.IO) { store.clear() }
                    mutableState.update { it.copy(unlimited = false, pending = false) }
                }
                if (restore && mutableState.value.message == null) {
                    mutableState.update { it.copy(message = when {
                        relevant.isNotEmpty() && it.unlimited -> BillingMessage.RESTORED
                        !hasNetwork() -> BillingMessage.UNAVAILABLE
                        else -> BillingMessage.NO_PURCHASE
                    }) }
                }
                if (loadPrice && !mutableState.value.unlimited) {
                    // Prices are always supplied by Play, never hard-coded currency conversions.
                    val offer = runCatching { queryOffer() }.getOrNull()
                    mutableState.update { it.copy(price = offer?.second?.formattedPrice) }
                    if (offer == null && mutableState.value.message == null) unavailable()
                }
                acknowledged
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { unavailable(); false }
            finally { mutableState.update { it.copy(busy = purchaseInProgress) } }
        } }

    fun purchase(activity: Activity) {
        if (purchaseInProgress || mutableState.value.busy || mutableState.value.unlimited) return
        scope.launch {
            operations.withLock {
                mutableState.update { it.copy(busy = true, message = null) }
                try {
                    if (!verifier.isConfigured || activity.isFinishing || activity.isDestroyed) throw BillingUnavailable()
                    connect()
                    // Recover an existing/pending order before offering another purchase.
                    val owned = queryPurchases().filter { LibraryLimit.PRODUCT_ID in it.products }
                    processPurchases(owned)
                    if (mutableState.value.unlimited || owned.isNotEmpty()) return@withLock
                    val (product, offer) = queryOffer()
                    mutableState.update { it.copy(price = offer.formattedPrice) }
                    val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product)
                            .setOfferToken(requireNotNull(offer.offerToken)).build()
                    )).build()
                    purchaseInProgress = true
                    val result = client.launchBillingFlow(activity, params)
                    if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                        finishFlow()
                        if (result.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) refresh(restore = true)
                        else if (result.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) unavailable()
                    } else {
                        // Recover a lost callback without keeping Settings permanently disabled.
                        flowTimeout?.cancel()
                        flowTimeout = scope.launch { delay(120_000); flowTimeout = null; finishFlow(); refresh() }
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { finishFlow(); unavailable() }
                finally { mutableState.update { it.copy(busy = purchaseInProgress) } }
            }
        }
    }

    private fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        scope.launch {
            operations.withLock {
                finishFlow()
                when (result.responseCode) {
                    BillingClient.BillingResponseCode.OK -> {
                        if (purchases == null) { unavailable(); return@withLock }
                        try { processPurchases(purchases.filter { LibraryLimit.PRODUCT_ID in it.products }) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { unavailable(); PurchaseAcknowledgementWorker.enqueue(context) }
                    }
                    BillingClient.BillingResponseCode.USER_CANCELED -> Unit
                    BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refresh(restore = true)
                    else -> unavailable()
                }
            }
        }
    }

    private suspend fun processPurchases(purchases: List<Purchase>): Boolean {
        var complete = true
        mutableState.update { it.copy(pending = purchases.any { p -> p.purchaseState == Purchase.PurchaseState.PENDING }) }
        for (purchase in purchases) {
            if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
                finishFlow()
                mutableState.update { it.copy(message = BillingMessage.PENDING) }
                continue
            }
            val receipt = SignedPurchase(purchase.originalJson, purchase.signature)
            val verified = verifier.verify(receipt)
            if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED || verified == null || verified.token != purchase.purchaseToken) {
                mutableState.update { it.copy(message = BillingMessage.VERIFY_FAILED) }
                complete = false
                continue
            }
            // Persist before acknowledgement. Never consume this permanent unlock.
            withContext(Dispatchers.IO) { store.save(receipt) }
            finishFlow()
            mutableState.update { it.copy(unlimited = true, pending = false, message = null) }
            if (!purchase.isAcknowledged) {
                PurchaseAcknowledgementWorker.enqueue(context)
                val result = timedCallback<BillingResult> { resume ->
                    client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(verified.token).build(), resume)
                }
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    complete = false
                    mutableState.update { it.copy(message = BillingMessage.RETRY_ACKNOWLEDGEMENT) }
                }
            }
        }
        return complete
    }

    private suspend fun connect() {
        if (client.isReady) return
        val result = timedCallback<BillingResult> { resume ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) = resume(result)
                override fun onBillingServiceDisconnected() = Unit // Auto-reconnection handles the next request.
            })
        }
        if (result.responseCode != BillingClient.BillingResponseCode.OK) throw BillingUnavailable()
    }

    private suspend fun queryPurchases(): List<Purchase> {
        val (result, purchases) = timedCallback<Pair<BillingResult, List<Purchase>>> { resume ->
            client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) {
                result, purchases -> resume(result to purchases)
            }
        }
        if (result.responseCode != BillingClient.BillingResponseCode.OK) throw BillingUnavailable()
        return purchases
    }

    private suspend fun queryOffer(): Pair<ProductDetails, ProductDetails.OneTimePurchaseOfferDetails> {
        val (result, details) = timedCallback<Pair<BillingResult, QueryProductDetailsResult>> { resume ->
            client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(listOf(
                QueryProductDetailsParams.Product.newBuilder().setProductId(LibraryLimit.PRODUCT_ID)
                    .setProductType(BillingClient.ProductType.INAPP).build()
            )).build()) { result, details -> resume(result to details) }
        }
        if (result.responseCode != BillingClient.BillingResponseCode.OK) throw BillingUnavailable()
        val product = details.productDetailsList.singleOrNull { it.productId == LibraryLimit.PRODUCT_ID } ?: throw BillingUnavailable()
        val offer = product.oneTimePurchaseOfferDetailsList.orEmpty().filter {
            it.purchaseOptionId == "buy" && !it.offerToken.isNullOrBlank() && it.rentalDetails == null && it.preorderDetails == null
        }.minByOrNull { it.priceAmountMicros } ?: throw BillingUnavailable()
        return product to offer
    }

    private fun hasNetwork(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        return manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    }
    private fun unavailable() { mutableState.update { it.copy(message = BillingMessage.UNAVAILABLE, price = null) } }
    private fun finishFlow() { purchaseInProgress = false; flowTimeout?.cancel(); flowTimeout = null; mutableState.update { it.copy(busy = false) } }
    private class BillingUnavailable : Exception()

    companion object {
        @Volatile private var instance: PlayBillingRepository? = null
        fun get(context: Context): PlayBillingRepository = instance ?: synchronized(this) {
            instance ?: PlayBillingRepository(context).also { instance = it }
        }
    }
}

private suspend fun <T> timedCallback(start: ((T) -> Unit) -> Unit): T = withTimeoutOrNull(20_000) {
    suspendCancellableCoroutine { continuation ->
        val delivered = java.util.concurrent.atomic.AtomicBoolean(false)
        start { value -> if (delivered.compareAndSet(false, true) && continuation.isActive) continuation.resume(value) }
    }
} ?: throw java.io.IOException("Play Billing request timed out")
