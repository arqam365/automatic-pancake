package com.example.surakshakavachui.uidesign

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Male
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(12.dp)) {
        Image(
            modifier = modifier.size(120.dp),
            contentDescription = "UserProfilePicture",
            painter = painterResource(R.drawable.sosbutton)
        )
        Spacer(modifier=Modifier.height(12.dp))
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
        Row(verticalAlignment = Alignment.CenterVertically){
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
        Spacer(modifier.height(8.dp))
        Button(onClick = {}){
            Text("Sign Out")
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

