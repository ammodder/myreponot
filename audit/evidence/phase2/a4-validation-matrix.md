# A4 evidence — validation matrix (client vs server), static analysis

Legend — Client = AreenaxNativeAndroid app/src (Compose, Kotlin); Server = /home/z/my-project/src/app/api/**
(Next.js route handlers, actually read). ✅ match · ⚠️ drift (direction noted) · ❌ one side absent ·
🔒 guest/RBAC row. Static analysis only — no runtime requests were made (no device/emulator; owner mandate).

## Auth & signup (rows 1-11)

| # | Field | Client-side (rule + file:line) | Server-side (route:line) | Match? |
|---|---|---|---|---|
|1| login identifier | non-empty after trim; phone-vs-email via `^\+?[0-9]{7,15}$` (LoginScreen.kt:360-364, :415-420); digits-first then as-typed retry (:372-384) | email XOR phone required (auth/login/route.ts:30-32); timing-equalized unknown account :16-21, :94-99 | ✅ |
|2| login password | non-empty only (LoginScreen.kt:365-368) | non-empty string (auth/login/route.ts:35-37); per-identifier+IP rate limit :41-60 | ✅ (both minimal by design) |
|3| signup fullName | non-empty after trim (SignupScreen.kt:291-294) | required truthy (auth/register/route.ts:46-48), stored trimmed :114 | ✅ |
|4| signup gameName | non-empty; auth/check pre-flight → inline taken-msg (SignupScreen.kt:291-302) | required + trim + DB unique → 409 field=gameName (auth/register/route.ts:58, :69-75) | ✅ (charset/length absent BOTH sides) |
|5| signup phone | non-empty only; NO digit filter/format (SignupStep1Screen.kt:311-314, :220-224) | optional, trimmed, DB-unique (register:57, :76-84); no format rule | ✅ absent-both (no +92 / digit-count rule anywhere; stored as typed) |
|6| signup gameUid | digits-only input filter, non-empty (SignupStep1Screen.kt:246-247, :312-315) | optional, trimmed, DB-unique (register:59, :85-93) | ⚠️ client stricter (strips letters; server would accept "abc") |
|7| signup email format | `^\S+@\S+\.\S+$` (SignupStep3Screen.kt:385-388, :454) | NO format check — truthy + lowercase only (register:46, :56); unique → 409 :62-68 | ⚠️ client stricter (safe direction) |
|8| signup password — 3-rule checklist | 10 chars + 1 letter + 1 number-or-special (SignupStep2Screen.kt:101-103, gate :309-311) | min 10, ≥1 letter, ≥1 number/special (password-policy.ts:49-53 via register:51-54) | ✅ on the 3 shared rules |
|9| signup password — server extras | no max-length / breached check client-side (SignupStep2Screen.kt:101-103) | max 128 + top-200 breached deny-list (password-policy.ts:50, :55-57) | ⚠️ client accepts what server rejects (400) → A4-09 |
|10| referral code | optional, trimmed, empty→omitted (SignupStep3Screen.kt:416-417) | optional; unknown code silently ignored (register:96-99) | ✅ |
|11| auth/check pre-flight | best-effort every step; navigates on check failure (SignupScreen.kt:96, :300-306; SignupStep1Screen.kt:90, :339) | per-field taken/messages (auth/check/route.ts:40-76), IP rate-limited :19-34 | ✅ |

## Wallet — deposit (rows 12-23)

| # | Field | Client-side | Server-side | Match? |
|---|---|---|---|---|
|12| deposit amount min | >0 and ≥ SettingsCache.minDeposit, fallback 100 (DepositScreen.kt:140-152, :118, :128-131) | ≥ getSetting("minDeposit","100"); falsy rejected (wallet/deposit/route.ts:65-69) | ✅ same default |
|13| deposit amount max | NONE — pad allows up to 8 significant digits (DepositScreen.kt:80-92) | ≤ getSetting("maxDeposit","100000") → 400 (deposit/route.ts:71-75) | ❌ client accepts X, server rejects Y → A4-03 |
|14| deposit amount shape | custom pad: digits only, one ".", 8-sig-digit cap, "0" base (DepositScreen.kt:80-92); no negative/zero possible; decimals allowed | `Number(amount)`; `!amt` rejects 0/NaN (deposit/route.ts:65) | ✅ |
|15| deposit receipt presence | REQUIRED toast (DepositConfirmScreen.kt:164-168) | required receiptName (deposit/route.ts:80-82) | ✅ |
|16| deposit receipt size/format | picker cap 8 MB (PhotoPickers.kt:32); over-cap SILENTLY DOWNSCALED (PhotoPickers.kt:122-131) | magic-byte sniff + re-encode, 8 MB (upload-security.ts:23, :96-110; deposit/route.ts:93-97) | ⚠️ over-cap: native downscales vs web rejects (Q2/Q3) |
|17| TrxID presence | REQUIRED non-blank (DepositConfirmScreen.kt:169-173) | required non-blank (deposit/route.ts:77-79) | ✅ |
|18| TrxID format/length | NONE — free text (DepositConfirmScreen.kt:410-415) | trim + silent slice(0,64); uniqueness on the SLICED value (deposit/route.ts:84, :175-193) | ⚠️ silent server truncation → A4-07 |
|19| TrxID one-time | none client-side; 409 text toasted verbatim | partial unique on clientRef → 409 "This transaction reference has already been submitted." (deposit/route.ts:175-193) | ✅ server-authoritative |
|20| deposit accountId | passes selected bound id (DepositConfirmScreen.kt:118, :183) | ownership-checked, silently dropped if foreign (deposit/route.ts:141-148) | ✅ |
|🔒| deposit guest gate | AccountRequiredDialog (DepositConfirmScreen.kt:475; WalletScreen.kt:298) | requireNonGuest (deposit/route.ts:19-20) | ✅ |

## Wallet — withdraw (rows 24-27)

| # | Field | Client-side | Server-side | Match? |
|---|---|---|---|---|
|21| withdraw amount min | ≥ SettingsCache.minWithdraw, fallback 500 (WithdrawScreen.kt:137-140, :95, :114-116) | ≥ getSetting("minWithdraw","200") (wallet/withdraw/route.ts:64-67) | ⚠️ fallback drift 500 vs 200 (settings-failure path) → A4-03 |
|22| withdraw amount ≤ balance | value > user.balance blocked (WithdrawScreen.kt:142-145) | atomic `balance >= amt` guard in tx (withdraw/route.ts:68-70, :100-106) | ✅ (server atomic = authority) |
|23| withdraw accountNumber | bound-account picker REQUIRED (WithdrawScreen.kt:127, :120-123); sends account?.accountNumber (ConfirmWithdrawScreen.kt:104-108) | must exactly match a bound account, normalized (withdraw/route.ts:72-94); one-PENDING unique :118-135 | ✅ |
|🔒| withdraw guest gate | gate (ConfirmWithdrawScreen.kt:295; WalletScreen.kt:310) | requireNonGuest (withdraw/route.ts:20-21) | ✅ |

## Wallet — transfer (rows 28-32)

| # | Field | Client-side | Server-side | Match? |
|---|---|---|---|---|
|28| transfer recipient | non-empty after trim (TransferScreen.kt:123-127) | exists by uid/email → 404; self → 400; guest recipient → 400 (wallet/transfer/route.ts:55-86) | ⚠️ no client self/guest pre-check (server error surfaces as toast only) |
|29| transfer amount min/balance | >0 and ≤ balance (TransferScreen.kt:128-137) | ≥1 and ≤ balance (transfer/route.ts:58-68) | ✅ |
|30| transfer amount max | NONE (pad, TransferScreen.kt:120-131) | ≤ getSetting("maxTransfer","50000") → 400 (transfer/route.ts:61-65) | ❌ → A4-03 |
|31| transfer note | optional, trimmed, NO cap (TransferScreen.kt:141-145) | silent slice(0,200) (transfer/route.ts:70) | ⚠️ silent truncation, no error |
|🔒| transfer guest gate | ABSENT on TransferScreen confirm (TransferScreen.kt:120-145; entry gate only WalletScreen.kt:322) | requireNonGuest (transfer/route.ts:16-17) | ❌ client gap → A4-06 |

## Tournaments — join & proofs (rows 33-40)

| # | Field | Client-side | Server-side | Match? |
|---|---|---|---|---|
|🔒| join guest gate | gate (TournamentDetailsScreen.kt:381) | requireNonGuest (tournaments/[id]/join/route.ts:27-28) | ✅ |
|33| join eligibility | none besides server-text lock "insufficient" → InsufficientBalanceDialog (TournamentDetailsScreen.kt:283-285) | UPCOMING-only :72-74; slots :75-77; not-joined :78-81; balance :83-85; atomic re-checks :89-108; rate limit :33-49 | ✅ server-authoritative |
|34| join body | always `{}` (Api.kt:165; TournamentDetailsScreen.kt:271; web TournamentDetailsScreen.tsx:197 also no body) | reads only optional teamName (join/route.ts:52-59) — vestigial | ✅ consistent (DTO members/slotNumber dead → A4-10) |
|35| one-time /proof image | picker cap 8 MB, over-cap downscaled (TournamentDetailsScreen.kt:163-170; PhotoPickers.kt:122-131) | validateAndReencodeImage "proof" 8 MB + magic bytes (proof/route.ts:61-64; upload-security.ts:23) | ✅ cap equal; over-cap behavior differs from web (Q2) |
|36| one-time /proof enforcement | UI lock on server-text match "already submitted" (TournamentDetailsScreen.kt:179-186) | joined-only :38-46; proof-mode :49-54; DB unique → 409 + alreadySubmitted:true (:66-73, :107-120) | ✅ (client lock is message-text based → A4-11) |
|37| /result-proof cap | picker cap 4 MB, over-cap downscaled (ResultProofSheet.kt:77, :181; PhotoPickers.kt:33) | `data:image/` prefix + ≤5,500,000 chars (≈4.1 MB) → 400; stored RAW, no re-encode (result-proof/route.ts:22-27, :64-69) | ⚠️ triple drift native 4MB-downscale / web reject / server raw-accept → A4-08 |
|38| /result-proof eligibility | none client-side (sheet shown per tournament state) | user-created only :43-45; matchInProgress disabled :47-49; ONGOING :51-54; joined-only :56-62 | ✅ server-authoritative |
|🔒| /result-proof guest | no client gate on sheet submit | NO requireNonGuest — transitively blocked by joined-only :56-62 | ⚠️ defense-in-depth gap → A4-17 |

## Host creation (rows 41-46)

| # | Field | Client-side | Server-side | Match? |
|---|---|---|---|---|
|41| host name/mode/startTime required | non-blank checks (HostCreationScreen.kt:202-204) | gameId+name+startTime required (tournaments/route.ts:86-88); mode must exist on game :111-115 | ✅ |
|42| host startTime future | NOT checked (HostCreationScreen.kt:200-208; toUtcIso :726-733) | "Invalid start time" / "must be in the future" (tournaments/route.ts:122-127) | ⚠️ server-only → A4-16 |
|43| host entryFee | toDoubleOrNull ≥ 0 else inline error (HostCreationScreen.kt:205-208) | Math.max(0, Number(entryFee)||0) (tournaments/route.ts:93) | ✅ |
|44| host economics | preview: collection=fee×slots; prizePool=round(×0.5); loserPrize=round(0.5×collection/(slots−1)); ranks 50/30/20 (HostCreationScreen.kt:169-177); sends computed values :220-223 | non-admin: perKill=0; maxPlayers=gameMode.slots (client value ignored); prizePool/loserPrize recomputed identically (tournaments/route.ts:100-127) | ✅ formula-identical, server authoritative |
|45| host image | picker cap 5 MB (UploadCaps.HOST_IMAGE; HostCreationScreen.kt:225) | "banner" 5 MB re-encode + 10/h upload rate-limit (tournaments/route.ts:141-166; upload-security.ts:26) | ✅ |
|🔒| host guest gate | gate (HostCreationScreen.kt:650; HostTournamentScreen.kt:115) | requireNonGuest (tournaments/route.ts:80-81) | ✅ |

## Team / friends / chat (rows 47-53)

| # | Field | Client-side | Server-side | Match? |
|---|---|---|---|---|
|47| team name | required; input hard-capped 24 chars (TeamCreationScreen.kt:101-104, :172-174) | required trim only, no length cap (team/route.ts:55-57); one-team-per-user :59-63 | ⚠️ client stricter → A4-14 |
|48| team tag | letters/digits only, auto-uppercase, max 4 (TeamCreationScreen.kt:213-214) | uppercase only, no charset/length (team/route.ts:56-57) | ⚠️ client stricter |
|49| team memberUids | trimmed non-empty (TeamCreationScreen.kt:115) | silently skips unknown/self/already-teamed UIDs (team/route.ts:68-83) | ⚠️ silent partial success |
|🔒| team create guest gate | gate (TeamCreationScreen.kt:107; MyTeamScreen.kt:212) | requireNonGuest (team/route.ts:47-48) | ✅ |
|50| friend request uid | non-empty + guest gate (FriendsScreen.kt:168-177) | required → 404 → self 400 → already 409 (friends/requests/route.ts:63-70+) | ✅ |
|51| chat message | trim non-empty (ChatScreen.kt:128-131) | truthy + trim (friends/[id]/messages/route.ts:58-66); NO length cap either side | ✅ absent-both |
|🔒| QR mutations (friend request / team join) | NO client gate in QrSheet (QrSheet.kt:299, :331) | requireNonGuest (friends/requests/route.ts:61-62; team/join-requests/route.ts) | ❌ client gate gap → A4-15 |

## Profile edit (rows 54-61)

| # | Field | Client-side | Server-side | Match? |
|---|---|---|---|---|
|54| profile fullName/gameName | required non-blank (EditProfileScreen.kt:165-166) | NO validation, stored untrimmed (me/route.ts:24-25, :73); gameName conflict → 409 :88-93 | ⚠️ required-check client-only |
|55| profile email format | regex if non-blank (EditProfileScreen.kt:167-169, :377) | lowercase only, no format (me/route.ts:26); unique → 409 :82-87 | ⚠️ client stricter |
|56| profile email clearing | IMPOSSIBLE — explicitNulls=false omits email:null (EditProfileScreen.kt:86-88 documented) | supports email:null (me/route.ts:26) | ❌ native can't express → A4-12 |
|57| password change — current pw | collected (EditProfileScreen.kt:214-219) | verified → 400 "Current password is incorrect" (me/route.ts:65-70) | ✅ |
|58| password change — new pw policy | ≥6 chars + confirm-match ONLY (EditProfileScreen.kt:238-243) | NO policy call — hashPassword directly (me/route.ts:65-72); contradicts password-policy.ts:5-7 "used by BOTH signup and password change" | ❌ server gap → A4-01 |
|59| profile-save stray password | Save Changes sends password=newPw, no currentPassword (EditProfileScreen.kt:177) | then requires currentPassword → 400 (me/route.ts:65-70) | ❌ cross-card contamination → A4-02 (web-inherited: EditProfileScreen.tsx:106) |
|60| avatar | picker cap 8 MB, over-cap downscaled (UploadCaps.AVATAR PhotoPickers.kt:35) | data:image/ → re-encode "avatar" 8 MB (me/route.ts:29-64); http(s) URL stored as-is ≤11M chars :52-60 | ✅ (over-cap behavior = Q2) |
|🔒| profile guest gate | both actions gated (EditProfileScreen.kt:164, :237) | requireNonGuest (me/route.ts:19-20) | ✅ |

## Bank bind (rows 62-63)

| # | Field | Client-side | Server-side | Match? |
|---|---|---|---|---|
|62| bank fields required | bank+title non-blank; accountNumber ≥8 chars (BindAccountScreen.kt:399-405) | truthy all three only (bank/route.ts:22-25) | ⚠️ ≥8 rule client-only → A4-13 |
|🔒| bind guest gate | gate (BindAccountScreen.kt:397; ProfileScreen.kt:256) | requireNonGuest (bank/route.ts:19-20) | ✅ |

## Admin (rows 64-68)

| # | Field | Client-side | Server-side | Match? |
|---|---|---|---|---|
|64| admin settings numeric keys | free-text FormField, KeyboardType.Decimal hint only, no filter (AdminPanelScreen.kt:253-335, :2152-2158; setField :165-167) | finite ≥0, commission ≤100 else 400 (admin/settings/route.ts:51-60, :164-175) | ⚠️ server-only |
|65| admin settings unknown keys / role | sends whole form map (AdminPanelScreen.kt:403) | unknown key → 400 :111-130; per-key FINANCE/SUPER authority, skips logged :136-162 | ✅ server-authoritative by design |
|66| admin room fields | allows EITHER roomId OR password ("Enter a Room ID and/or Password first", AdminTournamentsScreen.kt:148-153) | requires BOTH → 400 (admin/tournaments/[id]/room/route.ts:31-37) | ❌ client OR vs server AND → A4-05 |
|67| admin results rows | toIntOrNull/toDoubleOrNull with fallbacks (AdminTournamentsScreen.kt:194-202); no dup-rank/budget preview | rank int ≥1 (0=null), kills int ≥0, prize finite ≥0; duplicate ranks → 400; budget bound; one-time 409 (admin/tournaments/[id]/results/route.ts:101-158) | ✅ server-authoritative (richer) |
|68| admin games name/image | non-empty both (GamesAdminSection.kt:119-121) | non-empty both + Cloudinary URL check (admin/games/route.ts:39-42, :52) | ✅ |

## Misc (rows 69-72)

| # | Field | Client-side | Server-side | Match? |
|---|---|---|---|---|
|69| unread badge poll | UnreadManager 30s throttle (UnreadManager.kt:38-48) calls unreadCount() which sends NO countOnly param (Api.kt:240-241) | ?countOnly=1 → {unread} only; without it full 50-row list + unread (notifications/route.ts:14-17) | ❌ poll fetches full payload → A4-04 |
|🔒| task claim | guest gate (TasksScreen.kt:95) | requireNonGuest + rate limit + (userId,taskId,day) unique backstop (tasks/route.ts; :88-89) | ✅ |
|70| qr/lookup params | uid XOR teamId from payload (QrSheet.kt:262-264) | session required, guests allowed (qr/lookup/route.ts:17-20); 404 unknown | ✅ |
|71| bootstrap / games.config | no inputs | public routes (see a4-guest-auth-map.log) | ✅ by design |
|72| chat/friend data rendering | plain Compose Text — no HTML/Markdown parsing anywhere in app/src (rg sweep in a4-guest-auth-map.log) | server returns plain strings | ✅ |

TOTAL: 72 matrix rows (incl. 11 🔒 guest/RBAC rows). Mismatch rows: 18 ⚠️ + 7 ❌ = 25; ✅ = 47.
