package com.nextlevelprogrammers.surakshakawach.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import kotlinx.coroutines.launch
import com.nextlevelprogrammers.surakshakawach.api.Api
import androidx.compose.ui.draw.clip
import com.nextlevelprogrammers.surakshakawach.api.UserData
import com.nextlevelprogrammers.surakshakawach.R // Ensure you replace with correct resource import for your icons

@Composable
fun DashboardScreen(firebaseUID: String) {
    var userProfile by remember { mutableStateOf<UserData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            val api = Api()
            val response = api.getUserProfile(firebaseUID)
            if (response != null) {
                userProfile = response.data
                isLoading = false
            } else {
                errorMessage = "Failed to load user data"
                isLoading = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF2F2F7))) { // Light background color for better contrast
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF3A7CA5))
            }
        } else {
            errorMessage?.let {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = it, color = Color.Red, fontWeight = FontWeight.SemiBold)
                }
            } ?: run {
                userProfile?.let { user ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 32.dp),
                        verticalArrangement = Arrangement.Top,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Header
                        Text(
                            text = "Welcome, ${user.displayName}",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF3A7CA5)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Profile Info Card
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
                                // Profile picture placeholder
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_profile), // Replace with profile image or placeholder
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

                                // User details
                                Divider(color = Color(0xFF79CFF2), thickness = 1.dp)

                                Spacer(modifier = Modifier.height(8.dp))

                                // Email
                                UserInfoRow(iconId = R.drawable.ic_email, label = "Email", value = user.email)

                                Spacer(modifier = Modifier.height(8.dp))

                                // Gender
                                UserInfoRow(iconId = R.drawable.ic_gender, label = "Gender", value = user.gender)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Actions
                        Button(
                            onClick = { /* Implement action */ },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A7CA5)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .padding(vertical = 8.dp)
                        ) {
                            Text(text = "Edit Profile", color = Color.White, fontSize = 16.sp)
                        }

                        Button(
                            onClick = { /* Implement action */ },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5DB075)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .padding(vertical = 8.dp)
                        ) {
                            Text(text = "Settings", color = Color.White, fontSize = 16.sp)
                        }
                    }
                }
            }
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