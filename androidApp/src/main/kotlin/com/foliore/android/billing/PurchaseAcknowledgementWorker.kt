package com.foliore.android.billing

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

class PurchaseAcknowledgementWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        if (PlayBillingRepository.get(applicationContext).retryAcknowledgement()) Result.success() else Result.retry()

    companion object {
        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork("play-purchase-acknowledgement", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<PurchaseAcknowledgementWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setInitialDelay(30, TimeUnit.SECONDS)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES).build())
        }
    }
}
