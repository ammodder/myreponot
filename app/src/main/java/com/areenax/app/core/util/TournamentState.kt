package com.areenax.app.core.util

import com.areenax.app.data.Tournament
import com.areenax.app.data.TournamentStatuses

/*
 * EXACT Kotlin port of src/lib/tournament-state.ts — the one shared source of
 * truth for EVERY tournament card / details surface in the app.
 */

/** Join-state machine action kinds (labels match the web card exactly). */
enum class TournamentCardActionKind {
    JOIN, SLOTS_FULL, JOINED, ROOM, LIVE, PROOF, COMPLETED, RESULTS,
}

data class TournamentCardAction(
    val kind: TournamentCardActionKind,
    val label: String,
    /** true when the element is a tappable action (vs. an informational state). */
    val interactive: Boolean,
)

fun hasRoomCredentials(t: Tournament): Boolean = !t.roomId.isNullOrBlank() && !t.roomPassword.isNullOrBlank()

/**
 * Effective, time-aware status — flips to ONGOING the moment startTime passes
 * and to COMPLETED as soon as results are published (or admin marks completed).
 */
fun deriveTournamentStatus(
    status: String,
    startTime: String,
    resultsPublishedAt: String?,
    nowMillis: Long = System.currentTimeMillis(),
): String {
    if (!resultsPublishedAt.isNullOrBlank() || status == TournamentStatuses.COMPLETED) {
        return TournamentStatuses.COMPLETED
    }
    if (status == TournamentStatuses.CANCELLED) return TournamentStatuses.CANCELLED
    if (status == TournamentStatuses.ONGOING) return TournamentStatuses.ONGOING
    val start = startTime.parseEpochMillis() ?: 0L
    return if (nowMillis >= start) TournamentStatuses.ONGOING else TournamentStatuses.UPCOMING
}

fun deriveTournamentStatus(t: Tournament, nowMillis: Long = System.currentTimeMillis()): String =
    deriveTournamentStatus(t.status, t.startTime, t.resultsPublishedAt, nowMillis)

/** Credentials are available to joined users until the admin-set expiry. */
fun isRoomOpen(t: Tournament, nowMillis: Long = System.currentTimeMillis()): Boolean {
    if (!hasRoomCredentials(t)) return false
    val expiry = t.roomExpiresAt ?: return true
    val expiryMillis = expiry.parseEpochMillis() ?: return true
    return nowMillis < expiryMillis
}

/**
 * Result Proof mode — active while ONGOING (results unpublished) when either
 * the admin disabled the tournament or the game's match-duration elapsed
 * (from room expiry, or startTime when no expiry).
 */
fun proofModeActive(
    disabled: Boolean?,
    status: String,
    startTime: String,
    roomExpiresAt: String?,
    resultsPublishedAt: String?,
    gameMatchDurationMinutes: Int?,
    nowMillis: Long = System.currentTimeMillis(),
): Boolean {
    val derived = deriveTournamentStatus(status, startTime, resultsPublishedAt, nowMillis)
    if (derived != TournamentStatuses.ONGOING) return false
    if (disabled == true) return true
    val durationMinutes = (gameMatchDurationMinutes ?: 60).coerceAtLeast(0)
    val base = roomExpiresAt.parseEpochMillis()
        ?: startTime.parseEpochMillis()
        ?: return false
    return nowMillis >= base + durationMinutes * 60_000L
}

fun proofModeActive(t: Tournament, nowMillis: Long = System.currentTimeMillis()): Boolean =
    proofModeActive(
        disabled = t.disabled,
        status = t.status,
        startTime = t.startTime,
        roomExpiresAt = t.roomExpiresAt,
        resultsPublishedAt = t.resultsPublishedAt,
        gameMatchDurationMinutes = t.game?.matchDurationMinutes,
        nowMillis = nowMillis,
    )

/**
 * Card action state machine (computeTournamentAction) — [proofActive] is the
 * server-computed per-viewer flag; joined-only states are never surfaced to
 * un-joined users.
 */
fun computeTournamentAction(
    t: Tournament,
    joined: Boolean,
    proofActive: Boolean? = t.proofActive,
    nowMillis: Long = System.currentTimeMillis(),
): TournamentCardAction {
    if (joined) {
        if (!t.resultsPublishedAt.isNullOrBlank()) {
            return TournamentCardAction(TournamentCardActionKind.RESULTS, "RESULTS", true)
        }
        if (t.status == TournamentStatuses.CANCELLED) {
            return TournamentCardAction(TournamentCardActionKind.COMPLETED, "CANCELLED", false)
        }
        if (t.status == TournamentStatuses.ONGOING && proofActive == true) {
            return TournamentCardAction(TournamentCardActionKind.PROOF, "SUBMIT RESULT PROOF", true)
        }
        if (isRoomOpen(t, nowMillis)) {
            return TournamentCardAction(TournamentCardActionKind.ROOM, "ROOM ID & PASSWORD", true)
        }
        if (t.status == TournamentStatuses.ONGOING) {
            return TournamentCardAction(TournamentCardActionKind.LIVE, "MATCH PROCESSING", false)
        }
        if (t.status == TournamentStatuses.COMPLETED) {
            return TournamentCardAction(TournamentCardActionKind.COMPLETED, "COMPLETED", false)
        }
        return TournamentCardAction(TournamentCardActionKind.JOINED, "JOINED", false)
    }

    // Not joined — generic lifecycle states only.
    if (!t.resultsPublishedAt.isNullOrBlank() || t.status == TournamentStatuses.COMPLETED) {
        return TournamentCardAction(TournamentCardActionKind.COMPLETED, "COMPLETED", false)
    }
    if (t.status == TournamentStatuses.CANCELLED) {
        return TournamentCardAction(TournamentCardActionKind.COMPLETED, "CANCELLED", false)
    }
    if (t.status == TournamentStatuses.ONGOING ||
        (t.maxPlayers > 0 && t.currentPlayers >= t.maxPlayers)
    ) {
        return TournamentCardAction(TournamentCardActionKind.SLOTS_FULL, "SLOTS FULL", false)
    }
    return TournamentCardAction(TournamentCardActionKind.JOIN, "JOIN NOW", true)
}

/** "Open" / "Live" / status — the card's right-hand status label. */
fun tournamentStatusLabel(t: Tournament, nowMillis: Long = System.currentTimeMillis()): String =
    when (deriveTournamentStatus(t, nowMillis)) {
        TournamentStatuses.UPCOMING -> "Open"
        TournamentStatuses.ONGOING -> "Live"
        else -> deriveTournamentStatus(t, nowMillis)
    }

/**
 * The fixed host prize economics (host creation preview + admin auto-prize):
 * collection = fee × slots; prizePool = round(50%); rank splits 50/30/20;
 * loserPrize = round(remaining / (slots-1)).
 */
fun hostPrizeMath(entryFee: Double, slots: Int): HostPrizeBreakdown {
    val collection = entryFee * slots
    val prizePool = Math.round(collection * 0.5).toDouble()
    val rank1 = Math.round(prizePool * 0.5).toDouble()
    val rank2 = Math.round(prizePool * 0.3).toDouble()
    val rank3 = Math.round(prizePool * 0.2).toDouble()
    val loserPrize = if (slots > 1) Math.round(collection * 0.5 / (slots - 1)).toDouble() else 0.0
    return HostPrizeBreakdown(collection, prizePool, rank1, rank2, rank3, loserPrize)
}

data class HostPrizeBreakdown(
    val collection: Double,
    val prizePool: Double,
    val rank1: Double,
    val rank2: Double,
    val rank3: Double,
    val loserPrize: Double,
)

/** DepositConfirm display reference: "#DEP-<random 6 digits>" (client-generated). */
fun newDepositDisplayRef(): String = "#DEP-" + (100000..999999).random()

/** FUNDING_BANKS — Q13: "Alphla" corrected to "Alfalah" (owner decision 2026-09-20). */
val FUNDING_BANKS = listOf("Jazzcash", "Easypaisa", "Sadapay", "Alfalah Bank", "Mezan Bank")
