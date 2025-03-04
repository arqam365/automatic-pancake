package com.nextlevelprogrammers.surakshakawach.uidesign

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import com.nextlevelprogrammers.surakshakawach.data.remote.ApiService
import com.nextlevelprogrammers.surakshakawach.emergency_videos.VideoRecorder
import com.nextlevelprogrammers.surakshakawach.model.TicketResponse
import com.nextlevelprogrammers.surakshakawach.utils.LocationUtils
import io.ktor.client.call.body
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun SOSGranted(context: Context, userId: String) {
    val locationUtils = remember { LocationUtils(context) }
    val apiService = remember { ApiService() }
    val videoRecorder = remember { VideoRecorder(context) }
    var locationText by remember { mutableStateOf("Fetching location...") }
    var ticketId by remember { mutableStateOf<String?>(null) }
    var ticketStatus by remember { mutableStateOf("Pending") }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        videoRecorder.initializeCamera()

        videoRecorder.startContinuousRecording(userId) { videoUrl, bucketUrl ->
            Log.d("SOSGranted", "✅ Video Uploaded, Sending to API")

            ticketId?.let { ticket ->
                coroutineScope.launch {
                    val response = apiService.uploadVideo(userId, ticket, videoUrl, bucketUrl)
                    if (response != null) {
                        Log.d("SOSGranted", "✅ Video Sent to API")
                    } else {
                        Log.e("SOSGranted", "❌ Failed to Send Video to API")
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        locationUtils.getLastKnownLocation { latitude, longitude ->
            locationText = "Lat: $latitude, Long: $longitude"
            Log.d("SOSGranted", "Live Location: Lat: $latitude, Long: $longitude")

            // **Step 1: Create SOS Ticket**
            coroutineScope.launch {
                val ticketData = createSOS(apiService, userId, latitude, longitude)
                ticketId = ticketData?.ticketId
                ticketStatus = ticketData?.status ?: "Unknown"
                Log.d("SOSGranted", "✅ Ticket Created: ID = $ticketId, Status = $ticketStatus")
            }
        }
    }

    LaunchedEffect(ticketId) {
        ticketId?.let { id ->
            while (ticketStatus == "Active") {
                locationUtils.getLastKnownLocation { latitude, longitude ->
                    locationText = "Lat: $latitude, Long: $longitude"
                    Log.d("SOSGranted", "Updating SOS Location: Lat: $latitude, Long: $longitude")

                    // **Step 2: Update Location Every 5 Sec**
                    coroutineScope.launch {
                        updateLocation(apiService, userId, id, latitude, longitude)
                    }
                }
                delay(5000) // Fetch and send updated location every 5 seconds
            }
        }
    }

    Box(contentAlignment = Alignment.Center) {
        Text("Status: $ticketStatus\n$locationText")
    }
}

@RequiresApi(Build.VERSION_CODES.O)
suspend fun createSOS(apiService: ApiService, userId: String, latitude: Double, longitude: Double): TicketResponse? {
    return try {
        val response = apiService.createSOS(userId, latitude, longitude)
        if (response.status.isSuccess()) {
            val responseBody = response.body<String>()
            Log.d("SOSGranted", "✅ SOS Created: Response -> $responseBody")

            // Extract `ticket_id` and `status` from JSON response
            extractTicketData(responseBody)
        } else {
            Log.e("SOSGranted", "❌ Failed to Create SOS: ${response.status}")
            null
        }
    } catch (e: Exception) {
        Log.e("SOSGranted", "❌ Error: ${e.localizedMessage}")
        null
    }
}

@RequiresApi(Build.VERSION_CODES.O)
suspend fun updateLocation(apiService: ApiService, userId: String, ticketId: String, latitude: Double, longitude: Double) {
    try {
        val response = apiService.updateLocation(userId, ticketId, latitude, longitude)
        val responseBody = response.body<String>() // ✅ Capture response body for debugging

        if (response.status.isSuccess()) {
            Log.d("SOSGranted", "✅ Location Updated Successfully!")
        } else {
            Log.e("SOSGranted", "❌ Failed to Update Location: ${response.status} - Response: $responseBody")
        }
    } catch (e: Exception) {
        Log.e("SOSGranted", "❌ Error: ${e.localizedMessage}")
    }
}

// **Helper function to extract `ticket_id` and `status` from API response**
fun extractTicketData(responseBody: String): TicketResponse? {
    return try {
        val jsonObject = Json.parseToJsonElement(responseBody).jsonObject
        val ticketData = jsonObject["data"]?.jsonObject
        val ticketId = ticketData?.get("ticket_id")?.jsonPrimitive?.content
        val status = ticketData?.get("status")?.jsonPrimitive?.content

        if (ticketId != null && status != null) {
            TicketResponse(ticketId, status)
        } else {
            null
        }
    } catch (e: Exception) {
        Log.e("SOSGranted", "❌ JSON Parsing Error: ${e.localizedMessage}")
        null
    }
}