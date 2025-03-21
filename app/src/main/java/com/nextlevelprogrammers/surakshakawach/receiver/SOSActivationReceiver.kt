package com.nextlevelprogrammers.surakshakawach.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.edit

class SOSActivationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("SOSActivationReceiver", "Wake Word Broadcast Received!")

        val prefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        prefs.edit() { putBoolean("SOS_TRIGGERED", true) }

        Log.d("SOSActivationReceiver", "SOS_TRIGGERED flag saved.")
    }
}