package com.areenax.app.core.util

import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/*
 * EXACT Kotlin ports of the web formatters in src/lib/api.ts (+ qr.ts masking,
 * ResultsScreen compact notation, HostCreation/BindAccount date helpers).
 * Locale.US everywhere — the web output is locale-stable ("Rs 1,250.00").
 *
 * The binding rules:
 *   formatMoney    → "1,250" / "1,250.5"   (0–2 decimals)   → "Rs «n»"
 *   formatBalance  → ALWAYS 2 decimals      ("Rs 1,250.00" balances)
 *   formatDateTime → "05-10-2025 3:07 pm" style (dd-MMM-yyyy hh:mm am/pm lowercase)
 *   timeAgo        → "Just now" | "5m ago" | "3h ago" | "2d ago" | "05-10-2025" (≥7d)
 *   countdownText  → "Starting soon" | "{d}d {h}h {m}m" | "{h}h {m}m {s}s" | "{m}m {s}s"
 *   maskAccountNumber → "PK••••••••6702" / "••••1234" / "—"
 */

private val MONEY_FORMAT: NumberFormat = NumberFormat.getNumberInstance(Locale.US).apply {
    minimumFractionDigits = 0
    maximumFractionDigits = 2
}

private val BALANCE_FORMAT: NumberFormat = NumberFormat.getNumberInstance(Locale.US).apply {
    minimumFractionDigits = 2
    maximumFractionDigits = 2
}

private val GROUPED_INT_FORMAT: NumberFormat = NumberFormat.getNumberInstance(Locale.US).apply {
    isGroupingUsed = true
    maximumFractionDigits = 0
}

private val MONTHS = arrayOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

/** "1,250" / "1,250.5" — grouping, 0..2 decimals (never null). */
fun formatMoney(n: Double?): String = MONEY_FORMAT.format(n ?: 0.0)

/** Convenience for integer-typed money fields. */
fun formatMoney(n: Int?): String = MONEY_FORMAT.format((n ?: 0).toDouble())

/** Canonical balance format — ALWAYS 2 decimals ("1,250.00"). */
fun formatBalance(n: Double?): String = BALANCE_FORMAT.format(n ?: 0.0)

/** "Rs «formatMoney»" — general money label. */
fun rsMoney(n: Double?): String = "Rs ${formatMoney(n)}"

/** "Rs «formatBalance»" — canonical balance label. */
fun rsBalance(n: Double?): String = "Rs ${formatBalance(n)}"

/** LeaderboardScreen fmt(): Locale.US grouping with 0 decimals. */
fun formatGroupedInt(n: Double?): String = GROUPED_INT_FORMAT.format(n ?: 0.0)

/**
 * "05-10-2025 3:07 pm" — dd-MMM-yyyy hh:mm am/pm with lowercase meridiem
 * (web formatDateTime). Uses the DEVICE timezone like the web's Date methods.
 */
fun formatDateTime(iso: String?): String {
    val millis = iso.parseEpochMillis() ?: return ""
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    val month = MONTHS[cal.get(Calendar.MONTH)]
    val day = cal.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
    val year = cal.get(Calendar.YEAR)
    var h = cal.get(Calendar.HOUR)
    val ampm = if (cal.get(Calendar.AM_PM) == Calendar.PM) "pm" else "am"
    if (h == 0) h = 12
    val minute = cal.get(Calendar.MINUTE).toString().padStart(2, '0')
    return String.format(Locale.US, "%s-%s-%d %02d:%s %s", day, month, year, h, minute, ampm)
}

/** "Just now" | "5m ago" | "3h ago" | "2d ago" | "05-10-2025" (≥7d, date part). */
fun timeAgo(iso: String?): String {
    val millis = iso.parseEpochMillis() ?: return ""
    val diff = System.currentTimeMillis() - millis
    val mins = diff / 60000
    if (mins < 1) return "Just now"
    if (mins < 60) return "${mins}m ago"
    val hours = mins / 60
    if (hours < 24) return "${hours}h ago"
    val days = hours / 24
    if (days < 7) return "${days}d ago"
    return formatDateTime(iso).split(" ").firstOrNull() ?: ""
}

/**
 * Future countdown until a tournament start:
 * ≤0 → "Starting soon"; days>0 → "{d}d {h}h {m}m"; hours>0 → "{h}h {m}m {s}s"; else "{m}m {s}s".
 */
fun countdownText(startIso: String?): String {
    val millis = startIso.parseEpochMillis() ?: return "Starting soon"
    val diff = millis - System.currentTimeMillis()
    if (diff <= 0) return "Starting soon"
    val days = diff / 86400000
    val hours = (diff % 86400000) / 3600000
    val mins = (diff % 3600000) / 60000
    val secs = (diff % 60000) / 1000
    return when {
        days > 0 -> "${days}d ${hours}h ${mins}m"
        hours > 0 -> "${hours}h ${mins}m ${secs}s"
        else -> "${mins}m ${secs}s"
    }
}

/**
 * ResultsScreen compact numbers — Intl "compact" notation, maxFractionDigits 1
 * ("12500 → 12.5K", "3.4M").
 */
fun formatCompact(n: Double?): String {
    val v = n ?: 0.0
    return when {
        v >= 1_000_000.0 -> stripTrailingZero(Math.round(v / 100_000.0) / 10.0) + "M"
        v >= 1_000.0 -> stripTrailingZero(Math.round(v / 100.0) / 10.0) + "K"
        else -> GROUPED_INT_FORMAT.format(v)
    }
}

private fun stripTrailingZero(v: Double): String =
    if (v == Math.floor(v)) v.toLong().toString() else v.toString()

/**
 * maskAccountNumber (src/lib/qr.ts):
 *   "" | null → "—"
 *   len ≤ 7   → "••••" + last4
 *   else      → first2 + "••••••••" (8 dots) + last4
 *   "PK36SCBL0000001123456702" → "PK••••••••6702"
 */
fun maskAccountNumber(acc: String?): String {
    val digits = acc?.trim() ?: ""
    if (digits.isEmpty()) return "—"
    if (digits.length <= 7) return "••••" + digits.takeLast(4)
    return digits.take(2) + "•".repeat(8) + digits.takeLast(4)
}

/** HostCreationScreen formatDisplayDate: "Oct 25, 2025, 11:06 AM". */
fun formatDisplayDate(iso: String?): String {
    val millis = iso.parseEpochMillis() ?: return ""
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    val month = MONTHS[cal.get(Calendar.MONTH)]
    var h = cal.get(Calendar.HOUR)
    val ampm = if (cal.get(Calendar.AM_PM) == Calendar.PM) "PM" else "AM"
    if (h == 0) h = 12
    val minute = cal.get(Calendar.MINUTE).toString().padStart(2, '0')
    return String.format(
        Locale.US, "%s %d, %d, %d:%s %s",
        month, cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.YEAR), h, minute, ampm,
    )
}

/** BindAccountSuccess linkedDate: "Oct 25, 2025" (month short, day numeric, year). */
fun formatShortDate(iso: String?): String {
    val millis = iso.parseEpochMillis() ?: return ""
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    return String.format(
        Locale.US, "%s %d, %d",
        MONTHS[cal.get(Calendar.MONTH)], cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.YEAR),
    )
}

/** Chat bubble time: "05:42 PM" (2-digit hour/minute + uppercase meridiem). */
fun formatChatTime(iso: String?): String {
    val millis = iso.parseEpochMillis() ?: return ""
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    var h = cal.get(Calendar.HOUR)
    val ampm = if (cal.get(Calendar.AM_PM) == Calendar.PM) "PM" else "AM"
    if (h == 0) h = 12
    val minute = cal.get(Calendar.MINUTE).toString().padStart(2, '0')
    return String.format(Locale.US, "%02d:%s %s", h, minute, ampm)
}

/**
 * Parse an ISO-8601 timestamp ("2025-10-25T11:06:00.000Z" etc.) to epoch millis.
 * ISO strings ending in Z are UTC (server timestamps); naive local datetimes
 * (datetime-local, no zone) are treated as device-local like the web's `new Date()`.
 */
fun String?.parseEpochMillis(): Long? {
    if (this.isNullOrBlank()) return null
    return try {
        if (endsWith("Z") || contains('+') || Regex("-\\d\\d:00$").containsMatchIn(this)) {
            java.time.Instant.parse(this).toEpochMilli()
        } else {
            java.time.LocalDateTime.parse(this)
                .atZone(TimeZone.getDefault().toZoneId())
                .toInstant().toEpochMilli()
        }
    } catch (_: Exception) {
        try {
            java.time.OffsetDateTime.parse(this).toInstant().toEpochMilli()
        } catch (_: Exception) {
            try {
                @Suppress("DEPRECATION")
                java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(this)?.time
            } catch (_: Exception) {
                null
            }
        }
    }
}
