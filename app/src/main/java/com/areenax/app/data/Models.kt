package com.areenax.app.data

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/*
 * AREENAX DTOs — field names match the API JSON EXACTLY as the routes return
 * them (camelCase; verified against the route files — see SPEC/02-API.md).
 * All models are @Serializable with ignoreUnknownKeys=true, explicitNulls=false,
 * encodeDefaults=true (ApiClient JSON config), so unknown server fields are
 * dropped and absent fields fall back to the defaults below.
 *
 * String-based enums: the web compares raw strings and the server may add new
 * values, so type/status/category fields are plain Strings. Use the constants
 * objects below instead of magic literals.
 */

// ---------------------------------------------------------------------------
// Role model (src/lib/roles.ts)
// ---------------------------------------------------------------------------
object Roles {
    const val USER = "USER"
    const val MODERATOR = "MODERATOR"
    const val FINANCE_ADMIN = "FINANCE_ADMIN"
    const val SUPER_ADMIN = "SUPER_ADMIN"
    const val ADMIN = "ADMIN" // legacy alias of SUPER_ADMIN

    private val RANK = mapOf(
        USER to 0, MODERATOR to 1, FINANCE_ADMIN to 2, SUPER_ADMIN to 3, ADMIN to 3,
    )

    /** Client visibility gate for the admin console/panel (server enforces the real RBAC). */
    val ADMIN_PANEL_ROLES = listOf(MODERATOR, FINANCE_ADMIN, SUPER_ADMIN, ADMIN)

    fun roleRank(role: String?): Int = RANK[role ?: ""] ?: 0
    fun hasRole(role: String?, minimum: String): Boolean = roleRank(role) >= roleRank(minimum)
    fun isAdminPanelRole(role: String?): Boolean = role in ADMIN_PANEL_ROLES
}

// ---------------------------------------------------------------------------
// Core identity
// ---------------------------------------------------------------------------
/** The `publicUser` projection every authenticated endpoint returns. */
@Immutable
@Serializable
data class User(
    val id: String,
    val email: String? = null,
    val fullName: String = "",
    val gameName: String = "",
    val phone: String? = null,
    val gameUid: String? = null,
    val uid: String = "",
    val avatar: String? = null,
    val role: String = Roles.USER,
    val referralCode: String = "",
    val referredById: String? = null,
    val balance: Double = 0.0,
    val isGuest: Boolean = false,
    val createdAt: String = "",
)

/** Shorthand user summary embedded in tournament entries / lookups / requests. */
@Immutable
@Serializable
data class UserSummary(
    val id: String,
    val gameName: String = "",
    val fullName: String = "",
    val uid: String = "",
    val avatar: String? = null,
)

// ---------------------------------------------------------------------------
// Games & bootstrap
// ---------------------------------------------------------------------------
@Immutable
@Serializable
data class Game(
    val id: String,
    val name: String = "",
    val image: String = "",
    val isActive: Boolean = true,
    val sortOrder: Int = 0,
    /** Admin-decided minutes the "Match in Progress" state lasts for this game. */
    val matchDurationMinutes: Int? = null,
    /** Admin-managed per-game configuration (host creation flow consumes these). */
    val modes: List<GameMode> = emptyList(),
    val maps: List<GameMap> = emptyList(),
    val perspectives: List<GamePerspective> = emptyList(),
)

/** Match type — created & managed ONLY by the Admin Panel; slots are fixed. */
@Serializable
data class GameMode(
    val id: String,
    val gameId: String = "",
    val name: String = "",
    val slots: Int = 0,
    val sub: String = "",
    val icon: String = "",
    val sortOrder: Int = 0,
    val isActive: Boolean = true,
)

@Serializable
data class GameMap(
    val id: String,
    val gameId: String = "",
    val name: String = "",
    val sortOrder: Int = 0,
    val isActive: Boolean = true,
)

@Serializable
data class GamePerspective(
    val id: String,
    val gameId: String = "",
    val name: String = "",
    val sortOrder: Int = 0,
    val isActive: Boolean = true,
)

@Serializable
data class Banner(
    val id: String,
    val title: String? = null,
    val image: String? = null,
    val sortOrder: Int = 0,
)

/**
 * App settings from GET /bootstrap. `depositAccounts` keys include the
 * raw / lowercase / UPPERCASE / Title-case spellings of each admin-configured
 * account name (server expands the variants — key lookups must be defensive).
 */
@Serializable
data class AppSettings(
    val appName: String = "",
    val whatsapp: String = "",
    val instagram: String = "",
    val telegram: String = "",
    val discord: String = "",
    val youtube: String = "",
    val minDeposit: Double = 0.0,
    val minWithdraw: Double = 0.0,
    val maxDeposit: Double = 0.0,   // A4-03: server-side max, client pre-gates
    val maxTransfer: Double = 0.0,  // A4-03
    val referralBonus: Double = 0.0,
    val welcomeBonus: Double = 0.0,
    val commission: Double = 0.0,
    val version: String = "",
    val aboutMission: String = "",
    val appDownloadUrl: String = "",
    val depositAccounts: Map<String, String> = emptyMap(),
)

@Serializable
data class BootstrapResponse(
    val games: List<Game> = emptyList(),
    val banners: List<Banner> = emptyList(),
    val settings: AppSettings = AppSettings(),
)

// ---------------------------------------------------------------------------
// Tournaments
// ---------------------------------------------------------------------------
object TournamentStatuses {
    const val UPCOMING = "UPCOMING"
    const val ONGOING = "ONGOING"
    const val COMPLETED = "COMPLETED"
    const val CANCELLED = "CANCELLED"
}

@Immutable
@Serializable
data class Tournament(
    val id: String,
    val gameId: String = "",
    val hostId: String = "",
    val name: String = "",
    /** Solo | Duo | Squad | 1v1 | 2v2 | 4v4 … (Admin-managed match type name). */
    val mode: String = "",
    val map: String = "",
    /** TPP | FPP */
    val perspective: String = "",
    val entryFee: Double = 0.0,
    val prizePool: Double = 0.0,
    val perKill: Double = 0.0,
    val loserPrize: Double = 0.0,
    val maxPlayers: Int = 0,
    val currentPlayers: Int = 0,
    val status: String = TournamentStatuses.UPCOMING,
    val rules: String = "",
    /** Room credentials are only returned for joined users (server strips them otherwise). */
    val roomId: String? = null,
    val roomPassword: String? = null,
    val roomExpiresAt: String? = null,
    val resultsPublishedAt: String? = null,
    /** Server-computed: Room ID & Password are set (meaningful for joined users). */
    val hasRoom: Boolean? = null,
    /** Server-computed: the current caller has an entry in this tournament. */
    val joined: Boolean? = null,
    /** Admin Panel toggle — joined players see "Submit Result Proof" instead of Match in Progress. */
    val disabled: Boolean? = null,
    /** Server-computed: proof mode is active for the current viewer. */
    val proofActive: Boolean? = null,
    /** Server-computed: the current user already submitted a result proof. */
    val myProofSubmitted: Boolean? = null,
    /** Details endpoint only: the user's own submitted proof image (data URL). */
    val myProofImage: String? = null,
    val bannerImage: String? = null,
    val region: String = "",
    val startTime: String = "",
    val createdAt: String = "",
    val host: UserSummary? = null,
    val game: Game? = null,
    val myEntry: TournamentEntry? = null,
    // -- Detail endpoint enrichments --
    val entries: List<TournamentEntry> = emptyList(),
    // -- Admin 48-Slot Extensions --
    val totalSlots: Int = 48,
    val freeSlotNumber: Int? = null,
)

@Serializable
data class TournamentEntry(
    val id: String,
    val tournamentId: String = "",
    val userId: String = "",
    val teamName: String? = null,
    val kills: Int = 0,
    val rank: Int? = null,
    val prize: Double = 0.0,
    val status: String = "",
    val createdAt: String = "",
    val user: UserSummary? = null,
    /** Embedded live-status tournament (GET /my/tournaments rows). */
    val tournament: Tournament? = null,
)

@Serializable
data class TournamentDetailResponse(
    val tournament: Tournament = Tournament(id = ""),
)

@Serializable
data class TournamentListResponse(
    val tournaments: List<Tournament> = emptyList(),
)

@Serializable
data class MyTournamentsResponse(
    val entries: List<TournamentEntry> = emptyList(),
)

@Serializable
data class MyHostedResponse(
    val tournaments: List<Tournament> = emptyList(),
)

@Serializable
data class JoinTournamentResponse(
    val entry: TournamentEntry = TournamentEntry(id = ""),
)

@Serializable
data class TournamentStats(
    val totalKills: Int = 0,
    val topKills: Int = 0,
    val totalPrize: Double = 0.0,
    val players: Int = 0,
)

@Serializable
data class TournamentResultsResponse(
    val tournament: Tournament = Tournament(id = ""),
    val stats: TournamentStats = TournamentStats(),
)

// ---------------------------------------------------------------------------
// Wallet
// ---------------------------------------------------------------------------
object TransactionTypes {
    const val DEPOSIT = "DEPOSIT"
    const val WITHDRAW = "WITHDRAW"
    const val TRANSFER = "TRANSFER"
    const val PRIZE = "PRIZE"
    const val ENTRY_FEE = "ENTRY_FEE"
    const val REFUND = "REFUND"
    const val REFERRAL_BONUS = "REFERRAL_BONUS"
    const val TASK_REWARD = "TASK_REWARD"
    const val ADJUSTMENT = "ADJUSTMENT"
}

object TransactionStatuses {
    const val PENDING = "PENDING"
    const val COMPLETED = "COMPLETED"
    const val FAILED = "FAILED"
    const val REJECTED = "REJECTED"
}

@Serializable
data class Transaction(
    val id: String,
    val userId: String = "",
    val type: String = "",
    val amount: Double = 0.0,
    val method: String? = null,
    val status: String = "",
    val reference: String = "",
    val note: String? = null,
    val createdAt: String = "",
)

@Serializable
data class WalletResponse(
    val balance: Double = 0.0,
    val transactions: List<Transaction> = emptyList(),
)

@Serializable
data class WalletOpResponse(
    val success: Boolean = false,
    val reference: String = "",
    val balance: Double? = null,
    /** POST /wallet/transfer returns the receiver's gameName. */
    val receiver: String? = null,
)

// ---------------------------------------------------------------------------
// Team
// ---------------------------------------------------------------------------
@Serializable
data class TeamMemberInfo(
    val id: String,
    val userId: String = "",
    /** "OWNER" | "MEMBER" */
    val role: String = "MEMBER",
    val user: UserSummary = UserSummary(id = ""),
)

@Serializable
data class Team(
    val id: String,
    val name: String = "",
    val tag: String? = null,
    val ownerId: String = "",
    val createdAt: String = "",
    val members: List<TeamMemberInfo> = emptyList(),
    /** POST /team response only — UIDs that were invited. */
    val invited: List<String> = emptyList(),
)

@Serializable
data class TeamResponse(val team: Team? = null)

@Serializable
data class TeamStats(
    val matches: Int = 0,
    val wins: Int = 0,
    val kills: Int = 0,
)

@Serializable
data class TeamScreenResponse(
    val team: Team? = null,
    val stats: TeamStats = TeamStats(),
)

@Serializable
data class TeamJoinRequestRow(
    val id: String,
    /** "PENDING" | "ACCEPTED" | "DECLINED" */
    val status: String = "PENDING",
    val createdAt: String = "",
    val user: UserSummary = UserSummary(id = ""),
)

@Serializable
data class TeamJoinRequestsResponse(
    val requests: List<TeamJoinRequestRow> = emptyList(),
    /** Non-owner variant: the caller's own pending request. */
    val myRequest: TeamJoinRequestRow? = null,
)

// ---------------------------------------------------------------------------
// Friends & chat
// ---------------------------------------------------------------------------
@Serializable
data class FriendUser(
    val id: String,
    val gameName: String = "",
    val fullName: String = "",
    val uid: String = "",
    val avatar: String? = null,
    val isOnline: Boolean? = null,
    val lastSeen: String? = null,
)

@Serializable
data class Friend(
    val id: String,
    val gameName: String = "",
    val fullName: String = "",
    val uid: String = "",
    val avatar: String? = null,
    val friendshipId: String = "",
    val lastMessage: String? = null,
    val lastMessageAt: String? = null,
    val unread: Int = 0,
)

@Serializable
data class FriendsResponse(val friends: List<Friend> = emptyList())

@Serializable
data class FriendRequestRow(
    val id: String,
    /** "PENDING" | "ACCEPTED" | "DECLINED" */
    val status: String = "PENDING",
    /** "UID" | "QR" */
    val source: String = "UID",
    val createdAt: String = "",
    /** The OTHER user in the request (sender for received, receiver for sent). */
    val user: UserSummary = UserSummary(id = ""),
)

@Serializable
data class FriendRequestsResponse(
    val received: List<FriendRequestRow> = emptyList(),
    val sent: List<FriendRequestRow> = emptyList(),
    val receivedPending: Int = 0,
)

@Serializable
data class FriendRequestCreateResponse(
    val request: FriendRequestRow = FriendRequestRow(id = ""),
    /** They had already sent you a pending request → auto-accepted. */
    val autoAccepted: Boolean? = null,
)

@Serializable
data class ChatMessage(
    val id: String,
    val senderId: String = "",
    val receiverId: String = "",
    val text: String = "",
    val createdAt: String = "",
    val read: Boolean = false,
)

@Serializable
data class ChatResponse(
    val friend: FriendUser = FriendUser(id = ""),
    val messages: List<ChatMessage> = emptyList(),
)

@Serializable
data class MessageResponse(val message: ChatMessage = ChatMessage(id = ""))

// ---------------------------------------------------------------------------
// Notifications
// ---------------------------------------------------------------------------
@Serializable
data class AppNotification(
    val id: String,
    val title: String = "",
    val message: String = "",
    val type: String = "",
    val isRead: Boolean = false,
    /** Deep-link target ("tournament:<id>" | "host:<id>" | "wallet" | …). */
    val link: String? = null,
    val createdAt: String = "",
)

@Serializable
data class NotificationsResponse(
    val notifications: List<AppNotification> = emptyList(),
    val unread: Int = 0,
)

@Serializable
data class UnreadResponse(val unread: Int = 0)

@Serializable
data class OkResponse(val success: Boolean = false, val unread: Int? = null)

// ---------------------------------------------------------------------------
// Tasks / referrals / stats / leaderboard / achievements
// ---------------------------------------------------------------------------
@Serializable
data class Task(
    val id: String,
    val title: String = "",
    val description: String = "",
    /** Material Symbol name (resolve via core.ui.AreenaxIcon). */
    val icon: String = "",
    val reward: Double = 0.0,
    val type: String = "",
    /** Claimed in the current period. */
    val claimed: Boolean = false,
    val completed: Boolean = false,
)

@Serializable
data class TasksResponse(val tasks: List<Task> = emptyList())

@Serializable
data class TaskClaimResponse(
    val success: Boolean = false,
    val reward: Double = 0.0,
)

@Serializable
data class ReferralInfo(
    val id: String,
    val gameName: String = "",
    val fullName: String = "",
    val createdAt: String = "",
    val bonusEarned: Double = 0.0,
)

@Serializable
data class ReferralsResponse(
    val referralCode: String = "",
    val bonus: Double = 0.0,
    val referrals: List<ReferralInfo> = emptyList(),
    val totalEarned: Double = 0.0,
    val count: Int = 0,
)

@Serializable
data class MyStats(
    val totalMatches: Int = 0,
    val wins: Int = 0,
    val kills: Int = 0,
    val earnings: Double = 0.0,
    val winRate: Double = 0.0,
    val top3: Int = 0,
    val leaderboardRank: Int? = null,
    val kd: Double = 0.0,
)

@Serializable
data class StatsResponse(val stats: MyStats = MyStats())

@Serializable
data class LeaderboardRow(
    val rank: Int = 0,
    val userId: String = "",
    val gameName: String = "",
    val fullName: String = "",
    val uid: String = "",
    val avatar: String? = null,
    val points: Double = 0.0,
    val wins: Int = 0,
    val kills: Int = 0,
    val isMe: Boolean? = null,
)

@Serializable
data class LeaderboardResponse(val leaderboard: List<LeaderboardRow> = emptyList())

object AchievementCategories {
    const val TOURNAMENTS = "TOURNAMENTS"
    const val KILLS = "KILLS"
    const val EARNINGS = "EARNINGS"
    const val SOCIAL = "SOCIAL"
}

@Serializable
data class Achievement(
    val id: String,
    val title: String = "",
    val description: String = "",
    /** Material Symbol name (resolve via core.ui.AreenaxIcon). */
    val icon: String = "",
    val category: String = "",
    val target: Int = 0,
    val reward: Double = 0.0,
    val progress: Int = 0,
    val unlocked: Boolean = false,
    val unlockedAt: String? = null,
)

@Serializable
data class AchievementsResponse(
    val achievements: List<Achievement> = emptyList(),
    val unlockedCount: Int = 0,
    val total: Int = 0,
)

// ---------------------------------------------------------------------------
// Bank accounts
// ---------------------------------------------------------------------------
@Serializable
data class BankAccount(
    val id: String,
    val bankName: String = "",
    val accountTitle: String = "",
    val accountNumber: String = "",
    val isPrimary: Boolean = false,
)

@Serializable
data class BankAccountsResponse(val accounts: List<BankAccount> = emptyList())

@Serializable
data class BankAccountResponse(val account: BankAccount = BankAccount(id = ""))

// ---------------------------------------------------------------------------
// QR & player lookup
// ---------------------------------------------------------------------------
/** "self" | "friend" | "request_sent" | "request_received" | "none" */
object QrRelations {
    const val SELF = "self"
    const val FRIEND = "friend"
    const val REQUEST_SENT = "request_sent"
    const val REQUEST_RECEIVED = "request_received"
    const val NONE = "none"
}

@Serializable
data class QrUserLookup(
    val kind: String = "user",
    val user: UserSummary = UserSummary(id = ""),
    val relation: String = QrRelations.NONE,
)

@Serializable
data class QrTeamOwner(
    val id: String,
    val gameName: String = "",
    val uid: String = "",
)

@Serializable
data class QrTeamMember(
    val id: String,
    val gameName: String = "",
    val uid: String = "",
    val avatar: String? = null,
)

@Serializable
data class QrTeamInfo(
    val id: String,
    val name: String = "",
    val tag: String? = null,
    val memberCount: Int = 0,
    val maxMembers: Int = 4,
    val owner: QrTeamOwner = QrTeamOwner(id = ""),
    val members: List<QrTeamMember> = emptyList(),
)

@Serializable
data class QrTeamLookup(
    val kind: String = "team",
    val team: QrTeamInfo = QrTeamInfo(id = ""),
    val isMember: Boolean = false,
    val joinRequested: Boolean = false,
)

@Serializable
data class PlayerLookupResponse(val user: UserSummary = UserSummary(id = ""))

// ---------------------------------------------------------------------------
// Games config (host flow)
// ---------------------------------------------------------------------------
@Serializable
data class GamesConfigResponse(val games: List<Game> = emptyList())

// ---------------------------------------------------------------------------
// Push (register/unregister are wired when FCM is activated; poll is web-only)
// ---------------------------------------------------------------------------
@Serializable
data class PushRegisterRequest(
    val token: String,
    val platform: String = "ANDROID",
    val deviceId: String = "",
)

@Serializable
data class PushUnregisterRequest(val token: String)

@Serializable
data class PushPollItem(
    val id: String,
    val title: String = "",
    val message: String = "",
    val link: String? = null,
    val createdAt: String = "",
)

@Serializable
data class PushPollResponse(
    val unread: Int = 0,
    val latest: List<PushPollItem> = emptyList(),
    val now: String = "",
)

// ---------------------------------------------------------------------------
// Admin (user-panel mobile admin; server RBAC enforced)
// ---------------------------------------------------------------------------
@Serializable
data class PaymentRequest(
    val id: String,
    /** "DEPOSIT" | "WITHDRAW" */
    val type: String = "",
    val amount: Double = 0.0,
    val method: String? = null,
    val reference: String = "",
    val note: String? = null,
    val createdAt: String = "",
    /** Four-eyes: reviewer recorded, second approval pending. */
    val reviewedById: String? = null,
    val approvedById: String? = null,
    val user: AdminRequestUser = AdminRequestUser(id = ""),
)

@Serializable
data class AdminRequestUser(
    val id: String,
    val fullName: String = "",
    val gameName: String = "",
    val uid: String = "",
    val email: String? = null,
    val phone: String? = null,
    val balance: Double = 0.0,
)

@Serializable
data class AdminRequestsResponse(val requests: List<PaymentRequest> = emptyList())

/** GET /admin/requests/:id and GET /admin/proofs/:id — receipt/proof image (data URL). */
@Serializable
data class AdminImageResponse(
    val id: String,
    val image: String? = null,
)

/** Admin tournaments list rows (full Tournament + entries + disabled + resultsPublishedAt). */
@Serializable
data class AdminTournamentsResponse(val tournaments: List<Tournament> = emptyList())

@Serializable
data class AdminProof(
    val id: String,
    val createdAt: String = "",
    val user: UserSummary = UserSummary(id = ""),
    val tournament: Tournament = Tournament(id = ""),
)

@Serializable
data class AdminProofsResponse(val proofs: List<AdminProof> = emptyList())

@Serializable
data class AdminSettingsResponse(val settings: Map<String, String> = emptyMap())

@Serializable
data class AdminSettingsSaveResponse(
    val success: Boolean = false,
    val updated: List<String> = emptyList(),
)

/** GET /admin/security — events and audit rows share one flexible shape. */
@Serializable
data class AdminSecurityItem(
    val id: String = "",
    val createdAt: String = "",
    // events
    val userId: String? = null,
    val endpoint: String? = null,
    val action: String = "",
    val decision: String? = null,
    val reason: String? = null,
    val riskScore: Int? = null,
    val ip: String? = null,
    // audit
    val actorId: String? = null,
    val actorRole: String? = null,
    val targetType: String? = null,
    val targetId: String? = null,
)

@Serializable
data class AdminSecurityResponse(
    val ok: Boolean = false,
    val items: List<AdminSecurityItem> = emptyList(),
    // reconcile variant
    val checkedCount: Int? = null,
    val reconcileItems: List<AdminReconcileRow> = emptyList(),
)

@Serializable
data class AdminReconcileRow(
    val userId: String = "",
    val gameName: String = "",
    val ledgerSum: Double = 0.0,
    val balance: Double = 0.0,
    val drift: Double = 0.0,
)

@Serializable
data class AdminGamesResponse(val games: List<Game> = emptyList())

// ---------------------------------------------------------------------------
// Request bodies (JSON POST/PATCH/DELETE bodies)
// ---------------------------------------------------------------------------
@Serializable
data class LoginRequest(
    val email: String? = null,
    val phone: String? = null,
    val password: String,
)

@Serializable
data class RegisterRequest(
    val fullName: String,
    val gameName: String,
    val phone: String,
    val gameUid: String,
    val password: String,
    val email: String,
    val referralCode: String? = null,
)

/** POST /auth/check — any subset of the fields (signup pre-flight). */
@Serializable
data class AuthCheckRequest(
    val email: String? = null,
    val phone: String? = null,
    val gameName: String? = null,
    val gameUid: String? = null,
)

@Serializable
data class AuthCheckResponse(
    val taken: Map<String, Boolean> = emptyMap(),
    val messages: Map<String, String> = emptyMap(),
)

@Serializable
data class AuthResponse(
    val token: String,
    val user: User = User(id = ""),
)

/** POST /auth/logout — `{}` for the current session, `{all:true}` revokes all sessions. */
@Serializable
data class LogoutRequest(val all: Boolean? = null)

@Serializable
data class MeResponse(val user: User = User(id = ""))

/** PATCH /me — profile save (`password?` also present) or password change. */
@Serializable
data class PatchProfileRequest(
    val fullName: String? = null,
    val gameName: String? = null,
    val email: String? = null,
    val password: String? = null,
    val currentPassword: String? = null,
    /** Optional avatar data URL ≤ 8 MB (route supports it). */
    val avatar: String? = null,
)

@Serializable
data class DepositRequest(
    val amount: Double,
    val method: String,
    val trxId: String,
    val receiptName: String,
    /** data:image/...;base64 — ≤ 8 MB. */
    val image: String? = null,
    val accountId: String? = null,
)

@Serializable
data class WithdrawRequest(
    val amount: Double,
    val method: String,
    val accountNumber: String,
    val accountTitle: String? = null,
)

@Serializable
data class TransferRequest(
    val recipient: String,
    val amount: Double,
    val note: String? = null,
)

/** POST /tournaments/:id/join — solo join serializes to `{}` (all nulls). */
@Serializable
data class JoinTournamentRequest(
    val members: List<JoinMember>? = null,
    val slotNumber: Int? = null,
)

@Serializable
data class JoinMember(val uid: String)

@Serializable
data class CreateTournamentRequest(
    val gameId: String,
    val name: String,
    val mode: String,
    val map: String,
    val perspective: String,
    val entryFee: Double,
    val prizePool: Double,
    val loserPrize: Double,
    val maxPlayers: Int,
    val rules: String,
    val startTime: String,
    /** data URL ≤ 5 MB. */
    val image: String? = null,
)

@Serializable
data class TournamentResponse(val tournament: Tournament = Tournament(id = ""))

/** POST /tournaments/:id/proof and POST /tournaments/:id/result-proof. */
@Serializable
data class ProofImageRequest(val image: String)

/** Result proof row (POST /tournaments/:id/result-proof → {proof}). */
@Serializable
data class ResultProof(
    val id: String,
    val tournamentId: String = "",
    val userId: String = "",
    val image: String? = null,
    val createdAt: String = "",
)

@Serializable
data class ProofResponse(val proof: ResultProof? = null)

@Serializable
data class TeamCreateRequest(
    val name: String,
    val tag: String? = null,
    val memberUids: List<String> = emptyList(),
)

@Serializable
data class TeamJoinRequestBody(val teamId: String)

@Serializable
data class TeamJoinRequestActionBody(val action: String)

@Serializable
data class FriendRequestCreateBody(
    val uid: String,
    /** "UID" | "QR" */
    val source: String = "UID",
)

@Serializable
data class FriendRequestActionBody(val action: String)

@Serializable
data class SendMessageBody(val text: String)

@Serializable
data class TaskClaimBody(val taskId: String)

@Serializable
data class NotificationIdsBody(val ids: List<String>)

@Serializable
data class BankCreateRequest(
    val bankName: String,
    val accountTitle: String,
    val accountNumber: String,
)

@Serializable
data class AdminStatusBody(val status: String)

@Serializable
data class AdminRoomBody(
    val roomId: String,
    val roomPassword: String,
    /** ISO timestamp or null (visible until = forever). */
    val roomExpiresAt: String? = null,
)

@Serializable
data class AdminDisableBody(val disabled: Boolean)

@Serializable
data class AdminResultEntry(
    val entryId: String,
    val rank: Int,
    val kills: Int,
    val prize: Double,
)

@Serializable
data class AdminResultsBody(val entries: List<AdminResultEntry>)

@Serializable
data class AdminActionBody(val action: String)

/** POST /admin/settings — the settings form map (server filters keys by role). */
@Serializable
data class AdminSettingsBody(val settings: Map<String, String>)

@Serializable
data class AdminGameCreateBody(
    val name: String,
    val image: String,
)

@Serializable
data class AdminGameUpdateBody(
    val isActive: Boolean? = null,
    val matchDurationMinutes: Int? = null,
)

@Serializable
data class AdminGameConfigOp(
    val kind: String,
    val op: String,
    val name: String? = null,
    val slots: Int? = null,
    val sub: String? = null,
    val id: String? = null,
)
