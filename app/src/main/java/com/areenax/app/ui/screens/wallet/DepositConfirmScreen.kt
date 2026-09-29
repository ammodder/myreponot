package com.areenax.app.ui.screens.wallet

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.areenax.app.core.nav.NavEnv
import com.areenax.app.core.nav.ScreenKeys
import com.areenax.app.core.network.ApiResult
import com.areenax.app.core.network.safeCall
import com.areenax.app.core.session.SettingsCache
import com.areenax.app.core.theme.Type
import com.areenax.app.core.theme.areenaColors
import com.areenax.app.core.ui.AppBar
import com.areenax.app.core.ui.GuestGateDialog
import com.areenax.app.core.ui.ToastVariant
import com.areenax.app.core.ui.AreenaxIcon
import com.areenax.app.core.ui.UploadCaps
import com.areenax.app.core.ui.cardShadow
import com.areenax.app.core.ui.pressScale
import com.areenax.app.core.ui.primaryGlow
import com.areenax.app.core.ui.rememberGuestGate
import com.areenax.app.core.ui.rememberImagePicker
import com.areenax.app.core.util.formatMoney
import com.areenax.app.core.util.maskAccountNumber
import com.areenax.app.core.util.newDepositDisplayRef
import com.areenax.app.data.BankAccount
import com.areenax.app.data.DepositRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * DepositConfirmScreen — 1:1 port of src/components/screens/wallet/
 * DepositConfirmScreen.tsx (SPEC/01 C3, key `depositConfirm`,
 * HTML depositconfirm.html).
 *
 *  - Invalid amount param → replace(deposit) (web useEffect).
 *  - Hero: 56dp account_balance circle + 36px amount + "Instant Deposit"
 *    (HTML subtitle — the task pins the depositconfirm.html string).
 *  - Details card (Amount / Deposit ID #DEP-XXXXXX / Account Number /
 *    Account Name / [bound Account] / Our Fee 0.00 / You Get). Account
 *    number comes from SettingsCache.depositAccounts[method] (bootstrap).
 *    Q1 (owner): the React hardcoded FALLBACK_ACCOUNTS map was REMOVED —
 *    real-money destinations must come from the server, never from code.
 *    When the server has no number for the method the row shows "Not
 *    configured" instead of a fake account.
 *  - Upload Receipt row (REQUIRED) → image picker ≤ 8MB data URL + exact
 *    attach/validation toasts; over-cap picks are REFUSED (Q2); TrxID input
 *    (≤ 64 chars, A4-07 — the server slices silently at 64 and uniqueness is
 *    computed post-slice); helper note card.
 *  - Fixed footer: deposit terms disclaimer + "Confirm Deposit" (guest gate,
 *    disabled while submitting) → POST /wallet/deposit {amount, method,
 *    trxId, receiptName, image?, accountId?} → setUser(balance) →
 *    depositSuccess {amount, method, reference}; errors → "Deposit failed"
 *    destructive toast with the server message.
 */

/** A4-07: the server silently slices TrxID to 64 chars and uniqueness is
 *  computed on the sliced value — cap the input so users see the real limit. */
private const val TRX_ID_MAX = 64

private data class DepositDetailRow(val icon: String, val label: String, val value: String)

@Composable
fun DepositConfirmScreen(env: NavEnv) {
    val colors = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    // ---- Params (read once) ------------------------------------------------
    val amount = (env.params["amount"] as? Number)?.toDouble() ?: Double.NaN
    val method = env.params["method"] as? String ?: "Jazzcash"
    val accountId = env.params["accountId"] as? String

    val user by env.session.user.collectAsState()
    val gate = rememberGuestGate()

    var trxId by rememberSaveable { mutableStateOf("") }
    var receiptName by rememberSaveable { mutableStateOf<String?>(null) }
    var receiptImage by rememberSaveable { mutableStateOf<String?>(null) }
    var submitting by rememberSaveable { mutableStateOf(false) }
    val depositId = remember { newDepositDisplayRef() }
    // Q1: no hardcoded fallback — server settings are the only source.
    var accountNumber by rememberSaveable { mutableStateOf("") }
    var boundAccount by remember { mutableStateOf<BankAccount?>(null) }

    // Invalid amount → back to the deposit screen (web useEffect).
    LaunchedEffect(amount) {
        if (amount.isNaN()) env.replace(ScreenKeys.DEPOSIT)
    }

    // Account number from the cached bootstrap settings (exact-key lookup).
    LaunchedEffect(method) {
        SettingsCache.get { env.api }?.depositAccounts?.get(method)?.let { accountNumber = it }
    }

    // Resolve the chosen bound account for the optional details row.
    LaunchedEffect(accountId) {
        if (accountId.isNullOrBlank()) return@LaunchedEffect
        when (val res = safeCall { env.api.bankAccounts() }) {
            is ApiResult.Success -> boundAccount = res.data.accounts.firstOrNull { it.id == accountId }
            else -> Unit // details row simply stays hidden
        }
    }

    val picker = rememberImagePicker(UploadCaps.RECEIPT, onError = { msg ->
        env.toast.show("Receipt too large", description = msg, variant = ToastVariant.Destructive)
    }) { picked ->
        if (picked != null) {
            receiptImage = picked.dataUrl
            receiptName = picked.fileName
            env.toast.show("Receipt attached", description = picked.fileName)
        }
        // cancel = no change (web file input behaves the same)
    }

    val onConfirmClick: () -> Unit = {
        when {
            submitting -> Unit
            receiptName == null -> env.toast.show(
                "Payment receipt required",
                description = "Please attach a copy of your payment receipt so we can verify this transaction.",
                variant = ToastVariant.Destructive,
            )
            trxId.isBlank() -> env.toast.show(
                "Transaction ID required",
                description = "Please enter the transaction ID (TrxID) shown in your payment provider's receipt.",
                variant = ToastVariant.Destructive,
            )
            else -> {
                val receipt = receiptName ?: ""
                submitting = true
                val body = DepositRequest(
                    amount = amount,
                    method = method,
                    trxId = trxId.trim(),
                    receiptName = receipt,
                    image = receiptImage?.takeIf { it.isNotEmpty() },
                    accountId = accountId,
                )
                scope.launch {
                    when (val res = safeCall { env.api.deposit(body) }) {
                        is ApiResult.Success -> {
                            // Web: setUser({...u, balance: res.balance})
                            env.session.user.value?.let { u ->
                                res.data.balance?.let { b -> env.session.setUser(u.copy(balance = b)) }
                            }
                            env.navigate(
                                ScreenKeys.DEPOSIT_SUCCESS,
                                mapOf(
                                    "amount" to amount,
                                    "method" to method,
                                    "reference" to res.data.reference,
                                ),
                            )
                        }
                        is ApiResult.Error -> env.toast.show(
                            "Deposit failed",
                            description = res.message,
                            variant = ToastVariant.Destructive,
                        )
                        is ApiResult.NetworkError -> env.toast.show(
                            "Deposit failed",
                            description = res.message,
                            variant = ToastVariant.Destructive,
                        )
                    }
                    submitting = false
                }
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .walletRadialBackground(
                topColor = colors.surfaceContainer, // confirm variant uses surface-container
                baseColor = colors.background,
            ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding(),
        ) {
            AppBar(title = "Confirm Deposit")

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 24.dp, bottom = 210.dp), // pt-6 + fixed-footer clearance
                verticalArrangement = Arrangement.spacedBy(24.dp), // gap-6
            ) {
                // ---- Hero ----
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Box(
                        Modifier
                            .size(56.dp) // w-14 h-14
                            .cardShadow(CircleShape)
                            .clip(CircleShape)
                            .background(colors.surfaceContainerLowest),
                        contentAlignment = Alignment.Center,
                    ) {
                        AreenaxIcon(
                            name = "account_balance",
                            contentDescription = null,
                            modifier = Modifier.size(28.dp), // text-[1.75rem]
                            tint = colors.primary,
                        )
                    }
                    Spacer(Modifier.height(12.dp)) // mb-3
                    Text(
                        text = formatMoney(amount.takeIf { !it.isNaN() }),
                        style = Type.heroAmount.copy(fontSize = 36.sp, lineHeight = 43.sp), // text-[2.25rem]
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                    )
                    Spacer(Modifier.height(4.dp)) // mb-1
                    Text(
                        text = "Instant Deposit", // HTML depositconfirm.html subtitle
                        style = Type.bodyMd,
                        color = colors.onSurfaceVariant,
                    )
                }

                // ---- Transaction Details Card ----
                Surface(
                    shape = RoundedCornerShape(16.dp), // rounded-2xl
                    color = colors.surfaceContainerLowest,
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.3f)),
                    shadowElevation = 2.dp, // shadow-[0_4px_20px_rgba(0,0,0,0.04)]
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        val rows = buildList {
                            add(DepositDetailRow("payments", "Amount", formatMoney(amount.takeIf { !it.isNaN() })))
                            add(DepositDetailRow("tag", "Deposit ID", depositId))
                            add(DepositDetailRow("credit_card", "Account Number", accountNumber.ifBlank { "Not configured" }))
                            add(DepositDetailRow("person", "Account Name", user?.fullName ?: "John Doe"))
                            boundAccount?.let { bound ->
                                add(
                                    DepositDetailRow(
                                        "credit_card",
                                        "Account",
                                        "${bound.bankName} ${maskAccountNumber(bound.accountNumber)}",
                                    ),
                                )
                            }
                            add(DepositDetailRow("remove_circle_outline", "Our Fee", "0.00"))
                            add(DepositDetailRow("account_balance_wallet", "You Get", formatMoney(amount.takeIf { !it.isNaN() })))
                        }
                        rows.forEach { row -> DetailRowItem(row) }

                        // divide-y hairline before the Upload row
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(colors.outlineVariant.copy(alpha = 0.2f)),
                        )

                        // ---- Upload Receipt (REQUIRED) ----
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { picker.launch() }
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (receiptName != null) {
                                Row(
                                    Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    if (receiptImage != null) {
                                        ReceiptThumbnail(
                                            dataUrl = receiptImage ?: "",
                                            modifier = Modifier
                                                .size(36.dp) // w-9 h-9
                                                .clip(RoundedCornerShape(8.dp))
                                                .border(1.dp, colors.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                                        )
                                    } else {
                                        AreenaxIcon(
                                            name = "check_circle",
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
                                            tint = colors.secondary,
                                        )
                                    }
                                    Text(
                                        text = receiptName ?: "",
                                        style = Type.labelMd,
                                        color = colors.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(colors.surfaceContainer)
                                            .padding(horizontal = 12.dp, vertical = 6.dp), // px-3 py-1.5
                                    )
                                }
                                Spacer(Modifier.width(12.dp)) // ml-3
                                Text(
                                    text = "Tap to change",
                                    style = Type.labelMd,
                                    color = colors.primary,
                                )
                            } else {
                                Row(
                                    Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    AreenaxIcon(
                                        name = "receipt_long",
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = colors.onSurfaceVariant,
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Upload Receipt ",
                                            style = Type.bodyMd,
                                            color = colors.onSurfaceVariant,
                                        )
                                        Text(
                                            text = "*",
                                            style = Type.bodyMd.copy(fontWeight = FontWeight.Bold),
                                            color = colors.error, // text-destructive
                                        )
                                    }
                                }
                                AreenaxIcon(
                                    name = "upload",
                                    contentDescription = "Upload receipt",
                                    modifier = Modifier.size(20.dp),
                                    tint = colors.primary,
                                )
                            }
                        }
                    }
                }

                // ---- TrxID Input (REQUIRED) ----
                Column {
                    Row(
                        Modifier.padding(start = 8.dp, bottom = 8.dp), // ml-2 mb-2
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Transaction ID (TrxID) ",
                            style = Type.bodyMd.copy(fontWeight = FontWeight.Medium),
                            color = colors.onSurface,
                        )
                        Text(
                            text = "*",
                            style = Type.bodyMd.copy(fontWeight = FontWeight.Bold),
                            color = colors.error,
                        )
                    }
                    WalletPillTextField(
                        value = trxId,
                        onValueChange = { trxId = it.take(TRX_ID_MAX) }, // A4-07: server slices at 64
                        placeholder = "Enter TrxID / payment reference",
                        leadingIcon = "tag",
                    )
                    if (trxId.length >= TRX_ID_MAX - 8) {
                        Text(
                            text = "${trxId.length}/$TRX_ID_MAX",
                            style = Type.labelSm,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp, top = 4.dp),
                        )
                    }
                }

                // ---- Helper note ----
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(areenaColors().surfaceContainerLavender)
                        .padding(horizontal = 16.dp, vertical = 14.dp), // px-4 py-3.5
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AreenaxIcon(
                        name = "info",
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = colors.primary,
                    )
                    Text(
                        text = "Send the payment to the account number shown above, then upload your payment receipt here.",
                        style = Type.bodyMd,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }

        // ---- Fixed footer ----
        Surface(
            color = colors.surface, // bg-surface
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .imePadding(), // A3-05: keep "Confirm Deposit" above the keyboard
        ) {
            Column(
                Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 20.dp), // px-4 py-5
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "By confirming, you agree to the deposit terms & conditions.",
                    style = Type.labelSm.copy(fontWeight = FontWeight.Normal),
                    color = colors.onSurfaceVariant, // A9-01: was alpha 0.7 → 2.67:1; full opacity passes
                )
                Spacer(Modifier.height(10.dp)) // gap-2.5
                val confirmInteraction = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp) // h-[3.5rem]
                        .primaryGlow(RoundedCornerShape(50))
                        .pressScale(confirmInteraction, pressedScale = 0.98f)
                        .clip(CircleShape)
                        .background(colors.primary.copy(alpha = if (submitting) 0.6f else 1f)) // disabled:opacity-60
                        .clickable(
                            interactionSource = confirmInteraction,
                            indication = null,
                            enabled = !submitting,
                        ) {
                            if (gate.requireAccount(user?.isGuest == true, "make a deposit")) {
                                onConfirmClick()
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Confirm Deposit",
                        style = Type.labelLg,
                        color = colors.onPrimary,
                    )
                }
            }
        }

        GuestGateDialog(gate, env)
    }
}

/** base64 data-URL → downsampling bitmap thumbnail (A7-05: decode with
 *  inSampleSize so an 8 MB receipt never decodes at full resolution for a
 *  36 dp preview). */
@Composable
private fun ReceiptThumbnail(dataUrl: String, modifier: Modifier = Modifier) {
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = dataUrl) {
        value = withContext(Dispatchers.IO) {
            try {
                val base64 = dataUrl.substringAfter("base64,", "")
                if (base64.isEmpty()) {
                    null
                } else {
                    val bytes = Base64.decode(base64, Base64.DEFAULT)
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    var sample = 1
                    val largest = maxOf(bounds.outWidth, bounds.outHeight)
                    while (largest / (sample * 2) >= 144) sample *= 2 // 36 dp @4x is plenty
                    val options = BitmapFactory.Options().apply { inSampleSize = sample }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                }
            } catch (_: Exception) {
                null
            }
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Receipt preview",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun DetailRowItem(row: DepositDetailRow) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp), // px-4 py-4
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AreenaxIcon(
                name = row.icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp), // text-[1.25rem]
                tint = colors.onSurfaceVariant,
            )
            Text(text = row.label, style = Type.bodyMd, color = colors.onSurfaceVariant)
        }
        Text(
            text = row.value,
            style = Type.bodyMd.copy(fontWeight = FontWeight.Bold),
            color = colors.onSurface,
        )
    }
}
