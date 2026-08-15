package dev.exau.photos.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val Colors = darkColorScheme(
    primary = Amber,
    onPrimary = Paper,
    secondary = AmberDim,
    onSecondary = Ink,
    background = Ink,
    onBackground = Paper,
    surface = InkRaised,
    onSurface = Paper,
    surfaceVariant = InkSoft,
    onSurfaceVariant = Mist,
    error = Danger,
    onError = Ink,
)

private val Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun LocalPhotosTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Colors,
        typography = Typography,
        shapes = Shapes,
        content = content,
    )
}
