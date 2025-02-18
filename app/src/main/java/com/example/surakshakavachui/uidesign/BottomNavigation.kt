package com.example.surakshakavachui.uidesign

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.surakshakavachui.R
import com.example.surakshakavachui.ui.theme.SurakshaKavachUITheme
import com.nafis.bottomnavigation.NafisBottomNavigation

@Composable
fun MeowBottomNavBar(
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit

) {
    var primary= MaterialTheme.colorScheme.primary
    var onPrimary= MaterialTheme.colorScheme.onPrimary
    var containerColor= MaterialTheme.colorScheme.surfaceContainer
    var backgroundcolor= MaterialTheme.colorScheme.background
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { context ->
            NafisBottomNavigation(context).apply {
                // Set properties
                circleColor = primary.toArgb()
                defaultIconColor = primary.toArgb()
                selectedIconColor = onPrimary.toArgb()
                hasAnimation= true
                backgroundBottomColor= containerColor.toArgb()
                setBackgroundColor(backgroundcolor.toArgb())

                // Add menu items
                add(NafisBottomNavigation.Model(0, R.drawable.home))
                add(NafisBottomNavigation.Model(1, R.drawable.contacts_icon))
                add(NafisBottomNavigation.Model(2, R.drawable.profile_icon))

                setOnClickMenuListener {
                    onItemSelected(it.id)
                }
            }
        },
        update = { view ->
            view.show(selectedIndex, true)
        }
    )
}
