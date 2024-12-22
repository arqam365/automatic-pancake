package com.nextlevelprogrammers.surakshakawach.ui

import com.nextlevelprogrammers.surakshakawach.api.Api
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.CountDownTimer
import android.os.Looper
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.nextlevelprogrammers.surakshakawach.R
import com.nextlevelprogrammers.surakshakawach.SOSActivity
import com.nextlevelprogrammers.surakshakawach.VoiceRecognitionService
import com.nextlevelprogrammers.surakshakawach.WatchActivity
import com.nextlevelprogrammers.surakshakawach.data.ContactDatabase
import com.nextlevelprogrammers.surakshakawach.utils.NetworkMonitor
import com.nextlevelprogrammers.surakshakawach.utils.UserSessionManager
import com.nextlevelprogrammers.surakshakawach.viewmodel.HomeViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavHostController, fusedLocationClient: FusedLocationProviderClient,homeViewModel: HomeViewModel = viewModel() ) {
    val context = LocalContext.current
    var currentLocation by remember { mutableStateOf<LatLng?>(null) }
    var hasLocationPermission by remember { mutableStateOf(false) }
    var showModal by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(3) }
    var isSOSCanceled by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var countdownTimer: CountDownTimer? = null
    var isLoading by remember { mutableStateOf(false) }
    var isProcessCompleted by remember { mutableStateOf(true) }
    var isRequestSent by remember { mutableStateOf(false) }
    val triggerSOS by homeViewModel.triggerSOS.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val isNetworkAvailable = remember { mutableStateOf(checkNetworkAvailability(context)) }

    DisposableEffect(Unit) {
        val networkMonitor = NetworkMonitor(context, object : NetworkMonitor.NetworkCallback {
            override fun onNetworkChanged(isAvailable: Boolean) {
                coroutineScope.launch {
                    isNetworkAvailable.value = isAvailable
                    snackbarHostState.showSnackbar(
                        if (isAvailable) "Network is available" else "No network available"
                    )
                }
            }
        })

        networkMonitor.startMonitoring()
        onDispose { networkMonitor.stopMonitoring() }
    }

    val markerState = remember { MarkerState(position = LatLng(0.0, 0.0)) }
    var showMapTypeSelector by remember { mutableStateOf(false) }
    var mapType by remember { mutableStateOf(MapType.NORMAL) }
//    var isMapLoaded by remember { mutableStateOf(true) } // Track map loading state
    val cameraPositionState = rememberCameraPositionState()

    // Firebase UID
    val firebaseAuth = FirebaseAuth.getInstance()
    var firebaseUID = firebaseAuth.currentUser?.uid

    // Store ticket ID after the first ticket is created
    var sosTicketId: String? = null
    val drawerState = rememberDrawerState(DrawerValue.Closed)


    // Request permission for location
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Location permission granted")
            }
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let { currentLocation = LatLng(it.latitude, it.longitude) }
            }
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Location permission denied")
            }
        }
    }

    // Check if permission is granted
    LaunchedEffect(Unit) {
        hasLocationPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasLocationPermission) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    LaunchedEffect(currentLocation) {
        currentLocation?.let {
            markerState.position = it
            cameraPositionState.position = CameraPosition.fromLatLngZoom(it, 15f)
        }
    }

    // Launch permission request
    LaunchedEffect(Unit) {
        val hasAudioPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasAudioPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            // Start service directly if permission already granted
            val intent = Intent(context, VoiceRecognitionService::class.java)
            context.startService(intent)
        }
    }

    DisposableEffect(Unit) {
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.locations.firstOrNull()?.let { location ->
                    currentLocation = LatLng(location.latitude, location.longitude)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            LocationRequest.create().apply {
                interval = 5000
                fastestInterval = 2000
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            },
            locationCallback,
            Looper.getMainLooper()
        )

        onDispose {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }



    // Function to handle SOS ticket creation
    suspend fun handleSOSTicket(firebaseUID: String?) {
        Log.d("SOS_TICKET", "Entered handleSOSTicket function.")

        if (firebaseUID.isNullOrEmpty()) {
            Log.e("SOS_TICKET", "Firebase UID is null or empty.")
            return
        }

        val api = Api()
        sosTicketId = api.checkActiveTicket(firebaseUID)

        if (sosTicketId == null) {
            val timestamp = getCurrentTimestamp()
            sosTicketId = api.sendSosTicket(
                firebaseUID = firebaseUID,
                latitude = currentLocation?.latitude.toString(),
                longitude = currentLocation?.longitude.toString(),
                timestamp = timestamp
            )
            if (sosTicketId != null) {
                Log.d("SOS_TICKET", "New SOS ticket created: $sosTicketId")
            } else {
                Log.e("SOS_TICKET", "Failed to create SOS ticket.")
            }
        } else {
            Log.d("SOS_TICKET", "Active ticket found: $sosTicketId")
        }
    }

    // Start the countdown timer and navigate to SOSActivity
    fun startTimer() {
        Log.d("SOS_TIMER", "Starting timer.")
        countdown = 5 // Reset countdown
        isSOSCanceled = false // Reset cancel flag

        // Initialize and start the countdown timer
        countdownTimer = object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                countdown = (millisUntilFinished / 1000).toInt()
                Log.d("SOS_TIMER", "Countdown ticking: $countdown seconds remaining.")
            }

            override fun onFinish() {
                if (isSOSCanceled) {
                    Log.d("SOS_TIMER", "SOS request canceled by the user.")
                    return
                }

                if (!isRequestSent) {
                    Log.d("SOS_TIMER", "SOS request will be sent now.")
                    isRequestSent = true

                    coroutineScope.launch {
                        try {
                            // Fetch Firebase UID from UserSessionManager
                            val sessionData = UserSessionManager.getSession(context)
                            firebaseUID = sessionData["userId"]

                            if (firebaseUID.isNullOrEmpty()) {
                                Log.e("SOS", "Invalid user session. Aborting SOS request.")
                                isRequestSent = false
                                return@launch
                            }

                            // Handle the SOS ticket creation
                            handleSOSTicket(firebaseUID)

                            // Proceed to SOSActivity only if ticket is created successfully
                            if (!sosTicketId.isNullOrEmpty()) {
                                val intent = Intent(context, SOSActivity::class.java).apply {
                                    putExtra("sosTicketId", sosTicketId)
                                }
                                context.startActivity(intent)
                            } else {
                                Log.e("SOS_TIMER", "Failed to create SOS ticket. Cannot navigate to SOSActivity.")
                            }
                        } catch (e: Exception) {
                            Log.e("SOS", "Failed to send SOS request: ${e.localizedMessage}", e)
                        } finally {
                            isRequestSent = false
                        }
                    }
                }
            }
        }.start()
        Log.d("SOS_TIMER", "Timer started.")
    }

    LaunchedEffect(triggerSOS) {
        if (triggerSOS) {
            if (currentLocation != null) {
                homeViewModel.resetSOS()
                showModal = true
                startTimer()
            } else {
                Log.e("SOS", "Location not available.")
            }
        }
    }

    fun sendSMSToContacts(
        context: Context,
        firebaseUID: String,
        ticketId: String,
        latitude: Double?,
        longitude: Double?
    ) {
        if (latitude == null || longitude == null) {
            Log.e("SOS_SMS", "Location not available, cannot send SMS.")
            Toast.makeText(context, "Location not available", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d("SOS_SMS", "Starting SMS sending process. Firebase UID: $firebaseUID, Ticket ID: $ticketId")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = ContactDatabase.getInstance(context)
                val contacts = database.contactDao().getAllContacts()

                if (contacts.isEmpty()) {
                    Log.w("SOS_SMS", "No emergency contacts found in local database.")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "No emergency contacts found", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                Log.d("SOS_SMS", "Emergency contacts fetched from database: ${contacts.size} contacts found.")

                val smsManager = SmsManager.getDefault()
                val url = if (ticketId == "offline_ticket") {
                    "Location: https://www.google.com/maps?q=$latitude,$longitude\nUser ID: $firebaseUID"
                } else {
                    "https://suraksha.pantharinfohub.com/sos/view?ticketId=$ticketId&firebaseUID=$firebaseUID"
                }
                val message = "SOS Alert!\n$url"

                Log.d("SOS_SMS", "Prepared SMS content: $message")

                for (contact in contacts) {
                    try {
                        Log.d("SOS_SMS", "Attempting to send SMS to ${contact.mobile}")
                        smsManager.sendTextMessage(contact.mobile, null, message, null, null)
                        Log.i("SOS_SMS", "SMS sent successfully to ${contact.mobile}")
                    } catch (e: Exception) {
                        Log.e("SOS_SMS", "Failed to send SMS to ${contact.mobile}: ${e.localizedMessage}", e)
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "SOS SMS sent to emergency contacts", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("SOS_SMS", "Error during SMS sending process: ${e.localizedMessage}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to send SMS", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Unified function to handle SOS sending
    fun sendSOS() {
        coroutineScope.launch {
            try {
                Log.d("SOS_FLOW", "Initiating sendSOS function.")

                // Retrieve Firebase UID from SharedPreferences if null
                if (firebaseUID == null) {
                    firebaseUID = UserSessionManager.getSession(context)["userId"]
                    Log.d("SOS_SMS", "Fetched Firebase UID from session: $firebaseUID")
                }

                // Log current location
                if (currentLocation == null) {
                    Log.d("SOS_SMS", "Attempting to fetch current location.")
                    fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                        location?.let {
                            currentLocation = LatLng(it.latitude, it.longitude)
                            Log.d("SOS_SMS", "Fetched location: $currentLocation")
                        } ?: run {
                            Log.e("SOS_SMS", "Unable to fetch location.")
                        }
                    }
                }

                fun isNetworkAvailable(context: Context): Boolean {
                    val connectivityManager =
                        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                    val network = connectivityManager.activeNetwork
                    val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
                    return networkCapabilities != null &&
                            (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
                }

                // Proceed with network-based or SMS-based SOS
                if (firebaseUID != null && currentLocation != null) {
                    if (isNetworkAvailable(context)) {
                        Log.d("SOS_FLOW", "Network is available. Proceeding with API-based SOS ticket handling.")

                        handleSOSTicket(firebaseUID)
                        if (sosTicketId != null) {
                            Log.d("SOS_FLOW", "SOS Ticket ID: $sosTicketId, Location: $currentLocation")

                            val intent = Intent(context, SOSActivity::class.java).apply {
                                putExtra("sosTicketId", sosTicketId)
                            }
                            context.startActivity(intent)
                        } else {
                            Log.e("SOS_TICKET", "SOS Ticket ID is null! Cannot proceed to SOSActivity.")
                        }
                    } else {
                        Log.d("SOS_FLOW", "Network is not available. Attempting to send SMS to emergency contacts.")

                        sendSMSToContacts(
                            context = context,
                            firebaseUID = firebaseUID!!,
                            ticketId = "offline_ticket", // Use a placeholder ticket ID for offline
                            latitude = currentLocation?.latitude,
                            longitude = currentLocation?.longitude
                        )
                    }
                } else {
                    Log.e("SOS_SMS", "Firebase UID or location is null! Cannot send SMS.")
                }
            } catch (e: Exception) {
                Log.e("SOS", "Failed to send SOS request", e)
            } finally {
                Log.d("SOS_FLOW", "Finalizing SOS request. Resetting button state.")

                isLoading = false
                isRequestSent = false
                isProcessCompleted = true
            }
        }
    }

    // Function to manually trigger SOS after "Confirm"
    fun sendSOSManually() {
        if (!isRequestSent) {
            isRequestSent = true
            coroutineScope.launch {
                val intent = Intent(context, SOSActivity::class.java)
                context.startActivity(intent)
                sendSOS()
                isRequestSent = false // Reset after sending SOS
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(
                onProfileClicked = { navController.navigate("dashboard") },
                onEmergencyContactsClicked = {
                    coroutineScope.launch {
                        navController.navigate("emergency_contacts")
                        drawerState.close()
                    }
                },
                onLogoutClicked = {
                    coroutineScope.launch {
                        // Clear user session
                        UserSessionManager.clearSession(context)

                        // Sign out from Firebase
                        FirebaseAuth.getInstance().signOut()

                        // Navigate to Login screen and clear backstack
                        val intent = Intent(context, LoginScreen::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        context.startActivity(intent)
                    }
                },
                onHelpClicked = { /* Handle Help click */ },
                onCloseDrawer = { coroutineScope.launch { drawerState.close() } }
            )
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Profile Icon
                            IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_profile),
                                    contentDescription = "User Profile",
                                    tint = Color.Black,
                                    modifier = Modifier.size(40.dp)
                                )
                            }

                            // Location Section
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.White)
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_location),
                                    contentDescription = "Location Icon",
                                    tint = Color.Blue,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Location",
                                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.Black)
                                )
                            }

                            // Notifications Icon
                            IconButton(onClick = {
                                Toast.makeText(context, "Work on notifications is ongoing", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.outline_notifications_24),
                                    contentDescription = "Notifications",
                                    tint = Color.Black,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    ),
                    scrollBehavior = null
                )
            },
            bottomBar = {
                BottomNavBar(navController)
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Gray.copy(alpha = 0.1f))
                        .weight(1f)
                ) {
                    if (currentLocation != null) {
                        GoogleMap(
                            modifier = Modifier.fillMaxSize(),
                            cameraPositionState = rememberCameraPositionState {
                                position = CameraPosition.fromLatLngZoom(currentLocation!!, 15f)
                            },
                            uiSettings = MapUiSettings(zoomControlsEnabled = false),
                            properties = MapProperties(mapType = mapType)
                        ) {
                            Marker(
                                state = markerState,
                                title = "You are here",
                                snippet = "Real-time Location"
                            )
                        }
                    } else {
                        ShimmerEffect()
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = { showMapTypeSelector = true },
                            modifier = Modifier
                                .background(Color.White, CircleShape)
                                .size(48.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.map_type),
                                contentDescription = "Map Type",
                                tint = Color.Black
                            )
                        }
                    }

                    if (showMapTypeSelector) {
                        Card(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(16.dp)
                                .background(Color.White, shape = RoundedCornerShape(12.dp)),
                            elevation = CardDefaults.cardElevation(8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = "Select Map Type",
                                    fontSize = 18.sp,
                                    color = Color.Black,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = Color.Gray)
                                Spacer(modifier = Modifier.height(8.dp))

                                MapTypeOption("Normal View") { mapType = MapType.NORMAL; showMapTypeSelector = false }
                                MapTypeOption("Satellite View") { mapType = MapType.SATELLITE; showMapTypeSelector = false }
                                MapTypeOption("Terrain View") { mapType = MapType.TERRAIN; showMapTypeSelector = false }
                                MapTypeOption("Hybrid View") { mapType = MapType.HYBRID; showMapTypeSelector = false }
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        if (hasLocationPermission) {
                            showModal = true
                            startTimer()
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(vertical = 24.dp)
                        .size(150.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    shape = CircleShape
                ) {
                    Text(text = "SOS", color = Color.White, fontSize = 40.sp)
                }

                if (showModal && !isRequestSent && isProcessCompleted) {
                    SOSConfirmationDialog(
                        countdown = countdown,
                        isLoading = isLoading,
                        onConfirm = {
                            isLoading = true
                            isProcessCompleted = false
                            sendSOSManually() // Trigger SOS request
                        },
                        onDismiss = {
                            showModal = false
                            isLoading = false
                            isProcessCompleted = true
                            countdownTimer?.cancel()
                            isRequestSent = false
                        }
                    )
                }
            }
        }
    }
}

fun checkNetworkAvailability(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
}

@Composable
fun SOSConfirmationDialog(
    countdown: Int,
    isLoading: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send SOS?") },
        text = {
            Column {
                Text("SOS will be sent in $countdown seconds.")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Do you want to cancel or confirm?")
                if (isLoading) {
                    Spacer(modifier = Modifier.height(8.dp))
                    CircularProgressIndicator()
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isLoading
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DrawerContent(
    onProfileClicked: () -> Unit,
    onEmergencyContactsClicked: () -> Unit,
    onLogoutClicked: () -> Unit,
    onHelpClicked: () -> Unit,
    onCloseDrawer: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(300.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Column {
            // Drawer Header with close button
            DrawerHeader(onCloseDrawer)

            DividerItem()

            // Drawer Items
            DrawerItem(text = "Profile", onClick = onProfileClicked)
            DrawerItem(text = "Emergency Contacts", onClick = onEmergencyContactsClicked)
            DrawerItem(text = "Logout", onClick = onLogoutClicked)
            Spacer(modifier = Modifier.weight(1f))
            DrawerItem(text = "Help", onClick = onHelpClicked)
        }
    }
}

@Composable
fun DrawerHeader(onCloseDrawer: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_profile),
            contentDescription = "Profile Icon",
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "User's Name",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onCloseDrawer) {
            Icon(
                painter = painterResource(id = R.drawable.map_type),
                contentDescription = "Close Drawer"
            )
        }
    }
}

@Composable
fun DrawerItem(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = R.drawable.map_type), // Replace with appropriate icons
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun DividerItem(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(vertical = 8.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    )
}

@Composable
fun BottomNavBar(navController: NavHostController) {
    val context = LocalContext.current
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startVoiceRecognitionService(context)
        } else {
            Log.e("HomeScreen", "Microphone permission denied.")
            Toast.makeText(context, "Microphone permission is required for voice recognition.", Toast.LENGTH_LONG).show()
        }
    }
    NavigationBar {
        NavigationBarItem(
            selected = true,
            onClick = { navController.navigate("home") },
            icon = { Icon(
                painter = painterResource(id = R.drawable.home),
                contentDescription = "Home"
            ) }
        )
        NavigationBarItem(
            selected = false,
            onClick = {
                val hasAudioPermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED

                if (hasAudioPermission) {
                    startVoiceRecognitionService(context)
                } else {
                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            icon = {
                Icon(
                    painter = painterResource(id = R.drawable.search),
                    contentDescription = "Start Voice Recognition"
                )
            }
        )
        NavigationBarItem(
            selected = false,
            onClick = {
                val intent = Intent(context, WatchActivity::class.java)
                context.startActivity(intent)
            },
            icon = { Icon(
                painter = painterResource(id = R.drawable.watch),
                contentDescription = "Home"
            ) }
        )
        NavigationBarItem(
            selected = false,
            onClick = {
                Toast.makeText(context, "Ongoing Work", Toast.LENGTH_SHORT).show()
            },
            icon = { Icon(
                painter = painterResource(id = R.drawable.notifications),
                contentDescription = "Home"
            ) }
        )
        NavigationBarItem(
            selected = false,
            onClick = {
                Toast.makeText(context, "Ongoing Work", Toast.LENGTH_SHORT).show()
            },
            icon = { Icon(
                painter = painterResource(id = R.drawable.history),
                contentDescription = "Home"
            ) }
        )
    }
}

// Function to get current timestamp
fun getCurrentTimestamp(): String {
    val sdf = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
    return sdf.format(Date())
}

@Composable
fun MapTypeOption(name: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(text = name, fontSize = 16.sp, color = Color.Black)
    }
}

@Composable
fun ShimmerEffect() {
    // Shimmer effect for loading state
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.LightGray.copy(alpha = 0.3f))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = Color.Gray)
    }
}
fun startVoiceRecognitionService(context: Context) {
    val intent = Intent(context, VoiceRecognitionService::class.java)
    context.startService(intent)
    Log.d("HomeScreen", "VoiceRecognitionService started for testing.")
}