package com.foliore.android.storage

sealed interface BookEditResult {
    data class Success(val bookId: String) : BookEditResult
    data object InvalidTitle : BookEditResult
    data object NotFound : BookEditResult
    data object Failed : BookEditResult
}

