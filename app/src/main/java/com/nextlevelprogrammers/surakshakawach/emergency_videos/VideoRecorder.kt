package com.nextlevelprogrammers.surakshakawach.emergency_videos

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class VideoRecorder(private val context: Context) {

    private var videoCapture: VideoCapture<Recorder>? = null
    private var currentRecording: Recording? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var isRecordingActive = false
    private val storageReference: StorageReference =
        FirebaseStorage.getInstance("gs://suraksha-kawach-151024-v2-development")
            .reference.child("emergency_videos")
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    /**
     * ✅ Initializes CameraX and sets up Video Capture in SD quality.
     */
    @OptIn(ExperimentalCamera2Interop::class)
    @RequiresApi(Build.VERSION_CODES.P) // Ensure this is only used in API 28+
    fun initializeCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                val qualitySelector = QualitySelector.from(Quality.SD)

                val recorder = Recorder.Builder()
                    .setQualitySelector(qualitySelector)
                    .setExecutor(cameraExecutor) // ✅ Set executor for threading
                    .build()

                videoCapture = VideoCapture.withOutput(recorder)

                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    context as androidx.lifecycle.LifecycleOwner,
                    cameraSelector,
                    videoCapture
                )

                Log.d("VideoRecorder", "✅ CameraX Initialized Successfully")

            } catch (e: Exception) {
                Log.e("VideoRecorder", "❌ Camera Initialization Failed: ${e.localizedMessage}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * ✅ Retrieves CameraCharacteristics using reflection to bypass API restrictions.
     */
    @OptIn(ExperimentalCamera2Interop::class)
    private fun getCameraCharacteristics(cameraInfo: CameraInfo): CameraCharacteristics? {
        return try {
            val method = Camera2CameraInfo::class.java.getDeclaredMethod("extractCameraCharacteristics", CameraInfo::class.java)
            method.isAccessible = true
            method.invoke(null, cameraInfo) as? CameraCharacteristics
        } catch (e: Exception) {
            Log.e("CameraX", "❌ Failed to get CameraCharacteristics: ${e.localizedMessage}")
            null
        }
    }

    /**
     * ✅ Starts continuous video recording with 60s recording, then 10s break.
     */
    fun startContinuousRecording(onVideoUploaded: (String, String) -> Unit) {
        isRecordingActive = true
        CoroutineScope(Dispatchers.IO).launch {
            while (isRecordingActive) {
                val videoFile = startVideoRecording() ?: continue
                delay(60000) // 🎥 Record for 60 seconds

                stopVideoRecording(videoFile) { uploadedUrl, bucketUrl ->
                    if (uploadedUrl.isEmpty()) {
                        Log.e("VideoRecorder", "❌ Video upload failed, stopping continuous recording.")
                        stopRecording()
                    } else {
                        onVideoUploaded(uploadedUrl, bucketUrl)
                    }
                }

                delay(10000) // ⏳ Wait before the next recording
            }
        }
    }

    /**
     * ✅ Checks if Camera and Audio permissions are granted.
     */
    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * ✅ Starts recording a video safely and returns the file.
     */
    private fun startVideoRecording(): File? {
        if (!hasCameraPermission() || !hasAudioPermission()) {
            Log.e("VideoRecorder", "❌ Camera or Audio permission not granted!")
            return null
        }

        if (videoCapture == null) {
            Log.e("VideoRecorder", "❌ VideoCapture not initialized!")
            return null
        }

        val outputFile = createVideoFile()
        val outputOptions = FileOutputOptions.Builder(outputFile).build()

        currentRecording = videoCapture?.output
            ?.prepareRecording(context, outputOptions)
            ?.withAudioEnabled()
            ?.start(ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        Log.d("VideoRecorder", "🎥 Recording Started")
                    }
                    is VideoRecordEvent.Finalize -> {
                        Log.d("VideoRecorder", "✅ Recording Finalized")
                    }
                    is VideoRecordEvent.Status -> {
                        // 🔧 Instead of logging as an error, handle it properly
                        Log.d("VideoRecorder", "ℹ Recording Status Update: ${event.recordingStats}")
                    }
                    else -> {
                        Log.e("VideoRecorder", "❌ Unknown Event: ${event.javaClass.simpleName}")
                    }
                }
            }

        return outputFile
    }

    /**
     * ✅ Stops recording and uploads video to Firebase.
     */
    private fun stopVideoRecording(file: File, onUploaded: (String, String) -> Unit) {
        currentRecording?.stop()
        currentRecording = null

        Log.d("VideoRecorder", "⏹ Video Recording Stopped: ${file.absolutePath}")

        CoroutineScope(Dispatchers.IO).launch {
            uploadVideoToFirebase(file, onUploaded)
        }
    }

    /**
     * ✅ Creates a temporary file to store the video before uploading.
     */
    private fun createVideoFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        return File.createTempFile("SOS_$timeStamp", ".mp4", storageDir)
    }

    /**
     * ✅ Uploads recorded video to Firebase Storage.
     */
    private suspend fun uploadVideoToFirebase(
        file: File,
        onUploaded: (String, String) -> Unit
    ) {
        val fileName = file.name
        val storageReference = storageReference.child(fileName)

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Log.e("FirebaseUpload", "❌ Upload Failed: User is not authenticated!")
            return
        }

        var retryCount = 0
        val maxRetries = 3

        while (retryCount < maxRetries) {
            try {
                Log.d("FirebaseUpload", "🔄 Attempt ${retryCount + 1}: Uploading file: $fileName")

                storageReference.putFile(Uri.fromFile(file)).await()
                val downloadUrl = storageReference.downloadUrl.await().toString()
                val bucketUrl = "gs://suraksha-kawach-151024-v2-development/emergency_videos/$fileName"

                Log.d("FirebaseUpload", "✅ File Uploaded Successfully: $downloadUrl")

                onUploaded(downloadUrl, bucketUrl)
                file.delete() // ✅ Delete local file after successful upload
                return

            } catch (e: Exception) {
                retryCount++
                Log.e("FirebaseUpload", "❌ Upload Failed: ${e.localizedMessage} (Attempt $retryCount)")

                if (retryCount >= maxRetries) {
                    Log.e("FirebaseUpload", "🚨 Max retries reached. Stopping video recording.")
                    stopRecording()
                    return
                }

                delay(2000L * retryCount) // ⏳ Exponential backoff before retrying
            }
        }
    }

    /**
     * ✅ Stops continuous video recording.
     */
    fun stopRecording() {
        isRecordingActive = false
        Log.d("VideoRecorder", "⛔ Stopped Continuous Recording")
    }

    /**
     * ✅ Unbinds camera and releases resources.
     */
    fun closeCamera() {
        Log.d("VideoRecorder", "🛑 Closing CameraX Service")
        stopRecording()
        currentRecording?.close()
        cameraProvider?.unbindAll()
        videoCapture = null
        cameraProvider = null
        Log.d("VideoRecorder", "✅ CameraX Service Closed Successfully")
    }
}