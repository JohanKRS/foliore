package com.foliore.android.ai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.foliore.android.MainActivity
import com.foliore.android.R
import com.foliore.android.storage.AppLanguagePreferenceRepository

/**
 * Keeps the process alive while the browser shows the ChatGPT sign-in, so the loopback
 * listener can answer the redirect. It only runs during a sign-in and shows no other work.
 */
class ChatGptSignInForegroundService : Service() {
    private var attemptId = 0L
    private var serviceStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        attemptId = intent?.getLongExtra(EXTRA_ATTEMPT, 0L) ?: 0L
        serviceStartId = startId
        val localized = AppLanguagePreferenceRepository.localizedContext(this)
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    localized.getString(R.string.chatgpt_notification_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent.makeMainActivity(ComponentName(this, MainActivity::class.java)),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(localized.getString(R.string.chatgpt_notification_title))
            .setContentText(localized.getString(R.string.chatgpt_notification_text))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setSilent(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE else 0
        runCatching { ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type) }
            .onFailure { end(startId, ChatGptSignInFailure.FAILED) }
        return START_NOT_STICKY
    }

    @Deprecated("Replaced by onTimeout(Int, Int) on API 35")
    override fun onTimeout(startId: Int) = end(startId, ChatGptSignInFailure.TIMED_OUT)

    override fun onTimeout(startId: Int, fgsType: Int) = end(startId, ChatGptSignInFailure.TIMED_OUT)

    private fun end(startId: Int, reason: ChatGptSignInFailure) {
        if (startId == serviceStartId) ChatGptAuthService.get(this).serviceEnded(attemptId, reason)
        stopSelf(startId)
    }

    companion object {
        private const val CHANNEL_ID = "chatgpt_sign_in"
        private const val NOTIFICATION_ID = 4107

        private const val EXTRA_ATTEMPT = "sign_in_attempt"

        fun start(context: Context, attemptId: Long): Boolean = runCatching {
            val intent = Intent(context, ChatGptSignInForegroundService::class.java).putExtra(EXTRA_ATTEMPT, attemptId)
            ContextCompat.startForegroundService(context, intent) != null
        }.getOrDefault(false)

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, ChatGptSignInForegroundService::class.java)) }
        }
    }
}
