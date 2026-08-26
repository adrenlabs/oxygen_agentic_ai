package com.oxygen.ai.telegram

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.oxygen.ai.OxygenApplication
import com.oxygen.ai.R
import com.oxygen.ai.core.identity.OxygenBrand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class TelegramService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Telegram gateway", NotificationManager.IMPORTANCE_LOW),
        )
        startForeground(42, notification("Telegram gateway is running"))
        val graph = (application as OxygenApplication).graph
        job = scope.launch { graph.telegram.pollLoop() }
    }

    override fun onDestroy() {
        (application as? OxygenApplication)?.graph?.telegram?.stop()
        job?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle(OxygenBrand.APP_NAME)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .build()

    companion object {
        const val CHANNEL = "oxygen.telegram"
    }
}
