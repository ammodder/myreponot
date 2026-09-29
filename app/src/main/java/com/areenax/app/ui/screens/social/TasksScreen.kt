package com.areenax.app.ui.screens.social

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.areenax.app.core.nav.NavEnv
import com.areenax.app.core.network.ApiResult
import com.areenax.app.core.network.safeCall
import com.areenax.app.core.theme.Type
import com.areenax.app.core.theme.areenaColors
import com.areenax.app.core.theme.tasksGradientEnd
import com.areenax.app.core.ui.AppBarBackButton
import com.areenax.app.core.ui.AreenaxIcon
import com.areenax.app.core.ui.AreenaxSpinner
import com.areenax.app.core.ui.BellButton
import com.areenax.app.core.ui.GuestGateDialog
import com.areenax.app.core.ui.SupportFab
import com.areenax.app.core.ui.ToastVariant
import com.areenax.app.core.ui.cardShadow
import com.areenax.app.core.ui.pressScale
import com.areenax.app.core.ui.rememberGuestGate
import com.areenax.app.core.util.formatMoney
import com.areenax.app.data.Task
import kotlinx.coroutines.launch

/*
 * TasksScreen — key `tasks` (Profile sub-page, SPEC/01 D7, build record 3-d):
 * sticky header w/ back, "Tasks" title, Rs earned-today chip (bg
 * surface-container-low text-primary) + bell, radial white background
 * (light), task rows (48dp rounded-2xl icon tile w/ task.icon in the
 * blue-50/blue-700 pattern → surface-container-low/primary, title/desc,
 * "+Rs reward" chip bg-secondary/15 text-secondary, Claim primary pill →
 * POST /tasks → toast "+Rs X" + balance sync + reload; claimed → filled
 * green check_circle "Claimed"), exact "No tasks available." empty state,
 * SupportFab + bottom padding (no bottom nav on this sub-page).
 */

@Composable
fun TasksScreen(env: NavEnv) {
    val extended = areenaColors()
    var tasks by remember { mutableStateOf<List<Task>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var claimingId by remember { mutableStateOf<String?>(null) }
    val gate = rememberGuestGate()
    val scope = rememberCoroutineScope()

    suspend fun loadTasks() {
        when (val res = safeCall { env.api.tasks() }) {
            is ApiResult.Success -> tasks = res.data.tasks
            else -> env.toast("Failed to load tasks", ToastVariant.Destructive)
        }
        loading = false
    }

    LaunchedEffect(Unit) { loadTasks() }

    val earnedToday = tasks.filter { it.claimed }.sumOf { it.reward }

    fun claim(task: Task) {
        if (claimingId != null) return
        if (!gate.requireAccount(env.user?.isGuest == true, "claim task rewards")) return
        claimingId = task.id
        scope.launch {
            when (val res = safeCall { env.api.claimTask(com.areenax.app.data.TaskClaimBody(taskId = task.id)) }) {
                is ApiResult.Success -> {
                    env.toast.show(
                        "+Rs ${formatMoney(res.data.reward)}",
                        description = "Task reward added to your wallet",
                    )
                    // Balance sync (web: setUser({...user, balance: +reward}))
                    val current = env.session.user.value
                    if (current != null) {
                        env.session.setUser(current.copy(balance = current.balance + res.data.reward))
                    }
                    loadTasks()
                }
                is ApiResult.Error, is ApiResult.NetworkError -> {
                    env.toast.show(
                        "Claim failed",
                        description = (res as? ApiResult.Error)?.message ?: (res as? ApiResult.NetworkError)?.message,
                        variant = ToastVariant.Destructive,
                    )
                }
            }
            claimingId = null
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                if (!extended.isDark) {
                    // tasks.html: radial-gradient(circle at top, #FFFFFF 0%, #F4F5F9 100%)
                    drawRect(
                        brush = Brush.verticalGradient(
                            listOf(Color.White, tasksGradientEnd),
                        ),
                    )
                }
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            // ===== Sticky header: back + title + Rs earned-today chip + bell =====
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(64.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppBarBackButton(onClick = { env.goBack() })
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Tasks",
                    style = Type.headlineMd,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                // Earned-today pill
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Text(
                        text = "Rs ${formatMoney(earnedToday)}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                BellButton()
            }

            // ===== Main content =====
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp, bottom = 120.dp),
            ) {
                if (loading) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 96.dp),
                        contentAlignment = Alignment.Center,
                    ) { AreenaxSpinner() }
                } else if (tasks.isEmpty()) {
                    // Exact tasks.html empty state
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp, bottom = 48.dp)
                            .height(300.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLow),
                            contentAlignment = Alignment.Center,
                        ) {
                            AreenaxIcon(
                                name = "assignment",
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                        Text(
                            text = "No tasks available.",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Check back later for new tasks to complete and earn rewards.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        tasks.forEach { task -> TaskRow(task, claimingId) { claim(task) } }
                    }
                }
            }
        }

        // SupportFab (offset — sub-page, no bottom nav)
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 96.dp),
        ) {
            SupportFab(offset = true)
        }
    }

    GuestGateDialog(gate, env)
}

/** One task row — icon tile, title/desc, +Rs chip, Claim button / Claimed state. */
@Composable
private fun TaskRow(task: Task, claimingId: String?, onClaim: () -> Unit) {
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Icon tile — surface-container-low bg / primary symbol (blue-50/blue-700 pattern)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
                contentAlignment = Alignment.Center,
            ) {
                AreenaxIcon(
                    name = task.icon.ifBlank { "assignment" },
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = task.description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                ) {
                    Text(
                        text = "+Rs ${formatMoney(task.reward)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                if (task.claimed) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AreenaxIcon(
                            name = "check_circle",
                            contentDescription = null,
                            filled = true,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Claimed",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                } else {
                    val busy = claimingId == task.id
                    val claimInteraction = remember { MutableInteractionSource() }
                    Surface(
                        onClick = onClaim,
                        enabled = !busy,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = if (busy) 0.6f else 1f),
                        interactionSource = claimInteraction,
                        modifier = Modifier.pressScale(claimInteraction, 0.95f),
                    ) {
                        Text(
                            text = if (busy) "Claiming…" else "Claim",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}
