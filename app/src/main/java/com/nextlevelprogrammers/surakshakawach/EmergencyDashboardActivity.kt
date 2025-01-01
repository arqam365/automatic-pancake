package com.nextlevelprogrammers.surakshakawach

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import coil.compose.rememberAsyncImagePainter
import coil.transform.CircleCropTransformation
import com.google.android.gms.maps.model.*
import androidx.media3.exoplayer.*
import androidx.media3.common.MediaItem
import coil.compose.rememberImagePainter
import com.google.maps.android.compose.*
import com.nextlevelprogrammers.surakshakawach.api.*
import com.nextlevelprogrammers.surakshakawach.ui.theme.SurakshaKawachTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class EmergencyDashboardActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Retrieve deep link data
        val data = intent?.data
        val ticketId = data?.getQueryParameter("ticketId")
        val firebaseUID = data?.getQueryParameter("firebaseUID")

        Log.d("EmergencyDashboard", "Ticket ID: $ticketId")
        Log.d("EmergencyDashboard", "Firebase UID: $firebaseUID")

        setContent {
            SurakshaKawachTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    EmergencyDashboardScreen(ticketId, firebaseUID)
                }
            }
        }
    }
}

@Composable
fun EmergencyDashboardScreen(
    ticketId: String?,
    firebaseUID: String?
) {
    // States for ticket data
    var ticketStatus by remember { mutableStateOf<String?>(null) }
    var userName by remember { mutableStateOf("Unknown User") }
    var profileImageUrl by remember { mutableStateOf<String?>(null) }

    // Multimedia and location
    var coordinates by remember { mutableStateOf<Coordinates?>(null) }
    var images by remember { mutableStateOf<List<String>>(emptyList()) }
    var audios by remember { mutableStateOf<List<String>>(emptyList()) }

    // Navigation state
    var selectedTab by remember { mutableStateOf("Map") }

    val coroutineScope = rememberCoroutineScope()

    /**
     * Single LaunchedEffect for syncing all data (status, location, images, audio).
     */
    LaunchedEffect(ticketId, firebaseUID) {
        if (ticketId != null && firebaseUID != null) {
            while (true) {
                coroutineScope.launch {
                    try {
                        // 1) Fetch ticket info
                        val ticketInfo = Api().fetchTicketStatus(firebaseUID, ticketId)
                        if (ticketInfo == null) {
                            Log.e("Sync", "fetchTicketStatus returned null for ticket=$ticketId")
                        } else {
                            ticketStatus = ticketInfo.status ?: "Unknown"
                            userName = ticketInfo.userName ?: "Unknown User"
                            images = ticketInfo.images
                            audios = ticketInfo.audios

                            Log.d("Sync", "Fetched status=$ticketStatus, userName=$userName")
                            Log.d("Sync", "Fetched images=${images.size}: $images")
                            Log.d("Sync", "Fetched audios=${audios.size}: $audios")
                        }

                        // 2) Fetch location
                        val latestCoords = Api().fetchLatestLocation(firebaseUID, ticketId)
                        if (latestCoords == null) {
                            Log.e("Sync", "No coordinates returned for ticket=$ticketId")
                        } else {
                            coordinates = latestCoords
                            Log.d("Sync", "Fetched coordinates=$latestCoords")
                        }
                    } catch (e: Exception) {
                        Log.e("Sync", "Error in fetch loop: ${e.localizedMessage}", e)
                    }
                }
                // Sync every 50 Milliseconds
                delay(500L)
            }
        }
    }

    // Determine color ring based on ticket status
    val statusColor = when (ticketStatus) {
        "closed" -> Color.Red
        "active" -> Color.Green
        else -> Color.Gray
    }

    // Main layout
    Box(modifier = Modifier.fillMaxSize()) {

        // Conditionally show Map / Images / Audio
        when (selectedTab) {
            "Map" -> {
                coordinates?.let {
                    FullScreenMap(
                        latitude = it.latitude,
                        longitude = it.longitude,
                        userName = userName
                    )
                } ?: Text(
                    text = "Location data unavailable",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            "Images" -> {
                ImagesSection(ticketStatus = ticketStatus, images = images)
            }
            "Audio" -> {
                AudioSection(ticketStatus = ticketStatus, audios = audios)
            }
        }

        // Top overlay with profile + status
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color(0xFFE3F2FD), Color(0xFFC8E6C9))
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Profile Image with Status Dot
            Box(
                modifier = Modifier.size(50.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                // Profile (circle)
                Image(
                    painter = rememberImagePainter(
                        data = profileImageUrl ?: "https://via.placeholder.com/150",
                        builder = {
                            transformations(CircleCropTransformation())
                        }
                    ),
                    contentDescription = "Profile Picture",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                )

                // Status Dot
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(statusColor, shape = CircleShape)
                        .align(Alignment.TopEnd)
                        .offset(4.dp, (-4).dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // User Name
            Column {
                Text(
                    text = userName,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                    color = Color.Black
                )
                if (ticketStatus == "closed") {
                    Text(
                        text = "Ticket is closed",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.Red,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }

        // Bottom Navigation
        BottomNavBar(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/** Displays images with a heading. Shows them even if ticket is "closed". */
@Composable
fun ImagesSection(ticketStatus: String?, images: List<String>) {
    Column(
        modifier = Modifier
            .padding(top = 45.dp)
            .fillMaxSize()
    ) {
        Text(
            text = "Images Gallery",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.SemiBold,
                color = if (ticketStatus == "closed") Color.Gray else Color.Black
            ),
            modifier = Modifier.padding(16.dp)
        )

        if (images.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No images yet",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(images.size) { index ->
                    val imageUrl = images[index]
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Image(
                            painter = rememberAsyncImagePainter(model = imageUrl),
                            contentDescription = "Multimedia Image",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }
    }
}

/** Displays audio clips with a heading. Shows them even if ticket is "closed". */
@Composable
fun AudioSection(ticketStatus: String?, audios: List<String>) {
    Column(
        modifier = Modifier
            .padding(top = 45.dp)
            .fillMaxSize()
    ) {
        Text(
            text = "Audio Clips",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.SemiBold,
                color = if (ticketStatus == "closed") Color.Gray else Color.Black
            ),
            modifier = Modifier.padding(16.dp)
        )

        if (audios.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No audio clips yet",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(audios.size) { index ->
                    val audioUrl = audios[index]
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        AudioPlayer(audioUrl, index + 1)
                    }
                }
            }
        }
    }
}

/** Composable for playing audio from URL using Media3 ExoPlayer. */
@Composable
fun AudioPlayer(audioUrl: String, index: Int) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(audioUrl)
            setMediaItem(mediaItem)
            prepare()
        }
    }

    var isPlaying by remember { mutableStateOf(false) }

    // Keep player in sync with isPlaying
    LaunchedEffect(isPlaying) {
        exoPlayer.playWhenReady = isPlaying
    }

    // Release resources on disposal
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    // Simple UI row
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { isPlaying = !isPlaying }) {
            val iconId = if (isPlaying) R.drawable.pause else R.drawable.play
            Icon(
                painter = painterResource(id = iconId),
                contentDescription = if (isPlaying) "Pause" else "Play"
            )
        }
        Text(
            text = "Audio $index",
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
        )
    }
}

/** Map composable */
@Composable
fun FullScreenMap(latitude: Double, longitude: Double, userName: String) {
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(latitude, longitude), 17f)
    }
    val markerState = remember { MarkerState(position = LatLng(latitude, longitude)) }
    var isZoomInitialized by remember { mutableStateOf(false) }

    // Update marker position if coordinates change, but keep the initial zoom
    LaunchedEffect(latitude, longitude) {
        markerState.position = LatLng(latitude, longitude)
        if (!isZoomInitialized) {
            cameraPositionState.position =
                CameraPosition.fromLatLngZoom(markerState.position, 17f)
            isZoomInitialized = true
        } else {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(
                markerState.position,
                cameraPositionState.position.zoom
            )
        }
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        uiSettings = MapUiSettings(zoomControlsEnabled = true),
        properties = MapProperties(mapType = MapType.NORMAL)
    ) {
        Marker(
            state = markerState,
            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE),
            title = userName,
            snippet = "User's location"
        )
    }
}

/** Bottom navigation: Map, Images, Audio. */
@Composable
fun BottomNavBar(
    selectedTab: String,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 4.dp,
    ) {
        NavigationBarItem(
            selected = selectedTab == "Map",
            onClick = { onTabSelected("Map") },
            icon = {
                Icon(
                    painter = painterResource(id = R.drawable.map_type),
                    contentDescription = "Map"
                )
            },
            label = { Text("Map") }
        )
        NavigationBarItem(
            selected = selectedTab == "Images",
            onClick = { onTabSelected("Images") },
            icon = {
                Icon(
                    painter = painterResource(id = R.drawable.baseline_image),
                    contentDescription = "Images"
                )
            },
            label = { Text("Images") }
        )
        NavigationBarItem(
            selected = selectedTab == "Audio",
            onClick = { onTabSelected("Audio") },
            icon = {
                Icon(
                    painter = painterResource(id = R.drawable.baseline_audiotrack),
                    contentDescription = "Audio"
                )
            },
            label = { Text("Audio") }
        )
    }
}