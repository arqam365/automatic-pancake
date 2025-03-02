package com.nextlevelprogrammers.surakshakawach.data.remote

import ApiResponse
import com.nextlevelprogrammers.surakshakawach.model.ContactRequest
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class ApiService(private val client: HttpClient) {

    private val BASE_URL = "https://kawach-v2-backend-production-809410945582.asia-south1.run.app"

    suspend fun addContact(userId: String, contact: ContactRequest): ApiResponse {
        return try {
            println("🔍 Sending ContactRequest for user: $userId with data: $contact") // Debugging Log

            val response: HttpResponse = client.post("$BASE_URL/v2/user/$userId/contacts/") {
                contentType(io.ktor.http.ContentType.Application.Json)
                setBody(contact)
            }

            if (response.status.value in 200..299) {
                val responseBody: ApiResponse = response.body()
                println("✅ Contact successfully sent to API: $responseBody")
                responseBody
            } else {
                val errorBody = response.body<String>() // Capture error details
                println("❌ API responded with error: ${response.status} - $errorBody")
                ApiResponse(success = false, message = "API Error: ${response.status} - $errorBody")
            }
        } catch (e: Exception) {
            println("❌ API Call Failed: ${e.localizedMessage}")
            ApiResponse(success = false, message = "Error: ${e.localizedMessage}")
        }
    }
    suspend fun getContacts(userId: String): List<ContactResponse> {
        return try {
            client.get("$BASE_URL/v2/user/$userId/contacts/").body()
        } catch (e: Exception) {
            println("❌ Failed to fetch contacts: ${e.localizedMessage}")
            emptyList()
        }
    }
}