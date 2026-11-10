package com.locora.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*

/**
 * Foreground GPS collector. Coordinates are cached locally so they can be
 * synchronized when a backend is configured and connectivity returns.
 * This service does not secretly track: Android location permission and the
 * persistent foreground-service notification are required.
 */
class LocationTrackerService : Service() {
    private lateinit var fused: FusedLocationProviderClient
    private lateinit var callback: LocationCallback

    override fun onCreate() {
        super.onCreate()
        createChannel()
        fused = LocationServices.getFusedLocationProviderClient(this)
        callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                getSharedPreferences("locora_local", Context.MODE_PRIVATE).edit()
                    .putString("last_lat", loc.latitude.toString())
                    .putString("last_lon", loc.longitude.toString())
                    .putLong("last_location_at", System.currentTimeMillis())
                    .apply()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(4101, buildNotification())
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
            .setMinUpdateIntervalMillis(3000L)
            .build()
        try {
            fused.requestLocationUpdates(request, callback, mainLooper)
        } catch (_: SecurityException) {
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        if (::fused.isInitialized && ::callback.isInitialized) fused.removeLocationUpdates(callback)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "locora_location", "LOCORA location sharing", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Visible notification while location sharing is active" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, "locora_location")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("LOCORA location sharing")
            .setContentText("Location sharing is active. Tap LOCORA to manage it.")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
}
