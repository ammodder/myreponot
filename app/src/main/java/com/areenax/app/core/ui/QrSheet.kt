package com.areenax.app.core.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.areenax.app.R
import com.areenax.app.core.nav.NavEnv
import com.areenax.app.core.nav.ScreenKeys
import com.areenax.app.core.network.ApiResult
import com.areenax.app.core.network.safeCall
import com.areenax.app.core.theme.Type
import com.areenax.app.core.theme.areenaColors
import com.areenax.app.core.util.QrKind
import com.areenax.app.core.util.buildQrLink
import com.areenax.app.core.util.parseQrPayload
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch

/*
 * QrSheet — port of src/components/shared/QrSheet.tsx (SPEC/03 §21, §00 §2i).
 * Used by ProfileScreen (user, scan), FriendsScreen (user, scan), MyTeamScreen
 * (team, mine). Tabs: My QR / Scan QR (segmented pill, active = white pill on
 * a balance-chip container).
 *
 * Display: locally generated deep-link QR (ZXing core, 512px, margin 2,
 * #0b1c30 on white) — `<app_base_url>/?qr=u%3A<uid>` / `t%3A<teamId>`.
 * Share: Android share sheet with the EXACT web copy; clipboard fallback
 * toasts "Copied!".
 * Scan: the camera runs in the zxing-embedded CaptureActivity (ScanContract);
 * decoded text → parseQrPayload → GET /qr/lookup → result cards. (The web has
 * an in-sheet viewfinder; native delegates to the scanner activity — same
 * lookup flow, same result cards, same toasts.)
 * Manual fallback: numeric UID field ("e.g. 782104") + Find.
 */

enum class QrTab { MINE, SCAN }
enum class QrTarget { USER, TEAM }

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun QrSheet(
    open: Boolean,
    onClose: () -> Unit,
    env: NavEnv,
    target: QrTarget,
    modifier: Modifier = Modifier,
    teamId: String? = null,
    teamName: String? = null,
    initialTab: QrTab = QrTab.MINE,
) {
    if (!open) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tab by remember(open) { mutableStateOf(initialTab) }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Segmented pill
            Surface(
                shape = CircleShape,
                color = areenaColors().balanceChip,
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                Row(Modifier.padding(4.dp)) {
                    QrTabButton("My QR", tab == QrTab.MINE) { tab = QrTab.MINE }
                    QrTabButton("Scan QR", tab == QrTab.SCAN) { tab = QrTab.SCAN }
                }
            }

            when (tab) {
                QrTab.MINE -> MineTab(env, target, teamId, teamName)
                QrTab.SCAN -> ScanTab(env)
            }
        }
    }
}

@Composable
private fun QrTabButton(label: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (active) MaterialTheme.colorScheme.surfaceContainerLowest else Color.Transparent,
    ) {
        Text(
            label,
            style = Type.labelLg,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun MineTab(env: NavEnv, target: QrTarget, teamId: String?, teamName: String?) {
    val user = env.user
    val context = LocalContext.current
    val payloadId = if (target == QrTarget.USER) user?.uid ?: "" else (teamId ?: "")
    val link = remember(payloadId) {
        buildQrLink(if (target == QrTarget.USER) QrKind.USER else QrKind.TEAM, payloadId, env.baseUrl)
    }
    val shareText = if (target == QrTarget.USER) {
        "Add me on AREENAX! Scan my QR or use my UID ${user?.uid ?: ""}."
    } else {
        "Join my team $teamName on AREENAX! Scan this QR."
    }
    val infoLine = if (target == QrTarget.USER) {
        "Anyone who scans this QR will be taken straight to your profile to add you."
    } else {
        "Anyone who scans this QR will be taken straight to your team to send a join request."
    }

    Spacer(Modifier.height(12.dp))
    QRCodeImage(content = link, modifier = Modifier.size(176.dp))
    Spacer(Modifier.height(16.dp))

    if (target == QrTarget.USER) {
        Text(user?.gameName ?: "", style = Type.headlineMd, color = MaterialTheme.colorScheme.onSurface)
        Text(
            "UID: ${user?.uid ?: ""}",
            style = Type.bodyMd,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Scan to add me as a friend",
            style = Type.bodyMd,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Text(teamName ?: "", style = Type.headlineMd, color = MaterialTheme.colorScheme.onSurface)
        Text(
            "Scan to view & join my team",
            style = Type.bodyMd,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(16.dp))

    OutlinedButton(
        onClick = {
            // Android share sheet; clipboard fallback + "Copied!" toast (web parity)
            val sent = try {
                context.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        },
                        "Share",
                    ),
                )
                true
            } catch (_: Exception) {
                false
            }
            if (!sent) {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("AREENAX", shareText))
                env.toast("Copied!")
            }
        },
        shape = CircleShape,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Icon(
            painterResource(R.drawable.ic_share), null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text("Share", style = Type.labelLg)
    }
    Spacer(Modifier.height(12.dp))
    Text(
        infoLine,
        style = Type.labelMd,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}

@Composable
private fun ScanTab(env: NavEnv) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var lookupUser by remember { mutableStateOf<com.areenax.app.data.QrUserLookup?>(null) }
    var lookupTeam by remember { mutableStateOf<com.areenax.app.data.QrTeamLookup?>(null) }
    var resolving by remember { mutableStateOf(false) }
    var manualUid by remember { mutableStateOf("") }
    var busyCard by remember { mutableStateOf(false) }

    fun resetResults() {
        lookupUser = null
        lookupTeam = null
    }

    suspend fun resolve(rawText: String) {
        val payload = parseQrPayload(rawText)
        if (payload == null) {
            env.toast("This is not an AREENAX QR code.", ToastVariant.Destructive)
            return
        }
        resolving = true
        resetResults()
        val result = safeCall {
            if (payload.kind == QrKind.USER) {
                env.api.qrLookup(uid = payload.id)
            } else {
                env.api.qrLookup(teamId = payload.id)
            }
        }
        resolving = false
        when (result) {
            is ApiResult.Success -> {
                val body = result.data
                lookupUser = body.asUser
                lookupTeam = body.asTeam
                if (body.asUser == null && body.asTeam == null) {
                    env.toast("No player/team found for this code.", ToastVariant.Destructive)
                }
            }
            is ApiResult.Error -> env.toast(result.message, ToastVariant.Destructive)
            is ApiResult.NetworkError -> env.toast(result.message, ToastVariant.Destructive)
        }
    }

    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { contents ->
            scope.launch { resolve(contents) }
        }
    }

    Spacer(Modifier.height(12.dp))
    if (lookupUser != null || lookupTeam != null) {
        lookupUser?.let {
            UserResultCard(
                data = it,
                busy = busyCard,
                env = env,
                onAddFriend = {
                    scope.launch {
                        busyCard = true
                        val res = safeCall {
                            env.api.sendFriendRequest(
                                com.areenax.app.data.FriendRequestCreateBody(
                                    uid = it.user.uid, source = "QR",
                                ),
                            )
                        }
                        busyCard = false
                        when (res) {
                            is ApiResult.Success ->
                                if (res.data.autoAccepted == true) {
                                    env.toast("You are now friends!")
                                } else {
                                    env.toast("Friend request sent!")
                                }
                            else -> env.toast("Failed to send request", ToastVariant.Destructive)
                        }
                    }
                },
                onMessage = {
                    env.navigate(ScreenKeys.CHAT, mapOf("friendId" to it.user.id))
                },
            )
        }
        lookupTeam?.let {
            TeamResultCard(
                data = it,
                busy = busyCard,
                env = env,
                onJoin = {
                    scope.launch {
                        busyCard = true
                        val res = safeCall {
                            env.api.requestTeamJoin(
                                com.areenax.app.data.TeamJoinRequestBody(teamId = it.team.id),
                            )
                        }
                        busyCard = false
                        if (res is ApiResult.Success) {
                            env.toast("Join request sent to the team owner!")
                        } else if (res is ApiResult.Error) {
                            env.toast(res.message, ToastVariant.Destructive)
                        } else {
                            env.toast("Could not send request", ToastVariant.Destructive)
                        }
                    }
                },
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Scan something else",
            style = Type.labelLg,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = { resetResults() })
                .padding(8.dp),
        )
    } else {
        // Square viewfinder (tap launches the zxing-embedded scanner)
        Box(
            Modifier
                .size(220.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(areenaColors().balanceChip)
                .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_qr_code_2),
                contentDescription = "Scan QR",
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            )
        }
        Spacer(Modifier.height(12.dp))
        Surface(shape = CircleShape, color = areenaColors().balanceChip) {
            Text(
                "Align the QR code within the frame",
                style = Type.labelMd,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        if (resolving) {
            AreenaxSpinner()
            Text("Looking up…", style = Type.bodyMd, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Button(
                onClick = {
                    scanner.launch(
                        ScanOptions()
                            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            .setPrompt("Place an AREENAX QR code inside the viewfinder")
                            .setBeepEnabled(false)
                            .setOrientationLocked(true),
                        // A10-03: the denial-dialog text is corrected by the app
                        // resource override `zxing_msg_camera_framework_bug`
                        // (values/strings.xml) — zxing-embedded 4.3.0 reads it
                        // from merged app resources; the setter suggested by the
                        // audit does not exist in this version.
                    )
                },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(painterResource(R.drawable.ic_qr_code_2), null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Open Camera", style = Type.labelLg)
            }
        }
    }

    Spacer(Modifier.height(16.dp))
    Text("Or enter a Player UID", style = Type.labelLg, color = MaterialTheme.colorScheme.onSurface)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        AreenaxPillField(
            value = manualUid,
            onValueChange = { value -> manualUid = value.filter { it.isDigit() }.take(12) },
            placeholder = "e.g. 782104",
            leadingIcon = "tag",
            textStyle = Type.bodyMd,
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Default, // pre-migration KeyboardOptions(Number)
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = {
                val uid = manualUid.trim()
                if (uid.isEmpty()) {
                    env.toast("Player UID is required", ToastVariant.Destructive)
                } else {
                    scope.launch { resolve("u:$uid") }
                }
            },
            shape = CircleShape,
        ) {
            Icon(painterResource(R.drawable.ic_search), null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(4.dp))
            Text("Find", style = Type.labelLg)
        }
    }
}

/*
 * UserResultCard — relation actions per QrUserLookup.relation:
 *   self → info chip "This is your own QR code";
 *   friend → disabled "Friends ✓" + Message (→ chat);
 *   request_sent → disabled "Request Sent";
 *   request_received → primary "Accept Request";
 *   none → primary "Add Friend" (guest gate "add friends" at call sites).
 */
@Composable
fun UserResultCard(
    data: com.areenax.app.data.QrUserLookup,
    busy: Boolean,
    env: NavEnv,
    onAddFriend: () -> Unit,
    onMessage: () -> Unit,
) {
    val relation = data.relation
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        UserAvatar(name = data.user.gameName, size = 56.dp)
        Text(data.user.gameName, style = Type.headlineMd, color = MaterialTheme.colorScheme.onSurface)
        Text(
            "UID: ${data.user.uid} • ${data.user.fullName}",
            style = Type.bodyMd,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (relation) {
            com.areenax.app.data.QrRelations.SELF -> {
                Surface(shape = CircleShape, color = areenaColors().balanceChip) {
                    Text(
                        "This is your own QR code",
                        style = Type.labelMd,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            com.areenax.app.data.QrRelations.FRIEND -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {}, enabled = false, shape = CircleShape) {
                        Text("Friends ✓", style = Type.labelLg)
                    }
                    Button(onClick = onMessage, shape = CircleShape) {
                        Text("Message", style = Type.labelLg)
                    }
                }
            }
            com.areenax.app.data.QrRelations.REQUEST_SENT -> {
                Button(onClick = {}, enabled = false, shape = CircleShape) {
                    Text("Request Sent", style = Type.labelLg)
                }
            }
            else -> {
                Button(
                    onClick = onAddFriend,
                    enabled = !busy,
                    shape = CircleShape,
                ) {
                    if (busy) AreenaxSpinner(size = 16, strokeWidth = 2)
                    Spacer(Modifier.size(6.dp))
                    Text(
                        if (relation == com.areenax.app.data.QrRelations.REQUEST_RECEIVED) {
                            "Accept Request"
                        } else {
                            "Add Friend"
                        },
                        style = Type.labelLg,
                    )
                }
            }
        }
    }
}

/*
 * TeamResultCard — UPPERCASE name + tag chip, owner line, member count;
 * isMember → "You're in this team" chip; joinRequested → disabled "Request
 * Sent"; else primary "Request to Join".
 */
@Composable
fun TeamResultCard(
    data: com.areenax.app.data.QrTeamLookup,
    busy: Boolean,
    env: NavEnv,
    onJoin: () -> Unit,
) {
    val team = data.team
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(areenaColors().balanceChip),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                team.name.take(1).uppercase(),
                style = Type.headlineMd,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                team.name.uppercase(),
                style = Type.headlineMd,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!team.tag.isNullOrBlank()) {
                Surface(shape = CircleShape, color = areenaColors().balanceChip) {
                    Text(
                        team.tag,
                        style = Type.labelMd,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
        Text(
            "Owner: ${team.owner.gameName} • UID ${team.owner.uid}",
            style = Type.bodyMd,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "${team.memberCount}/${team.maxMembers} Members",
            style = Type.bodyMd,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when {
            data.isMember -> {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)) {
                    Text(
                        "You're in this team",
                        style = Type.labelMd,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
            data.joinRequested -> {
                Button(onClick = {}, enabled = false, shape = CircleShape) {
                    Text("Request Sent", style = Type.labelLg)
                }
            }
            else -> {
                Button(onClick = onJoin, enabled = !busy, shape = CircleShape) {
                    if (busy) AreenaxSpinner(size = 16, strokeWidth = 2)
                    Spacer(Modifier.size(6.dp))
                    Text("Request to Join", style = Type.labelLg)
                }
            }
        }
    }
}

/** Initial-circle avatar (web's universal avatar fallback: bg-primary + first letter). */
@Composable
fun UserAvatar(name: String, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 40.dp) {
    val initial = name.trim().take(1).uppercase().ifEmpty { "?" }
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initial,
            style = Type.labelLg.copy(fontSize = androidx.compose.ui.unit.TextUnit(size.value * 0.4f, androidx.compose.ui.unit.TextUnitType.Sp)),
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}
