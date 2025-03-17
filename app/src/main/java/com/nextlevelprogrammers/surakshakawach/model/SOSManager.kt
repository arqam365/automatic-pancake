package com.nextlevelprogrammers.surakshakawach.model


import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.nextlevelprogrammers.surakshakawach.data.remote.ApiService
import com.nextlevelprogrammers.surakshakawach.emergency_videos.VideoRecorder
import com.nextlevelprogrammers.surakshakawach.utils.LocationUtils
import io.ktor.client.call.body
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SOSManager(
    private val context: Context,
    private val userId: String,
    private val apiService: ApiService,
    private val coroutineScope: CoroutineScope
) {
    private val locationUtils = LocationUtils(context)
    private val videoRecorder = VideoRecorder(context)

    private var ticketId: String? = null
    private var ticketStatus: String = "Pending"
    private var locationUpdateJob: Job? = null

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun startSOS(onSOSCreated: (Boolean) -> Unit) {
        locationUtils.getLastKnownLocation { latitude, longitude ->
            coroutineScope.launch {
                val ticketData = createTicket(latitude, longitude)
                ticketId = ticketData?.ticketId
                ticketStatus = ticketData?.status ?: "Unknown"
                Log.d("SOSManager", "Creating SOS Ticket: userId=$userId, lat=$latitude, long=$longitude, ticketStatus=$ticketStatus")
                if (ticketId != null) {
                    Log.d("SOSManager", "✅ SOS Ticket Created: $ticketId")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        startVideoRecording()
                    }
                    startLocationUpdates()
                    onSOSCreated(true)
                } else {
                    Log.e("SOSManager", "❌ Failed to create SOS ticket")
                    onSOSCreated(false)
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun startVideoRecording() {
        videoRecorder.initializeCamera()
        videoRecorder.startContinuousRecording { videoUrl, bucketUrl ->
            ticketId?.let { id ->
                coroutineScope.launch {
                    val response = apiService.uploadVideo(userId, id, videoUrl, bucketUrl)
                    if (response != null) {
                        Log.d("SOSManager", "✅ Video uploaded and sent to API")
                    } else {
                        Log.e("SOSManager", "❌ Failed to send video")
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startLocationUpdates() {
        locationUpdateJob = coroutineScope.launch {
            while (ticketStatus == "Active") {
                locationUtils.getLastKnownLocation { latitude, longitude ->
                    coroutineScope.launch {
                        updateLocation(latitude, longitude)
                    }
                }
                delay(5000) // Update every 5 seconds
            }
        }
    }

    suspend fun stopSOS(onComplete: (Boolean) -> Unit) {
        if (ticketId == null) {
            Log.e("SOSManager", "❌ No active ticket to close!")
            onComplete(false)
            return
        }

        Log.d("SOSManager", "🛑 Stopping video recording")
        videoRecorder.stopRecording()
        videoRecorder.closeCamera()

        Log.d("SOSManager", "🛑 Stopping location updates")
        locationUpdateJob?.cancel()

        val isClosed = apiService.closeTicket(userId, ticketId!!)
        if (isClosed) {
            Log.d("SOSManager", "✅ SOS Ticket Closed")
        } else {
            Log.e("SOSManager", "❌ Failed to close SOS Ticket")
        }

        onComplete(isClosed)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun createTicket(latitude: Double, longitude: Double): TicketResponse? {
        return try {
            val response = apiService.createSOS(userId, latitude, longitude)
            val responseBody = response.body<String>()
            if (response.status.isSuccess()) {
                val responseBodyS = response.body<String>()
                Log.d("SOSManager", "✅ SOS Created: Response -> $responseBodyS")
                extractTicketData(responseBody)
            } else {
                Log.e("SOSManager", "❌ Failed to Create SOS: ${response.status} → $responseBody")
                null
            }
        } catch (e: Exception) {
            Log.e("SOSManager", "❌ Error: ${e.localizedMessage}")
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun updateLocation(latitude: Double, longitude: Double) {
        try {
            val response = apiService.updateLocation(userId, ticketId!!, latitude, longitude)
            val responseBody = response.body<String>()
            if (response.status.isSuccess()) {
                Log.d("SOSManager", "✅ Location Updated")
            } else {
                Log.e("SOSManager", "❌ Failed to Update Location: ${response.status} - $responseBody")
            }
        } catch (e: Exception) {
            Log.e("SOSManager", "❌ Error updating location: ${e.localizedMessage}")
        }
    }

    private fun extractTicketData(responseBody: String): TicketResponse? {
        return try {
            val jsonObject = Json.parseToJsonElement(responseBody).jsonObject
            val ticketData = jsonObject["data"]?.jsonObject
            val ticketId = ticketData?.get("ticket_id")?.jsonPrimitive?.content
            val status = ticketData?.get("status")?.jsonPrimitive?.content
            if (ticketId != null && status != null) {
                TicketResponse(ticketId, status)
            } else null
        } catch (e: Exception) {
            Log.e("SOSManager", "❌ JSON Parsing Error: ${e.localizedMessage}")
            null
        }
    }
}
