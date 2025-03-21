package com.nextlevelprogrammers.surakshakawach.service

import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.nextlevelprogrammers.surakshakawach.R

class VoiceWakeupService : Service() {

    private var porcupineManager: PorcupineManager? = null
    private var audioRecorder: AudioRecord? = null
    private var isListening = false
    private val sampleRate = 16000

    private val logHandler = Handler(Looper.getMainLooper())
    private val logRunnable = object : Runnable {
        override fun run() {
            Log.d("VoiceWakeupService", "Listening status: $isListening")
            logHandler.postDelayed(this, 10000L) // Log every 5 seconds
        }
    }

    private val porcupineCallback = PorcupineManagerCallback { keywordIndex ->
        Log.d("VoiceWakeupService", "Wake word detected! Performing action.")
        sendWakeWordDetectedBroadcast()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("VoiceWakeupService", "Starting VoiceWakeupService...")

        startForeground(1, createForegroundNotification())

        if (checkRecordAudioPermission()) {
            startWakeWordDetection()
        } else {
            Log.e("VoiceWakeupService", "Microphone permission not granted.")
        }

        // Start periodic logging
        logHandler.post(logRunnable)

        return START_STICKY
    }

    private fun startWakeWordDetection() {
        Log.d("VoiceWakeupService", "Initializing wake word detection...")

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            try {
                porcupineManager = PorcupineManager.Builder()
                    .setAccessKey("ak93lw6wZ9tO8T4qXAp2ej22WPGXooH6yzwVuW0e1L03xaMXdgtTpw==") // Replace with valid key
                    .setKeywordPath("Help_Me.ppn") // Ensure the file is in assets
                    .setSensitivity(0.7f)
                    .build(applicationContext, porcupineCallback)

                val bufferSize = AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )

                audioRecorder = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )

                if (audioRecorder?.state == AudioRecord.STATE_INITIALIZED) {
                    audioRecorder?.startRecording()
                    isListening = true
                    porcupineManager?.start()
                    Log.d("VoiceWakeupService", "PorcupineManager started.")
                } else {
                    Log.e("VoiceWakeupService", "Failed to initialize AudioRecord.")
                }
            } catch (e: Exception) {
                Log.e("VoiceWakeupService", "Error initializing Porcupine: ${e.message}", e)
            }
        } else {
            Log.e("VoiceWakeupService", "RECORD_AUDIO permission not granted.")
        }
    }

    private fun createForegroundNotification(): Notification {
        val channelId = "VOICE_WAKEUP_CHANNEL"
        val channelName = "Voice Wakeup Service"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(notificationChannel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Listening for Wake Word")
            .setContentText("Wake word detection active.")
            .setSmallIcon(R.drawable.logo)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    private fun checkRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun sendWakeWordDetectedBroadcast() {
        val intent = Intent("com.nextlevelprogrammers.surakshakawach.WAKE_WORD_DETECTED")
        sendBroadcast(intent)
        Log.d("VoiceWakeupService", "Wake word broadcast sent.")
    }

    @RequiresPermission(Manifest.permission.SCHEDULE_EXACT_ALARM)
    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d("VoiceWakeupService", "Service removed. Scheduling restart...")

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            val restartServiceIntent = Intent(applicationContext, this::class.java)
            val restartServicePendingIntent = PendingIntent.getService(
                this, 1, restartServiceIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + 1000,
                restartServicePendingIntent
            )
        } else {
            promptUserForExactAlarmPermission()
        }

        super.onTaskRemoved(rootIntent)
    }

    private fun promptUserForExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = "package:$packageName".toUri()
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Log.e("VoiceWakeupService", "Failed to prompt exact alarm permission: ${e.message}", e)
            }
        }
    }

    override fun onDestroy() {
        porcupineManager?.stop()
        porcupineManager?.delete()

        audioRecorder?.stop()
        audioRecorder?.release()
        isListening = false

        // Stop logging
        logHandler.removeCallbacks(logRunnable)

        Log.d("VoiceWakeupService", "Service destroyed, resources released.")

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}