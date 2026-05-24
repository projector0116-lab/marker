package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class TrackingService : Service() {

    companion object {
        const val CHANNEL_ID = "com.example.gps_tracking_channel"
        private const val NOTIFICATION_ID = 202688

        var isRunning = false
            private set

        fun startService(context: Context) {
            val intent = Intent(context, TrackingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, TrackingService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("TrackingService", "Background location tracking service initialized")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("TrackingService", "Background tracking service started")
        isRunning = true

        val mainActivityClass = com.example.MainActivity::class.java
        val notificationIntent = Intent(this, mainActivityClass)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GPSルート記録中 / バックグラウンド動作中")
            .setContentText("バックグラウンドで走行データをリアルタイムで測定・道路中央補正（吸着）しています")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setColor(Color.parseColor("#1D4ED8")) // Slate Blue tint
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        Log.d("TrackingService", "Background location tracking service stopped")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "位置情報バックグラウンドトラッキング",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "バックグラウンドでGPS情報を取得し、正確なルート記録を継続します"
                enableLights(true)
                lightColor = Color.BLUE
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
            manager?.createNotificationChannel(serviceChannel)
        }
    }
}
