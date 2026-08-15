package dev.exau.photos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.exau.photos.lock.AppLock
import dev.exau.photos.ui.theme.Amber
import dev.exau.photos.ui.theme.Danger
import dev.exau.photos.ui.theme.Ink
import dev.exau.photos.ui.theme.InkRaised
import dev.exau.photos.ui.theme.Mist

@Composable
fun LockScreen(
    lock: AppLock,
    setup: Boolean,
    onUnlocked: () -> Unit,
    onCancel: (() -> Unit)? = null,
) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    if (onCancel != null) BackHandler(onBack = onCancel)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Files", color = Color.White, style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                !setup -> "Enter PIN"
                confirm == null -> "Choose a PIN"
                else -> "Enter it again"
            },
            color = Mist,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            "•".repeat(pin.length).ifBlank { " " },
            color = Amber,
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
        )
        if (!error.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(error ?: "", color = Danger)
        }
        Spacer(Modifier.height(24.dp))
        listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("⌫", "0", "OK"),
        ).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(InkRaised)
                            .clickable {
                                when (key) {
                                    "⌫" -> {
                                        pin = pin.dropLast(1)
                                        error = null
                                    }
                                    "OK" -> {
                                        if (setup) {
                                            if (confirm == null) {
                                                if (pin.length < 4) {
                                                    error = "Use at least 4 digits"
                                                } else {
                                                    confirm = pin
                                                    pin = ""
                                                    error = null
                                                }
                                            } else if (pin == confirm) {
                                                runCatching { lock.setPin(pin) }
                                                    .onSuccess { onUnlocked() }
                                                    .onFailure { error = it.message }
                                            } else {
                                                error = "PINs didn’t match"
                                                confirm = null
                                                pin = ""
                                            }
                                        } else if (lock.matches(pin)) {
                                            onUnlocked()
                                        } else {
                                            error = "Wrong PIN"
                                            pin = ""
                                        }
                                    }
                                    else -> {
                                        if (pin.length < 12) pin += key
                                        error = null
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(key, color = Color.White, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
        if (onCancel != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Cancel",
                color = Mist,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onCancel)
                    .padding(12.dp),
            )
        }
        if (setup && lock.enabled) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Turn off lock",
                color = Danger,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        lock.clear()
                        onUnlocked()
                    }
                    .padding(12.dp),
            )
        }
    }
}
