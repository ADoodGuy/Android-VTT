package com.adoodguy.androidvtt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.adoodguy.androidvtt.tabletop.TabletopMapHost
import com.adoodguy.androidvtt.tabletop.TabletopMapStore
import com.adoodguy.androidvtt.tabletop.TabletopScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TabletopMapStore.initialize(applicationContext)
        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    TabletopMapHost {
                        TabletopScreen()
                    }
                }
            }
        }
    }
}
