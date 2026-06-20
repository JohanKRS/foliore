package com.foliore.android.storage

import android.content.Intent

sealed interface PreparedEpubResult {
    data class Prepared(val bookId: String) : PreparedEpubResult
    data class ShareIntentReady(val intent: Intent) : PreparedEpubResult
    data object Sent : PreparedEpubResult
    data object NotFound : PreparedEpubResult
    data object Failed : PreparedEpubResult
}
