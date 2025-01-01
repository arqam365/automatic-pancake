package com.nextlevelprogrammers.surakshakawach

import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import android.Manifest
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
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
        try {
            startWakeWordDetection()
        } catch (e: Exception) {
            Log.e("VoiceRecognitionService", "Error starting wake word detection: ${e.message}", e)
            stopSelf() // Stop the service to prevent unexpected behavior
        }
        return START_STICKY
    }

    private fun startWakeWordDetection() {
        try {
            porcupineManager = PorcupineManager.Builder()
                .setAccessKey("Pi4BPLjLwlkdzArXawqTYlE1+k5pG2paTGPrQH6RVXx4mDyjeIeosw==")
                .setKeywordPath("help_us.ppn")
                .setSensitivity(1f)
                .build(applicationContext, porcupineCallback)
            porcupineManager?.start()
            isListening = true
        } catch (e: Exception) {
            Log.e("VoiceRecognitionService", "Failed to initialize PorcupineManager: ${e.message}", e)
            porcupineManager = null
        }
    }

    private fun sendWakeWordDetectedBroadcast() {
        try {
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
        } catch (e: Exception) {
            Log.e("VoiceRecognitionService", "Error sending wake word detected broadcast: ${e.message}", e)
        }
    }

    private fun isMicrophonePermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        Log.d("VoiceRecognitionService", "onDestroy: Stopping PorcupineManager and releasing resources.")
        try {
            porcupineManager?.stop()
            porcupineManager?.delete()
        } catch (e: Exception) {
            Log.e("VoiceRecognitionService", "Error stopping or deleting PorcupineManager: ${e.message}", e)
        }
        isListening = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}