package com.nextlevelprogrammers.surakshakawach.ui

import com.nextlevelprogrammers.surakshakawach.api.Api
import android.content.Intent
import androidx.compose.animation.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.nextlevelprogrammers.surakshakawach.HomeActivity
import com.nextlevelprogrammers.surakshakawach.R
import com.nextlevelprogrammers.surakshakawach.api.UserPreferences
import kotlinx.coroutines.delay

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun IntroductionScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val api = Api()
    var isUserExists by remember { mutableStateOf<Boolean?>(null) }
    var currentPage by remember { mutableIntStateOf(0) }
    val descriptions = listOf(
        "Suraksha Kawach ensures your personal safety",
        "Quick emergency alerts and live sharing"
    )

    // Checking user preferences and Firebase user status
    val isUserCreatedInPrefs = remember { UserPreferences.isUserCreated(context) }
    LaunchedEffect(Unit) {
        // If user exists, navigate to HomeActivity
        if (isUserCreatedInPrefs) {
            context.startActivity(Intent(context, HomeActivity::class.java))
        } else {
            val firebaseUID = FirebaseAuth.getInstance().currentUser?.uid
            firebaseUID?.let {
                val userExists = api.checkIfUserExists(it)
                isUserExists = userExists
                if (userExists) {
                    UserPreferences.setUserCreated(context, true)
                    context.startActivity(Intent(context, HomeActivity::class.java))
                }
            } ?: run { isUserExists = false }
        }
    }

    // Carousel page change
    LaunchedEffect(currentPage) {
        delay(3000L)
        currentPage = (currentPage + 1) % descriptions.size
    }

    // Display Introduction screen if user is not authenticated or not in backend
    if (isUserExists == false || !isUserCreatedInPrefs) {
        Scaffold(
            content = { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent)
                        .padding(paddingValues)
                ) {

                    Image(
                        painter = painterResource(id = R.drawable.theme), // Replace with your actual drawable ID
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // App logo
                        Image(
                            painter = painterResource(id = R.drawable.suraksha_kawach_logo),
                            contentDescription = "Suraksha Kawach Logo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(300.dp)
                                .padding(16.dp)
                        )

                        // Animated carousel with descriptions
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedContent(
                                targetState = descriptions[currentPage],
                                transitionSpec = {
                                    (slideInHorizontally(initialOffsetX = { -it }) + fadeIn()).togetherWith(
                                        slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
                                    ) using SizeTransform(clip = false)
                                }
                            ) { description ->
                                Text(
                                    text = description,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }

                        // Pagination dots
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            repeat(2) { index ->
                                val color by animateColorAsState(
                                    targetValue = if (index == currentPage) Color.White else Color.Gray
                                )
                                Box(
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .size(if (index == currentPage) 36.dp else 24.dp, 6.dp)
                                        .background(color, shape = RoundedCornerShape(3.dp))
                                )
                            }
                        }

                        // "Get Started" button
                        Button(
                            onClick = {
                                val intent = Intent(context, LoginScreen::class.java)
                                context.startActivity(intent)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Text(
                                text = "Get Started",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        )
    }
}