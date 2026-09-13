package com.foliore.android.billing

data class BillingState(
    val unlimited: Boolean = false,
    val busy: Boolean = false,
    val pending: Boolean = false,
    val price: String? = null,
    val message: BillingMessage? = null,
)

enum class BillingMessage { UNAVAILABLE, PENDING, RESTORED, NO_PURCHASE, VERIFY_FAILED, RETRY_ACKNOWLEDGEMENT }

/** An incomplete or failed query cannot revoke a previously verified purchase. */
internal fun shouldRevokePurchase(querySucceeded: Boolean, networkValidated: Boolean,
    hasProduct: Boolean, purchaseInProgress: Boolean): Boolean =
    querySucceeded && networkValidated && !hasProduct && !purchaseInProgress
