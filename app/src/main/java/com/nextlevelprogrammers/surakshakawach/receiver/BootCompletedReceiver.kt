package com.nextlevelprogrammers.surakshakawach.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.nextlevelprogrammers.surakshakawach.service.VoiceWakeupService

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action.equals(Intent.ACTION_BOOT_COMPLETED)) {
            val serviceIntent = Intent(context, VoiceWakeupService::class.java)
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }
}