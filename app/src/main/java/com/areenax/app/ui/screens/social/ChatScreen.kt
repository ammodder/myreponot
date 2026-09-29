package com.areenax.app.ui.screens.social

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.areenax.app.R
import com.areenax.app.core.nav.NavEnv
import com.areenax.app.core.nav.ScreenKeys
import com.areenax.app.core.network.ApiResult
import com.areenax.app.core.network.safeCall
import com.areenax.app.core.theme.Type
import com.areenax.app.core.theme.onlineGreen
import com.areenax.app.core.ui.AreenaxIcon
import com.areenax.app.core.ui.AreenaxPillField
import com.areenax.app.core.ui.AreenaxSpinner
import com.areenax.app.core.ui.ToastVariant
import com.areenax.app.core.util.formatChatTime
import com.areenax.app.data.ChatMessage
import com.areenax.app.data.FriendUser
import com.areenax.app.data.SendMessageBody
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/*
 * ChatScreen — key `chat` (SPEC/01 D5, build record 3-d):
 * reads params.friendId; GET /friends/{id}/messages on mount + poll every
 * 3000ms (LaunchedEffect + delay loop — cancelled automatically on dispose;
 * poll errors silent, state write skipped when the list is identical).
 * chat.html header (back, avatar + online dot, name, "Online"), static
 * "Today, 5:42 PM" divider pill, left white bubbles (border + 28dp avatar)
 * vs right bg-primary bubbles (time + done/done_all), fixed bottom rounded-
 * full input bar (emoji + primary send circle), send → POST + append,
 * auto-scroll to the latest message, empty/error states.
 */

private const val CHAT_POLL_MS = 3_000L
// R3: token sweep — was Color(0xFF10B981), now the Tokens.kt constant (same value).
private val ChatOnlineDot = onlineGreen

@Composable
fun ChatScreen(env: NavEnv) {
    val friendId = env.params["friendId"] as? String ?: ""

    var friend by remember { mutableStateOf<FriendUser?>(null) }
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    suspend fun load() {
        if (friendId.isEmpty()) return
        // A7-04 delta-sync: after the first full load, poll with the createdAt
        // cursor of the newest known message so the server returns only new
        // rows (gte re-delivers the boundary message; deduped by id below).
        // Server-generated timestamps → no client-clock involvement.
        val after = messages.lastOrNull()?.createdAt?.takeIf { it.isNotBlank() }
        when (val res = safeCall { env.api.chatMessages(friendId, after) }) {
            is ApiResult.Success -> {
                friend = res.data.friend
                val next = res.data.messages
                if (after == null) {
                    val prev = messages
                    // chat history is append-only: skip the rebuild when identical
                    if (!(prev.size == next.size && prev.lastOrNull()?.id == next.lastOrNull()?.id)) {
                        messages = next
                    }
                } else if (next.isNotEmpty()) {
                    // A15-1: the optimistic send() append can race an in-flight
                    // poll response carrying an OLDER bot reply — sort on merge
                    // so conversation order is always chronological.
                    val merged = (messages + next)
                        .distinctBy { it.id }
                        .sortedWith(compareBy<ChatMessage> { it.createdAt }.thenBy { it.id })
                    if (merged.size != messages.size || merged.lastOrNull()?.id != messages.lastOrNull()?.id) {
                        messages = merged
                    }
                }
            }
            else -> {
                // Poll errors are silent; first-load failure surfaces via !friend.
            }
        }
        loading = false
    }

    // Initial load + 3s polling — LaunchedEffect cancels the loop on dispose.
    // A3-06: gated on STARTED so the 3 s poll (1,200 req/h) pauses while the
    // app is backgrounded instead of running until the process dies.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(friendId) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                load()
                delay(CHAT_POLL_MS)
            }
        }
    }

    // Auto-scroll to the latest message.
    LaunchedEffect(messages.size, loading) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.size) // index 0 = divider
        }
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || sending || friendId.isEmpty()) return
        sending = true
        scope.launch {
            when (val res = safeCall {
                env.api.sendMessage(friendId, SendMessageBody(text = text))
            }) {
                is ApiResult.Success -> {
                    messages = messages + res.data.message
                    input = ""
                }
                is ApiResult.Error, is ApiResult.NetworkError -> {
                    env.toast.show(
                        "Failed to send",
                        description = (res as? ApiResult.Error)?.message ?: (res as? ApiResult.NetworkError)?.message,
                        variant = ToastVariant.Destructive,
                    )
                }
            }
            sending = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        ChatHeader(
            env = env,
            friend = friend,
            letter = (friend?.gameName ?: "?").firstOrNull()?.uppercaseChar()?.toString() ?: "?",
        )

        // Messages — independently scrollable area above the pinned input bar
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Static "Today, 5:42 PM" divider (from HTML)
            item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Surface(
                        shape = CircleShape,
                        color = areenaLavender(),
                    ) {
                        Text(
                            text = "Today, 5:42 PM",
                            style = Type.labelMd,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            when {
                loading -> item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) { AreenaxSpinner() }
                }

                friend == null -> item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AreenaxIcon(
                            name = "wifi_off",
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = "Could not load this chat.",
                            style = Type.bodyMd,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                messages.isEmpty() -> item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AreenaxIcon(
                            name = "forum",
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Say hi to ${friend?.gameName ?: "your friend"} — start the squad talk!",
                            style = Type.bodyMd,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                else -> items(messages, key = { it.id }) { m ->
                    if (m.senderId == friendId) {
                        FriendBubble((friend?.gameName ?: "?").firstOrNull()?.uppercaseChar()?.toString() ?: "?", m)
                    } else {
                        MyBubble(m)
                    }
                }
            }
        }

        // Chat input bar — pinned bottom, rounded-full container
        Column(Modifier.fillMaxWidth().background(Color.Transparent)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            ) {
                AreenaxPillField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "Type a message...",
                    leadingIcon = "chat_bubble",
                    height = 56.dp,
                    imeAction = ImeAction.Send,
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    trailing = {
                        // Emoji button — appends 😊
                        Surface(
                            onClick = { input += "😊" },
                            shape = CircleShape,
                            color = Color.Transparent,
                        ) {
                            AreenaxIcon(
                                name = "sentiment_satisfied",
                                contentDescription = "Insert emoji",
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(2.dp))
                        // Primary send FAB
                        Surface(
                            onClick = { send() },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                        ) {
                            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_send),
                                    contentDescription = "Send message",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}

/** chat.html header: back, avatar + online dot, name, "Online", notifications. */
@Composable
private fun ChatHeader(env: NavEnv, friend: FriendUser?, letter: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(64.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(onClick = { env.goBack() }, shape = CircleShape, color = Color.Transparent) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = "Back",
                modifier = Modifier
                    .padding(8.dp)
                    .size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(40.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = letter,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(ChatOnlineDot)
                    .border(2.dp, MaterialTheme.colorScheme.surfaceContainerLowest, CircleShape),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = friend?.gameName?.ifBlank { "Chat" } ?: "Chat",
                style = Type.labelLg,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(ChatOnlineDot),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Online",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
        Surface(
            onClick = { env.navigate(ScreenKeys.NOTIFICATIONS) },
            shape = CircleShape,
            color = Color.Transparent,
        ) {
            AreenaxIcon(
                name = "notifications",
                contentDescription = "Notifications",
                modifier = Modifier
                    .padding(8.dp)
                    .size(22.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Left bubble — white w/ border + 28dp avatar, time under. */
@Composable
private fun FriendBubble(letter: String, m: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = letter,
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp,
                ),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                ),
                shadowElevation = 1.dp,
            ) {
                Text(
                    text = m.text,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .padding(16.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatChatTime(m.createdAt),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/** Right bubble — bg-primary, time + done/done_all read state under. */
@Composable
private fun MyBubble(m: ChatMessage) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp, bottomEnd = 4.dp, bottomStart = 16.dp,
            ),
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 1.dp,
        ) {
            Text(
                text = m.text,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .padding(16.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatChatTime(m.createdAt),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            AreenaxIcon(
                name = if (m.read) "done_all" else "done",
                contentDescription = null,
                filled = true,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** surface-container-lavender token for the divider pill. */
@Composable
private fun areenaLavender(): Color = com.areenax.app.core.theme.areenaColors().surfaceContainerLavender
