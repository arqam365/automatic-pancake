package com.nextlevelprogrammers.surakshakawach

import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioRecord
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class VoiceRecognitionService : Service() {
    private var porcupineManager: PorcupineManager? = null
    private var isListening = false

    private val porcupineCallback = PorcupineManagerCallback { keywordIndex ->
        Log.d("VoiceRecognitionService", "Wake word detection is working.")
        sendWakeWordDetectedBroadcast()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isMicrophonePermissionGranted()) {
            Log.e("VoiceRecognitionService", "Microphone permission not granted.")
            stopSelf()
            return START_NOT_STICKY
        }
        startWakeWordDetection()
        return START_STICKY
    }

    private fun startWakeWordDetection() {
        porcupineManager = PorcupineManager.Builder()
            .setAccessKey("Pi4BPLjLwlkdzArXawqTYlE1+k5pG2paTGPrQH6RVXx4mDyjeIeosw==")
            .setKeywordPath("help_us.ppn")
            .setSensitivity(1f)
            .build(applicationContext, porcupineCallback)
        porcupineManager?.start()
    }



    private fun sendWakeWordDetectedBroadcast() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Use the broadcast method
            sendBroadcast(Intent("com.nextlevelprogrammers.surakshakawach.WAKE_WORD_DETECTED"))
        } else {
            // Use the PendingIntent method
            val intent = Intent(this, HomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent.send()
        }
    }

    private fun isMicrophonePermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        Log.d("VoiceRecognitionService", "onDestroy: Stopping PorcupineManager and releasing AudioRecord.")
        porcupineManager?.stop()
        porcupineManager?.delete()
        isListening = false
        Log.d("VoiceRecognitionService", "onDestroy: PorcupineManager stopped, AudioRecord released.")

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}