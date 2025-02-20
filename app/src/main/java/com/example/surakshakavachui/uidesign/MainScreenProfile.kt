package com.example.surakshakavachui.uidesign

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Male
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.surakshakavachui.R
import com.example.surakshakavachui.ui.theme.SurakshaKavachUITheme

@Composable
fun MainScreenProfile(modifier: Modifier=Modifier){
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center){
        ProfileCard()
    }
}

@Composable
fun ProfileCard(modifier: Modifier=Modifier) {
    val user_name="Sharad Pratap Singh"
    val user_email="sharadsengar2003@gmail.com"
    val user_gender="Male"
    Box(modifier=modifier.shadow(4.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.secondaryContainer)){
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp),
            modifier = modifier.padding(20.dp, 32.dp)
        ) {
            Image(
                modifier = modifier.size(120.dp),
                contentDescription = "UserProfilePicture",
                painter = painterResource(R.drawable.sosbutton)
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)){
                Text(
                    text = user_name,
                    textAlign = TextAlign.Center,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = user_email,
                    textAlign = TextAlign.Center,
                    fontSize = 18.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = user_gender,
                        textAlign = TextAlign.Center,
                        fontSize = 18.sp
                    )
                    Icon(
                        imageVector = Icons.Default.Male,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
            Button(onClick = {},
                modifier=modifier.fillMaxWidth(0.8f),
                shape = RoundedCornerShape(16.dp)
                ) {
                Text("Sign Out",
                    fontSize = 20.sp)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DisplyProfile(){
    SurakshaKavachUITheme {
        MainScreenProfile()
    }
}

