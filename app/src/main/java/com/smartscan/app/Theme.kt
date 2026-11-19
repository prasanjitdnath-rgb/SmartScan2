package com.smartscan.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val Brand = Color(0xFF1A56DB)
val BrandDark = Color(0xFF0B3AA8)

private val LightScheme = lightColorScheme(
    primary = Brand, onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6FF), onPrimaryContainer = Color(0xFF0A2A7A),
    secondary = Color(0xFF00897B), onSecondary = Color.White,
    background = Color(0xFFF4F6FB), onBackground = Color(0xFF111827),
    surface = Color.White, onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFE8ECF6), onSurfaceVariant = Color(0xFF49515F)
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF8AB4FF), onPrimary = Color(0xFF002A78),
    primaryContainer = Color(0xFF1B3A7A), onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = Color(0xFF4DB6AC), onSecondary = Color(0xFF00201C),
    background = Color(0xFF0E1117), onBackground = Color(0xFFE6E9F0),
    surface = Color(0xFF171B24), onSurface = Color(0xFFE6E9F0),
    surfaceVariant = Color(0xFF232A36), onSurfaceVariant = Color(0xFFB4BAC6)
)

@Composable
fun SmartScanTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography(),
        shapes = Shapes(
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp)
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = scheme.background) { content() }
    }
}
