package com.foliore.android.storage

/** Shared by UI containers and WorkManager containers in this process. */
internal object SyncOperationCoordinator {
    val remoteLock = Any()
    val localDataLock = Any()
}
