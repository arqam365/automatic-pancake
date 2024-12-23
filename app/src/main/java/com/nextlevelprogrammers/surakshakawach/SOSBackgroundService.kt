package com.nextlevelprogrammers.surakshakawach

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.nextlevelprogrammers.surakshakawach.api.Api
import com.nextlevelprogrammers.surakshakawach.api.ClipData
import com.nextlevelprogrammers.surakshakawach.api.ImageData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

class SOSBackgroundService(override val lifecycle: Lifecycle) : Service(), LifecycleOwner {

    private lateinit var lifecycleRegistry: LifecycleRegistry
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var mediaRecorder: MediaRecorder
    private var sosTicketId: String? = null
    private val apiClient = Api()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        const val CHANNEL_ID = "sos_channel"
        const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry = LifecycleRegistry(this)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        cameraExecutor = Executors.newSingleThreadExecutor()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mediaRecorder = MediaRecorder()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        sosTicketId = intent?.getStringExtra("sosTicketId")

        startForeground(NOTIFICATION_ID, createNotification())
        startImageCapture()
        startLocationUpdates()
        startAudioRecording()

        return START_STICKY
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SOS Active")
            .setContentText("SOS operations running in the background.")
            .setSmallIcon(R.drawable.icon)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "SOS Background Service",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Handles SOS-related tasks in the background."
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun getFirebaseUID(): String? {
        return FirebaseAuth.getInstance().currentUser?.uid
    }

    private fun startImageCapture() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            imageCapture = ImageCapture.Builder().build()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, // Use LifecycleOwner from Service
                    cameraSelector,
                    imageCapture
                )
                scheduleImageCapture()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun scheduleImageCapture() {
        val captureRunnable = object : Runnable {
            override fun run() {
                captureImage()
                handler.postDelayed(this, 60000)
            }
        }
        handler.post(captureRunnable)
    }


    private fun captureImage() {
        val imageCaptureInstance = imageCapture ?: return
        val file = File(filesDir, "sos_image_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

        imageCaptureInstance.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Log.d("SOS", "Image captured and saved: ${file.absolutePath}")
                }

                override fun onError(exception: ImageCaptureException) {
                    exception.printStackTrace()
                }
            }
        )
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.create().apply {
            interval = 5000
            fastestInterval = 3000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.locations.forEach { location ->
                    sendLocationToBackend(location.latitude, location.longitude)
                }
            }
        }, Looper.getMainLooper())
    }

    private fun sendLocationToBackend(latitude: Double, longitude: Double) {
        val firebaseUID = getFirebaseUID() ?: return
        sosTicketId?.let { ticketId ->
            coroutineScope.launch {
                try {
                    val success = apiClient.updateCoordinates(
                        firebaseUID,
                        ticketId,
                        latitude.toString(),
                        longitude.toString(),
                        System.currentTimeMillis().toString()
                    )
                    if (success) {
                        Log.d("SOS", "Location updated successfully")
                    } else {
                        Log.e("SOS", "Failed to update location")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun startAudioRecording() {
        val audioFile = File(filesDir, "sos_audio_${System.currentTimeMillis()}.mp3")
        mediaRecorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(audioFile.absolutePath)
            prepare()
            start()
        }

        handler.postDelayed({
            try {
                mediaRecorder.stop()
                mediaRecorder.reset()
                sendAudioToBackend(audioFile)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, 15000) // Record for 15 seconds
    }

    private fun sendAudioToBackend(audioFile: File) {
        val firebaseUID = getFirebaseUID() ?: return
        sosTicketId?.let { ticketId ->
            val clipData = ClipData(
                url = audioFile.absolutePath,
                timestamp = System.currentTimeMillis(),
                gsBucketUrl = audioFile.name
            )

            coroutineScope.launch {
                try {
                    val success = apiClient.sendAudioClips(ticketId, firebaseUID, listOf(clipData))
                    if (success) {
                        audioFile.delete()
                        Log.d("SOS", "Audio uploaded successfully")
                    } else {
                        Log.e("SOS", "Failed to upload audio")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        mediaRecorder.release()
        cameraExecutor.shutdown()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}