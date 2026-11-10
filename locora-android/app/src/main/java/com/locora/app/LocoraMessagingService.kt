package com.locora.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class LocoraMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val pairId = getSharedPreferences("locora_local", Context.MODE_PRIVATE)
            .getString("cloud_pair_id", "").orEmpty()
        if (pairId.isNotBlank()) CloudRepository.saveFcmToken(this, pairId, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        showPrivateNotice(message.data["kind"].orEmpty())
    }

    private fun showPrivateNotice(kind: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "locora_private_alerts"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "LOCORA private alerts", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val open = PendingIntent.getActivity(
            this, 81, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = if (kind == "missing_you") "Someone is thinking of you." else "You have a private update."
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_locora_notification)
            .setContentTitle("LOCORA")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
    }
}