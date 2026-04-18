package com.lazycontainer.compose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.lazycontainer.compose.demo.SettingsScreen
import com.lazycontainer.compose.ui.theme.LazyContainerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LazyContainerTheme {
                SettingsScreen()
            }
        }
    }
}
