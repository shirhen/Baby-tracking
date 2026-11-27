package com.shirhen.babytracker.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shirhen.babytracker.R
import com.shirhen.babytracker.data.EventType

/** Our own line icons (res/drawable/ic_*.xml), used instead of emoji everywhere. */
object AppIcons {
    @DrawableRes
    fun of(type: EventType): Int = when (type) {
        EventType.FEED -> R.drawable.ic_feed
        EventType.DIAPER -> R.drawable.ic_diaper
        EventType.PUMP -> R.drawable.ic_pump
        EventType.BATH -> R.drawable.ic_bath
        EventType.MED -> R.drawable.ic_med
        EventType.CHECKIN -> R.drawable.ic_checkin
    }
}

@Composable
fun AppIcon(
    @DrawableRes res: Int,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    tint: Color = LocalContentColor.current,
    description: String? = null,
) {
    Icon(painterResource(res), contentDescription = description, modifier = modifier.size(size), tint = tint)
}

/** Icon inside a soft circle, e.g. at the start of a list row or a window title. */
@Composable
fun IconBadge(
    @DrawableRes res: Int,
    background: Color,
    tint: Color,
    size: Dp = 40.dp,
    iconSize: Dp = 22.dp,
    modifier: Modifier = Modifier,
) {
    Box(modifier.size(size).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
        AppIcon(res, size = iconSize, tint = tint)
    }
}

@Composable
fun TypeBadge(type: EventType, size: Dp = 40.dp, iconSize: Dp = 22.dp) =
    IconBadge(AppIcons.of(type), Palette.light(type), Palette.inkOf(type), size, iconSize)

/** Icon followed by a title, on one line. */
@Composable
fun IconTitle(
    @DrawableRes res: Int,
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    tint: Color = LocalContentColor.current,
    iconSize: Dp = 20.dp,
    bold: Boolean = true,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppIcon(res, size = iconSize, tint = tint)
        Text(text, style = style, fontWeight = if (bold) FontWeight.Bold else null)
    }
}

/** Window title with the entry's icon in a circle, as in the design. */
@Composable
fun TypeTitle(type: EventType, text: String = Fmt.title(type)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TypeBadge(type)
        Text(text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    }
}
