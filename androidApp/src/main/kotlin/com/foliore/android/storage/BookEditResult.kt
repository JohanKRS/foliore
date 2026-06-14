package com.foliore.android.storage

sealed interface BookEditResult {
    data class Success(val bookId: String) : BookEditResult
    data object InvalidTitle : BookEditResult
    data object NotFound : BookEditResult
    data object Failed : BookEditResult
}

sealed interface BatchBookEditResult {
    data class Success(val count: Int) : BatchBookEditResult
    data object EmptySelection : BatchBookEditResult
    data object NoChanges : BatchBookEditResult
    data object Failed : BatchBookEditResult
}
