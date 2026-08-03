package com.sikoclaw.app.floating

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.sikoclaw.app.R
import com.sikoclaw.app.ui.chat.ComposeChatActivity

class FloatingAssistantService : Service() {
    companion object {
        fun stop(context: Context) = context.stopService(Intent(context, FloatingAssistantService::class.java))
    }

    override fun onCreate() {
        super.onCreate()
        val channel = "floating_assistant"
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(channel, "Floating Assistant", NotificationManager.IMPORTANCE_LOW))
        val pending = PendingIntent.getActivity(this, 0, Intent(this, ComposeChatActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        startForeground(7402, NotificationCompat.Builder(this, channel).setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("OctoBot floating assistant").setContentText("Tap to open the conversation")
            .setOngoing(true).setContentIntent(pending).build())
        FloatingAssistantManager.show(application)
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        FloatingAssistantManager.hide()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
