package com.areenax.app.ui.screens.social

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.areenax.app.core.nav.NavEnv
import com.areenax.app.core.nav.parseDeepLink
import com.areenax.app.core.network.ApiResult
import com.areenax.app.core.network.safeCall
import com.areenax.app.core.theme.Type
import com.areenax.app.core.theme.areenaColors
import com.areenax.app.core.theme.DarkInversePrimary
import com.areenax.app.core.theme.roseInk
import com.areenax.app.core.theme.roseSoft
import com.areenax.app.core.ui.AppBarCircleButton
import com.areenax.app.core.ui.AreenaxIcon
import com.areenax.app.core.ui.AreenaxSpinner
import com.areenax.app.core.ui.EmptyState
import com.areenax.app.core.ui.ToastVariant
import com.areenax.app.core.ui.cardShadow
import com.areenax.app.core.util.timeAgo
import com.areenax.app.data.AppNotification
import com.areenax.app.data.NotificationIdsBody
import kotlinx.coroutines.launch

/*
 * NotificationsScreen — key `notifications` (build record 3-d):
 * notification.html — back/title/white-circle header; the right white-circle
 * button = mark-all-read (done_all) + an inline "Mark all read" link while
 * unread > 0; single white card list with hairline dividers (ml-20 mr-4);
 * 48dp type icon circles (TOURNAMENT emoji_events/primary, PAYMENT
 * payments/primary-fixed, SUCCESS check_circle/secondary-container, WARNING
 * warning/tertiary-fixed, INFO info/surface-container-high), timeAgo, unread
 * red dot, read rows dimmed; tap = expand in place + background mark-read
 * (PATCH {ids:[id]} → sync UnreadManager); expanded deep-link rows show
 * "Open →"; empty EmptyState "No notifications yet.".
 *
 * Q5 select+delete (web NotificationsScreen.tsx): "Select" (or long-press,
 * messaging-app style) enters multi-select — rows toggle check_circle /
 * radio_button_unchecked, selected rows tinted primary/10; header swap to
 * delete; Delete = DELETE {ids} → remove locally, toast, sync UnreadManager.
 */

@Composable
fun NotificationsScreen(env: NavEnv) {
    val extended = areenaColors()
    var notifications by remember { mutableStateOf<List<AppNotification>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var expanded by remember { mutableStateOf<Set<String>>(emptySet()) }
    val scope = rememberCoroutineScope()

    // Q5 multi-select (web: long-press; native adds an explicit "Select" toggle)
    var selectMode by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var deleting by remember { mutableStateOf(false) }
    val selectionActive = selectMode

    suspend fun load() {
        when (val res = safeCall { env.api.notifications() }) {
            is ApiResult.Success -> {
                notifications = res.data.notifications
                env.unread.setLocal(res.data.unread)
            }
            else -> env.toast("Failed to load notifications", ToastVariant.Destructive)
        }
        loading = false
    }

    LaunchedEffect(Unit) { load() }

    fun markReadInBackground(n: AppNotification) {
        // Optimistic local read; the server response syncs the bell badge.
        notifications = notifications.map { if (it.id == n.id) it.copy(isRead = true) else it }
        scope.launch {
            when (val res = safeCall {
                env.api.markRead(NotificationIdsBody(ids = listOf(n.id)))
            }) {
                is ApiResult.Success -> {
                    res.data.unread?.let { env.unread.setLocal(it) }
                }
                else -> {
                    // keep the optimistic state — next load reconciles
                }
            }
        }
    }

    fun toggleSelect(id: String) {
        selected = if (id in selected) selected - id else selected + id
    }

    fun selectAll() {
        selected = notifications.map { it.id }.toSet()
    }

    fun exitSelectMode() {
        selectMode = false
        selected = emptySet()
    }

    /** Delete ONLY the selected notifications (web deleteSelected). */
    fun deleteSelected() {
        if (deleting || selected.isEmpty()) return
        deleting = true
        val ids = selected.toList()
        scope.launch {
            when (val res = safeCall { env.api.deleteNotifications(NotificationIdsBody(ids = ids)) }) {
                is ApiResult.Success -> {
                    notifications = notifications.filter { it.id !in ids }
                    expanded = expanded - ids.toSet()
                    exitSelectMode()
                    // Sync the bell badge the same way mark-read does.
                    res.data.unread?.let { env.unread.setLocal(it) }
                    env.unread.refresh(force = true)
                    env.toast("Deleted ${ids.size} notification" + if (ids.size == 1) "" else "s")
                }
                is ApiResult.Error, is ApiResult.NetworkError -> {
                    env.toast.show(
                        "Failed to delete",
                        description = (res as? ApiResult.Error)?.message ?: (res as? ApiResult.NetworkError)?.message,
                        variant = ToastVariant.Destructive,
                    )
                }
            }
            deleting = false
        }
    }

    fun markAllRead() {
        scope.launch {
            when (val res = safeCall { env.api.markAllRead() }) {
                is ApiResult.Success -> {
                    notifications = notifications.map { it.copy(isRead = true) }
                    // Sync the UnreadManager count (optimistic 0, then server truth).
                    env.unread.setLocal(0)
                    env.unread.refresh(force = true)
                }
                is ApiResult.Error, is ApiResult.NetworkError -> {
                    env.toast.show(
                        "Failed to mark all read",
                        description = (res as? ApiResult.Error)?.message ?: (res as? ApiResult.NetworkError)?.message,
                        variant = ToastVariant.Destructive,
                    )
                }
            }
        }
    }

    val unreadCount = notifications.count { !it.isRead }
    val rowTopColor = MaterialTheme.colorScheme.surfaceContainerLow
    val rowBottomColor = MaterialTheme.colorScheme.background

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                // radial-gradient(circle at top right, surface-container-low, background)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(rowTopColor, rowBottomColor),
                        center = androidx.compose.ui.geometry.Offset(size.width, 0f),
                        radius = size.width * 1.2f,
                    ),
                )
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            // ===== Header: back / title / white-circle mark-all-read =====
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(64.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppBarCircleButton(onClick = { env.goBack() }) {
                    AreenaxIcon(
                        name = "arrow_back",
                        contentDescription = "Back",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "Notifications",
                    style = Type.headlineMd.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                // Selection mode: the header action becomes Delete (web swaps
                // the bell for Select-All + delete); normal: mark-all-read.
                if (selectionActive) {
                    AppBarCircleButton(onClick = { deleteSelected() }) {
                        AreenaxIcon(
                            name = "delete",
                            contentDescription = "Delete ${selected.size} selected notification" +
                                if (selected.size == 1) "" else "s",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    AppBarCircleButton(onClick = { markAllRead() }) {
                        AreenaxIcon(
                            name = "done_all",
                            contentDescription = "Mark all read",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (loading) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 96.dp),
                        contentAlignment = Alignment.Center,
                    ) { AreenaxSpinner() }
                } else if (notifications.isEmpty()) {
                    EmptyState(message = "No notifications yet.", icon = "notifications")
                } else {
                    if (selectionActive) {
                        // Select mode — Select All + Cancel (Delete is in the header)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Select all",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable { selectAll() }
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = "Cancel",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clickable { exitSelectMode() }
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                            )
                        }
                    } else {
                        // Normal mode — inline "Mark all read" while unread + the
                        // Q5 "Select" toggle (messaging-app long-press also works).
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (unreadCount > 0) {
                                Text(
                                    text = "Mark all read",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clickable { markAllRead() }
                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                )
                                Spacer(Modifier.width(12.dp))
                            }
                            Text(
                                text = "Select",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable { selectMode = true }
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                            )
                        }
                    }

                    // Single white card list with hairline dividers
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .cardShadow(RoundedCornerShape(16.dp)),
                    ) {
                        Column {
                            notifications.forEachIndexed { i, n ->
                                if (i > 0) {
                                    Box(
                                        Modifier
                                            .padding(start = 80.dp, end = 16.dp)
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                                    )
                                }
                                NotificationRow(
                                    n = n,
                                    isExpanded = expanded.contains(n.id),
                                    selectionActive = selectionActive,
                                    isSelected = n.id in selected,
                                    onTap = {
                                        if (!expanded.contains(n.id)) {
                                            expanded = expanded + n.id
                                            if (!n.isRead) markReadInBackground(n)
                                        }
                                    },
                                    onToggle = { toggleSelect(n.id) },
                                    onLongPress = {
                                        selectMode = true
                                        selected = selected + n.id
                                    },
                                    onOpen = {
                                        val target = parseDeepLink(n.link)
                                        if (target != null) {
                                            if (!n.isRead) markReadInBackground(n)
                                            env.navigate(target.screen, target.params)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One notification row — type icon circle, title/timeAgo, message, red dot.
 *  Selection mode: tap toggles the check, long-press enters selection (web). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotificationRow(
    n: AppNotification,
    isExpanded: Boolean,
    selectionActive: Boolean,
    isSelected: Boolean,
    onTap: () -> Unit,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
    onOpen: () -> Unit,
) {
    val visual = typeVisual(n.type)
    val extended = areenaColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (selectionActive) onToggle() else onTap() },
                onLongClick = onLongPress,
            )
            // read rows dimmed (notification.html opacity-50 row)
            .alpha(if (n.isRead) 0.5f else 1f)
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = if (extended.isDark) 0.2f else 0.1f)
                } else {
                    Color.Transparent
                },
            )
            .padding(20.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(visual.bg),
            contentAlignment = Alignment.Center,
        ) {
            AreenaxIcon(
                name = visual.icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = visual.fg,
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!n.isRead) {
                    // Unread red dot
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = n.title,
                    style = Type.bodyLg,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = timeAgo(n.createdAt),
                    style = Type.labelSm.copy(fontWeight = FontWeight.Normal),
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = n.message,
                style = Type.bodyMd,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 32.dp),
            )
            if (isExpanded && parseDeepLink(n.link) != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clickable(onClick = onOpen),
                ) {
                    Text(
                        text = "Open",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(4.dp))
                    AreenaxIcon(
                        name = "arrow_forward",
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        // Selection indicator — messaging-app style (web trailing check)
        if (selectionActive) {
            AreenaxIcon(
                name = if (isSelected) "check_circle" else "radio_button_unchecked",
                contentDescription = if (isSelected) "Selected" else "Not selected",
                modifier = Modifier.align(Alignment.CenterVertically).size(22.dp),
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/**
 * Type → (icon, circle bg, icon fg). Matches the enforced color pairs:
 * TOURNAMENT emoji_events/primary · PAYMENT payments/primary-fixed ·
 * SUCCESS check_circle/secondary-container · WARNING warning/tertiary-fixed ·
 * INFO info/surface-container-high · default notifications.
 */
private data class NotificationVisual(val icon: String, val bg: Color, val fg: Color)

@Composable
private fun typeVisual(type: String): NotificationVisual {
    val extended = areenaColors()
    return when (type) {
        "TOURNAMENT" -> NotificationVisual(
            icon = "emoji_events",
            bg = MaterialTheme.colorScheme.primary,
            fg = MaterialTheme.colorScheme.onPrimary,
        )
        "PAYMENT" -> NotificationVisual(
            icon = "payments",
            bg = extended.primaryFixed, // A8-06: token instead of hardcoded pair
            fg = if (extended.isDark) DarkInversePrimary else MaterialTheme.colorScheme.primary,
        )
        "SUCCESS" -> NotificationVisual(
            icon = "check_circle",
            bg = MaterialTheme.colorScheme.secondaryContainer,
            fg = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        "WARNING" -> NotificationVisual(
            icon = "warning",
            bg = if (extended.isDark) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                roseSoft
            },
            fg = if (extended.isDark) {
                MaterialTheme.colorScheme.onTertiaryContainer
            } else {
                roseInk
            },
        )
        "INFO" -> NotificationVisual(
            icon = "info",
            bg = MaterialTheme.colorScheme.surfaceContainerHigh,
            fg = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> NotificationVisual(
            icon = "notifications",
            bg = MaterialTheme.colorScheme.surfaceContainerHigh,
            fg = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
