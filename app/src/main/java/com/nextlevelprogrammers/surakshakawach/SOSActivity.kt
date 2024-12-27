package com.nextlevelprogrammers.surakshakawach

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.nextlevelprogrammers.surakshakawach.api.Api
import com.nextlevelprogrammers.surakshakawach.api.ClipData
import com.nextlevelprogrammers.surakshakawach.api.ImageData
import com.nextlevelprogrammers.surakshakawach.ui.getCurrentTimestamp
import com.nextlevelprogrammers.surakshakawach.utils.UserSessionManager
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SOSActivity : ComponentActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private val handler = Handler(Looper.getMainLooper())
    private var sosTicketId: String? = null
    private var dynamicInterval: Long = 10000 // Capture every 10 seconds
    private var isRecordingAudio = false
    private val audioRecordingInterval: Long = 50000 // 40 seconds interval
    private val audioRecordingDuration: Long = 15000 // 15 seconds duration
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var isCapturingImages = false
    private var mediaRecorder: MediaRecorder? = null
    private lateinit var locationCallback: LocationCallback
    private val imageDataList = mutableListOf<ImageData>()
    private var lastCaptureTimestamp = 0L // Store the last capture timestamp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get the SOS ticket ID passed from HomeScreen
        sosTicketId = intent.getStringExtra("sosTicketId")

        // Make sure we have a valid ticket ID
        if (sosTicketId == null) {
            Log.e("SOS_TICKET", "SOS Ticket ID is null! Cannot proceed.")
            finish() // Close the activity if no valid ticket ID
            return
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    // Update coordinates when the location changes
                    updateCoordinates(location.latitude, location.longitude)
                }
            }
        }

        // Initialize the camera executor
        cameraExecutor = Executors.newSingleThreadExecutor()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        checkMultiplePermissionsAndStartUpdates()

        // Start the service in foreground mode for background execution
        startSOSService()

        setContent {
            val isTicketClosed = remember { mutableStateOf(false) }
            val errorMessage = remember { mutableStateOf<String?>(null) }

            SOSScreen(
                onCloseTicket = {
                    stopSOSService(
                        onSuccess = {
                            isTicketClosed.value = true // Set ticket closed state to true on success
                            Log.d("SOSActivity", "SOS ticket closed successfully.")
                        },
                        onError = { error ->
                            errorMessage.value = error // Set error message on failure
                            Log.e("SOSActivity", "Failed to close SOS ticket: $error")
                        }
                    )
                    finish() // Close the activity after stopping the service
                },
                isTicketClosed = isTicketClosed.value, // Pass state to the screen
                errorMessage = errorMessage.value // Pass error message to the screen
            )
        }

        // Start background tasks
        startCamera()
        startAudioRecordingAtIntervals()
        startImageCapture()
        startUpdatingCoordinates()
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

    private fun checkMultiplePermissionsAndStartUpdates() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA
        )

        when {
            permissions.all {
                ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            } -> {
                // All permissions are granted, proceed
                startUpdatingCoordinates()
                startCamera()
            }
            else -> {
                // Request multiple permissions
                requestMultiplePermissionsLauncher.launch(permissions)
            }
        }
    }

    private fun startSOSService() {
        // Start foreground service for continuous background execution
        val serviceIntent = Intent(this, SOSBackgroundService::class.java)
        serviceIntent.putExtra("sosTicketId", sosTicketId)
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun stopSOSService(
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        // Stop image capture
        stopCapturingImages()
        stopAudioRecordingAtIntervals()
        fusedLocationClient.removeLocationUpdates(locationCallback)

        // Close the ticket with API call
        sosTicketId?.let {
            closeSOSTicket(
                onSuccess = {
                    onSuccess() // Signal success after closing
                    navigateToHome(this@SOSActivity)
                },
                onError = { error ->
                    onError(error) // Signal error if closing fails
                }
            )
        } ?: run {
            onError("SOS Ticket ID is null.")
        }

        // Stop the SOS background service
        val stopIntent = Intent(this, SOSBackgroundService::class.java)
        stopService(stopIntent)
        Log.d("SOSActivity", "SOS service stopped.")
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // ImageCapture instance
            imageCapture = ImageCapture.Builder()
                .setTargetResolution(Size(1080, 1080)) // Set a desired resolution
                .build()

            // Select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // Unbind any previous use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to the camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, imageCapture
                )

                // Start image capture only after the camera is bound
                startImageCapture()

            } catch (exc: Exception) {
                Log.e("CameraX", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun startImageCapture() {
        isCapturingImages = true
        val imageCaptureRunnable = object : Runnable {
            override fun run() {
                if (isCapturingImages) {
                    val startTime = System.currentTimeMillis()

                    captureImage {
                        val endTime = System.currentTimeMillis()
                        val processingTime = endTime - startTime

                        // Set next interval based on processing time with a buffer
                        dynamicInterval = processingTime + 2000 // Add 2 seconds buffer
                        Log.d("CameraX", "Dynamic interval adjusted to: $dynamicInterval ms")

                        // Schedule the next capture
                        handler.postDelayed(this, dynamicInterval)
                    }
                }
            }
        }
        handler.post(imageCaptureRunnable)
    }

    private fun captureImage(onComplete: () -> Unit) {
        val currentTimestamp = System.currentTimeMillis()

        // Check if the interval has passed since the last capture
        if (currentTimestamp - lastCaptureTimestamp < dynamicInterval) {
            Log.d("CameraX", "Skipping capture to respect interval")
            onComplete()
            return
        }

        lastCaptureTimestamp = currentTimestamp

        val imageCapture = imageCapture ?: return onComplete()
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val photoFile = File(externalMediaDirs.first(), "IMG_$timestamp.jpg")
        val firebaseUID = getFirebaseUIDOrFallback() ?: ""

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Log.d("CameraX", "Photo captured: ${photoFile.absolutePath}")
                    val compressedFile = compressImage(photoFile)
                    uploadImageToFirebase(compressedFile, firebaseUID, currentTimestamp)
                    onComplete() // Notify completion
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraX", "Photo capture failed: ${exception.message}", exception)
                    onComplete() // Notify completion
                }
            }
        )
    }

    private fun compressImage(file: File): File {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        val targetSize = 100 * 1024 // 100 KB
        var quality = 100
        var compressedFile = file

        do {
            val byteArrayOutputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, byteArrayOutputStream)
            val compressedData = byteArrayOutputStream.toByteArray()

            compressedFile = File(file.parent, "COMPRESSED_${file.name}")
            FileOutputStream(compressedFile).use {
                it.write(compressedData)
                it.flush()
            }

            quality -= 5
        } while (compressedFile.length() > targetSize && quality > 0)

        Log.d("CameraX", "Image compressed to: ${compressedFile.length() / 1024} KB")
        return compressedFile
    }

    private fun uploadImageToFirebase(file: File, firebaseUID: String, captureTimestamp: Long) {
        val fileUri: Uri = Uri.fromFile(file)
        val fileName = file.name
        val storageReference = FirebaseStorage.getInstance()
            .getReference("emergency-images/$fileName")

        storageReference.putFile(fileUri)
            .addOnSuccessListener {
                storageReference.downloadUrl.addOnSuccessListener { uri ->
                    Log.d("SOS_TICKET", "Image uploaded successfully: $uri")

                    val gsBucketUrl = generateGsBucketImagesUrl(fileName)

                    val imageData = ImageData(
                        url = uri.toString(),
                        timestamp = captureTimestamp,
                        gsBucketUrl = gsBucketUrl
                    )
                    imageDataList.add(imageData)

                    sendImageDataToBackend(firebaseUID, imageDataList)

                    file.delete()
                }
            }
            .addOnFailureListener {
                Log.e("SOS_TICKET", "Failed to upload image: ${it.message}")
            }
    }
    private fun sendImageDataToBackend(firebaseUID: String, imagesData: List<ImageData>) {
        if (sosTicketId != null) {
            Log.d("SOS_TICKET", "Preparing to send image data to backend. SOS Ticket ID: $sosTicketId")

            lifecycleScope.launch {
                try {
                    val modifiedImagesData = imagesData.map { imageData ->
                        imageData.copy(
                            gsBucketUrl = generateGsBucketImagesUrl(imageData.url)
                        )
                    }

                    Log.d("SOS_TICKET", "Modified Images Data: $modifiedImagesData")

                    val success = Api().sendImages(sosTicketId!!, firebaseUID, modifiedImagesData)
                    if (success) {
                        Log.d("SOS_TICKET", "Image data sent successfully to the server")
                    } else {
                        Log.e("SOS_TICKET", "Failed to send image data for ticket ID: $sosTicketId")
                    }
                } catch (e: Exception) {
                    Log.e("SOS_TICKET", "Error while sending image data: ${e.localizedMessage}", e)
                }
            }
        } else {
            Log.e("SOS_TICKET", "Cannot send image data. SOS ticket ID is null.")
        }
    }

    private fun generateGsBucketImagesUrl(url: String): String {
        val bucketName = "suraksha-kawach-24ff7.appspot.com"
        val folderName = "emergency-images"
        // Extract the file name without the redundant folder prefix
        val fileName = url.substringAfterLast("/") // Extract file name
            .substringBefore("?") // Remove query parameters
            .replace("emergency-images%2F", "") // Remove "emergency-images%2F" if present
        return "gs://$bucketName/$folderName/$fileName"
    }

    private fun generateGsBucketAudioUrl(url: String): String {
        val bucketName = "suraksha-kawach-24ff7.appspot.com"
        val folderName = "emergency-audio"
        // Extract the file name without the redundant folder prefix
        val fileName = url.substringAfterLast("/") // Extract file name
            .substringBefore("?") // Remove query parameters
            .replace("emergency-audio%2F", "") // Remove "emergency-audio%2F" if present
        return "gs://$bucketName/$folderName/$fileName"
    }

    private fun startAudioRecordingAtIntervals() {
        val audioRecordingRunnable = object : Runnable {
            override fun run() {
                if (isRecordingAudio) {
                    startAudioRecording()
                    handler.postDelayed({ stopAudioRecording() }, audioRecordingDuration)
                    handler.postDelayed(this, audioRecordingInterval)
                }
            }
        }
        isRecordingAudio = true
        handler.post(audioRecordingRunnable)
    }

    private fun startAudioRecording() {
        val timestamp = System.currentTimeMillis()
        val formattedTimestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val audioFile = File(externalMediaDirs.first(), "AUDIO_$formattedTimestamp.mp3")
        val firebaseUID = FirebaseAuth.getInstance().currentUser?.uid ?: ""

        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(audioFile.absolutePath)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            try {
                prepare()
                start()
                Log.d("SOSActivity", "Audio recording started: ${audioFile.absolutePath}")
            } catch (e: IOException) {
                Log.e("SOSActivity", "Audio recording failed: ${e.message}")
            }
        }

        handler.postDelayed({
            stopAudioRecording()
            uploadAudioToFirebase(audioFile, firebaseUID, timestamp)
        }, audioRecordingDuration)
    }

    private fun uploadAudioToFirebase(audioFile: File, firebaseUID: String, captureTimestamp: Long) {
        val firebaseUID = getFirebaseUIDOrFallback() ?: return
        val fileUri: Uri = Uri.fromFile(audioFile)
        val fileName = audioFile.name
        val storageReference = FirebaseStorage.getInstance()
            .getReference("emergency-audio/${audioFile.name}")

        storageReference.putFile(fileUri)
            .addOnSuccessListener {
                Log.d("SOS_TICKET", "Audio uploaded successfully to Firebase Storage.")

                // Get the audio file URL from Firebase
                storageReference.downloadUrl.addOnSuccessListener { uri ->
                    val audioUrl = uri.toString()
                    Log.d("SOS_TICKET", "Audio URL retrieved: $audioUrl")

                    val gsBucketUrl = generateGsBucketAudioUrl(fileName)

                    // Create a ClipData object with URL and timestamp
                    val clipData = ClipData(url = audioUrl, timestamp = captureTimestamp, gsBucketUrl = gsBucketUrl)
                    sendClipsDataToBackend(firebaseUID, listOf(clipData))

                    audioFile.delete()
                }.addOnFailureListener {
                    Log.e("SOS_TICKET", "Failed to get audio URL: ${it.message}")
                }
            }
            .addOnFailureListener {
                Log.e("SOS_TICKET", "Failed to upload audio: ${it.message}")
            }
    }

    private fun sendClipsDataToBackend(firebaseUID: String, clipsData: List<ClipData>) {
        if (sosTicketId != null) {
            Log.d("SOS_TICKET", "Preparing to send audio clip data to backend. SOS Ticket ID: $sosTicketId")

            lifecycleScope.launch {
                try {
                    val modifiedClipsData = clipsData.map { clipData ->
                        clipData.copy(
                            gsBucketUrl = generateGsBucketAudioUrl(clipData.url)
                        )
                    }

                    Log.d("SOS_TICKET", "Modified Clips Data: $modifiedClipsData")


                    val success = Api().sendAudioClips(sosTicketId!!, firebaseUID, modifiedClipsData)
                    if (success) {
                        Log.d("SOS_TICKET", "Audio clip data sent successfully to the server")
                    } else {
                        Log.e("SOS_TICKET", "Failed to send audio clip data for ticket ID: $sosTicketId")
                    }
                } catch (e: Exception) {
                    Log.e("SOS_TICKET", "Error while sending audio clip data: ${e.localizedMessage}", e)
                }
            }
        } else {
            Log.e("SOS_TICKET", "Cannot send audio clip data. SOS ticket ID is null.")
        }
    }

    private fun startUpdatingCoordinates() {
        val locationRequest = LocationRequest.create().apply {
            interval = 5000
            fastestInterval = 2000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Permissions are not granted, so don't start updates
            Log.e("SOSActivity", "Location permissions are not granted.")
            return
        }

        // Request location updates
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private val requestMultiplePermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineLocationGranted || coarseLocationGranted) {
            // At least one location permission granted
            startUpdatingCoordinates()
        } else {
            // Location permission was denied
            Log.e("SOSActivity", "Location permissions denied.")
        }
    }

    private fun updateCoordinates(latitude: Double, longitude: Double) {
        val api = Api()

        // Check if the SOS ticket ID exists
        if (sosTicketId != null) {
            // Update the coordinates for the existing ticket
            lifecycleScope.launch {
                val success = api.updateCoordinates(
                    firebaseUID = getFirebaseUIDOrFallback() ?: return@launch,
                    ticketId = sosTicketId!!,
                    latitude = latitude.toString(),
                    longitude = longitude.toString(),
                    timestamp = getCurrentTimestamp()
                )

                if (success) {
                    Log.d("SOS_TICKET", "Coordinates updated for ticket ID: $sosTicketId")
                } else {
                    Log.e("SOS_TICKET", "Failed to update coordinates for ticket ID: $sosTicketId")
                }
            }
        } else {
            // Log an error if sosTicketId is null, indicating no active SOS ticket
            Log.e("SOS_TICKET", "SOS ticket ID is null. Cannot update coordinates.")
        }
    }

    private fun stopSendingLocation() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        Log.d("SOSActivity", "Stopped sending coordinates updates.")
    }


    private fun stopAudioRecording() {
        mediaRecorder?.apply {
            stop()
            release()
        }
        mediaRecorder = null
        Log.d("SOSActivity", "Audio recording stopped.")
    }

    private fun stopCapturingImages() {
        isCapturingImages = false
        handler.removeCallbacksAndMessages(null)
        Log.d("SOSActivity", "Stopped capturing images.")
    }

    private fun stopAudioRecordingAtIntervals() {
        isRecordingAudio = false
        handler.removeCallbacksAndMessages(null)
        stopAudioRecording()
    }


    private fun closeSOSTicket(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val firebaseUID = getFirebaseUIDOrFallback()
        val api = Api()

        if (firebaseUID != null && sosTicketId != null) { // Ensure we have a ticket ID
            Log.d("SOSActivity", "Attempting to close ticket with ID: $sosTicketId for UID: $firebaseUID")

            lifecycleScope.launch {
                try {
                    // Close the ticket using the stored `sosTicketId`
                    val success = api.closeTicket(firebaseUID, sosTicketId!!)

                    if (success) {
                        onSuccess()
                        Log.d("SOSActivity", "Ticket closed successfully for UID: $firebaseUID, Ticket ID: $sosTicketId")

                        // Stop background processes and go back to the home screen
                        stopSendingLocation()
                        navigateToHome(this@SOSActivity)
                    } else {
                        onError("Failed to close the ticket.")
                        Log.e("SOSActivity", "Failed to close the ticket for UID: $firebaseUID, Ticket ID: $sosTicketId")
                    }
                } catch (e: Exception) {
                    Log.e("SOSActivity", "Error while closing the ticket for UID: $firebaseUID - ${e.localizedMessage}")
                    onError("Error while closing the ticket: ${e.localizedMessage}")
                }
            }
        } else {
            onError("No active ticket found or user is not logged in.")
            Log.e("SOSActivity", "No active ticket found or user is not logged in.")
        }
    }



    override fun onDestroy() {
        val sharedDir = File(applicationContext.filesDir, "shared")
        if (sharedDir.exists()) {
            sharedDir.deleteRecursively()
        }
        super.onDestroy()

        // Stop all background tasks
        stopCapturingImages()
        stopAudioRecordingAtIntervals()
        stopUpdatingCoordinates()

        // Shutdown camera executor
        if (::cameraExecutor.isInitialized) {
            cameraExecutor.shutdown()
            Log.d("SOSActivity", "Camera executor shutdown successfully.")
        } else {
            Log.e("SOSActivity", "Camera executor was not initialized, skipping shutdown.")
        }

        // Remove all handler callbacks
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
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.padding(bottom = 24.dp)
            )

            Text(
                text = "Help is on the way!",
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 18.sp
                ),
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
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            // Stop SOS and Close Ticket button with loading state
            Button(
                onClick = {
                    isLoading.value = true // Set loading state
                    onCloseTicket() // Trigger the close operation
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading.value // Disable during loading
            ) {
                if (isLoading.value) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp)
                    ) // Show loading spinner
                } else {
                    Text(text = "Stop SOS and Close Ticket")
                }
            }

            // Return to Home button
            Button(
                onClick = {
                    stopUpdatingCoordinates()
                    navigateToHome(context)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
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

// Function to navigate back to the HomeActivity
private fun navigateToHome(context: Context) {
    val intent = Intent(context, HomeActivity::class.java)
    context.startActivity(intent)
    (context as? Activity)?.finish() // Finish the current activity
}