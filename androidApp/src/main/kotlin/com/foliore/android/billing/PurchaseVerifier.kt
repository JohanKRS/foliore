package com.foliore.android.billing

import java.security.KeyFactory
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
internal data class SignedPurchase(val data: String, val signature: String)
internal data class VerifiedPurchase(val token: String, val acknowledged: Boolean)

/** Verify the original Play receipt, never an editable "premium" preference. */
internal class PurchaseVerifier(publicKey: String, private val packageName: String) {
    private val key = runCatching {
        (KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(
            Base64.getDecoder().decode(publicKey.filterNot(Char::isWhitespace))
        )) as RSAPublicKey).also { require(it.modulus.bitLength() >= 2048) }
    }.getOrNull()
    val isConfigured get() = key != null

    fun verify(receipt: SignedPurchase): VerifiedPurchase? = runCatching {
        val signingKey = key ?: return null
        if (receipt.data.length > 64_000 || receipt.signature.length > 4_096) return null
        val valid = Signature.getInstance("SHA1withRSA").run {
            initVerify(signingKey)
            update(receipt.data.toByteArray(Charsets.UTF_8))
            verify(Base64.getDecoder().decode(receipt.signature))
        }
        if (!valid) return null
        val data = Json.parseToJsonElement(receipt.data).jsonObject
        fun text(name: String) = (data[name] as? JsonPrimitive)?.contentOrNull
        val products = (data["productIds"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?: listOfNotNull(text("productId"))
        if (text("packageName") != packageName || products != listOf(LibraryLimit.PRODUCT_ID)) return null
        // The JSON wire format uses 0 for purchased, unlike BillingClient's PurchaseState constants.
        if (text("purchaseState")?.toIntOrNull() != 0) return null
        if (data.containsKey("quantity") && text("quantity")?.toIntOrNull() != 1) return null
        val token = (text("purchaseToken") ?: text("token"))?.takeIf { it.isNotBlank() } ?: return null
        VerifiedPurchase(token, (data["acknowledged"] as? JsonPrimitive)?.booleanOrNull == true)
    }.getOrNull()
}
