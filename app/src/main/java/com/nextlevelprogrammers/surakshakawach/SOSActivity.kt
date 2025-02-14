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
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.VideoRecordEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.*
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.nextlevelprogrammers.surakshakawach.api.Api
import com.nextlevelprogrammers.surakshakawach.api.VideoClipData
import com.nextlevelprogrammers.surakshakawach.ui.getCurrentTimestamp
import com.nextlevelprogrammers.surakshakawach.utils.UserSessionManager
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SOSActivity : ComponentActivity() {

    // --- Location Updates ---
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    // A dedicated handler for any scheduled coordinate tasks
    private val coordinateUpdateHandler = Handler(Looper.getMainLooper())

    // --- Video Recording with CameraX ---
    private lateinit var cameraExecutor: ExecutorService
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    // --- Firebase Storage Reference ---
    private lateinit var storageReference: StorageReference

    // --- Handler for scheduling tasks ---
    private val handler = Handler(Looper.getMainLooper())

    // --- Video recording settings ---
    private val recordingDuration = 15000L  // 15 seconds per clip
    private val recordingInterval = 25000L   // 25-second cycle (15 sec recording + 10 sec break)

    // --- SOS ticket and user info ---
    private var sosTicketId: String? = null
    private var firebaseUID: String? = null

    // Configure the quality selector for SD (480p)
//    val qualitySelector = QualitySelector.from(Quality.SD)
//    val recorder = Recorder.Builder()
//        .setQualitySelector(qualitySelector)
//        .build()


    // --- Permissions Launcher for CAMERA, STORAGE, and LOCATION ---
    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        Log.d("SOSActivity", "Permissions result: $permissions")
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Log.d("SOSActivity", "All required permissions granted.")
            bindCamera()
            scheduleVideoRecording()
            startUpdatingCoordinates()
        } else {
            Log.e("SOSActivity", "Required permissions not granted: $permissions")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        logNetworkStatus()

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

        // Initialize fusedLocationClient and location callback.
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    Log.d("SOSActivity", "Received location update: ${location.latitude}, ${location.longitude}")
                    updateCoordinates(location.latitude, location.longitude)
                }
            }
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Set up the Compose UI.
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
                    finish()
                },
                isTicketClosed = isTicketClosed.value,
                errorMessage = errorMessage.value
            )
        }

        // Check for required permissions.
        if (checkPermissions()) {
            // Additional check for audio permission
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // Handle the missing audio permission here if needed
                return
            }
            Log.d("SOSActivity", "Permissions already granted.")
            bindCamera()
            scheduleVideoRecording()
            startUpdatingCoordinates()
        } else {
            permissionsLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.RECORD_AUDIO
                )
            )
        }

        // Start the SOS background service.
        startSOSService()
    }

    private fun startSOSService() {
        val serviceIntent = Intent(this, SOSBackgroundService::class.java)
        serviceIntent.putExtra("sosTicketId", sosTicketId)
        ContextCompat.startForegroundService(this, serviceIntent)
        Log.d("SOSActivity", "SOS service started.")
    }

    private fun getFirebaseUIDOrFallback(): String? {
        val sessionData = UserSessionManager.getSession(this)
        val firebaseUID = sessionData["userId"]
        if (firebaseUID.isNullOrEmpty()) {
            Log.e("SOSActivity", "Firebase UID not found in session.")
            return null
        }
        return firebaseUID
    }

    /**
     * Checks that CAMERA, WRITE_EXTERNAL_STORAGE, and LOCATION permissions are granted.
     * (For Android Q and above, WRITE_EXTERNAL_STORAGE is not required if using getExternalFilesDir().)
     */
    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED &&
                (if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q)
                    ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            == PackageManager.PERMISSION_GRANTED
                else true) &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
    }

    /**
     * Binds the VideoCapture use case (without a preview UI) to the activity's lifecycle.
     */
    private fun bindCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(Quality.SD))
                    .build()

                videoCapture = VideoCapture.withOutput(recorder)
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                // Unbind before rebinding
                cameraProvider.unbindAll()

                // Bind lifecycle
                cameraProvider.bindToLifecycle(this, cameraSelector, videoCapture)

                Log.d("SOSActivity", "VideoCapture use case bound successfully.")
            } catch (exc: Exception) {
                Log.e("SOSActivity", "Failed to bind VideoCapture use case: ${exc.message}", exc)
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
                    handler.postDelayed({
                        Log.d("SOSActivity", "Stopping video recording after duration.")
                        recording?.stop()
                    }, recordingDuration)
                }
                handler.postDelayed(this, recordingInterval)
            }
        })
    }

    /**
     * Starts a video recording session (without any preview UI) and listens for recording events.
     */
    private fun startVideoRecording() {
        // Check if RECORD_AUDIO permission is granted
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e("SOSActivity", "RECORD_AUDIO permission is not granted.")
            // Optionally, request the permission or inform the user.
            return
        }

        val outputFile = createVideoFile()
//        Log.d("SOSActivity", "Preparing video recording. Output file: ${outputFile.absolutePath}")
        val outputOptions = FileOutputOptions.Builder(outputFile).build()

        recording = videoCapture?.output
            ?.prepareRecording(this, outputOptions)
            ?.withAudioEnabled() // Safe to call now, as permission has been checked.
            ?.start(ContextCompat.getMainExecutor(this)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
//                        Log.d("SOSActivity", "Video recording started.")
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (!event.hasError()) {
//                            Log.d("SOSActivity", "Video recording finalized: ${outputFile.absolutePath}")
                            val captureTimestamp = System.currentTimeMillis()
                            uploadToFirebase(outputFile, firebaseUID!!, captureTimestamp)
                        } else {
//                            Log.e("SOSActivity", "Video recording error: ${event.error}")
                        }
                        recording = null
                    }
                }
            }
    }

    /**
     * Creates a temporary video file.
     */
    private fun createVideoFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        return File.createTempFile("SOS_$timeStamp", ".mp4", storageDir)
    }

    private fun logNetworkStatus() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val activeNetwork = cm.activeNetworkInfo
        if (activeNetwork != null && activeNetwork.isConnected) {
            Log.d("NETWORK_STATUS", "Connected to ${if (activeNetwork.type == android.net.ConnectivityManager.TYPE_WIFI) "Wi-Fi" else "Mobile Data"}")
        } else {
            Log.e("NETWORK_STATUS", "No Internet Connection Detected!")
        }
    }

    private fun uploadToFirebase(videoFile: File, firebaseUID: String, captureTimestamp: Long) {
        logNetworkStatus() // Check current network status before uploading

        val fileUri: Uri = Uri.fromFile(videoFile)
        val fileName = videoFile.name
        val videoRef: StorageReference = FirebaseStorage.getInstance().getReference("emergency_videos/$fileName")

        Log.d("SOS_TICKET", "Uploading video file: $fileName")

        val uploadTask = videoRef.putFile(fileUri)

        // Track progress
        uploadTask.addOnProgressListener { taskSnapshot ->
            val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount)
            Log.d("SOS_TICKET", "Upload Progress: $progress%")
        }

        // Handle successful upload
        uploadTask.addOnSuccessListener {
            Log.d("SOS_TICKET", "Video file uploaded successfully. Retrieving download URL...")

            videoRef.downloadUrl.addOnSuccessListener { uri ->
                Log.d("SOS_TICKET", "Download URL retrieved: $uri")
                val gsBucketUrl = generateGsBucketVideoUrl(fileName)

                val videoData = VideoClipData(
                    url = uri.toString(),
                    timestamp = captureTimestamp,
                    gsBucketUrl = gsBucketUrl
                )

                sendVideoClips(firebaseUID, listOf(videoData))
                videoFile.delete() // Clean up the local file.
            }.addOnFailureListener { uriError ->
                Log.e("SOS_TICKET", "Failed to get download URL: ${uriError.message}", uriError)
            }
        }

        // Handle failures & retry
        uploadTask.addOnFailureListener { exception ->
            Log.e("SOS_TICKET", "Failed to upload video: ${exception.message}", exception)

            // Retry upload with a delay if mobile network is unstable
            Handler(Looper.getMainLooper()).postDelayed({
                Log.d("SOS_TICKET", "Retrying video upload...")
                uploadToFirebase(videoFile, firebaseUID, captureTimestamp)
            }, 5000) // Retry after 5 seconds
        }

        // Handle paused uploads (resumable uploads)
        uploadTask.addOnPausedListener {
            Log.d("SOS_TICKET", "Upload paused. Resuming...")
            uploadTask.resume()
        }
    }

    /**
     * Sends the video clip metadata to your backend API.
     */
    private fun sendVideoClips(firebaseUID: String, videoDataList: List<VideoClipData>) {
        sosTicketId?.let { ticketId ->
            Log.d("SOS_TICKET", "Sending video clip data for SOS Ticket ID: $ticketId")
            lifecycleScope.launch {
                try {
                    val modifiedVideoData = videoDataList.map { videoData ->
                        // Extract the file name from the URL (if required) for the gs:// path
                        videoData.copy(
                            gsBucketUrl = generateGsBucketVideoUrl(
                                videoData.url.substringAfterLast("/")
                            )
                        )
                    }

                    Log.d("SOS_TICKET", "Modified Video Data: $modifiedVideoData")

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
     * Starts location updates to continuously send coordinates to the backend.
     */
    private fun startUpdatingCoordinates() {
        val locationRequest = LocationRequest.create().apply {
            interval = 5000      // 5 seconds
            fastestInterval = 2000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
        Log.d("SOS_TICKET", "Requesting location updates...")
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("SOS_TICKET", "Location permissions are not granted.")
            return
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    /**
     * Sends updated coordinates to the backend.
     */
    private fun updateCoordinates(latitude: Double, longitude: Double) {
        Log.d("SOS_TICKET", "Updating coordinates: lat=$latitude, lon=$longitude, ticketId=$sosTicketId")
        val uid = getFirebaseUIDOrFallback() ?: run {
            Log.e("SOS_TICKET", "Firebase UID is null. Cannot update coordinates.")
            return
        }
        val timestamp = getCurrentTimestamp()
        lifecycleScope.launch {
            val success = Api().updateCoordinates(
                firebaseUID = uid,
                ticketId = sosTicketId!!,
                latitude = latitude.toString(),
                longitude = longitude.toString(),
                timestamp = timestamp
            )
            if (success) {
                Log.d("SOS_TICKET", "Coordinates updated for ticket ID: $sosTicketId")
            } else {
                Log.e("SOS_TICKET", "Failed to update coordinates for ticket ID: $sosTicketId")
            }
        }
    }

    /**
     * Stops sending location updates.
     */
    private fun stopUpdatingCoordinates() {
        // Remove callbacks from our dedicated coordinate handler.
        coordinateUpdateHandler.removeCallbacksAndMessages(null)
        // Also remove location updates from fusedLocationClient.
        if (::fusedLocationClient.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
            Log.d("SOS_TICKET", "Stopped sending coordinates updates.")
        } else {
            Log.d("SOS_TICKET", "FusedLocationClient not initialized; no location updates to stop.")
        }
    }

    /**
     * Closes the SOS ticket by calling your API, stops background tasks, and navigates home.
     */
    private fun closeSOSTicket(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val firebaseUID = getFirebaseUIDOrFallback()
        val api = Api()
        if (firebaseUID != null && sosTicketId != null) {
            Log.d("SOS_TICKET", "Attempting to close ticket with ID: $sosTicketId for UID: $firebaseUID")
            lifecycleScope.launch {
                try {
                    val success = Api().closeTicket(firebaseUID, sosTicketId!!)
                    if (success) {
                        onSuccess()
                        Log.d("SOS_TICKET", "Ticket closed successfully for UID: $firebaseUID, Ticket ID: $sosTicketId")
                        stopSendingLocation()
                        navigateToHome(this@SOSActivity)
                    } else {
                        onError("Failed to close the ticket.")
                        Log.e("SOS_TICKET", "Failed to close ticket for UID: $firebaseUID, Ticket ID: $sosTicketId")
                    }
                } catch (e: Exception) {
                    Log.e("SOS_TICKET", "Error closing ticket for UID: $firebaseUID - ${e.localizedMessage}")
                    onError("Error closing the ticket: ${e.localizedMessage}")
                }
            }
        } else {
            onError("No active ticket found or user is not logged in.")
            Log.e("SOS_TICKET", "No active ticket found or user is not logged in.")
        }
    }

    private fun stopSendingLocation() {
        stopUpdatingCoordinates() // Already safely stops both handler callbacks and location updates.
    }

    override fun onDestroy() {
        val sharedDir = File(applicationContext.filesDir, "shared")
        if (sharedDir.exists()) {
            sharedDir.deleteRecursively()
        }
        super.onDestroy()
        // Stop all background tasks
        stopUpdatingCoordinates()
        if (::cameraExecutor.isInitialized) {
            cameraExecutor.shutdown()
            Log.d("SOS_TICKET", "Camera executor shutdown successfully.")
        } else {
            Log.e("SOS_TICKET", "Camera executor was not initialized, skipping shutdown.")
        }
        handler.removeCallbacksAndMessages(null)
    }
}

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
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, color = MaterialTheme.colorScheme.primary),
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
                onClick = {
                    stopUpdatingCoordinates()
                    navigateToHome(context)
                },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) {
                Text(text = "Go Back to Home")
            }
        }
    }
}

private val coordinateUpdateHandler = Handler(Looper.getMainLooper())

// Function to stop sending coordinate updates when SOS is stopped
private fun stopUpdatingCoordinates() {
    coordinateUpdateHandler.removeCallbacksAndMessages(null)
    Log.d("SOSActivity", "Stopped sending coordinates updates.")
}

/**
 * Navigates back to HomeActivity.
 */
private fun navigateToHome(context: Context) {
    val intent = Intent(context, HomeActivity::class.java)
    context.startActivity(intent)
    (context as? Activity)?.finish()
}