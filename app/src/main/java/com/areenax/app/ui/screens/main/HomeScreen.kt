package com.areenax.app.ui.screens.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.areenax.app.core.nav.NavEnv
import com.areenax.app.core.nav.ScreenKeys
import com.areenax.app.core.network.ApiResult
import com.areenax.app.core.network.safeCall
import com.areenax.app.core.session.SettingsCache
import com.areenax.app.core.theme.Type
import com.areenax.app.core.theme.areenaColors
import com.areenax.app.core.theme.bannerDarkStart
import com.areenax.app.core.theme.bannerDotDark
import com.areenax.app.core.theme.hairlineLavender
import com.areenax.app.core.ui.AppBar
import com.areenax.app.core.ui.AppBarMode
import com.areenax.app.core.ui.AreenaxSpinner
import com.areenax.app.core.ui.BellButton
import com.areenax.app.core.ui.SupportFab
import com.areenax.app.data.Banner
import com.areenax.app.data.BootstrapResponse
import com.areenax.app.data.Game

/**
 * HomeScreen — key `home` (SPEC/01 B1). 1:1 port of src/components/screens/main/HomeScreen.tsx
 * (behavior truth) + upload/pages_extracted/pages/home.html (pixel truth).
 *
 * AppBar tab mode: avatar + "Welcome," / gameName left slot, BalanceChip + BellButton right.
 * Content (px-4 pb-32 space-y-6):
 *   1. Hero banner — aspect 2:1 rounded-[1.5rem]; first banner with an image as cover,
 *      else the dark gradient placeholder with the dot pattern + "AREENAX" + 9-dot grid;
 *      3 static pagination dots (only when banners.length > 1).
 *   2. Games / Host pill switcher (fade-in tab content, web `.fade-in` 300ms).
 *   3. 2-column games grid (Coil covers, 4:3, top scrim, white footer with bold name).
 *      Games tab tap → tournaments {gameId}; Host tab tap → hostTournament {gameId, gameName}.
 *
 * API: GET /bootstrap — module-level 60s TTL cache (errors never cached), settings pushed
 * into the shared SettingsCache. Games/banners fall back to empty (home still renders).
 * SupportFab (offset=true — above the BottomNav).
 */

/** Web module-level 60s TTL cache for /bootstrap (Home remounts on every tab switch). */
private const val BOOTSTRAP_TTL_MS = 60_000L
private var bootstrapCacheData: BootstrapResponse? = null
private var bootstrapCacheAt: Long = 0L

/** Light-mode hairline used by home.html (#e2e2ec); dark re-inks to outline-variant. */
@Composable
private fun homeHairline(): Color =
    if (areenaColors().isDark) MaterialTheme.colorScheme.outlineVariant else hairlineLavender

@Composable
fun HomeScreen(env: NavEnv) {
    val user by env.session.user.collectAsState()
    val extended = areenaColors()

    var games by remember { mutableStateOf<List<Game>>(emptyList()) }
    var banners by remember { mutableStateOf<List<Banner>>(emptyList()) }
    var tab by remember { mutableStateOf("games") }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val cached = bootstrapCacheData
        if (cached != null && System.currentTimeMillis() - bootstrapCacheAt < BOOTSTRAP_TTL_MS) {
            games = cached.games
            banners = cached.banners
            SettingsCache.cache(cached.settings)
            loading = false
        } else {
            // Home still renders without bootstrap data — errors are swallowed (web parity).
            when (val res = safeCall { env.api.bootstrap() }) {
                is ApiResult.Success -> {
                    bootstrapCacheData = res.data
                    bootstrapCacheAt = System.currentTimeMillis()
                    games = res.data.games
                    banners = res.data.banners
                    SettingsCache.cache(res.data.settings)
                }
                else -> Unit
            }
            loading = false
        }
    }

    val banner = banners.firstOrNull { !it.image.isNullOrBlank() }

    // Captured BEFORE the draw lambdas (MaterialTheme reads are composable-only).
    val glowInner = MaterialTheme.colorScheme.surfaceContainerLow

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                // .bg-areena — radial top glow over surface-lavender (light only; the dark
                // canvas glow lives in AppScaffold, web forces .screen-root transparent).
                if (!extended.isDark) {
                    drawRect(extended.surfaceLavender)
                    drawRect(
                        brush = Brush.radialGradient(
                            0f to glowInner,
                            0.5f to extended.surfaceLavender,
                            1f to extended.surfaceLavender,
                            center = Offset(size.width / 2f, 0f),
                            radius = size.width,
                        ),
                    )
                }
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // ---- Unified MainHeader — avatar + welcome left, balance + bell right ----
            AppBar(
                mode = AppBarMode.Tab,
                balance = true,
                left = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = (user?.gameName ?: "A").take(1).uppercase(),
                                style = Type.bodyLg.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp),
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                        Column {
                            Text(
                                text = "Welcome,",
                                style = Type.labelMd,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = user?.gameName ?: "Player",
                                style = Type.bodyMd.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                right = { BellButton() },
            )

            // ---- Main content (px-4 pb-32 space-y-6) --------------------------------
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 128.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                // 1. Hero banner — aspect-[2/1] rounded-[1.5rem] on black
                HeroBanner(banner = banner, bannerCount = banners.size)

                // 2. Tabs container (switcher + tab content)
                Column {
                    TabSwitcher(tab = tab, onSelect = { tab = it })
                    Spacer(Modifier.height(24.dp)) // tab switcher mb-6

                    // 3. Tab content — web `.fade-in` (300ms ease, translateY 4px → 0)
                    val density = LocalDensity.current
                    AnimatedContent(
                        targetState = tab,
                        transitionSpec = {
                            (
                                fadeIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) +
                                    slideInVertically(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) {
                                        with(density) { 4.dp.roundToPx() }
                                    }
                                ) togetherWith ExitTransition.None
                        },
                        label = "homeTabContent",
                    ) { current ->
                        if (loading) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 64.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                AreenaxSpinner(size = 32, strokeWidth = 2)
                            }
                        } else {
                            GamesGrid(
                                games = games,
                                onGameClick = { game ->
                                    if (current == "games") {
                                        env.navigate(ScreenKeys.TOURNAMENTS, mapOf("gameId" to game.id))
                                    } else {
                                        env.navigate(
                                            ScreenKeys.HOST_TOURNAMENT,
                                            mapOf("gameId" to game.id, "gameName" to game.name),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        // Support FAB — above the bottom nav (web bottom-24 right-6)
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 20.dp),
        ) {
            SupportFab()
        }
    }
}

/** Hero banner — cover image or the AREENAX wordmark placeholder + static pager dots. */
@Composable
private fun HeroBanner(banner: Banner?, bannerCount: Int) {
    val hairline = homeHairline()
    val extended = areenaColors()
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(2f)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black)
            .border(1.dp, hairline, RoundedCornerShape(24.dp)),
    ) {
        if (banner?.image != null) {
            AsyncImage(
                model = banner.image,
                contentDescription = banner.title ?: "AREENAX",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // bg-gradient-to-tr from-gray-900 to-black + 10px dot pattern (opacity-20)
            Box(
                Modifier
                    .fillMaxSize()
                    .drawBehind {
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(bannerDarkStart, Color.Black),
                                start = Offset(0f, size.height),
                                end = Offset(size.width, 0f),
                            ),
                        )
                        val step = 10.dp.toPx()
                        val radius = 1.dp.toPx()
                        var y = 0f
                        while (y <= size.height) {
                            var x = 0f
                            while (x <= size.width) {
                                drawCircle(bannerDotDark, radius, Offset(x, y), alpha = 0.2f)
                                x += step
                            }
                            y += step
                        }
                    },
            )
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "AREENAX",
                        color = Color.White,
                        fontSize = 36.sp, // text-4xl
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.1.em, // tracking-widest
                    )
                    Spacer(Modifier.width(4.dp)) // gap-1
                    NineDotGrid(
                        dotColor = if (extended.isDark) {
                            Color.White.copy(alpha = 0.8f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLowest
                        },
                    )
                }
            }
        }

        // Pagination dots — only meaningful with more than one banner (static, web parity)
        if (bannerCount > 1) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val activeColor = if (extended.isDark) {
                    Color.White.copy(alpha = 0.9f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLowest
                }
                val inactiveColor = if (extended.isDark) {
                    Color.White.copy(alpha = 0.4f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.4f)
                }
                Box(
                    Modifier
                        .size(width = 16.dp, height = 6.dp)
                        .clip(CircleShape)
                        .background(activeColor),
                )
                repeat(2) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(inactiveColor),
                    )
                }
            }
        }
    }
}

/** The "X" of the AREENAX wordmark — 3x3 grid of 6dp dots with 2dp gaps. */
@Composable
private fun NineDotGrid(dotColor: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(3) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(3) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(dotColor),
                    )
                }
            }
        }
    }
}

/** Games / Host pill switcher — white pill container, active = primary (dark: white). */
@Composable
private fun TabSwitcher(tab: String, onSelect: (String) -> Unit) {
    val extended = areenaColors()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(1.dp, homeHairline(), RoundedCornerShape(percent = 50))
            .padding(4.dp),
    ) {
        HomeTab(
            label = "Games",
            active = tab == "games",
            isDark = extended.isDark,
            modifier = Modifier.weight(1f),
            onClick = { onSelect("games") },
        )
        HomeTab(
            label = "Host",
            active = tab == "host",
            isDark = extended.isDark,
            modifier = Modifier.weight(1f),
            onClick = { onSelect("host") },
        )
    }
}

@Composable
private fun HomeTab(
    label: String,
    active: Boolean,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(
                when {
                    active && isDark -> Color.White
                    active -> MaterialTheme.colorScheme.primary
                    else -> Color.Transparent
                },
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 14.sp, // text-sm
            fontWeight = FontWeight.SemiBold,
            color = when {
                active && isDark -> Color.Black
                active -> MaterialTheme.colorScheme.onPrimary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/** 2-column games grid (grid-cols-2 gap-4). Cover 4:3 + bottom scrim + white footer. */
@Composable
private fun GamesGrid(games: List<Game>, onGameClick: (Game) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        games.chunked(2).forEach { rowGames ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                rowGames.forEach { game ->
                    GameCard(game = game, modifier = Modifier.weight(1f), onClick = { onGameClick(game) })
                }
                if (rowGames.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GameCard(game: Game, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        shape = RoundedCornerShape(20.dp), // rounded-[1.25rem] / HTML rounded-[20px]
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = androidx.compose.foundation.BorderStroke(1.dp, homeHairline()),
        shadowElevation = 2.dp, // shadow-card
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .background(areenaColors().surfaceContainerHighLavender),
            ) {
                if (game.image.isNotBlank()) {
                    AsyncImage(
                        model = game.image,
                        contentDescription = game.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // absolute inset-0 bg-gradient-to-t from-black/60 … opacity-60
                Box(
                    Modifier
                        .fillMaxSize()
                        .alpha(0.6f)
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.6f),
                            ),
                        ),
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = game.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.025.em, // tracking-wide
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
