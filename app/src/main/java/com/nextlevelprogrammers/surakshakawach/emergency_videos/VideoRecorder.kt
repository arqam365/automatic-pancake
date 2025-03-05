package com.nextlevelprogrammers.surakshakawach.emergency_videos

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.util.Log
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

class VideoRecorder(private val context: Context) {

    private var videoCapture: VideoCapture<Recorder>? = null
    private var currentRecording: Recording? = null
    private val storageReference: StorageReference =
        FirebaseStorage.getInstance("gs://suraksha-kawach-151024-v2-development")
            .reference.child("emergency_videos")
    private var isRecordingActive = true

    /**
     * Initializes CameraX and sets up Video Capture in SD quality.
     */
    fun initializeCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val qualitySelector = QualitySelector.from(Quality.SD)
            val recorder = Recorder.Builder()
                .setQualitySelector(qualitySelector)
                .build()

            videoCapture = VideoCapture.withOutput(recorder)

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                context as androidx.lifecycle.LifecycleOwner,
                cameraSelector,
                videoCapture
            )

            Log.d("VideoRecorder", "✅ CameraX Initialized with SD Quality")
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Starts continuous video recording: 15s recording, then 10s gap.
     */
    fun startContinuousRecording(onVideoUploaded: (String, String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            while (isRecordingActive) {
                val videoFile = startVideoRecording() ?: continue
                delay(15000) // 🎥 Record for 15 seconds

                stopVideoRecording(videoFile) { uploadedUrl, bucketUrl ->
                    onVideoUploaded(uploadedUrl, bucketUrl)
                }

                delay(10000) // ⏳ Wait for 10 seconds before the next recording
            }
        }
    }

    /**
     * ✅ Checks Camera and Audio Permissions
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

        val outputFile = createVideoFile()
        val outputOptions = FileOutputOptions.Builder(outputFile).build()

        currentRecording = videoCapture?.output
            ?.prepareRecording(context, outputOptions)
            ?.withAudioEnabled()
            ?.start(ContextCompat.getMainExecutor(context)) { event ->
                if (event is VideoRecordEvent.Start) {
                    Log.d("VideoRecorder", "🎥 Recording Started")
                }
            }

        return outputFile
    }

    /**
     * ✅ Stops recording correctly and uploads video to Firebase.
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
    private suspend fun uploadVideoToFirebase(file: File, onUploaded: (String, String) -> Unit) {
        val fileName = file.name
        val storageReference = storageReference.child(fileName)

        // ✅ Ensure user is authenticated before uploading
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Log.e("FirebaseUpload", "❌ Upload Failed: User is not authenticated!")
            return
        }

        try {
            Log.d("FirebaseUpload", "Uploading file: $fileName to Firebase Storage")

            storageReference.putFile(Uri.fromFile(file)).await()
            val downloadUrl = storageReference.downloadUrl.await().toString()
            val bucketUrl = "gs://suraksha-kawach-151024-v2-development/emergency_videos/$fileName"

            Log.d("FirebaseUpload", "✅ File Uploaded Successfully: $downloadUrl")

            onUploaded(downloadUrl, bucketUrl)
            file.delete() // ✅ Delete local file after successful upload
        } catch (e: Exception) {
            Log.e("FirebaseUpload", "❌ Upload Failed: ${e.localizedMessage}")
        }
    }

    /**
     * ✅ Stops continuous video recording.
     */
    fun stopRecording() {
        isRecordingActive = false
        Log.d("VideoRecorder", "⛔ Stopped Continuous Recording")
    }
}