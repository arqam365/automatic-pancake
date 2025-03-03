package com.nextlevelprogrammers.surakshakawach.data.remote

import com.nextlevelprogrammers.surakshakawach.model.ApiResponse
import com.nextlevelprogrammers.surakshakawach.model.AuthRequest
import com.nextlevelprogrammers.surakshakawach.model.AuthResponse
import com.nextlevelprogrammers.surakshakawach.model.ContactRequest
import com.nextlevelprogrammers.surakshakawach.model.ContactResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

class ApiService(private val client: HttpClient) {

    private val BASE_URL = "https://kawach-v2-backend-production-809410945582.asia-south1.run.app"

    suspend fun authenticateUser(authRequest: AuthRequest): AuthResponse {
        return try {
            val response: HttpResponse = client.post("$BASE_URL/v2/auth/google/callback") {
                contentType(ContentType.Application.Json)
                setBody(authRequest) // ✅ Ensure proper JSON encoding
            }

            if (response.status.isSuccess()) {
                response.body<AuthResponse>()
            } else {
                val errorBody = response.body<String>()
                println("❌ API responded with error: ${response.status} - $errorBody")
                AuthResponse(success = false, message = "API Error: ${response.status} - $errorBody")
            }
        } catch (e: Exception) {
            println("❌ API Call Failed: ${e.localizedMessage}")
            AuthResponse(success = false, message = "Error: ${e.localizedMessage}")
        }
    }

    suspend fun addContact(userId: String, contact: ContactRequest): ContactResponse? {
        return try {
            println("🔍 Sending ContactRequest for user: $userId with data: $contact") // Debugging Log

            val response: HttpResponse = client.post("$BASE_URL/v2/user/$userId/contacts/") {
                contentType(io.ktor.http.ContentType.Application.Json)
                setBody(contact)
            }

            if (response.status.value in 200..299) {
                val apiResponse: ApiResponse<ContactResponse> = response.body()
                println("✅ Contact successfully created(API): ${apiResponse.data}")
                apiResponse.data // ✅ Return only the `data` part of the response
            } else {
                val errorBody = response.body<String>() // Capture error details
                println("❌ API responded with error: ${response.status} - $errorBody")
                null
            }
        } catch (e: Exception) {
            println("❌ API Call Failed: ${e.localizedMessage}")
            null
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