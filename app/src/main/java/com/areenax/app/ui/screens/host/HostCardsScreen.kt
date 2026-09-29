package com.areenax.app.ui.screens.host

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.areenax.app.core.nav.NavEnv
import com.areenax.app.core.nav.ScreenKeys
import com.areenax.app.core.network.ApiResult
import com.areenax.app.core.network.safeCall
import com.areenax.app.core.ui.AreenaxSpinner
import com.areenax.app.core.ui.EmptyState
import com.areenax.app.data.Tournament

/**
 * HostCardsScreen — key `hostTournamentCard` (SPEC/01 F4; web HostCardsScreen.tsx,
 * pixel truth hosttournamentcard.html).
 *
 * Simple "my created tournaments" list: AppBar "My Tournaments", `GET /my/hosted`,
 * HostCard replicas (shared with HostTournamentScreen) → tap navigates to
 * hostTournamentDetails {tournamentId}; trophy empty state while list is empty,
 * centered spinner while loading.
 */
@Composable
fun HostCardsScreen(env: NavEnv) {
    var tournaments by remember { mutableStateOf<List<Tournament>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        when (val res = safeCall { env.api.myHosted() }) {
            is ApiResult.Success -> tournaments = res.data.tournaments
            else -> tournaments = emptyList()
        }
        loading = false
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Transparent),
    ) {
        AppBarHost(title = "My Tournaments")

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            when {
                loading -> {
                    // flex-1 centered spinner (min-h-[18.75rem])
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .offset(y = (-80).dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        AreenaxSpinner(size = 32, strokeWidth = 2)
                    }
                }

                tournaments.isEmpty() -> {
                    EmptyState(message = "You haven't hosted any tournaments yet.")
                }

                else -> {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp), // gap-3
                    ) {
                        tournaments.forEach { tournament ->
                            HostTournamentCard(
                                tournament = tournament,
                                onClick = {
                                    env.navigate(
                                        ScreenKeys.HOST_TOURNAMENT_DETAILS,
                                        mapOf("tournamentId" to tournament.id),
                                    )
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp)) // pb-6
                }
            }
        }
    }
}
