package com.nextlevelprogrammers.surakshakawach

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.VideoRecordEvent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.nextlevelprogrammers.surakshakawach.api.Api
import com.nextlevelprogrammers.surakshakawach.api.VideoClipData
import com.nextlevelprogrammers.surakshakawach.ui.getCurrentTimestamp
import com.nextlevelprogrammers.surakshakawach.utils.UserSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SOSActivity : ComponentActivity() {

    // CameraX VideoCapture properties (we bind only the VideoCapture use case)
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    private lateinit var storageReference: StorageReference
    private val handler = Handler(Looper.getMainLooper())

    // Video recording settings: 15 seconds recording, 25 seconds cycle (15 sec recording + 10 sec break)
    private val recordingDuration = 15000L
    private val recordingInterval = 25000L

    // SOS ticket and user information
    private var sosTicketId: String? = null
    private var firebaseUID: String? = null

    // Permissions launcher for CAMERA and WRITE_EXTERNAL_STORAGE
    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Log.d("SOSActivity", "All required permissions granted.")
            bindCamera()
            scheduleVideoRecording()
        } else {
            Log.e("SOSActivity", "Required permissions not granted: $permissions")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Firebase Storage reference (for video uploads)
        storageReference = FirebaseStorage.getInstance().reference.child("sos_videos")

        // Retrieve SOS Ticket ID and Firebase UID from intent/session
        sosTicketId = intent.getStringExtra("sosTicketId")
        firebaseUID = getFirebaseUIDOrFallback()

        if (sosTicketId.isNullOrEmpty() || firebaseUID.isNullOrEmpty()) {
            Log.e("SOSActivity", "SOS Ticket ID or Firebase UID is null. Cannot proceed.")
            finish()
            return
        }

        setContent {
            val isTicketClosed = remember { mutableStateOf(false) }
            val errorMessage = remember { mutableStateOf<String?>(null) }

            SOSScreen(
                onCloseTicket = {
                    closeSOSTicket(
                        onSuccess = {
                            isTicketClosed.value = true
                            Log.d("SOSActivity", "SOS ticket closed successfully.")
                        },
                        onError = { error ->
                            errorMessage.value = error
                            Log.e("SOSActivity", "Failed to close SOS ticket: $error")
                        }
                    )
                },
                isTicketClosed = isTicketClosed.value,
                errorMessage = errorMessage.value
            )
        }

        if (checkPermissions()) {
            Log.d("SOSActivity", "Permissions already granted.")
            bindCamera()
            scheduleVideoRecording()
        } else {
            permissionsLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                    // Uncomment and add Manifest.permission.RECORD_AUDIO if audio is needed.
                )
            )
        }
    }

    /**
     * Checks that CAMERA and WRITE_EXTERNAL_STORAGE permissions are granted.
     */
    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED &&
                // Only check WRITE_EXTERNAL_STORAGE if necessary.
                (if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q)
                    ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            == PackageManager.PERMISSION_GRANTED
                else true)
    }

    /**
     * Binds the VideoCapture use case (without a Preview) to the activity's lifecycle.
     */
    private fun bindCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            // Build a Recorder with the highest available quality.
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, videoCapture)
                Log.d("SOSActivity", "VideoCapture use case bound successfully.")
            } catch (exc: Exception) {
                Log.e("SOSActivity", "Use case binding failed: ${exc.message}", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * Schedules repeating video recordings.
     */
    private fun scheduleVideoRecording() {
        handler.post(object : Runnable {
            override fun run() {
                if (recording == null) {
                    Log.d("SOSActivity", "Starting new video recording session.")
                    startVideoRecording()
                    // Stop recording after the specified duration.
                    handler.postDelayed({
                        Log.d("SOSActivity", "Stopping video recording after duration.")
                        recording?.stop()
                    }, recordingDuration)
                }
                // Reschedule after the total cycle time.
                handler.postDelayed(this, recordingInterval)
            }
        })
    }

    /**
     * Starts a video recording session without opening any preview UI.
     */
    private fun startVideoRecording() {
        val outputFile = createVideoFile()
        Log.d("SOSActivity", "Preparing video recording. Output file: ${outputFile.absolutePath}")
        val outputOptions = FileOutputOptions.Builder(outputFile).build()
        recording = videoCapture?.output
            ?.prepareRecording(this, outputOptions)
            // Chain .withAudioEnabled() if you require audio.
            ?.start(ContextCompat.getMainExecutor(this)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        Log.d("SOSActivity", "Video recording started.")
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (!event.hasError()) {
                            Log.d("SOSActivity", "Video recording finalized successfully: ${outputFile.absolutePath}")
                            val captureTimestamp = System.currentTimeMillis()
                            uploadToFirebase(outputFile, firebaseUID!!, captureTimestamp)
                        } else {
                            Log.e("SOSActivity", "Video recording error: ${event.error}")
                        }
                        recording = null
                    }
                }
            }
    }

    /**
     * Creates a temporary file to store the recorded video.
     */
    private fun createVideoFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        return File.createTempFile("SOS_$timeStamp", ".mp4", storageDir)
    }

    /**
     * Uploads the recorded video file to Firebase Storage and sends its metadata to the backend.
     */
    private fun uploadToFirebase(videoFile: File, firebaseUID: String, captureTimestamp: Long) {
        val fileUri: Uri = Uri.fromFile(videoFile)
        val fileName = videoFile.name
        val videoRef: StorageReference = FirebaseStorage.getInstance().getReference("emergency_videos/$fileName")
        Log.d("SOS_TICKET", "Uploading video file: $fileName")
        videoRef.putFile(fileUri)
            .addOnSuccessListener {
                Log.d("SOS_TICKET", "Video file uploaded. Retrieving download URL...")
                videoRef.downloadUrl.addOnSuccessListener { uri ->
                    Log.d("SOS_TICKET", "Download URL retrieved: $uri")
                    val gsBucketUrl = generateGsBucketVideoUrl(fileName)
                    val videoData = VideoClipData(
                        url = uri.toString(),
                        timestamp = captureTimestamp
                    )
                    sendVideoClips(firebaseUID, listOf(videoData))
                    videoFile.delete() // Clean up local file.
                }
            }
            .addOnFailureListener {
                Log.e("SOS_TICKET", "Failed to upload video: ${it.message}")
            }
    }

    /**
     * Sends video clip metadata to the backend API.
     */
    private fun sendVideoClips(firebaseUID: String, videoDataList: List<VideoClipData>) {
        sosTicketId?.let { ticketId ->
            Log.d("SOS_TICKET", "Sending video clip data for SOS Ticket ID: $ticketId")
            lifecycleScope.launch {
                try {
                    val success = Api().sendVideoClips(ticketId, firebaseUID, videoDataList)
                    if (success) {
                        Log.d("SOS_TICKET", "Video clip data sent successfully to server.")
                    } else {
                        Log.e("SOS_TICKET", "Failed to send video clip data for ticket ID: $ticketId")
                    }
                } catch (e: Exception) {
                    Log.e("SOS_TICKET", "Error sending video clip data: ${e.localizedMessage}", e)
                }
            }
        } ?: run {
            Log.e("SOS_TICKET", "SOS Ticket ID is null. Cannot send video clip data.")
        }
    }

    /**
     * Generates a gs:// URL for the uploaded video file.
     */
    private fun generateGsBucketVideoUrl(fileName: String): String {
        val bucketName = "suraksha-kawach-151024.firebasestorage.app"
        val folderName = "emergency_videos"
        return "gs://$bucketName/$folderName/$fileName"
    }

    /**
     * Closes the SOS ticket by calling your API, stops background tasks, and navigates home.
     */
    private fun closeSOSTicket(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val api = Api()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val response = api.closeTicket(firebaseUID!!, sosTicketId!!)
                if (response) {
                    Log.d("SOSActivity", "SOS ticket closed successfully.")
                    stopAllBackgroundTasks()
                    navigateToHome(this@SOSActivity)
                    onSuccess()
                } else {
                    onError("Failed to close SOS ticket.")
                }
            } catch (e: Exception) {
                onError("Error closing SOS ticket: ${e.message}")
            }
        }
    }

    /**
     * Stops all background tasks.
     */
    private fun stopAllBackgroundTasks() {
        handler.removeCallbacksAndMessages(null)
        Log.d("SOSActivity", "Stopped all background tasks.")
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    /**
     * Retrieves the Firebase UID from the user session.
     */
    private fun getFirebaseUIDOrFallback(): String? {
        val sessionData = UserSessionManager.getSession(this)
        return sessionData["userId"]?.takeIf { it.isNotEmpty() }
    }
}

/**
 * Compose UI for SOS Screen.
 */
@Composable
fun SOSScreen(
    onCloseTicket: () -> Unit,
    isTicketClosed: Boolean,
    errorMessage: String?
) {
    val context = LocalContext.current
    val isLoading = remember { mutableStateOf(false) }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "SOS Sent Successfully!",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(bottom = 24.dp)
            )
            Text(
                text = "Help is on the way!",
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                modifier = Modifier.padding(bottom = 32.dp)
            )
            if (isTicketClosed) {
                Text(
                    text = "SOS Ticket closed successfully.",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            errorMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, color = MaterialTheme.colorScheme.error),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            Button(
                onClick = {
                    isLoading.value = true
                    onCloseTicket()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading.value
            ) {
                if (isLoading.value) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Text(text = "Stop SOS and Close Ticket")
                }
            }
            Button(
                onClick = { navigateToHome(context) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Text(text = "Go Back to Home")
            }
        }
    }
}

/**
 * Navigates back to HomeActivity.
 */
fun navigateToHome(context: Context) {
    val intent = Intent(context, HomeActivity::class.java)
    context.startActivity(intent)
    (context as? Activity)?.finish() // Finish the current activity
}