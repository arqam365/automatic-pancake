package com.example.surakshakavachui

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.surakshakavachui.ui.theme.SurakshaKavachUITheme
import com.example.surakshakavachui.uidesign.GetStartedLogin
import com.example.surakshakavachui.uidesign.MainScreen

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SurakshaKavachUITheme {
                Scaffold{ padding_value->
                    GetStartedLogin(Modifier.padding(padding_value))
                }
            }
        }
    }
}

