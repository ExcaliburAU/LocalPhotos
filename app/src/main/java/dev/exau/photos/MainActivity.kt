package dev.exau.photos

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dev.exau.photos.ui.PhotosApp
import dev.exau.photos.ui.theme.Ink
import dev.exau.photos.ui.theme.LocalPhotosTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val openUri = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data
        setContent {
            LocalPhotosTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Ink) {
                    PhotosApp(openUri = openUri)
                }
            }
        }
    }
}
