package com.nextlevelprogrammers.surakshakawach.uidesign

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.nextlevelprogrammers.surakshakawach.R
import com.nextlevelprogrammers.surakshakawach.Routes
import com.nextlevelprogrammers.surakshakawach.utils.LocationUtils

@Composable

fun MainScreenHome(modifier: Modifier, navController: NavController, auth: FirebaseAuth) {
    val user= auth.currentUser
    val user_name = user?.displayName
    val user_profile_picture = user?.photoUrl
    Column(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .padding(top = 12.dp)
    )
    {
        Row(
            modifier = modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        )
        {
            Text(
                text = "Hi, $user_name!",
                fontSize = 24.sp
            )
            Box(
                modifier = modifier.size(44.dp).clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = user_profile_picture,
                    contentDescription = "Profile Picture",
                    modifier = modifier.size(36.dp).clip(CircleShape)
                )
            }
        }

        Box(modifier=modifier.weight(1f).fillMaxSize().padding(12.dp))
        {
            SOSDisplay(modifier, navController)
        }
    }
}

@Composable
fun SOSDisplay(modifier: Modifier, navController: NavController){
    val locationUtils = remember { LocationUtils(navController.context) }
    val userLocation = remember { mutableStateOf(LatLng(25.4485, 78.5689)) }

    // Fetch location on startup
    LaunchedEffect(Unit) {
        locationUtils.getLastKnownLocation { lat, long ->
            userLocation.value = LatLng(lat, long)
            Log.d("GoogleMap", "User Location: $lat, $long")
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter){
        Box(modifier.fillMaxWidth().fillMaxHeight(0.885f).align(Alignment.TopCenter), contentAlignment = Alignment.BottomCenter){
            Box(modifier=modifier.fillMaxSize().shadow(2.dp,RoundedCornerShape(28.dp)).clip(RoundedCornerShape(28.dp)).background(Color.Gray)) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = rememberCameraPositionState {
                        position = CameraPosition.fromLatLngZoom(userLocation.value, 15f)
                    }
                ) {
                    Marker(
                        state = rememberMarkerState(position = userLocation.value),
                        title = "Your Location"
                    )
                }
            }
            IconButton(modifier=modifier.size(150.dp).offset(y=(75.dp)),
                onClick = {navController.navigate(Routes.COUNTDOWN_SCREEN)}){
                Image(
                    painter=painterResource(R.drawable.sosbutton),
                    contentDescription = "SOSButton"
                )
            }
        }

    }
}