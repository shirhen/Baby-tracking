package com.shirhen.babytracker.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.shirhen.babytracker.data.Event
import com.shirhen.babytracker.data.EventType
import java.time.LocalTime

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

    // Strong marks used on the clock dial.
    val feedMark = Color(0xFFD85A30)
    val wetMark = Color(0xFF378ADD)
    val dirtyMark = Color(0xFFBA7517)
    val pumpMark = Color(0xFF7F77DD)
    val bathMark = Color(0xFF1D9E75)

    // Night mode: low brightness, warm amber accent.
    val nightBg = Color(0xFF121418)
    val nightTile = Color(0xFF23262D)
    val nightText = Color(0xFFEDE6DA)
    val nightMuted = Color(0xFF9AA0AE)
    val nightAmber = Color(0xFFFAC775)

    fun light(type: EventType) = when (type) {
        EventType.FEED -> feed
        EventType.DIAPER -> diaper
        EventType.PUMP -> pump
        EventType.BATH -> bath
    }

    fun mark(e: Event) = when (e.type) {
        EventType.FEED -> feedMark
        EventType.DIAPER -> if (e.dirty) dirtyMark else wetMark
        EventType.PUMP -> pumpMark
        EventType.BATH -> bathMark
    }

    fun mark(type: EventType) = when (type) {
        EventType.FEED -> feedMark
        EventType.DIAPER -> wetMark
        EventType.PUMP -> pumpMark
        EventType.BATH -> bathMark
    }
}

private val lightScheme = lightColorScheme(
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

private val nightScheme = darkColorScheme(
    primary = Palette.nightAmber,
    onPrimary = Color(0xFF412402),
    primaryContainer = Color(0xFF3A2F22),
    onPrimaryContainer = Palette.nightText,
    secondary = Color(0xFF85B7EB),
    secondaryContainer = Color(0xFF22303F),
    onSecondaryContainer = Palette.nightText,
    background = Palette.nightBg,
    onBackground = Palette.nightText,
    surface = Color(0xFF1B1E24),
    onSurface = Palette.nightText,
    surfaceVariant = Palette.nightTile,
    onSurfaceVariant = Palette.nightMuted,
    surfaceContainer = Color(0xFF1B1E24),
)

/** Night hours for the automatic night mode. */
fun isNightHour(time: LocalTime = LocalTime.now()) = time.hour >= 22 || time.hour < 6

/** The app is Hebrew-only, so the layout is right-to-left regardless of the phone's language. */
@Composable
fun BabyTheme(dark: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) nightScheme else lightScheme) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            content()
        }
    }
}
