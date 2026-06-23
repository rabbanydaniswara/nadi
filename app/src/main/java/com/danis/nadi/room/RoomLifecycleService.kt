package com.danis.nadi.room

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.danis.nadi.MainActivity
import com.danis.nadi.R

class RoomLifecycleService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_ROOM -> {
                RoomRuntime.controller(this).stopActiveRoom()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                createNotificationChannel()
                startForeground(NOTIFICATION_ID, activeRoomNotification())
            }
        }
        return START_NOT_STICKY
    }

    private fun activeRoomNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, RoomLifecycleService::class.java).setAction(ACTION_STOP_ROOM),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val roomName = RoomRuntime.controller(this).roomManager.currentSession()?.roomName ?: getString(R.string.app_name)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("$roomName aktif")
            .setContentText("Nadi menjaga room lokal tetap berjalan.")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, getString(R.string.stop_room), stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Room aktif Nadi",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Status room lokal Nadi yang sedang berjalan."
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val ACTION_START_ROOM = "com.danis.nadi.action.START_ROOM"
        private const val ACTION_STOP_ROOM = "com.danis.nadi.action.STOP_ROOM"
        private const val CHANNEL_ID = "nadi_active_room"
        private const val NOTIFICATION_ID = 42

        fun start(context: Context) {
            val intent = Intent(context, RoomLifecycleService::class.java).setAction(ACTION_START_ROOM)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RoomLifecycleService::class.java))
        }
    }
}
