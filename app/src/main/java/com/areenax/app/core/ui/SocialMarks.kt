package com.areenax.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.areenax.app.R
import com.areenax.app.core.theme.brandDiscord
import com.areenax.app.core.theme.brandFacebook
import com.areenax.app.core.theme.brandInstagram
import com.areenax.app.core.theme.brandYouTube

/**
 * ICON-01: the Profile social row used to render REMOTE googleusercontent
 * images (offline → blank boxes, URLs rot, two different social renderers in
 * one app). This composable draws the SAME local marks AboutScreen uses, so
 * every social row is offline-capable and visually consistent. Brand marks
 * are simplified in-app recreations, not bundled third-party assets.
 */
@Composable
fun SocialMark(alt: String, size: Dp = 20.dp, modifier: Modifier = Modifier) {
    when (alt.lowercase()) {
        "whatsapp" -> {
            Box(
                modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_inline_whatsappfab_whatsapp_logo),
                    contentDescription = alt,
                    modifier = Modifier.size(size * 0.68f),
                    tint = Color.White,
                )
            }
        }
        "telegram" -> Icon(
            painter = painterResource(R.drawable.ic_send),
            contentDescription = alt,
            modifier = modifier
                .size(size)
                .rotate(-25f),
            tint = MaterialTheme.colorScheme.primary,
        )
        "youtube" -> {
            Box(
                modifier
                    .size(size)
                    .clip(RoundedCornerShape((size.value / 4).dp))
                    .background(brandYouTube),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(size / 2)) {
                    val canvasSize = this.size // explicit DrawScope.size (param shadows)
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(canvasSize.width * 0.2f, 0f)
                        lineTo(canvasSize.width, canvasSize.height / 2f)
                        lineTo(canvasSize.width * 0.2f, canvasSize.height)
                        close()
                    }
                    drawPath(path, color = Color.White)
                }
            }
        }
        "instagram" -> {
            val pink = brandInstagram
            Box(
                modifier
                    .size(size)
                    .border(size * 0.09f, pink, RoundedCornerShape(size / 3.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(size * 0.45f).border(size * 0.09f, pink, CircleShape))
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding((size.value * 0.1f).dp)
                        .size(size * 0.12f)
                        .clip(CircleShape)
                        .background(pink),
                )
            }
        }
        "discord" -> Icon(
            painter = painterResource(R.drawable.ic_sports_esports),
            contentDescription = alt,
            modifier = modifier.size(size),
            tint = brandDiscord,
        )
        "facebook" -> {
            Box(
                modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(brandFacebook),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "f",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        else -> Icon(
            painter = painterResource(R.drawable.ic_help),
            contentDescription = alt,
            modifier = modifier.size(size),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
