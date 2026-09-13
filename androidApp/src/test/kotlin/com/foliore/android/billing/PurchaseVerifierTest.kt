package com.foliore.android.billing

import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import kotlin.test.*
import kotlinx.serialization.json.*

class PurchaseVerifierTest {
    private val keys = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val verifier = PurchaseVerifier(Base64.getEncoder().encodeToString(keys.public.encoded), "com.foliore.android")
    private fun receipt(overrides: Map<String, JsonElement> = emptyMap()): SignedPurchase {
        val values = mutableMapOf<String, JsonElement>("packageName" to JsonPrimitive("com.foliore.android"),
            "productId" to JsonPrimitive(LibraryLimit.PRODUCT_ID), "purchaseState" to JsonPrimitive(0),
            "purchaseToken" to JsonPrimitive("synthetic-token"), "acknowledged" to JsonPrimitive(false))
        values.putAll(overrides)
        val data = JsonObject(values).toString()
        val signature = Signature.getInstance("SHA1withRSA").run { initSign(keys.private); update(data.toByteArray()); sign() }
        return SignedPurchase(data, Base64.getEncoder().encodeToString(signature))
    }

    @Test fun validPurchaseDoesNotRequireAnOrderIdAndCanBeRevalidatedOffline() {
        val signed = receipt()
        repeat(2) { assertEquals(VerifiedPurchase("synthetic-token", false), verifier.verify(signed)) }
    }
    @Test fun aModifiedReceiptCannotUnlockTheCatalog() {
        val signed = receipt()
        assertNull(verifier.verify(signed.copy(data = signed.data.replace("synthetic-token", "forged-token"))))
        assertNull(verifier.verify(signed.copy(signature = "not-a-signature")))
    }
    @Test fun receiptsFromOtherAppsProductsOrKeysAreRejected() {
        assertNull(verifier.verify(receipt(mapOf("packageName" to JsonPrimitive("another.app")))))
        assertNull(verifier.verify(receipt(mapOf("productId" to JsonPrimitive("another_product")))))
        val another = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        assertNull(PurchaseVerifier(Base64.getEncoder().encodeToString(another.public.encoded), "com.foliore.android").verify(receipt()))
    }
    @Test fun pendingCanceledAndMalformedStatesDoNotUnlock() {
        for (state in listOf(1, 2, 3)) assertNull(verifier.verify(receipt(mapOf("purchaseState" to JsonPrimitive(state)))))
        assertNull(verifier.verify(receipt(mapOf("purchaseState" to JsonNull))))
        assertNull(verifier.verify(receipt(mapOf("purchaseToken" to JsonPrimitive("")))))
        assertNull(verifier.verify(receipt(mapOf("quantity" to JsonPrimitive(2)))))
    }
    @Test fun productArrayMustContainOnlyTheUnlimitedProduct() {
        assertNotNull(verifier.verify(receipt(mapOf("productIds" to JsonArray(listOf(JsonPrimitive(LibraryLimit.PRODUCT_ID)))))))
        assertNull(verifier.verify(receipt(mapOf("productIds" to JsonArray(listOf(JsonPrimitive("other")))))))
        assertNull(verifier.verify(receipt(mapOf("productIds" to JsonArray(emptyList())))))
    }
    @Test fun missingOrInvalidConfigurationFailsClosed() {
        for (key in listOf("", "invalid-key")) {
            val missing = PurchaseVerifier(key, "com.foliore.android")
            assertFalse(missing.isConfigured)
            assertNull(missing.verify(receipt()))
        }
    }
    @Test fun acknowledgesOnlyWhenTheReceiptSaysAcknowledged() {
        assertTrue(verifier.verify(receipt(mapOf("acknowledged" to JsonPrimitive(true))))!!.acknowledged)
    }
}
