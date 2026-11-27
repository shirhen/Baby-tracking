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
    // "Botanical garden": soft paper background, deep green accent, one soft colour per kind of entry.
    val background = Color(0xFFF6F6EE)
    val surface = Color(0xFFFFFFFF)
    val ink = Color(0xFF1F2A22)
    val muted = Color(0xFF566259)
    val primary = Color(0xFF2F7D5C)
    val primarySoft = Color(0xFFDDEDE2)
    val line = Color(0xFFE4E4D8)
    val feed = Color(0xFFFCE5C7)
    val diaper = Color(0xFFDCE9F5)
    val poop = Color(0xFFEFE3CF)
    val pump = Color(0xFFE6DDF0)
    val bath = Color(0xFFDDEDE2)
    val warn = Color(0xFFFFF1D6)
    val med = Color(0xFFF8DCDD)
    val checkin = Color(0xFFFAEEDA)
    val neutral = Color(0xFFECECE4)

    // Dark ink that sits on each soft colour (icons and titles).
    val feedInk = Color(0xFF7A4A0A)
    val diaperInk = Color(0xFF1F4466)
    val poopInk = Color(0xFF5C4316)
    val pumpInk = Color(0xFF43306E)
    val bathInk = Color(0xFF276B4E)
    val medInk = Color(0xFF7A2328)
    val checkinInk = Color(0xFF7A5212)
    val neutralInk = Color(0xFF566259)

    // Strong marks used on the clock dial and the hour charts.
    val feedMark = Color(0xFFD9822B)
    val wetMark = Color(0xFF3A7FC4)
    val dirtyMark = Color(0xFF9A6B1F)
    val pumpMark = Color(0xFF7A62C2)
    val bathMark = Color(0xFF2F7D5C)
    val medMark = Color(0xFFC2454C)
    val checkinMark = Color(0xFFBA7517)

    // Night mode: low brightness, warm amber accent, a dim tint per button.
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
        EventType.MED -> med
        EventType.CHECKIN -> checkin
    }

    fun inkOf(type: EventType) = when (type) {
        EventType.FEED -> feedInk
        EventType.DIAPER -> diaperInk
        EventType.PUMP -> pumpInk
        EventType.BATH -> bathInk
        EventType.MED -> medInk
        EventType.CHECKIN -> checkinInk
    }

    fun nightTileOf(type: EventType) = when (type) {
        EventType.FEED -> Color(0xFF3A2A20)
        EventType.DIAPER -> Color(0xFF22303D)
        EventType.PUMP -> Color(0xFF2B2638)
        EventType.BATH -> Color(0xFF1F3329)
        EventType.MED -> Color(0xFF36222A)
        EventType.CHECKIN -> Color(0xFF3A3220)
    }

    fun nightInkOf(type: EventType) = when (type) {
        EventType.FEED -> Color(0xFFE9A47F)
        EventType.DIAPER -> Color(0xFF8FB8DE)
        EventType.PUMP -> Color(0xFFB7AAF0)
        EventType.BATH -> Color(0xFF8FD1AE)
        EventType.MED -> Color(0xFFEFA3C0)
        EventType.CHECKIN -> Color(0xFFF0C987)
    }

    fun mark(e: Event) = when (e.type) {
        EventType.FEED -> feedMark
        EventType.DIAPER -> if (e.dirty) dirtyMark else wetMark
        EventType.PUMP -> pumpMark
        EventType.BATH -> bathMark
        EventType.MED -> medMark
        EventType.CHECKIN -> checkinMark
    }

    fun mark(type: EventType) = when (type) {
        EventType.FEED -> feedMark
        EventType.DIAPER -> wetMark
        EventType.PUMP -> pumpMark
        EventType.BATH -> bathMark
        EventType.MED -> medMark
        EventType.CHECKIN -> checkinMark
    }
}

private val lightScheme = lightColorScheme(
    primary = Palette.primary,
    onPrimary = Color.White,
    primaryContainer = Palette.primarySoft,
    onPrimaryContainer = Color(0xFF1D4A35),
    secondary = Color(0xFF3A7FC4),
    secondaryContainer = Palette.primarySoft,
    onSecondaryContainer = Color(0xFF1D4A35),
    tertiaryContainer = Palette.feed,
    onTertiaryContainer = Palette.ink,
    background = Palette.background,
    onBackground = Palette.ink,
    surface = Palette.surface,
    onSurface = Palette.ink,
    surfaceVariant = Color(0xFFEDEDE3),
    onSurfaceVariant = Palette.muted,
    surfaceContainer = Palette.surface,
    surfaceContainerLow = Color(0xFFFBFBF6),
    surfaceContainerHigh = Color(0xFFF0F0E6),
    outline = Color(0xFFB9C0B5),
    outlineVariant = Palette.line,
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
