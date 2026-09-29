package com.areenax.app.core.network

import com.areenax.app.data.AdminActionBody
import com.areenax.app.data.AdminDisableBody
import com.areenax.app.data.AdminGameConfigOp
import com.areenax.app.data.AdminGameCreateBody
import com.areenax.app.data.AdminGameUpdateBody
import com.areenax.app.data.AdminGamesResponse
import com.areenax.app.data.AdminImageResponse
import com.areenax.app.data.AdminProofsResponse
import com.areenax.app.data.AdminRequestsResponse
import com.areenax.app.data.AdminResultsBody
import com.areenax.app.data.AdminReconcileRow
import com.areenax.app.data.AdminRoomBody
import com.areenax.app.data.AdminSecurityResponse
import com.areenax.app.data.AdminSettingsBody
import com.areenax.app.data.AdminSettingsResponse
import com.areenax.app.data.AdminSettingsSaveResponse
import com.areenax.app.data.AdminStatusBody
import com.areenax.app.data.AdminTournamentsResponse
import com.areenax.app.data.AuthCheckRequest
import com.areenax.app.data.AuthCheckResponse
import com.areenax.app.data.AuthResponse
import com.areenax.app.data.AchievementsResponse
import com.areenax.app.data.BankAccountResponse
import com.areenax.app.data.BankAccountsResponse
import com.areenax.app.data.BankCreateRequest
import com.areenax.app.data.BootstrapResponse
import com.areenax.app.data.ChatResponse
import com.areenax.app.data.CreateTournamentRequest
import com.areenax.app.data.DepositRequest
import com.areenax.app.data.FriendRequestActionBody
import com.areenax.app.data.FriendRequestCreateBody
import com.areenax.app.data.FriendRequestCreateResponse
import com.areenax.app.data.FriendRequestsResponse
import com.areenax.app.data.FriendsResponse
import com.areenax.app.data.GamesConfigResponse
import com.areenax.app.data.JoinTournamentRequest
import com.areenax.app.data.JoinTournamentResponse
import com.areenax.app.data.LeaderboardResponse
import com.areenax.app.data.LoginRequest
import com.areenax.app.data.LogoutRequest
import com.areenax.app.data.MeResponse
import com.areenax.app.data.MessageResponse
import com.areenax.app.data.MyHostedResponse
import com.areenax.app.data.MyTournamentsResponse
import com.areenax.app.data.NotificationIdsBody
import com.areenax.app.data.NotificationsResponse
import com.areenax.app.data.OkResponse
import com.areenax.app.data.PatchProfileRequest
import com.areenax.app.data.PlayerLookupResponse
import com.areenax.app.data.ProofImageRequest
import com.areenax.app.data.ProofResponse
import com.areenax.app.data.PushRegisterRequest
import com.areenax.app.data.PushUnregisterRequest
import com.areenax.app.data.PushPollResponse
import com.areenax.app.data.QrTeamLookup
import com.areenax.app.data.QrUserLookup
import com.areenax.app.data.ReferralsResponse
import com.areenax.app.data.RegisterRequest
import com.areenax.app.data.SendMessageBody
import com.areenax.app.data.StatsResponse
import com.areenax.app.data.TaskClaimBody
import com.areenax.app.data.TaskClaimResponse
import com.areenax.app.data.TasksResponse
import com.areenax.app.data.TeamCreateRequest
import com.areenax.app.data.TeamJoinRequestActionBody
import com.areenax.app.data.TeamJoinRequestBody
import com.areenax.app.data.TeamJoinRequestsResponse
import com.areenax.app.data.TeamResponse
import com.areenax.app.data.TeamScreenResponse
import com.areenax.app.data.TournamentDetailResponse
import com.areenax.app.data.TournamentListResponse
import com.areenax.app.data.TournamentResponse
import com.areenax.app.data.TournamentResultsResponse
import com.areenax.app.data.TransferRequest
import com.areenax.app.data.UnreadResponse
import com.areenax.app.data.WalletOpResponse
import com.areenax.app.data.WalletResponse
import com.areenax.app.data.WithdrawRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * AREENAX user-panel API — every endpoint the panel calls (SPEC/02-API.md),
 * grouped by the 13 domains of the audit. Paths are relative to `<base>/api/`
 * (see ApiClient — base URL comes from the `app_base_url` string resource).
 *
 * Call sites MUST wrap calls in `safeCall { }` (core.network) so errors,
 * network failures and 401s are handled exactly like the web client.
 *
 * Uploads are base64 data-URL strings inside JSON bodies (NOT multipart):
 *   deposit receipt  ≤ 8 MB   (wallet/deposit)
 *   result proof     ≤ ~4 MB  (tournaments/:id/result-proof)
 *   host tournament image ≤ 5 MB (POST /tournaments)
 *   avatar           ≤ 8 MB   (PATCH /me)
 */
interface Api {

    // =======================================================================
    // 1. Bootstrap & session
    // =======================================================================
    @GET("bootstrap")
    suspend fun bootstrap(): BootstrapResponse

    @POST("auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResponse

    @POST("auth/guest")
    suspend fun guestLogin(): AuthResponse

    @POST("auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthResponse

    @POST("auth/check")
    suspend fun authCheck(@Body body: AuthCheckRequest): AuthCheckResponse

    @POST("auth/logout")
    suspend fun logout(@Body body: LogoutRequest = LogoutRequest()): OkResponse

    @GET("me")
    suspend fun me(): MeResponse

    @PATCH("me")
    suspend fun patchMe(@Body body: PatchProfileRequest): MeResponse

    /** Q15/A11-03/A6-03: permanent account deletion (Play User-Data policy).
     *  Server refuses while balance > 0, a hosted tournament is not finished, or
     *  the user owns a team with other members. Success = {ok:true}; every
     *  session is invalidated server-side. */
    @DELETE("me")
    suspend fun deleteAccount(): OkResponse

    /** Q12: update this user's coarse (≈2 km cell) location, used only for the
     *  "players nearby" suggestion list. Sent ONLY when the user opens that tab
     *  with location enabled — never backgrounded. */
    @POST("me/location")
    suspend fun updateLocation(@Body body: LocationUpdateRequest): OkResponse

    /** Q12: real-friend suggestions — mode=contacts (a picked contact's phone)
     *  or mode=nearby (coarse cell). Server excludes friends/self/guests. */
    @GET("friends/suggest")
    suspend fun suggestFriends(
        @Query("mode") mode: String,
        @Query("phone") phone: String? = null,
    ): FriendSuggestResponse

    // =======================================================================
    // 2. Wallet
    // =======================================================================
    @GET("wallet")
    suspend fun wallet(): WalletResponse

    @POST("wallet/deposit")
    suspend fun deposit(@Body body: DepositRequest): WalletOpResponse

    @POST("wallet/withdraw")
    suspend fun withdraw(@Body body: WithdrawRequest): WalletOpResponse

    @POST("wallet/transfer")
    suspend fun transfer(@Body body: TransferRequest): WalletOpResponse

    // =======================================================================
    // 3. Tournaments
    // =======================================================================
    @GET("tournaments")
    suspend fun tournaments(
        @Query("status") status: String? = null,
        @Query("gameId") gameId: String? = null,
    ): TournamentListResponse

    @GET("tournaments/{id}")
    suspend fun tournamentDetails(@Path("id") id: String): TournamentDetailResponse

    @POST("tournaments")
    suspend fun createTournament(@Body body: CreateTournamentRequest): TournamentResponse

    @POST("tournaments/{id}/join")
    suspend fun joinTournament(
        @Path("id") id: String,
        @Body body: JoinTournamentRequest = JoinTournamentRequest(),
    ): JoinTournamentResponse

    @GET("tournaments/{id}/results")
    suspend fun tournamentResults(@Path("id") id: String): TournamentResultsResponse

    /** One-time result proof (admin-disabled tournaments) — image ≤ 8 MB. */
    @POST("tournaments/{id}/proof")
    suspend fun submitProof(@Path("id") id: String, @Body body: ProofImageRequest): OkResponse

    /** Replaceable result proof (ResultProofSheet) — image ≤ ~4 MB. */
    @POST("tournaments/{id}/result-proof")
    suspend fun submitResultProof(@Path("id") id: String, @Body body: ProofImageRequest): ProofResponse

    @GET("my/tournaments")
    suspend fun myTournaments(): MyTournamentsResponse

    @GET("my/hosted")
    suspend fun myHosted(): MyHostedResponse

    // =======================================================================
    // 4. Team
    // =======================================================================
    @GET("team")
    suspend fun team(): TeamScreenResponse

    @POST("team")
    suspend fun createTeam(@Body body: TeamCreateRequest): TeamResponse

    @DELETE("team")
    suspend fun leaveTeam(): OkResponse

    @GET("team/join-requests")
    suspend fun teamJoinRequests(): TeamJoinRequestsResponse

    @POST("team/join-requests")
    suspend fun requestTeamJoin(@Body body: TeamJoinRequestBody): OkResponse

    @POST("team/join-requests/{id}")
    suspend fun respondTeamJoinRequest(
        @Path("id") id: String,
        @Body body: TeamJoinRequestActionBody,
    ): OkResponse

    // =======================================================================
    // 5. Friends & chat
    // =======================================================================
    @GET("friends")
    suspend fun friends(): FriendsResponse

    @GET("friends/requests")
    suspend fun friendRequests(): FriendRequestsResponse

    @POST("friends/requests")
    suspend fun sendFriendRequest(@Body body: FriendRequestCreateBody): FriendRequestCreateResponse

    @POST("friends/requests/{id}")
    suspend fun respondFriendRequest(
        @Path("id") id: String,
        @Body body: FriendRequestActionBody,
    ): OkResponse

    @GET("friends/{id}/messages")
    suspend fun chatMessages(@Path("id") friendId: String, @Query("after") after: String? = null): ChatResponse

    @POST("friends/{id}/messages")
    suspend fun sendMessage(@Path("id") friendId: String, @Body body: SendMessageBody): MessageResponse

    // =======================================================================
    // 6. Notifications
    // =======================================================================
    @GET("notifications")
    suspend fun notifications(@Query("countOnly") countOnly: Int? = null): NotificationsResponse

    /** countOnly poll returns only `{unread}` — dedicated typed variant.
     *  Q6/A4-04: the countOnly param was declared-but-never-sent, so every
     *  badge poll downloaded the full 50-row payload; the server skips the
     *  findMany when countOnly=1 (notifications/route.ts:14-17, web AppBar). */
    @GET("notifications")
    suspend fun unreadCount(@Query("countOnly") countOnly: Int = 1): UnreadResponse

    /** Mark ALL read. */
    @POST("notifications")
    suspend fun markAllRead(): OkResponse

    @PATCH("notifications")
    suspend fun markRead(@Body body: NotificationIdsBody): OkResponse

    @DELETE("notifications")
    suspend fun deleteNotifications(@Body body: NotificationIdsBody): OkResponse

    // =======================================================================
    // 7. Tasks / referrals / stats / leaderboard / achievements
    // =======================================================================
    @GET("tasks")
    suspend fun tasks(): TasksResponse

    @POST("tasks")
    suspend fun claimTask(@Body body: TaskClaimBody): TaskClaimResponse

    @GET("referrals")
    suspend fun referrals(): ReferralsResponse

    @GET("stats")
    suspend fun stats(): StatsResponse

    @GET("leaderboard")
    suspend fun leaderboard(): LeaderboardResponse

    @GET("achievements")
    suspend fun achievements(): AchievementsResponse

    // =======================================================================
    // 8. Bank accounts
    // =======================================================================
    @GET("bank")
    suspend fun bankAccounts(): BankAccountsResponse

    @POST("bank")
    suspend fun createBankAccount(@Body body: BankCreateRequest): BankAccountResponse

    // =======================================================================
    // 9. QR & player lookup
    // =======================================================================
    @GET("qr/lookup")
    suspend fun qrLookup(
        @Query("uid") uid: String? = null,
        @Query("teamId") teamId: String? = null,
    ): QrLookupResponse

    @GET("players/lookup")
    suspend fun playerLookup(@Query("uid") uid: String): PlayerLookupResponse

    // =======================================================================
    // 10. Games config (host flow)
    // =======================================================================
    @GET("games/config")
    suspend fun gamesConfig(): GamesConfigResponse

    // =======================================================================
    // 11. Push (FCM token lifecycle; poll is web/windows-only)
    // =======================================================================
    @POST("push/register")
    suspend fun pushRegister(@Body body: PushRegisterRequest): OkResponse

    @POST("push/unregister")
    suspend fun pushUnregister(@Body body: PushUnregisterRequest): OkResponse

    @GET("push/poll")
    suspend fun pushPoll(@Query("after") after: String? = null): PushPollResponse

    // =======================================================================
    // 12. Admin (mobile admin sections; server RBAC enforced)
    // =======================================================================
    @GET("admin/requests")
    suspend fun adminRequests(): AdminRequestsResponse

    @GET("admin/requests/{id}")
    suspend fun adminRequestImage(@Path("id") id: String): AdminImageResponse

    @POST("admin/requests/{id}")
    suspend fun adminRequestAction(@Path("id") id: String, @Body body: AdminActionBody): OkResponse

    @GET("admin/tournaments")
    suspend fun adminTournaments(): AdminTournamentsResponse

    @POST("admin/tournaments/{id}/status")
    suspend fun adminTournamentStatus(@Path("id") id: String, @Body body: AdminStatusBody): OkResponse

    @POST("admin/tournaments/{id}/room")
    suspend fun adminTournamentRoom(@Path("id") id: String, @Body body: AdminRoomBody): OkResponse

    @POST("admin/tournaments/{id}/disable")
    suspend fun adminTournamentDisable(@Path("id") id: String, @Body body: AdminDisableBody): OkResponse

    @POST("admin/tournaments/{id}/results")
    suspend fun adminTournamentResults(@Path("id") id: String, @Body body: AdminResultsBody): OkResponse

    @GET("admin/proofs")
    suspend fun adminProofs(): AdminProofsResponse

    @GET("admin/proofs/{id}")
    suspend fun adminProofImage(@Path("id") id: String): AdminImageResponse

    @GET("admin/settings")
    suspend fun adminSettings(): AdminSettingsResponse

    @POST("admin/settings")
    suspend fun adminSaveSettings(@Body body: AdminSettingsBody): AdminSettingsSaveResponse

    @GET("admin/security")
    suspend fun adminSecurity(
        @Query("type") type: String,
        @Query("take") take: Int = 50,
    ): AdminSecurityResponse

    /** GET /admin/security?type=reconcile — balance drift check. */
    @GET("admin/security")
    suspend fun adminReconcile(): AdminReconcileResponse

    @GET("admin/games")
    suspend fun adminGames(): AdminGamesResponse

    @POST("admin/games")
    suspend fun adminCreateGame(@Body body: AdminGameCreateBody): AdminGamesResponse

    @POST("admin/games/{id}")
    suspend fun adminUpdateGame(@Path("id") id: String, @Body body: AdminGameUpdateBody): OkResponse

    @DELETE("admin/games/{id}")
    suspend fun adminDeleteGame(@Path("id") id: String): OkResponse

    @POST("admin/games/{id}/config")
    suspend fun adminGameConfig(@Path("id") id: String, @Body body: AdminGameConfigOp): OkResponse
}

/**
 * The /qr/lookup endpoint returns a DIFFERENT shape per kind
 * (`{kind:"user", user, relation}` vs `{kind:"team", team, isMember, joinRequested}`).
 * kotlinx-serialization can't branch on a discriminated field automatically
 * here without a custom serializer, so both variants decode into one wrapper
 * with nullable halves — mirror of the web's discriminated union handling.
 */
@kotlinx.serialization.Serializable
data class QrLookupResponse(
    val kind: String? = null,
    val user: com.areenax.app.data.UserSummary? = null,
    val relation: String? = null,
    val team: com.areenax.app.data.QrTeamInfo? = null,
    val isMember: Boolean? = null,
    val joinRequested: Boolean? = null,
) {
    val asUser: QrUserLookup?
        get() = if (kind == "user" && user != null) QrUserLookup(kind = "user", user = user, relation = relation ?: "none") else null
    val asTeam: QrTeamLookup?
        get() = if (kind == "team" && team != null) QrTeamLookup(kind = "team", team = team, isMember = isMember ?: false, joinRequested = joinRequested ?: false) else null
}

/** Reconcile variant of /admin/security (`{ok, items: Row[], checkedCount}`). */
@kotlinx.serialization.Serializable
data class AdminReconcileResponse(
    val ok: Boolean = false,
    val items: List<AdminReconcileRow> = emptyList(),
    val checkedCount: Int = 0,
)

/** Q12 request body for POST /me/location (coarse cell only). */
@kotlinx.serialization.Serializable
data class LocationUpdateRequest(
    val lat: Double,
    val lon: Double,
)

/** Q12 response for GET /friends/suggest — server-shaped summaries. */
@kotlinx.serialization.Serializable
data class FriendSuggestResponse(
    val users: List<com.areenax.app.data.UserSummary> = emptyList(),
)
