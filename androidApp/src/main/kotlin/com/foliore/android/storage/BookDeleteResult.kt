package com.foliore.android.storage

sealed interface BookDeleteResult {
    data class Deleted(val title: String) : BookDeleteResult
    data object NotFound : BookDeleteResult
    data object Failed : BookDeleteResult
}
