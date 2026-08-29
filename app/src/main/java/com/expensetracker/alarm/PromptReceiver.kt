package com.expensetracker.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import com.expensetracker.R
import com.expensetracker.voice.VoiceCaptureActivity

class PromptReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "daily_prompt"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val ch = NotificationChannel(channelId, context.getString(R.string.prompt_channel_name), NotificationManager.IMPORTANCE_HIGH)
            ch.description = context.getString(R.string.prompt_channel_desc)
            nm.createNotificationChannel(ch)
        }
        val recordIntent = Intent(context, VoiceCaptureActivity::class.java)
        val pi = PendingIntent.getActivity(context, 0, recordIntent, PendingIntent.FLAG_IMMUTABLE)
        val snoozePi = PendingIntent.getBroadcast(context, 1, Intent(context, PromptReceiver::class.java).apply { putExtra("snooze", true) }, PendingIntent.FLAG_IMMUTABLE)

        val notif = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Time to log your spending")
            .setContentText(context.getString(R.string.voice_prompt))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .addAction(android.R.drawable.ic_btn_speak_now, "Record Now", pi)
            .addAction(android.R.drawable.ic_lock_idle_alarm, "Snooze 1h", snoozePi)
            .build()
        nm.notify(1001, notif)

        // Voice alarm if device interactive
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        if (pm.isInteractive) {
            // TTS will be handled by VoiceCaptureActivity when opened; optional direct TTS here requires service
        }
    }
}
