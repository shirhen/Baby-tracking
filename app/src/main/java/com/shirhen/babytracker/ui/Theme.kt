package com.shirhen.babytracker.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

object Palette {
    val background = Color(0xFFF7F2EE)
    val surface = Color(0xFFFFFFFF)
    val ink = Color(0xFF3B2F2A)
    val muted = Color(0xFF7A6A62)
    val primary = Color(0xFFC0714F)
    val feed = Color(0xFFF6DCC8)
    val diaper = Color(0xFFDCE9F5)
    val pump = Color(0xFFE6DFF3)
    val bath = Color(0xFFD9EEE1)
    val warn = Color(0xFFFFF1D6)
}

private val scheme = lightColorScheme(
    primary = Palette.primary,
    onPrimary = Color.White,
    primaryContainer = Palette.feed,
    onPrimaryContainer = Palette.ink,
    secondary = Color(0xFF6E8FAF),
    secondaryContainer = Palette.diaper,
    onSecondaryContainer = Palette.ink,
    background = Palette.background,
    onBackground = Palette.ink,
    surface = Palette.surface,
    onSurface = Palette.ink,
    surfaceVariant = Color(0xFFF1E8E2),
    onSurfaceVariant = Palette.muted,
    surfaceContainer = Color(0xFFFBF6F2),
)

/** The app is Hebrew-only, so the layout is right-to-left regardless of the phone's language. */
@Composable
fun BabyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            content()
        }
    }
}
