package com.areenax.app.core.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.areenax.app.R
import com.areenax.app.core.nav.NavEnv
import com.areenax.app.core.network.ApiResult
import com.areenax.app.core.network.safeCall
import com.areenax.app.core.theme.Type
import com.areenax.app.core.theme.areenaColors
import com.areenax.app.core.util.formatDateTime

/*
 * RoomInfoSheet — port of src/components/shared/RoomInfoSheet.tsx (SPEC/03 §16).
 * Fetches GET /tournaments/:id FRESH on every open (credentials are only
 * returned for joined users); refresh button re-fetches (epoch).
 * States: spinner → failure copy / "Room details not shared yet" → two rows
 * with copy buttons ("Room ID copied" / "Password copied") + expiry footer.
 * Used by TournamentsScreen + MyTournamentScreen.
 */

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun RoomInfoSheet(
    open: Boolean,
    onClose: () -> Unit,
    tournamentId: String?,
    env: NavEnv,
    modifier: Modifier = Modifier,
    tournamentName: String? = null,
) {
    if (!open || tournamentId == null) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var roomId by remember { mutableStateOf<String?>(null) }
    var roomPassword by remember { mutableStateOf<String?>(null) }
    var expiresAt by remember { mutableStateOf<String?>(null) }
    var epoch by remember { mutableIntStateOf(0) }

    LaunchedEffect(open, tournamentId, epoch) {
        loading = true
        failed = false
        val result = safeCall { env.api.tournamentDetails(tournamentId) }
        when (result) {
            is ApiResult.Success -> {
                val t = result.data.tournament
                roomId = t.roomId
                roomPassword = t.roomPassword
                expiresAt = t.roomExpiresAt
                failed = false
            }
            else -> failed = true
        }
        loading = false
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    tournamentName?.let { "Room Details — $it" } ?: "Room ID & Password",
                    style = Type.headlineMd,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { epoch++ }) {
                    Icon(
                        painterResource(R.drawable.ic_refresh),
                        contentDescription = "Refresh",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            when {
                loading -> Box(Modifier.height(120.dp), contentAlignment = Alignment.Center) {
                    AreenaxSpinner()
                }

                failed -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 24.dp),
                ) {
                    AreenaxIcon("error", null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.error)
                    Text(
                        "Could not load room details. Please try again.",
                        style = Type.bodyMd,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                roomId.isNullOrBlank() || roomPassword.isNullOrBlank() -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 24.dp),
                ) {
                    AreenaxIcon("schedule", null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(
                        "Room details not shared yet",
                        style = Type.headlineMd,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "The admin will share the Room ID & Password here before the match starts.",
                        style = Type.bodyMd,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                else -> {
                    RoomCredentialRow(
                        label = "Room ID",
                        value = roomId.orEmpty(),
                        onCopy = { copyAndToast(it, "Room ID copied", env) },
                    )
                    Spacer(Modifier.height(8.dp))
                    RoomCredentialRow(
                        label = "Password",
                        value = roomPassword.orEmpty(),
                        onCopy = { copyAndToast(it, "Password copied", env) },
                    )
                    expiresAt?.let { expiry ->
                        Spacer(Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_timer_off),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "Visible until ${formatDateTime(expiry)}",
                                style = Type.labelMd,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomCredentialRow(label: String, value: String, onCopy: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(areenaColors().surfaceContainerLavender)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = Type.labelMd, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = Type.labelLg,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(onClick = { onCopy(value) }, modifier = Modifier.size(36.dp)) {
                Icon(
                    painterResource(R.drawable.ic_content_copy),
                    contentDescription = "Copy $label",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

internal fun copyAndToast(text: String, message: String, env: NavEnv) {
    val context = env.context ?: return
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("AREENAX", text))
    env.toast(message)
}
