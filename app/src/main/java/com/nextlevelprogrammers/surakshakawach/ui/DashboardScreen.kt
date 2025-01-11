package com.nextlevelprogrammers.surakshakawach.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import kotlinx.coroutines.launch
import com.nextlevelprogrammers.surakshakawach.api.Api
import androidx.compose.ui.draw.clip
import com.nextlevelprogrammers.surakshakawach.api.UserData
import com.nextlevelprogrammers.surakshakawach.R

@Composable
fun DashboardScreen(firebaseUID: String) {
    var userProfile by remember { mutableStateOf<UserData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing)

    // Function to fetch user data
    val onRefresh: () -> Unit = {
        isRefreshing = true
        coroutineScope.launch {
            try {
                val api = Api()
                val response = api.getUserProfile(firebaseUID)
                if (response != null) {
                    userProfile = response.data
                    errorMessage = null
                } else {
                    errorMessage = "Failed to load user data"
                }
            } catch (e: Exception) {
                errorMessage = "Error: ${e.localizedMessage}"
            } finally {
                isRefreshing = false
                isLoading = false
            }
        }
    }

    // Initial Data Fetch
    LaunchedEffect(Unit) {
        onRefresh()
    }

    SwipeRefresh(
        state = swipeRefreshState ,
        onRefresh = { onRefresh() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF2F2F7)) // Light background color
        ) {
            when {
                isLoading -> LoadingIndicator()
                errorMessage != null -> ErrorMessage(message = errorMessage!!)
                userProfile != null -> DashboardContent(userProfile!!)
            }
        }
    }
}

@Composable
fun LoadingIndicator() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = Color(0xFF3A7CA5))
    }
}

@Composable
fun ErrorMessage(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = Color.Red,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun DashboardContent(user: UserData) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            // Header
            Text(
                text = "Welcome, ${user.displayName}",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF3A7CA5)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Profile Info Card
            ProfileCard(user)

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            ActionButton(
                label = "Edit Profile",
                backgroundColor = Color(0xFF3A7CA5),
                onClick = { /* Implement edit action */ }
            )
            ActionButton(
                label = "Settings",
                backgroundColor = Color(0xFF5DB075),
                onClick = { /* Implement settings action */ }
            )
        }
    }
}

@Composable
fun ProfileCard(user: UserData) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(6.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            // Profile Picture Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_profile), // Replace with actual profile image or placeholder
                    contentDescription = "Profile Picture",
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(colors = listOf(Color(0xFF3A7CA5), Color(0xFF79CFF2))))
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = user.displayName,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Divider
            Divider(color = Color(0xFF79CFF2), thickness = 1.dp)

            Spacer(modifier = Modifier.height(8.dp))

            // User Details
            UserInfoRow(iconId = R.drawable.ic_email, label = "Email", value = user.email)
            Spacer(modifier = Modifier.height(8.dp))
            UserInfoRow(iconId = R.drawable.ic_gender, label = "Gender", value = user.gender)
        }
    }
}

@Composable
fun UserInfoRow(iconId: Int, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = iconId),
            contentDescription = null,
            tint = Color(0xFF3A7CA5),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$label:",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = Color.Black
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            fontSize = 16.sp,
            color = Color.DarkGray
        )
    }
}

@Composable
fun ActionButton(label: String, backgroundColor: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth(0.8f)
            .padding(vertical = 8.dp)
    ) {
        Text(text = label, color = Color.White, fontSize = 16.sp)
    }
}