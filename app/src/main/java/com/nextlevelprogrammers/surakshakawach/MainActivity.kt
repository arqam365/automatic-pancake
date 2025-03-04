package com.nextlevelprogrammers.surakshakawach

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
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
import com.nextlevelprogrammers.surakshakawach.deviceadmin.MyDeviceAdminReceiver
import com.nextlevelprogrammers.surakshakawach.ui.theme.SurakshaKavachUITheme
import com.nextlevelprogrammers.surakshakawach.uidesign.CountdownWindow
import com.nextlevelprogrammers.surakshakawach.uidesign.GetStartedLogin
import com.nextlevelprogrammers.surakshakawach.uidesign.MainScreen
import com.nextlevelprogrammers.surakshakawach.uidesign.SOSGranted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var credentialManager: CredentialManager
    private lateinit var userData: UserData
    private lateinit var deviceAdminLauncher: ActivityResultLauncher<Intent>
    private lateinit var sharedPreferences: SharedPreferences


    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)

        deviceAdminLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                Toast.makeText(this, "Device Admin Enabled", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Device Admin Not Enabled", Toast.LENGTH_SHORT).show()
            }
        }

        if (!isDeviceAdminEnabled()) {
            requestDeviceAdmin()
        }

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
                        composable("CountScreen"){
                            CountdownWindow(navController=navController)
                        }
                        composable("SOSGranted"){
                            SOSGranted()
                        }
                    }
                }
            }
        }
    }

    /** 🔥 Function to Sign in with Google using Credential Manager API */
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

    private fun firebaseAuthWithGoogle(idToken: String, navController: NavHostController) {
        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(firebaseCredential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Sign-in successful: ${auth.currentUser?.displayName}")
                    navController.navigate("MainScreen"){
                        popUpTo("GetStarted"){inclusive=true}
                    }
                } else {
                    Log.e(TAG, "Firebase authentication failed: ${task.exception?.localizedMessage}")
                }
            }
    }


    private fun signOut(navController: NavHostController){
        auth.signOut()
        navController.navigate("GetStarted"){
            popUpTo("MainScreen"){inclusive=true}
        }
    }

    private fun isDeviceAdminEnabled(): Boolean {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val componentName = ComponentName(this, MyDeviceAdminReceiver::class.java)
        return dpm.isAdminActive(componentName) || sharedPreferences.getBoolean("isDeviceAdminEnabled", false)
    }

    private fun requestDeviceAdmin() {
        val componentName = ComponentName(this, MyDeviceAdminReceiver::class.java)
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Enable admin to protect your data.")
        }
        deviceAdminLauncher.launch(intent)
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
