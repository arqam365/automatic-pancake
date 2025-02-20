package com.example.surakshakavachui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.surakshakavachui.ui.theme.SurakshaKavachUITheme
import com.example.surakshakavachui.uidesign.MainScreen

class HomeScreenActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SurakshaKavachUITheme {
                Scaffold{ padding_value->
                    MainScreen(Modifier.padding(padding_value))
                }
            }
        }
    }
}
