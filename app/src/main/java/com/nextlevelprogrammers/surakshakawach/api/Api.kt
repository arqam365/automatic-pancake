    package com.nextlevelprogrammers.surakshakawach.api

    import android.util.Log
    import io.ktor.client.HttpClient
    import io.ktor.client.call.body
    import io.ktor.client.plugins.ClientRequestException
    import io.ktor.client.plugins.ServerResponseException
    import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
    import io.ktor.client.request.forms.*
    import io.ktor.client.request.get
    import io.ktor.client.request.post
    import io.ktor.client.request.setBody
    import io.ktor.client.statement.HttpResponse
    import io.ktor.client.statement.bodyAsText
    import io.ktor.http.*
    import io.ktor.serialization.kotlinx.json.json
    import kotlinx.serialization.json.Json
    import kotlinx.serialization.json.jsonObject
    import kotlinx.serialization.json.jsonPrimitive

    val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
            })
        }
    }

    class Api {

        suspend fun createUser(
            firebaseUID: String,
            name: String,
            email: String,
            gender: String
        ): Boolean {
            val response: HttpResponse = client.submitForm(
                url = "https://surakshakawach-mobilebackend-192854867616.asia-south2.run.app/api/v1/user",
                formParameters = Parameters.build {
                    append("firebaseUID", firebaseUID)
                    append("name", name)
                    append("email", email)
                    append("gender", gender)
                }
            )

            return response.status == HttpStatusCode.Created // Expect 201 for successful user creation
        }

        suspend fun checkIfUserExists(firebaseUID: String): Boolean {
            val response: HttpResponse = client.get("https://surakshakawach-mobilebackend-192854867616.asia-south2.run.app/api/v1/user") {
                url {
                    parameters.append("firebaseUID", firebaseUID)
                }
            }

            return response.status == HttpStatusCode.OK // 200 OK means user exists
            // Add error handling for 404 Not Found if the user doesn't exist
        }



        // Configure JSON parser with `ignoreUnknownKeys`
        private val json = Json {
            ignoreUnknownKeys = true  // This will ignore fields like `emergencyContacts` and `tickets`
        }

        // Function to get user profile data from the server using GET request
        suspend fun getUserProfile(firebaseUID: String): UserProfileResponse? {
            val response: HttpResponse = client.get("https://surakshakawach-mobilebackend-192854867616.asia-south2.run.app/api/v1/user") {
                url {
                    parameters.append("firebaseUID", firebaseUID)
                }
            }

            return if (response.status == HttpStatusCode.OK) {
                // Deserialize response body to UserProfileResponse, ignoring unknown fields
                json.decodeFromString(response.body())
            } else {
                null
            }
        }

        // Function to send emergency contact data to the server using x-www-form-urlencoded
        suspend fun sendEmergencyContactToServer(
            firebaseUID: String,
            name: String,
            email: String,
            mobile: String
        ): Boolean {
            val response: HttpResponse = client.submitForm(
                url = "https://surakshakawach-mobilebackend-192854867616.asia-south2.run.app/api/v1/user/create/emergency-contact",
                formParameters = Parameters.build {
                    append("firebaseUID", firebaseUID)
                    append("name", name)
                    append("email", email)
                    append("mobile", mobile)
                }
            )

            return response.status == HttpStatusCode.Created
        }

    //    // Function to get emergency contacts
    //    suspend fun getEmergencyContacts(firebaseUID: String): List<ApiContact>? {
    //        val response: List<ApiContact> = client.get("https://surakshakawach-mobilebackend-192854867616.asia-south2.run.app/api/v1/user/emergency-contacts") {
    //            parameter("firebaseUID", firebaseUID)
    //        }
    //        return response
    //    }


        suspend fun sendSosTicket(
            firebaseUID: String,
            latitude: String,
            longitude: String,
            timestamp: String
        ): String? {
            try {
                val url = "https://surakshakawach-mobilebackend-192854867616.asia-south2.run.app/api/v1/ticket/create"
                Log.d("SOS_TICKET", "Sending SOS ticket to URL: $url with parameters: firebaseUID=$firebaseUID, latitude=$latitude, longitude=$longitude, timestamp=$timestamp")

                val response: HttpResponse = client.submitForm(
                    url = url,
                    formParameters = Parameters.build {
                        append("firebaseUID", firebaseUID)
                        append("latitude", latitude)
                        append("longitude", longitude)
                        append("timestamp", timestamp)
                    }
                )

                val statusCode = response.status
                val responseBody = response.bodyAsText()

                Log.d("SOS_TICKET", "Server responded with status code: $statusCode and body: $responseBody")

                if (statusCode.isSuccess()) {
                    if (responseBody.isNotEmpty()) {
                        val json = Json.parseToJsonElement(responseBody).jsonObject
                        val ticketId = json["ticketId"]?.jsonPrimitive?.content
                        if (ticketId != null) {
                            Log.d("SOS_TICKET", "SOS ticket created with ID: $ticketId")
                            return ticketId
                        } else {
                            Log.e("SOS_TICKET", "Error: 'ticketId' field is missing in the response.")
                        }
                    } else {
                        Log.d("SOS_TICKET", "SOS ticket created successfully but response body is empty. Fetching ticketId using GET request.")

                        // Use the GET API to fetch the ticket details if the response body is empty
                        val ticketDetails = getSosTicketDetails(firebaseUID, timestamp)
                        if (ticketDetails != null) {
                            return ticketDetails.ticketId
                        } else {
                            Log.e("SOS_TICKET", "Failed to retrieve ticket ID using GET request.")
                        }
                    }
                } else {
                    Log.e("SOS_TICKET", "Failed to create SOS ticket: ${statusCode.value}")
                }
            } catch (e: ClientRequestException) {
                Log.e("SOS_TICKET", "Client request error: ${e.localizedMessage}")
            } catch (e: ServerResponseException) {
                Log.e("SOS_TICKET", "Server response error: ${e.localizedMessage}")
            } catch (e: Exception) {
                Log.e("SOS_TICKET", "Unexpected error creating SOS ticket: ${e.localizedMessage}")
            }
            return null
        }

        // Function to retrieve ticket details using GET request
        private suspend fun getSosTicketDetails(
            firebaseUID: String,
            timestamp: String
        ): TicketDetailsResponse? {
            try {
                val url = "https://surakshakawach-mobilebackend-192854867616.asia-south2.run.app/api/v1/ticket"
                val response: HttpResponse = client.get(url) {
                    url {
                        parameters.append("firebaseUID", firebaseUID)
                        parameters.append("timestamp", timestamp)
                    }
                }

                val statusCode = response.status
                val responseBody = response.bodyAsText()

                Log.d("SOS_TICKET", "GET request responded with status code: $statusCode and body: $responseBody")

                if (statusCode.isSuccess() && responseBody.isNotEmpty()) {
                    return Json.decodeFromString(responseBody)
                }
            } catch (e: Exception) {
                Log.e("SOS_TICKET", "Error retrieving ticket details: ${e.localizedMessage}")
            }
            return null
        }

        // Function to check if there is an active SOS ticket for the user
        suspend fun checkActiveTicket(firebaseUID: String): String? {
            try {
                val url = "https://surakshakawach-mobilebackend-192854867616.asia-south2.run.app/api/v1/ticket/active-ticket"
                val response: HttpResponse = client.get(url) {
                    url {
                        parameters.append("firebaseUID", firebaseUID)
                    }
                }

                val statusCode = response.status
                val responseBody = response.bodyAsText()

                Log.d("SOS_TICKET", "GET request for active ticket responded with status code: $statusCode and body: $responseBody")

                if (statusCode.isSuccess() && responseBody.isNotEmpty()) {
                    // Extract the `ticketId` from the response JSON
                    val json = Json.parseToJsonElement(responseBody).jsonObject
                    val ticketId = json["data"]?.jsonObject?.get("ticketId")?.jsonPrimitive?.content
                    if (ticketId != null) {
                        Log.d("SOS_TICKET", "Active ticket ID: $ticketId")
                        return ticketId
                    } else {
                        Log.e("SOS_TICKET", "Error: 'ticketId' field is missing in the active ticket response.")
                    }
                } else {
                    Log.e("SOS_TICKET", "Failed to retrieve active ticket: ${statusCode.value}")
                }
            } catch (e: Exception) {
                Log.e("SOS_TICKET", "Error checking active ticket: ${e.localizedMessage}")
            }
            return null
        }


        // Define the function to update coordinates using Ktor
        suspend fun updateCoordinates(
            firebaseUID: String,
            ticketId: String,
            latitude: String,
            longitude: String,
            timestamp: String
        ): Boolean {
            try {
                val response: HttpResponse = client.submitForm(
                    url = "https://surakshakawach-mobilebackend-192854867616.asia-south2.run.app/api/v1/ticket/coordinates", // Make sure to use the full server URL
                    formParameters = Parameters.build {
                        append("firebaseUID", firebaseUID)
                        append("ticketId", ticketId)
                        append("latitude", latitude)
                        append("longitude", longitude)
                        append("timestamp", timestamp)
                    }
                )

                // Check if the response status code indicates success (2xx)
                return response.status.isSuccess()
            } catch (e: Exception) {
                // Handle any exceptions (e.g., network issues)
                println("Error updating coordinates: ${e.localizedMessage}")
                return false
            }
        }

        // Define the function to close the ticket using Ktor
        suspend fun closeTicket(
            firebaseUID: String,
            ticketId: String
        ): Boolean {
            try {
                val response: HttpResponse = client.submitForm(
                    url = "https://surakshakawach-mobilebackend-192854867616.asia-south2.run.app/api/v1/ticket/close-ticket",
                    formParameters = Parameters.build {
                        append("firebaseUID", firebaseUID)
                        append("ticketId", ticketId)
                    }
                )

                // Check if the response status code indicates success (2xx)
                return response.status.isSuccess()
            } catch (e: Exception) {
                // Handle any exceptions (e.g., network issues)
                Log.e("SOS_TICKET", "Error closing ticket: ${e.localizedMessage}")
                return false
            }
        }

        suspend fun sendImages(ticketId: String, firebaseUID: String, imagesData: List<ImageData>): Boolean {
            val url = "https://surakshakawach-mobilebackend-192854867616.asia-south2.run.app/api/v1/ticket/add-images"
            val requestBody = AddImageRequest(ticketId, firebaseUID, imagesData)

            return try {
                val response: HttpResponse = client.post(url) {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)  // Serializing the AddImageRequest object to JSON
                }

                Log.d("API_SEND_IMAGES", "Response status: ${response.status}, Body: ${response.bodyAsText()}")

                if (response.status == HttpStatusCode.OK) {
                    Log.d("API_SEND_IMAGES", "Image data sent successfully. Status: ${response.status}")
                    true
                } else {
                    Log.e("API_SEND_IMAGES", "Failed to send image data. Status: ${response.status}")
                    false
                }
            } catch (e: ClientRequestException) {
                Log.e("API_SEND_IMAGES", "Client request error: ${e.localizedMessage}", e)
                false
            } catch (e: ServerResponseException) {
                Log.e("API_SEND_IMAGES", "Server response error: ${e.localizedMessage}", e)
                false
            } catch (e: Exception) {
                Log.e("API_SEND_IMAGES", "Unexpected error: ${e.localizedMessage}", e)
                false
            }
        }

        suspend fun sendAudioClips(ticketId: String, firebaseUID: String, clipsData: List<ClipData>): Boolean {
            val url = "https://surakshakawach-mobilebackend-192854867616.asia-south2.run.app/api/v1/ticket/add-audio-clips"
            val requestBody = AddAudioRequest(ticketId, firebaseUID, clipsData)

            return try {
                val response: HttpResponse = client.post(url) {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody) // Serializing the AddAudioRequest object to JSON
                }
                if (response.status == HttpStatusCode.OK) {
                    println("Audio clips sent successfully")
                    true
                } else {
                    println("Failed to send audio clips. Status: ${response.status}")
                    false
                }
            } catch (e: Exception) {
                println("Error sending audio clips: ${e.message}")
                false
            }
        }

        // Function to update emergency contacts
        suspend fun updateEmergencyContacts(
            firebaseUID: String,
            oldContacts: List<EmergencyContact>,
            newContacts: List<EmergencyContact>
        ): Boolean {
            return try {
                val response: HttpResponse = client.post("https://surakshakawach-mobilebackend-192854867616.asia-south2.run.app/api/v1/user/update/emergency-contact") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        UpdateEmergencyContactRequest(
                        firebaseUID = firebaseUID,
                        old_contacts = oldContacts,
                        new_contacts = newContacts
                    )
                    )
                }

                if (response.status.isSuccess()) {
                    Log.d("UPDATE_CONTACT", "Emergency contacts updated successfully.")
                    true
                } else {
                    Log.e("UPDATE_CONTACT", "Failed to update emergency contacts. Status: ${response.status}")
                    false
                }
            } catch (e: ClientRequestException) {
                Log.e("UPDATE_CONTACT", "Client request error: ${e.localizedMessage}")
                false
            } catch (e: ServerResponseException) {
                Log.e("UPDATE_CONTACT", "Server response error: ${e.localizedMessage}")
                false
            } catch (e: Exception) {
                Log.e("UPDATE_CONTACT", "Unexpected error: ${e.localizedMessage}")
                false
            }
        }

        suspend fun removeEmergencyContacts(
            firebaseUID: String,
            contactDetails: List<EmergencyContact>
        ): Boolean {
            return try {
                val response: HttpResponse = client.post("https://surakshakawach-mobilebackend-192854867616.asia-south2.run.app/api/v1/user/remove/emergency-contact") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        RemoveEmergencyContactRequest(
                        firebaseUID = firebaseUID,
                        contact_details = contactDetails
                    )
                    )
                }

                if (response.status.isSuccess()) {
                    Log.d("REMOVE_CONTACT", "Emergency contacts removed successfully.")
                    true
                } else {
                    Log.e("REMOVE_CONTACT", "Failed to remove emergency contacts. Status: ${response.status}")
                    false
                }
            } catch (e: ClientRequestException) {
                Log.e("REMOVE_CONTACT", "Client request error: ${e.localizedMessage}")
                false
            } catch (e: ServerResponseException) {
                Log.e("REMOVE_CONTACT", "Server response error: ${e.localizedMessage}")
                false
            } catch (e: Exception) {
                Log.e("REMOVE_CONTACT", "Unexpected error: ${e.localizedMessage}")
                false
            }
        }


        suspend fun fetchTicketStatus(firebaseUID: String, ticketId: String): TicketInfo? {
            val url = "https://surakshakawach-mobilebackend-192854867616.asia-south2.run.app/api/v1/ticket?firebaseUID=$firebaseUID&ticketId=$ticketId"
            Log.d("Api", "Requesting ticket status with URL: $url")

            return try {
                val response: HttpResponse = client.get(url)
                val responseBody = response.bodyAsText()
                Log.d("Api", "Response Body: $responseBody")

                if (response.status == HttpStatusCode.OK) {
                    val json = Json { ignoreUnknownKeys = true }
                    val apiResponse = json.decodeFromString<ApiResponse>(responseBody)

                    // Access user and ticket details
                    val status = apiResponse.data.ticket?.status
                    val userName = apiResponse.data.user?.displayName ?: "Unknown User"
                    val images = apiResponse.data.ticket?.images?.map { it.url } ?: emptyList()
                    val audios = apiResponse.data.ticket?.audioClips?.map { it.url } ?: emptyList()
                    Log.d("Api", "Fetched status: $status for user: $userName")

                    // Return as TicketInfo
                    return TicketInfo(status = status, userName = userName, images = images, audios = audios)
                } else {
                    Log.e("Api", "Failed to fetch ticket status. HTTP Status: ${response.status}")
                    null
                }
            } catch (e: Exception) {
                Log.e("Api", "Exception while fetching status: ${e.localizedMessage}")
                null
            }
        }

        suspend fun fetchLatestLocation(firebaseUID: String, ticketId: String): Coordinates? {
            val url = "https://surakshakawach-mobilebackend-192854867616.asia-south2.run.app/api/v1/ticket/latest-update?firebaseUID=$firebaseUID&ticketId=$ticketId"
            Log.d("Api", "Requesting latest coordinates with URL: $url")

            return try {
                val response: HttpResponse = client.get(url)
                val responseBody = response.bodyAsText()
                Log.d("Api", "Response Body: $responseBody")

                if (response.status == HttpStatusCode.OK) {
                    val json = Json { ignoreUnknownKeys = true }
                    val apiResponse = json.decodeFromString<ApiResponse>(responseBody)

                    // Access coordinates from locationInfo if available
                    val latestCoordinates = apiResponse.data.locationInfo?.coordinates
                    if (latestCoordinates != null) {
                        Log.d("Api", "Fetched coordinates: ${latestCoordinates.latitude}, ${latestCoordinates.longitude}")
                    } else {
                        Log.e("Api", "Location info not found in response.")
                    }
                    return latestCoordinates
                } else {
                    Log.e("Api", "Failed to fetch latest coordinates. HTTP Status: ${response.status}")
                    null
                }
            } catch (e: Exception) {
                Log.e("Api", "Exception while fetching latest coordinates: ${e.localizedMessage}")
                null
            }
        }
    }