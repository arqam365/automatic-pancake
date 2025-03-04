package com.nextlevelprogrammers.surakshakawach.uidesign

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.nextlevelprogrammers.surakshakawach.R
import com.nextlevelprogrammers.surakshakawach.Routes

@Composable

fun MainScreenHome(modifier: Modifier, navController: NavController) {
    val user_name = "Sharad"
    val user_profile_picture = painterResource(R.drawable.sosbutton)
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
                Image(
                    painter = user_profile_picture,
                    contentDescription = "Profile Picture",
                    modifier = modifier.size(36.dp)
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
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter){
        Box(modifier.fillMaxWidth().fillMaxHeight(0.885f).align(Alignment.TopCenter), contentAlignment = Alignment.BottomCenter){
            Box(modifier=modifier.fillMaxSize().shadow(2.dp,RoundedCornerShape(28.dp)).clip(RoundedCornerShape(28.dp)).background(Color.Gray))
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