package com.example.blackslate.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.blackslate.Appearance

private val Dark = darkColorScheme(
    primary = Color(0xFFD9DBE7), onPrimary = Color(0xFF15161B),
    background = Color(0xFF0A0B0D), onBackground = Color(0xFFEDECF2),
    surface = Color(0xFF1B1C21), onSurface = Color(0xFFEDECF2),
    surfaceVariant = Color(0xFF2A2B32), onSurfaceVariant = Color(0xFFA9ABB6),
    outline = Color(0xFF4A4D58), secondary = Color(0xFF31DBAB),
    secondaryContainer = Color(0xFF123C35), onSecondaryContainer = Color(0xFFC3FBEA),
    error = Color(0xFFFF7777)
)
private val Silver = lightColorScheme(
    primary = Color(0xFF282B33), onPrimary = Color.White,
    background = Color(0xFFE4E6EB), onBackground = Color(0xFF191B21),
    surface = Color(0xFFF8F9FC), onSurface = Color(0xFF191B21),
    surfaceVariant = Color(0xFFD3D6DD), onSurfaceVariant = Color(0xFF606573),
    outline = Color(0xFFB9BEC9), secondary = Color(0xFF00856A),
    secondaryContainer = Color(0xFFBCEBDD), onSecondaryContainer = Color(0xFF064C3B),
    error = Color(0xFFB52A35)
)
/**
 * Where the page gradient ends. In Matte Black it stays darker than `surface`, so cards, keys and buckets
 * always sit above the ground; in Silver it is `surfaceVariant`, as before.
 */
val ColorScheme.pageEnd: Color get() = if (background.luminance() < .5f) Color(0xFF101115) else surfaceVariant

@Composable
fun BlackSlateTheme(appearance: Appearance = Appearance.SILVER, content: @Composable () -> Unit) {
    val dark = appearance == Appearance.MATTE || (appearance == Appearance.SYSTEM && isSystemInDarkTheme())
    MaterialTheme(colorScheme = if (dark) Dark else Silver, typography = Typography, content = content)
}
