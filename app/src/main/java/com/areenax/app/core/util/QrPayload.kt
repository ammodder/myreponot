package com.areenax.app.core.util

/*
 * EXACT Kotlin port of the AREENAX QR payload system (src/lib/qr.ts).
 *
 *   user QR payload = "u:<6-digit uid>"   team QR payload = "t:<team id>"
 *   (long forms "user:<id>" / "team:<id>" are accepted by the parser)
 *
 * The scannable QR image encodes a full deep link built from app_base_url:
 *   https://<origin>/?qr=u%3A123456
 */

enum class QrKind { USER, TEAM }

data class QrPayload(val kind: QrKind, val id: String)

/** Build the raw payload string ("u:123456" / "t:clkx..."). */
fun buildQrPayload(kind: QrKind, id: String): String = "${kind.payloadPrefix}:$id"

private val QrKind.payloadPrefix: String get() = if (this == QrKind.USER) "u" else "t"

/** Build the full scannable deep-link URL for a QR image. */
fun buildQrLink(kind: QrKind, id: String, baseUrl: String): String {
    val base = baseUrl.trimEnd('/')
    return "$base/?qr=${java.net.URLEncoder.encode(buildQrPayload(kind, id), "UTF-8")}"
}

private val QR_PARAM_REGEX = Regex("[?&]qr=([^&\\s]+)", RegexOption.IGNORE_CASE)
private val QR_PAYLOAD_REGEX = Regex("^\\s*(u|user|t|team)\\s*[:\\-|]\\s*([A-Za-z0-9_-]+)\\s*$", RegexOption.IGNORE_CASE)

/**
 * Parse any scanned QR text into a payload. Accepts:
 *   - full deep links  → "https://host/?qr=u%3A123456" / ".../?qr=user:123456"
 *   - raw payloads     → "u:123456" / "user:123456" / "t:clkx9f..." / "team:clkx9f..."
 * Returns null when the text is not an AREENAX QR payload.
 */
fun parseQrPayload(raw: String?): QrPayload? {
    if (raw.isNullOrBlank()) return null
    var text = raw.trim()
    val qrMatch = QR_PARAM_REGEX.find(text)
    if (qrMatch != null) {
        text = try {
            java.net.URLDecoder.decode(qrMatch.groupValues[1], "UTF-8")
        } catch (_: Exception) {
            qrMatch.groupValues[1]
        }
    }
    val m = QR_PAYLOAD_REGEX.find(text) ?: return null
    val kind = if (m.groupValues[1].equals("u", true) || m.groupValues[1].equals("user", true)) {
        QrKind.USER
    } else {
        QrKind.TEAM
    }
    return QrPayload(kind, m.groupValues[2])
}

/** Human-friendly label for a pending-qr kind ("friend" / "team"). */
fun qrKindLabel(kind: QrKind): String = if (kind == QrKind.USER) "friend" else "team"
