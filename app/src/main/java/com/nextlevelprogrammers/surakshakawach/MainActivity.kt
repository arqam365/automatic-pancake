package com.nextlevelprogrammers.surakshakawach

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.messaging.FirebaseMessaging
import com.nextlevelprogrammers.surakshakawach.data.remote.ApiService
import com.nextlevelprogrammers.surakshakawach.model.AuthRequest
import com.nextlevelprogrammers.surakshakawach.ui.theme.SurakshaKavachUITheme
import com.nextlevelprogrammers.surakshakawach.uidesign.GetStartedLogin
import com.nextlevelprogrammers.surakshakawach.uidesign.MainScreen
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.Instant

class MainActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var credentialManager: CredentialManager
    private lateinit var userData: UserData

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()
        credentialManager = CredentialManager.create(this)

        enableEdgeToEdge()
        setContent {
            SurakshaKavachUITheme {
                Scaffold { innerPadding ->
                    val navController = rememberNavController()
                    val startDestination = if (auth.currentUser != null) "MainScreen" else "GetStarted"

                    NavHost(navController, startDestination = startDestination) {
                        composable("GetStarted") {
                            GetStartedLogin(
                                navController = navController,
                                onGoogleSignInClick = { signInWithGoogle(navController) } // ✅ Pass Sign-In Click
                            )
                        }
                        composable("MainScreen") {
                            MainScreen(
                                Modifier.padding(innerPadding),
                                navController = navController,
                                onSignOutClick={signOut(navController)}
                            )
                        }
                    }
                }
            }
        }
    }

    /** 🔥 Function to Sign in with Google using Credential Manager API */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun signInWithGoogle(navController: NavHostController) {
        val googleIdTokenRequest = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(baseContext.getString(R.string.client_id))
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdTokenRequest)
            .build()

        lifecycleScope.launch(Dispatchers.Main) {
            try {
                val result: GetCredentialResponse = credentialManager.getCredential(this@MainActivity, request)
                handleSignInResult(result, navController)
            } catch (e: GetCredentialException) {
                Log.e(TAG, "Google Sign-In failed: ${e.localizedMessage}")
            }
        }
    }

    /** 🔥 Handle Sign-In Result */
    @RequiresApi(Build.VERSION_CODES.O)
    private fun handleSignInResult(result: GetCredentialResponse, navController: NavHostController) {
        val credential = result.credential
        // ✅ Check if credential is of type Google ID Token
        if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data)
            firebaseAuthWithGoogle(googleIdToken.idToken, navController) // ✅ Proceed with Firebase Auth
        } else {
            Log.w(TAG, "Credential is not of type Google ID!")
        }
    }

    private suspend fun fetchUserDobFromGoogle(idToken: String): String {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "🔍 Skipping Google People API, using predefined DOB for testing.")

                val predefinedDob = "2000-01-01" // ✅ Use a fixed DOB for all users

                Log.d(TAG, "🎂 Using predefined DOB: $predefinedDob")
                return@withContext predefinedDob

            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to fetch DOB: ${e.localizedMessage}")
                return@withContext "unknown"
            }
        }
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private fun firebaseAuthWithGoogle(idToken: String, navController: NavHostController) {
        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(firebaseCredential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    val firebaseUid = user?.uid ?: return@addOnCompleteListener
                    Log.d(TAG, "✅ Firebase UID: $firebaseUid")

                    // ✅ Fetch DOB from Google People API
                    lifecycleScope.launch(Dispatchers.IO) {
                        val dateOfBirth = fetchUserDobFromGoogle(idToken) ?: "unknown"
                        Log.d(TAG, "🎂 User DOB: $dateOfBirth")

                        // ✅ Get FCM Token
                        FirebaseMessaging.getInstance().token.addOnCompleteListener { tokenTask ->
                            if (tokenTask.isSuccessful) {
                                val fcmToken = tokenTask.result ?: "unknown"
                                Log.d(TAG, "🔥 FCM Token: $fcmToken")

                                // ✅ Send Data to Backend
                                sendAuthDataToBackend(firebaseUid, dateOfBirth, fcmToken, navController)
                            } else {
                                Log.e(TAG, "❌ Failed to get FCM Token: ${tokenTask.exception?.message}")
                            }
                        }
                    }
                } else {
                    Log.e(TAG, "❌ Firebase authentication failed: ${task.exception?.localizedMessage}")
                }
            }
    }

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private fun sendAuthDataToBackend(firebaseUid: String, dateOfBirth: String, fcmId: String, navController: NavHostController) {
        lifecycleScope.launch(Dispatchers.IO) {
            val apiService = ApiService(httpClient)

            try {
                val formattedDob = try {
                    Instant.parse("${dateOfBirth}T00:00:00.000Z").toString()
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Invalid date format, defaulting to predefined date")
                    "2000-01-01T00:00:00.000Z" // ✅ Default if parsing fails
                }

                val authRequest = AuthRequest(
                    firebase_uid = firebaseUid,
                    date_of_birth = formattedDob, // ✅ Correctly formatted date
                    fcm_id = fcmId
                )

                val response = apiService.authenticateUser(authRequest)

                // ✅ Check for successful authentication
                if (response.success || response.message.contains("successfully", ignoreCase = true)) {
                    Log.d(TAG, "✅ User authenticated successfully! User ID: ${response.user_id}")
                    withContext(Dispatchers.Main) {
                        navController.navigate("MainScreen") {
                            popUpTo("MainScreenHome") { inclusive = true }
                        }
                    }
                } else {
                    Log.e(TAG, "❌ Authentication failed: ${response.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to send auth data: ${e.localizedMessage}")
            }
        }
    }

    private fun signOut(navController: NavHostController){
        auth.signOut()
        navController.navigate("GetStarted"){
            popUpTo("MainScreen"){inclusive=true}
        }
    }




    companion object {
        private const val TAG = "GoogleSignIn"
    }


    data class UserData(
        val uid: String = "",
        val displayName: String? = null,
        val email: String? = null,
        val photoUrl: String? = null,
        val phoneNumber: String? = null
    )
}
