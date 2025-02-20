package com.example.surakshakavachui.uidesign

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.surakshakavachui.R

@Composable
fun MainScreen(modifier: Modifier){

    val BottomShadowShape = GenericShape { size, _ ->
        moveTo(0f, 0f)
        lineTo(size.width, 0f)
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }

    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            Box(modifier=Modifier.fillMaxWidth()
                .shadow(elevation = 4.dp, shape = BottomShadowShape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                ){
                Row(
                    modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(R.drawable.logo),
                        contentDescription = "SK_Logo",
                        modifier = Modifier.size(44.dp)
                    )
                    Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.background).clickable {  },
                        contentAlignment = Alignment.Center){
                        Icon(
                            painter = painterResource(R.drawable.question_mark_circled_icon),
                            contentDescription = "Support",
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        bottomBar = {
            MeowBottomNavBar(selectedIndex = selectedIndex
            ) { newIndex ->
                selectedIndex = newIndex
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedIndex) {
                0 -> MainScreenHome(Modifier)
                1 -> MainScreenContactRoot(Modifier)
                2 -> MainScreenProfile()
            }
        }
    }
}

