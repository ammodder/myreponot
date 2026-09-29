# A4 evidence — endpoint ↔ call-site coverage (static analysis, read-only)

Command basis (run 2026-09-20, worktree /home/z/audit-wt @ audit/production-readiness):

```
rg -n "api\.(bootstrap|login|guestLogin|register|authCheck|logout|me|patchMe|wallet|deposit|
  withdraw|transfer|tournaments|tournamentDetails|createTournament|joinTournament|
  tournamentResults|submitProof|submitResultProof|myTournaments|myHosted|team|createTeam|
  leaveTeam|teamJoinRequests|requestTeamJoin|respondTeamJoinRequest|friends|friendRequests|
  sendFriendRequest|respondFriendRequest|chatMessages|sendMessage|notifications|unreadCount|
  markAllRead|markRead|deleteNotifications|tasks|claimTask|referrals|stats|leaderboard|
  achievements|bankAccounts|createBankAccount|qrLookup|playerLookup|gamesConfig|pushRegister|
  pushUnregister|pushPoll|adminRequests|adminRequestImage|adminRequestAction|adminTournaments|
  adminTournamentStatus|adminTournamentRoom|adminTournamentDisable|adminTournamentResults|
  adminProofs|adminProofImage|adminSettings|adminSaveSettings|adminSecurity|adminReconcile|
  adminGames|adminCreateGame|adminUpdateGame|adminDeleteGame|adminGameConfig)\b" app/src/main/java
```

All call sites are `safeCall { env.api.<fn>(…) }` wraps (except where noted). "Live" = ≥1 call site.
Declaration source of truth: `app/src/main/java/com/areenax/nativeapp/core/network/Api.kt`.

## Live (64)

| # | Api.kt fn (line) | Call site(s) file:line |
|---|---|---|
|1| bootstrap (:108) | HomeScreen.kt:119 |
|2| login (:111) | LoginScreen.kt:376, :378, :383 |
|3| guestLogin (:114) | LoginScreen.kt:404 |
|4| register (:117) | SignupStep3Screen.kt:406 |
|5| authCheck (:120) | SignupScreen.kt:300 (gameName); SignupStep1Screen.kt:321 (phone+gameUid); SignupStep3Screen.kt:396 (email) |
|6| logout (:123) | ProfileScreen.kt:390; EditProfileScreen.kt:326 (`all=true`) |
|7| me (:126) | AppShell.kt:95 |
|8| patchMe (:129) | EditProfileScreen.kt:179 (profile save), :248 (password change) |
|9| wallet (:135) | WalletScreen.kt:158 |
|10| deposit (:138) | DepositConfirmScreen.kt:186 |
|11| withdraw (:141) | ConfirmWithdrawScreen.kt:111 |
|12| transfer (:144) | TransferScreen.kt:147 |
|13| tournaments (:150) | TournamentsScreen.kt:95 |
|14| tournamentDetails (:156) | TournamentDetailsScreen.kt:152; HostSuccessScreen.kt:84; HostDetailsScreen.kt:115; RoomInfoSheet.kt:77 |
|15| createTournament (:159) | HostCreationScreen.kt:228 |
|16| joinTournament (:162) | TournamentDetailsScreen.kt:271 (default `{}` body) |
|17| tournamentResults (:168) | ResultsScreen.kt:98 |
|18| submitProof (:172) | TournamentDetailsScreen.kt:170 |
|19| submitResultProof (:176) | ResultProofSheet.kt:199 |
|20| myTournaments (:179) | MyTournamentScreen.kt:107 |
|21| myHosted (:182) | HostTournamentScreen.kt:89; HostCardsScreen.kt:48 |
|22| team (:188) | MyTeamScreen.kt:97; TeamJoinSheet.kt:87 |
|23| createTeam (:191) | TeamCreationScreen.kt:111 |
|24| leaveTeam (:194) | MyTeamScreen.kt:428 |
|25| requestTeamJoin (:200) | QrSheet.kt:331 |
|26| friends (:212) | FriendsScreen.kt:125 |
|27| friendRequests (:215) | FriendsScreen.kt:136 |
|28| sendFriendRequest (:218) | FriendsScreen.kt:179; QrSheet.kt:299 |
|29| respondFriendRequest (:221) | FriendsScreen.kt:215, :251 |
|30| chatMessages (:227) | ChatScreen.kt:95 |
|31| sendMessage (:230) | ChatScreen.kt:133 |
|32| notifications (:236) | NotificationsScreen.kt:81 (`countOnly` param never passed — see A4 evidence matrix row 61) |
|33| unreadCount (:240) | UnreadManager.kt:42 |
|34| markAllRead (:244) | NotificationsScreen.kt:112 |
|35| markRead (:247) | NotificationsScreen.kt:98 |
|36| tasks (:256) | TasksScreen.kt:82 |
|37| claimTask (:259) | TasksScreen.kt:98 |
|38| referrals (:262) | ReferEarnScreen.kt:89 |
|39| stats (:265) | LeaderboardScreen.kt:88; MyStatsScreen.kt:69 |
|40| leaderboard (:268) | LeaderboardScreen.kt:87 |
|41| achievements (:271) | AchievementsScreen.kt:94 |
|42| bankAccounts (:277) | SelectBankScreen.kt:91; DepositScreen.kt:124; WithdrawScreen.kt:101; DepositConfirmScreen.kt:146; BindAccountScreen.kt:108 |
|43| createBankAccount (:280) | BindAccountScreen.kt:409 |
|44| qrLookup (:286) | QrSheet.kt:262 (uid), :264 (teamId) |
|45| gamesConfig (:298) | HostCreationScreen.kt:145 |
|46| adminRequests (:316) | AdminPanelScreen.kt:468 |
|47| adminRequestImage (:319) | AdminPanelScreen.kt:502 |
|48| adminRequestAction (:322) | AdminPanelScreen.kt:530 |
|49| adminTournaments (:325) | AdminPanelScreen.kt:891; AdminTournamentsScreen.kt:114 |
|50| adminTournamentStatus (:328) | AdminPanelScreen.kt:964 |
|51| adminTournamentRoom (:331) | AdminPanelScreen.kt:999; AdminTournamentsScreen.kt:162 |
|52| adminTournamentDisable (:334) | AdminPanelScreen.kt:1035 |
|53| adminTournamentResults (:337) | AdminPanelScreen.kt:1077; AdminTournamentsScreen.kt:202 |
|54| adminProofs (:340) | AdminPanelScreen.kt:892; ResultProofsAdminSection.kt:129 |
|55| adminProofImage (:343) | AdminPanelScreen.kt:1113; ResultProofsAdminSection.kt:86 |
|56| adminSettings (:346) | AdminPanelScreen.kt:134 |
|57| adminSaveSettings (:349) | AdminPanelScreen.kt:403 |
|58| adminSecurity (:352) | AdminPanelScreen.kt:1761 (events), :1762 (audit) |
|59| adminReconcile (:359) | AdminPanelScreen.kt:1784 |
|60| adminGames (:362) | GamesAdminSection.kt:94 |
|61| adminCreateGame (:365) | GamesAdminSection.kt:125 |
|62| adminUpdateGame (:368) | GamesAdminSection.kt:148 |
|63| adminDeleteGame (:371) | GamesAdminSection.kt:165 |
|64| adminGameConfig (:374) | GamesAdminSection.kt:183 |

## Dead declarations (7) — no call sites in app/src

| Api fn | Api.kt:line | Server route exists | Note |
|---|---|---|---|
| teamJoinRequests | :197-198 | src/app/api/team/join-requests/route.ts (GET) | Q5 join-inbox pending (TASK-006 Q5: Api.kt:197-207 types exist) |
| respondTeamJoinRequest | :203-207 | src/app/api/team/join-requests/[id]/route.ts (POST) | Q5 pending |
| deleteNotifications | :250-251 | src/app/api/notifications/route.ts (DELETE) | Q5 pending (TASK-006: ":250-251 DELETE notifications") |
| playerLookup | :292-293 | src/app/api/players/lookup/route.ts | Web JoinTeamSheet teammate verification only; native TeamJoinSheet never calls it |
| pushRegister | :304-305 | src/app/api/push/register/route.ts | FCM not activated (Models.kt:665 "wired when FCM is activated") |
| pushUnregister | :307-308 | src/app/api/push/unregister/route.ts | same |
| pushPoll | :310-311 | src/app/api/push/poll/route.ts | SPEC/02:328 "web/windows only; native uses FCM" |

Dead fragments on live declarations: `notifications(countOnly)` query param never passed (Api.kt:237;
server optimization src/app/api/notifications/route.ts:14-17 unused by native); `JoinTournamentRequest.members/slotNumber`
(Models.kt:887-893) never populated — join always sends `{}` (web TournamentDetailsScreen.tsx:197 also sends no body).
