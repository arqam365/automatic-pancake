package com.nextlevelprogrammers.surakshakawach

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.nextlevelprogrammers.surakshakawach.ui.DashboardScreen
import com.nextlevelprogrammers.surakshakawach.ui.EmergencyContactsScreen
import com.nextlevelprogrammers.surakshakawach.ui.HomeScreen
import com.nextlevelprogrammers.surakshakawach.utils.NetworkMonitor
import com.nextlevelprogrammers.surakshakawach.viewmodel.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class HomeActivity : ComponentActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val homeViewModel: HomeViewModel by viewModels()
    private lateinit var networkMonitor: NetworkMonitor

    // Define the BroadcastReceiver
    private val wakeWordReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            homeViewModel.activateSOS()
            Toast.makeText(context, "Wake word detected!", Toast.LENGTH_SHORT).show()
            Log.d("HomeActivity", "Wake word detected! Triggering SOS action.")
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if all permissions are granted
        if (!areAllPermissionsGranted()) {
            redirectToPermissionActivity()
            return
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Register receiver with condition to support different API levels
        val intentFilter = IntentFilter("com.nextlevelprogrammers.surakshakawach.WAKE_WORD_DETECTED")
        registerReceiver(wakeWordReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)

        // Initialize and start network monitoring
        networkMonitor = NetworkMonitor(this, object : NetworkMonitor.NetworkCallback {
            override fun onNetworkChanged(isAvailable: Boolean) {
                if (isAvailable) {
                    lifecycleScope.launch {
                        try {
                            autoSyncData()
                        } catch (e: Exception) {
                            Log.e("HomeActivity", "Error during auto-sync: ${e.message}")
                        }
                    }
                } else {
                }
            }
        })

        networkMonitor.startMonitoring()

        // Start voice recognition service
        startService(Intent(this, VoiceRecognitionService::class.java))

        setContent {
            val navController = rememberNavController()
            AppNavigation(navController, fusedLocationClient, homeViewModel)
        }
    }

    override fun onDestroy() {
        try {
            val sharedDir = File(applicationContext.filesDir, "shared")
            if (sharedDir.exists()) {
                sharedDir.deleteRecursively()
            }
        } catch (e: Exception) {
            Log.e("HomeActivity", "Error during cleanup: ${e.message}", e)
        }

        try {
            unregisterReceiver(wakeWordReceiver)
        } catch (e: IllegalArgumentException) {
            Log.w("HomeActivity", "Receiver was not registered: ${e.message}")
        }

        networkMonitor.stopMonitoring()
        super.onDestroy()
    }

    private fun areAllPermissionsGranted(): Boolean {
        val requiredPermissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun redirectToPermissionActivity() {
        val intent = Intent(this, PermissionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

        private suspend fun autoSyncData() {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val firebaseUID = FirebaseAuth.getInstance().currentUser?.uid
                    if (firebaseUID != null) {
                        // Fetch or sync data with session manager
                        val sessionData = homeViewModel.fetchSessionData(firebaseUID)
                        homeViewModel.syncSessionData(firebaseUID, sessionData)

                        withContext(Dispatchers.Main) {
                        }
                    } else {
                        Log.e("HomeActivity", "Firebase UID is null. Cannot sync.")
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@HomeActivity, "Sync failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                    Log.e("HomeActivity", "Error during sync: ${e.message}", e)
                }
            }
        }
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    fusedLocationClient: FusedLocationProviderClient,
    homeViewModel: HomeViewModel
) {
    val firebaseUID = FirebaseAuth.getInstance().currentUser?.uid

    // Define the navigation routes
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                navController = navController,
                fusedLocationClient = fusedLocationClient,
                homeViewModel = homeViewModel // Pass ViewModel to HomeScreen
            )
        }
        composable("emergency_contacts") {
            EmergencyContactsScreen(onBackPress = { navController.popBackStack() })
        }
        composable("dashboard") {
            if (firebaseUID != null) {
                DashboardScreen(firebaseUID = firebaseUID)
            }
        }
    }
}