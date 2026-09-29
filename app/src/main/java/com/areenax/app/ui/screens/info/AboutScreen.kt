package com.areenax.app.ui.screens.info

import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.areenax.app.R
import com.areenax.app.core.nav.NavEnv
import com.areenax.app.core.nav.ScreenKeys
import com.areenax.app.core.session.SettingsCache
import com.areenax.app.core.theme.Type
import com.areenax.app.core.theme.areenaColors
import com.areenax.app.core.theme.brandDiscord
import com.areenax.app.core.theme.brandFacebook
import com.areenax.app.core.theme.brandInstagram
import com.areenax.app.core.theme.brandYouTube
import com.areenax.app.core.theme.chevronGray
import com.areenax.app.core.theme.chipBlueSoft
import com.areenax.app.core.theme.chipGreenSoft
import com.areenax.app.core.theme.chipPinkSoft
import com.areenax.app.core.theme.chipRedSoft
import com.areenax.app.core.theme.successGreen
import com.areenax.app.core.ui.AppBar
import com.areenax.app.core.ui.BellButton
import com.areenax.app.core.ui.cardShadow
import java.time.LocalDate

/**
 * AboutScreen — key `about` (SPEC/01 G1; web AboutScreen.tsx, pixel truth about.html).
 *
 * App identity block (96dp rounded-[2rem] white tile with the bundled AREENAX
 * logo — the web's remote googleusercontent image is replaced by the local
 * drawable), "Areenax Tournaments" + Version (settings.version, fallback
 * "1.0.0" per Q11 — server default matches now), Our Mission card (settings.aboutMission with the web's fallback
 * text), Follow Us card with 6 social chips (WhatsApp → wa.me/<settings.whatsapp
 * digits>, Telegram/YouTube/Instagram/Discord → settings links with their web
 * base URLs, Facebook → '#' = no-op) opened via ACTION_VIEW, legal rows
 * (Privacy Policy shield / Terms & Conditions gavel + chevrons), and the
 * copyright footer (dynamic year). The web's mock home-indicator bar is
 * intentionally not ported (native gesture bar).
 */
private const val FALLBACK_MISSION =
    "We are building the ultimate gaming tournament platform where players from around the world can compete, win prizes, and connect with a global community of gamers. Our platform supports multiple games, fair competitions, and transparent prize distribution."

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AboutScreen(env: NavEnv) {
    var settings by remember { mutableStateOf(SettingsCache.getCached()) }

    LaunchedEffect(Unit) {
        SettingsCache.get { env.api }?.let { settings = it }
    }

    val extended = areenaColors()
    val isDark = extended.isDark
    // Card border: #0b1c30/10 light, outline-variant/50 dark (web)
    val cardBorder = if (isDark) {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    } else {
        Color(0x1A0B1C30)
    }
    val legalDivider = if (isDark) {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    } else {
        Color(0x0D0B1C30)
    }

    // ---- Social hrefs (web socialHref + wa.me digits) ---------------------------
    val whatsappDigits = (settings?.whatsapp ?: "").filter { it.isDigit() }
    val whatsappHref = if (whatsappDigits.isNotBlank()) "https://wa.me/$whatsappDigits" else "#"
    val telegramHref = socialHref(settings?.telegram, "https://t.me/")
    val youtubeHref = socialHref(settings?.youtube, "https://youtube.com/")
    val instagramHref = socialHref(settings?.instagram, "https://instagram.com/")
    val discordHref = socialHref(settings?.discord, "https://discord.gg/")
    val facebookHref = "#" // web ships '#'

    val version = settings?.version?.takeIf { it.isNotBlank() } ?: "1.0.0" // A9-08/A13-04: Q11 version story
    val mission = settings?.aboutMission?.takeIf { it.isNotBlank() } ?: FALLBACK_MISSION

    Column(
        Modifier
            .fillMaxSize()
            // radial top-left / bottom-right rgba(0,74,198,0.03) tints over the canvas
            .drawBehind {
                val r = size.width * 1.1f
                drawCircle(
                    color = Color(0x08004AC6),
                    radius = r,
                    center = Offset(0f, 0f),
                )
                drawCircle(
                    color = Color(0x08004AC6),
                    radius = r,
                    center = Offset(size.width, size.height),
                )
            }
            .verticalScroll(rememberScrollState()),
    ) {
        AppBar(title = "About", right = { BellButton() })

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // ---- App identity ---------------------------------------------------
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    shape = RoundedCornerShape(32.dp), // rounded-[2rem]
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shadowElevation = 8.dp,
                    modifier = Modifier.size(96.dp),
                ) {
                    Box(
                        Modifier.padding(8.dp), // p-2
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.areenax_logo),
                            contentDescription = "AREENAX Logo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp)) // mb-4
                Text(
                    text = "Areenax Tournaments",
                    style = Type.headlineLg.copy(fontSize = 20.sp), // text-xl font-bold
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Version $version",
                    style = Type.bodyMd.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ---- Mission card ----------------------------------------------------
            InfoCard(borderColor = cardBorder) {
                Text(
                    text = "Our Mission",
                    style = Type.headlineLgMobile.copy(fontSize = 18.sp), // text-lg font-bold
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 12.dp).semantics { heading() }, // R5
                )
                Text(
                    text = mission,
                    style = Type.bodyMd.copy(fontWeight = FontWeight.Medium, lineHeight = 22.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ---- Follow Us card ---------------------------------------------------
            InfoCard(borderColor = cardBorder) {
                Text(
                    text = "Follow Us",
                    style = Type.headlineLgMobile.copy(fontSize = 18.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 16.dp).semantics { heading() }, // R5
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp), // gap-3
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SocialChip(
                        container = if (isDark) {
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                        } else {
                            chipGreenSoft // green-50
                        },
                        href = whatsappHref,
                        env = env,
                    ) {
                        // WhatsApp: white logo on the brand-green circle
                        Box(
                            Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(extended.whatsappGreen),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_inline_whatsappfab_whatsapp_logo),
                                contentDescription = "WhatsApp",
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                    SocialChip(
                        container = MaterialTheme.colorScheme.surfaceContainerLow,
                        href = telegramHref,
                        env = env,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_send),
                            contentDescription = "Telegram",
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(-25f),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    SocialChip(
                        container = if (isDark) {
                            MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                        } else {
                            chipRedSoft // red-50
                        },
                        href = youtubeHref,
                        env = env,
                    ) {
                        // YouTube mark: red rounded rect + white play triangle
                        Box(
                            Modifier
                                .size(20.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(brandYouTube),
                            contentAlignment = Alignment.Center,
                        ) {
                            Canvas(Modifier.size(10.dp)) {
                                val path = androidx.compose.ui.graphics.Path().apply {
                                    moveTo(size.width * 0.2f, 0f)
                                    lineTo(size.width, size.height / 2f)
                                    lineTo(size.width * 0.2f, size.height)
                                    close()
                                }
                                drawPath(path, color = Color.White)
                            }
                        }
                    }
                    SocialChip(
                        container = if (isDark) {
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f)
                        } else {
                            chipPinkSoft // pink-50
                        },
                        href = instagramHref,
                        env = env,
                    ) {
                        // Instagram mark: rounded-square outline + lens + dot
                        Box(
                            Modifier
                                .size(20.dp)
                                .border(1.8.dp, brandInstagram, RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier
                                    .size(9.dp)
                                    .border(1.8.dp, brandInstagram, CircleShape),
                            )
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 2.dp, end = 2.dp)
                                    .size(2.5.dp)
                                    .clip(CircleShape)
                                    .background(brandInstagram),
                            )
                        }
                    }
                    SocialChip(
                        container = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        href = discordHref,
                        env = env,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_sports_esports),
                            contentDescription = "Discord",
                            modifier = Modifier.size(20.dp),
                            tint = brandDiscord,
                        )
                    }
                    SocialChip(
                        container = if (isDark) {
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f) // A8-07: web dark:bg-secondary/15
                        } else {
                            chipBlueSoft // blue-50
                        },
                        href = facebookHref,
                        env = env,
                    ) {
                        // Facebook mark: blue circle + white f
                        Box(
                            Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(brandFacebook),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "f",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            // ---- Legal links ------------------------------------------------------
            Surface(
                shape = RoundedCornerShape(24.dp), // rounded-3xl
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .cardShadow(RoundedCornerShape(24.dp)),
            ) {
                Column(Modifier.padding(8.dp)) {
                    // Privacy Policy
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { env.navigate(ScreenKeys.PRIVACY) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isDark) {
                                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                                        } else {
                                            chipGreenSoft
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_shield),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isDark) MaterialTheme.colorScheme.secondary else successGreen,
                                )
                            }
                            Text(
                                text = "Privacy Policy",
                                style = Type.bodyMd.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Icon(
                            painter = painterResource(R.drawable.ic_inline_about_chevron_right),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else chevronGray,
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(legalDivider),
                    )
                    // Terms & Conditions
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { env.navigate(ScreenKeys.TERMS) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_gavel),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Text(
                                text = "Terms & Conditions",
                                style = Type.bodyMd.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Icon(
                            painter = painterResource(R.drawable.ic_inline_about_chevron_right),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else chevronGray,
                        )
                    }
                }
            }

            // ---- Footer -----------------------------------------------------------
            Text(
                text = "© ${LocalDate.now().year} Areenax Tournament App. All rights reserved.",
                style = Type.labelMd.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant, // A9-01: was alpha 0.6 → 2.15:1
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp, bottom = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun InfoCard(borderColor: Color, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp), // rounded-3xl
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .cardShadow(RoundedCornerShape(24.dp)),
    ) {
        Column(Modifier.padding(24.dp), content = content)
    }
}

/**
 * Follow Us chip — rounded-2xl pill; `href == "#"` is the web's no-op anchor
 * (Facebook). Real links open in the browser via ACTION_VIEW (web target=_blank).
 */
@Composable
private fun SocialChip(
    container: Color,
    href: String,
    env: NavEnv,
    icon: @Composable () -> Unit,
) {
    Surface(
        onClick = { openExternal(env, href) },
        shape = RoundedCornerShape(16.dp), // rounded-2xl
        color = container,
    ) {
        Box(
            Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            icon()
        }
    }
}

private fun socialHref(value: String?, base: String): String {
    if (value.isNullOrBlank() || value == "#") return "#"
    if (value.startsWith("http")) return value
    return "$base$value"
}

private fun openExternal(env: NavEnv, href: String) {
    if (href == "#") return // web '#' anchor — no navigation
    try {
        env.context.startActivity(Intent(Intent.ACTION_VIEW, href.toUri()))
    } catch (_: Exception) {
        // no browser available — silently ignore, like the web's failed window.open
    }
}
