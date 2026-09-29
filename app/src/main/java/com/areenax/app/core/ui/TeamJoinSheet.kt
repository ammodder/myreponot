package com.areenax.app.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.areenax.app.core.nav.NavEnv
import com.areenax.app.core.network.ApiResult
import com.areenax.app.core.network.safeCall
import com.areenax.app.core.theme.Type
import com.areenax.app.core.util.formatMoney
import com.areenax.app.core.util.rsMoney

/*
 * TeamJoinSheet — the 1v1/2v2/4v4 side-join sheet (SPEC/03 §19).
 * Joiner becomes CAPTAIN and pays entryFee × playersPerSide.
 * 1v1 → confirm only; 2v2/4v4 → "Use Your Team" (diversity_3) /
 * "Enter Manually" (edit_note); team path validates size ≥ playersPerSide
 * (error copy EXACT); manual = (playersPerSide−1) required UID fields
 * ("Player «i+2» UID is required."); footer shows
 * "Entry fee Rs «fee» × N players" + total (red when insufficient) +
 * "Your balance" + warning; confirm disabled while insufficient;
 * button labels "Join — Rs «total»" (solo) / "Pay Rs «total» & Join".
 *
 * (JoinTeamSheet / SlotPickerSheet / RoomSheet / AmountKeyboard* are DEAD
 * code in the web — deliberately NOT ported; see SPEC/05 guide.)
 */

sealed class TeamJoinChoice {
    data class UseTeam(val memberUids: List<String>) : TeamJoinChoice()
    data class Manual(val memberUids: List<String>) : TeamJoinChoice()
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TeamJoinSheet(
    open: Boolean,
    onClose: () -> Unit,
    env: NavEnv,
    mode: String,
    playersPerSide: Int,
    entryFee: Double,
    balance: Double,
    joining: Boolean,
    onConfirm: (choice: TeamJoinChoice) -> Unit,
) {
    if (!open) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var useTeam by remember(open) { mutableStateOf<Boolean?>(null) }
    var teamUids by remember(open) { mutableStateOf<List<String>>(emptyList()) }
    var manualUids by remember(open) { mutableStateOf<List<String>>(emptyList()) }
    var teamError by remember(open) { mutableStateOf<String?>(null) }

    LaunchedEffect(open) {
        if (open && playersPerSide > 1) {
            val result = safeCall { env.api.team() }
            if (result is ApiResult.Success) {
                teamUids = result.data.team?.members
                    ?.filter { it.userId != env.user?.id }
                    ?.map { it.user.uid }
                    .orEmpty()
            }
        }
    }

    val total = entryFee * playersPerSide
    val insufficient = balance < total
    val manualCount = playersPerSide - 1
    val missingManual = manualUids.take(manualCount).count { it.isBlank() }
    val teamTooSmall = useTeam == true && teamUids.size + 1 < playersPerSide
    val canConfirm = when {
        playersPerSide == 1 -> true
        useTeam == true -> !teamTooSmall
        useTeam == false -> missingManual == 0
        else -> false
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Join as $mode Side",
                style = Type.headlineMd,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "You'll join as the team captain with ${playersPerSide} player(s).",
                style = Type.bodyMd,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (playersPerSide > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceButton(
                        label = "Use Your Team",
                        icon = "diversity_3",
                        active = useTeam == true,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            useTeam = true
                            teamError = if (teamUids.size + 1 < playersPerSide) {
                                "This format needs $playersPerSide players — your team has ${teamUids.size + 1}. Enter members manually instead."
                            } else {
                                null
                            }
                        },
                    )
                    ChoiceButton(
                        label = "Enter Manually",
                        icon = "edit_note",
                        active = useTeam == false,
                        modifier = Modifier.weight(1f),
                        onClick = { useTeam = false; teamError = null },
                    )
                }
                teamError?.let {
                    Text(it, style = Type.labelMd, color = MaterialTheme.colorScheme.error)
                }
                if (useTeam == true && teamUids.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(
                                "${teamUids.size + 1}/$playersPerSide players",
                                style = Type.labelLg,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            teamUids.take(playersPerSide - 1).forEach { uid ->
                                Text("UID: $uid", style = Type.bodyMd, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (useTeam == false) {
                    for (i in 0 until manualCount) {
                        var value by remember(open, i) { mutableStateOf(manualUids.getOrNull(i).orEmpty()) }
                        Column {
                            Text(
                                "Player ${i + 2} UID",
                                style = Type.labelMd,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                            )
                            AreenaxPillField(
                                value = value,
                                onValueChange = { input ->
                                    val digits = input.filter { it.isDigit() }.take(12)
                                    value = digits
                                    manualUids = manualUids.toMutableList().apply {
                                        while (size <= i) add("")
                                        set(i, digits)
                                    }
                                },
                                placeholder = "Enter player UID",
                                leadingIcon = "person_search",
                                textStyle = Type.bodyMd,
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Default, // pre-migration KeyboardOptions(Number)
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (manualUids.getOrNull(i).isNullOrBlank()) {
                            Text(
                                "Player ${i + 2} UID is required.",
                                style = Type.labelMd,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            // Fee breakdown footer
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Entry fee ${rsMoney(entryFee)} × $playersPerSide players",
                        style = Type.bodyMd,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        formatMoney(total),
                        style = Type.headlineMd,
                        color = if (insufficient) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Text(
                        "Your balance: ${formatMoney(balance)}",
                        style = Type.labelMd,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (insufficient) {
                        Text(
                            "Insufficient balance — deposit first to join with your side.",
                            style = Type.labelMd,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            Button(
                onClick = {
                    val choice = when {
                        playersPerSide == 1 -> TeamJoinChoice.Manual(emptyList())
                        useTeam == true -> TeamJoinChoice.UseTeam(teamUids.take(playersPerSide - 1))
                        else -> TeamJoinChoice.Manual(manualUids.take(manualCount).map { it.trim() })
                    }
                    onConfirm(choice)
                },
                enabled = !joining && !insufficient && canConfirm,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                if (joining) {
                    AreenaxSpinner(size = 20, strokeWidth = 2)
                    Spacer(Modifier.size(8.dp))
                }
                Text(
                    if (playersPerSide == 1) "Join — ${rsMoney(total)}" else "Pay ${rsMoney(total)} & Join",
                    style = Type.labelLg,
                )
            }
        }
    }
}

@Composable
private fun ChoiceButton(
    label: String,
    icon: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (active) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLowest
            },
        ),
        modifier = modifier,
    ) {
        AreenaxIcon(
            name = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(6.dp))
        Text(
            label,
            style = Type.labelLg,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}
