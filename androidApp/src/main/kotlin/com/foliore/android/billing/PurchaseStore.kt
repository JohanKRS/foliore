package com.foliore.android.billing

import android.content.Context
import android.util.AtomicFile
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class PurchaseStore(context: Context, private val verifier: PurchaseVerifier) {
    // Never travels with a book, Drive backup, or Android backup.
    private val file = AtomicFile(File(context.noBackupFilesDir, "play-purchase.json"))

    @Synchronized fun read(): SignedPurchase? = runCatching {
        require(file.baseFile.length() <= 80_000)
        Json.decodeFromString<SignedPurchase>(file.openRead().bufferedReader().use { it.readText() })
            .takeIf { verifier.verify(it) != null }
    }.getOrNull()

    @Synchronized fun save(receipt: SignedPurchase) {
        require(verifier.verify(receipt) != null)
        val stream = file.startWrite()
        try {
            stream.write(Json.encodeToString(receipt).toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (error: Throwable) { file.failWrite(stream); throw error }
    }

    @Synchronized fun clear() = file.delete()
}
