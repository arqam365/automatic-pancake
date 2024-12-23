package com.nextlevelprogrammers.surakshakawach.ui

import com.nextlevelprogrammers.surakshakawach.api.Api
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.insets.ProvideWindowInsets
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.nextlevelprogrammers.surakshakawach.PermissionActivity
import com.nextlevelprogrammers.surakshakawach.R
import com.nextlevelprogrammers.surakshakawach.api.UserPreferences
import com.nextlevelprogrammers.surakshakawach.utils.UserSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginScreen : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private var selectedGender: String? = null
    private var isCreateAccount: Boolean = false // Added flag for create account

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Firebase
        FirebaseApp.initializeApp(this)
        auth = FirebaseAuth.getInstance()

        // Set up Google Sign-In options
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Set the UI content
       if (UserSessionManager.isLoggedIn(this)) {
           if (!isNetworkAvailable()) {
               Toast.makeText(this, "No internet connection. Some features may be limited.", Toast.LENGTH_LONG).show()
           }
           navigateToNextScreen()
       }
       else {
           setContent {
               ProvideWindowInsets {
                   LoginScreenUI(
                       onGenderSelected = { gender ->
                           selectedGender = gender
                       },
                       onSignInClick = {
                           // Check if gender is selected before sign-in
                           if (selectedGender != null) {
                               signInWithGoogle(false)
                           } else {
                               Toast.makeText(this, "Please select a gender before signing in.", Toast.LENGTH_SHORT).show()
                           }
                       },
                       onCreateAccountClick = {
                           // Check if gender is selected before account creation
                           if (selectedGender != null) {
                               signInWithGoogle(true)
                           } else {
                               Toast.makeText(this, "Please select a gender before creating an account.", Toast.LENGTH_SHORT).show()
                           }
                       }
                   )
               }
           }
       }
    }

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            handleSignInResult(task.result)
        } else {
            Toast.makeText(this, "Google Sign-In failed", Toast.LENGTH_SHORT).show()
        }
    }

    // Pass a flag to determine if this is a sign-in or create account
    private fun signInWithGoogle(createAccount: Boolean) {
        isCreateAccount = createAccount // Store the create account flag
        val signInIntent = googleSignInClient.signInIntent
        googleSignInLauncher.launch(signInIntent)
    }

    private fun handleSignInResult(account: GoogleSignInAccount?) {
        account?.let {
            val idToken = account.idToken
            val userName = account.displayName
            val userEmail = account.email
            val userGender = selectedGender

            if (idToken != null && userName != null && userEmail != null) {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                auth.signInWithCredential(credential)
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            UserSessionManager.saveSession(
                                this,
                                auth.currentUser!!.uid,
                                userName,
                                userEmail,
                                userGender.toString()
                            )
                            if (selectedGender != null) {
                                if (isCreateAccount) {
                                    createUserInBackend(userName, userEmail, selectedGender!!)
                                } else {
                                    sendUserDataToBackend(userName, userEmail, selectedGender!!)
                                }
                            }
                        } else {
                            Log.e("LoginScreen", "Authentication failed: ${task.exception?.message}")
                            Toast.makeText(this, "Authentication Failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
            } else {
                Toast.makeText(this, "Google Sign-In failed. Please try again.", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            Log.e("LoginScreen", "Google Sign-In failed: Account is null.")
            Toast.makeText(this, "Google Sign-In failed: Account is null.", Toast.LENGTH_SHORT).show()
        }
    }

    // Check if user exists or create a new account
    private fun sendUserDataToBackend(name: String?, email: String?, gender: String) {
        val firebaseUID = auth.currentUser?.uid

        if (firebaseUID != null && name != null && email != null) {
            Log.d("LoginScreen", "Attempting to send user data to backend: UID = $firebaseUID, Name = $name, Email = $email, Gender = $gender")

            // Check if the user already exists in MongoDB
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val api = Api()
                    val userExists = api.checkIfUserExists(firebaseUID) // Check if user exists in MongoDB

                    withContext(Dispatchers.Main) {
                        if (userExists) {
                            Log.d("LoginScreen", "User already exists in MongoDB: UID = $firebaseUID")
                            // User already exists, navigate to the next screen
                            navigateToNextScreen()
                        } else {
                            // User does not exist, ask user to create an account
                            Toast.makeText(this@LoginScreen, "No account found. Please create an account.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("LoginScreen", "Error checking user in MongoDB: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@LoginScreen, "Error checking user: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } else {
            Log.e("LoginScreen", "Invalid user data: firebaseUID = $firebaseUID, name = $name, email = $email")
            Toast.makeText(this, "Invalid user data.", Toast.LENGTH_SHORT).show()
        }
    }

    // Define the createUserInBackend function
    private fun createUserInBackend(name: String, email: String, gender: String) {
        val firebaseUID = auth.currentUser?.uid

        if (firebaseUID != null) {
            Log.d("LoginScreen", "Creating user in backend: UID = $firebaseUID, Name = $name, Email = $email, Gender = $gender")

            // Create user in MongoDB
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val api = Api()
                    val success = api.createUser(firebaseUID, name, email, gender) // Call API to create user

                    withContext(Dispatchers.Main) {
                        if (success) {
                            UserPreferences.setUserCreated(this@LoginScreen, true)

                            navigateToNextScreen()
                        } else {
                            Log.e("LoginScreen", "Failed to create user in backend for UID: $firebaseUID")
                            Toast.makeText(this@LoginScreen, "User is already created. Please try to Login instead.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("LoginScreen", "Error creating user in MongoDB: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@LoginScreen, "Error creating user: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        } else {
            Log.e("LoginScreen", "Invalid user data: firebaseUID = $firebaseUID, name = $name, email = $email")
            Toast.makeText(this, "Invalid user data.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToNextScreen() {
        val session = UserSessionManager.getSession(this)
        val userId = session["userId"]
        val userName = session["userName"]
        val userEmail = session["userEmail"]

        Log.d("LoginScreen", "User logged in: $userId, $userName, $userEmail")

        val intent = Intent(this, PermissionActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)
    }
}

@Composable
fun LoginScreenUI(
    onGenderSelected: (String) -> Unit,
    onSignInClick: () -> Unit,
    onCreateAccountClick: () -> Unit
) {
    var selectedGender by remember { mutableStateOf<String?>(null) }

    Image(
        painter = painterResource(id = R.drawable.theme), // Replace with your actual drawable ID
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
    )


    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        // Logo
        Image(
            painter = painterResource(id = R.drawable.suraksha_kawach_logo),
            contentDescription = "App Logo",
            modifier = Modifier.size(300.dp)
        )

        // Intro Text
        Text(
            text = "Suraksha Kawach is a safety app designed to protect and alert your loved ones during emergencies.",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        // Gender Selection
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = {
                    selectedGender = "male"
                    onGenderSelected("male")
                },
                modifier = Modifier.weight(1f).padding(8.dp),
                colors = if (selectedGender == "male") ButtonDefaults.buttonColors(containerColor = Color.Blue)
                else ButtonDefaults.buttonColors(containerColor = Color.Gray)
            ) {
                Text(text = "Male", color = Color.White)
            }

            Button(
                onClick = {
                    selectedGender = "female"
                    onGenderSelected("female")
                },
                modifier = Modifier.weight(1f).padding(8.dp),
                colors = if (selectedGender == "female") ButtonDefaults.buttonColors(containerColor = Color.Blue)
                else ButtonDefaults.buttonColors(containerColor = Color.Gray)
            ) {
                Text(text = "Female", color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        // Google Sign-In Button for Sign In
        Button(
            onClick = onSignInClick,
            enabled = selectedGender != null, // Disable button if gender is not selected
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(48.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(text = "Sign In with Google")
        }

        // Google Sign-In Button for Creating an Account
        Button(
            onClick = onCreateAccountClick,
            enabled = selectedGender != null, // Disable button if gender is not selected
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(48.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(text = "Create Account with Google")
        }


        Spacer(modifier = Modifier.height(40.dp))
    }
}