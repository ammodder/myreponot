# Attacker report — RTM hostile review (10-f)

- **Agent:** attacker (TASK-005 / 10-f). **Date:** 2026-09-20.
- **Mission:** three verifiers marked ~150/165 rows VERIFIED. Assume some are WRONG. Find rows
  "marked done but not truly working".
- **Method:** read worklog tail (Tasks 8/9 + orchestrator entries), TASK-005.md, ledger,
  all three part files, STATE.md, A1.md. Then attacked the APP SOURCE read-only at
  `app/src/main/java/com/areenax/nativeapp/**`, using the WEB as the parity judge wherever the
  ledger's standard is "1:1": `/home/z/my-project/src/components/screens/**`, `src/lib/*`,
  `src/app/api/**` (the server routes are in the web tree, so several "NOT VERIFIABLE from app
  repo" questions WERE resolvable — used and cited), and `upload/pages_extracted/pages/*.html`.
  Deep coverage on real-money / auth / security rows; breadth greps over the absence and
  parity rows; byte-exact extraction of every row I propose to correct (via `rg -n` on the part
  files, not from memory).
- **Constraints honored:** read-only; this file is the ONLY artifact written; no git, no app changes.

## Summary table

| # | Target REQ(s) | Claimed | Verdict | Severity |
|---|---|---|---|---|
| A-1 | REQ-008 | VERIFIED | **DOWNGRADE-TO-PARTIAL** | MEDIUM |
| A-2 | REQ-143 | VERIFIED | **DOWNGRADE-TO-PARTIAL** | MEDIUM |
| A-3 | REQ-151 | PARTIAL | **OVERTURNED→VERIFIED** | LOW (over-strict status, not a defect) |
| A-4 | REQ-013 (+REQ-012/107) | VERIFIED | HOLDS (verifier question b resolved) | — |
| A-5 | REQ-058 | PARTIAL | HOLDS as PARTIAL (verifier question a resolved with server evidence) | — |
| A-6 | REQ-096 | VERIFIED | HOLDS (attack failed — web does the same) | — |
| A-7 | REQ-059/060/142/146 | VERIFIED | HOLDS (all absence attacks failed) | — |
| A-8 | REQ-062 (new samples: TournamentDetails, Profile) | PARTIAL | HOLDS — no new drift found | — |
| A-9 | REQ-048/122 · REQ-101 · REQ-064 · REQ-027 | VERIFIED | HOLDS (verifier questions c/d/e/f resolved; one doc-only cite drift) | — |
| A-10 | REQ-017–025, REQ-100, REQ-102–107, REQ-136 | VERIFIED | HOLDS (money-path attack list, all negative) | — |
| A-11 | notes (no row changes) | — | 4 observations for the owner | LOW |

**Counts:** 11 findings — HOLDS 8 · DOWNGRADE-TO-PARTIAL 2 · OVERTURNED→VERIFIED 1 · NEW-FINDING 0 (edge folded into A-2).
**Row corrections proposed:** 3 (REQ-008 VERIFIED→PARTIAL · REQ-143 VERIFIED→PARTIAL · REQ-151 PARTIAL→VERIFIED).

---

## Findings

### A-1 — REQ-008 → DOWNGRADE-TO-PARTIAL (was VERIFIED). The post-auth QR payload is routed into a wall.

**Claimed:** VERIFIED (part-feature.md, row REQ-008). **Proof (both sides):**
native `core/ui/AppShell.kt:230-234` navigates `FRIENDS {qrUser}` / `MY_TEAM {qrTeam}` exactly as
worded — but a full grep of `ui/screens/**` for `env.params` shows **zero reads in
FriendsScreen.kt and MyTeamScreen.kt** (the only params consumers are wallet/success/auth/host/
main screens). The web destination screens DO consume them: `FriendsScreen.tsx:120-157` opens
the "Add Friend" popup from `params.qrUser` (resolve via `/qr/lookup?uid=…` → UserResultCard),
and `MyTeamScreen.tsx:68-105` opens the "Team Connect" popup from `params.qrTeam`; SPEC/00:343
explicitly says these navigations "open the respective connection popup", SPEC/01:410 and :460
repeat it per screen.
**Why it matters:** the end-to-end requirement ("route the stashed payload after auth") is only
half-alive: banner + stash + navigation work, then the id is silently dropped and the user lands
on a bare Friends/MyTeam screen. This is precisely a "marked done but not truly working" row —
the row's own Notes disclose the drop yet still stamp VERIFIED. (It also supplies the
Friends-side half of the already-recorded MyTeam divergence under REQ-027/REQ-129 — verifier
question (f) is hereby CONFIRMED: MyTeamScreen ignores `qrTeam`.)

### A-2 — REQ-143 → DOWNGRADE-TO-PARTIAL (was VERIFIED). Upload caps: native converts, web rejects — plus one real overflow edge.

**Claimed:** VERIFIED (part-business-security-perf.md, row REQ-143). **Proof (both sides):**
(a) The WEB **rejects** over-cap files with exact copy: receipt — "Receipt image too large /
Please attach an image up to 8 MB." (`DepositConfirmScreen.tsx:93-99`) and "Invalid receipt
file" (:103-110); proof — "Screenshot is too large / Maximum size is 8 MB."
(`TournamentDetailsScreen.tsx:164-168`) plus an `image/*` type check (:157-160). Native never
rejects: `PhotoPickers.kt:122-131` silently downscales/re-compresses over-cap picks to JPEG
("Over cap — downscale…"), and the reject copy is 0 hits in app/src (grep). A user's 20 MB
receipt becomes a different (recompressed) image on native vs a hard stop on web — behavior AND
copy parity break on the real-money upload path (also touches REQ-062's "copy strings EXACTLY").
(b) The cap is not strictly guaranteed before send: `compressToCap` last resort
(`PhotoPickers.kt:87-91`) compresses the (already sampled) bitmap at quality 40 and
**returns it unconditionally — no final size check** — so a pathological image (e.g. ~67 MP
after sampling) can still exceed the 4 MB proof cap on the wire.
(c) The "4 MB per endpoint" half of the claim is enforced only in DEAD code
(`ResultProofSheet.kt:77`, unwired per REQ-128); the live proof path uses 8 MB
(`TournamentDetailsScreen.kt:115,163`).
(d) `UploadCaps.AVATAR` (PhotoPickers.kt:35) has zero call sites — no avatar picker exists on
EITHER platform (web PATCH /me supports it server-side only), so the "avatar ≤8 MB" clause is a
declared-but-unwired constant, not an enforced cap.

### A-3 — REQ-151 → OVERTURNED→VERIFIED (was PARTIAL). The flagged "deviation" is exact web parity.

**Claimed:** PARTIAL — "the shared SettingsCache … returns its cached settings for the whole
process lifetime — no 60 s expiry". **Proof (both sides):** the native `SettingsCache.kt:18-45`
is a direct port of the web `src/lib/settings.ts`, which ALSO caches for process lifetime with
no TTL (`if (cached) return Promise.resolve(cached)`, settings.ts:11-30) and swallows fetch
errors (`.catch(() => null)`). The 60 s TTL + errors-never-cached requirement lives in the
HomeScreen bootstrap cache on BOTH platforms (`HomeScreen.kt:90-93,110-131`). The web behaves
identically to the native app, so "consumers can see settings older than 60 s" is true of the
web too and is not a native deviation. The part file's Uncertain-rows question 2 is hereby
resolved: **no** TTL change needed for SettingsCache.

### A-4 — REQ-013 (+ REQ-012/107) HOLDS — verifier question (b) resolved.

Native `TournamentDetailsScreen.kt:179` detects the duplicate-proof lock via
`res.message.contains("already submitted", ignoreCase = true)` — the verifier's quotes dropped
the `ignoreCase` flag. The WEB does exactly the same: `/already submitted/i.test(message)`
(`TournamentDetailsScreen.tsx:177`), and the server wording is a fixed contract string
("You have already submitted your result for this tournament…",
`src/app/api/tournaments/[id]/proof/route.ts:12`); the join path is the same
(`/insufficient/i` web :206 = native `contains("insufficient", ignoreCase = true)`
TournamentDetailsScreen.kt:283). Message-text detection is therefore web-faithful, not a native
defect. For the record: `ApiResult.Error.code: Int?` IS surfaced on native
(`ApiResult.kt:20`, set at :58 for the local 409 and :65 for `HttpException.code`), so a
`res.code == 409` check is possible in a future hardening pass — but doing it native-only would
DIVERGE from the web. Status stays VERIFIED; no row change.

### A-5 — REQ-058 HOLDS as PARTIAL — verifier question (a) resolved with server evidence the verifiers didn't have.

The WEB badge poll DOES send the parameter: `GET /notifications?countOnly=1`
(`src/components/shared/AppBar.tsx:30`; also `src/lib/push-client.ts:206`). The server honors it
as an optimization: with `countOnly` it returns `{unread}` only and **skips the 50-row
findMany** (`src/app/api/notifications/route.ts:12-16`); without it, the full payload is returned
and still carries a true `unread` total (route.ts:17-25). Therefore native's bare
`unreadCount()` (`Api.kt:240-241`, no `@Query`) works functionally — Models.kt:474 decodes
`unread` from the full body — but every 30 s badge poll does the 50-row findMany the server
comment explicitly optimizes away. The 10-e PARTIAL is correct and now fully evidenced on both
sides; open question answered: yes, the web sends `countOnly=1`, and the server already supports
the native doing the same.

### A-6 — REQ-096 HOLDS — attack failed, and the failure is instructive.

I went hunting for a missing guest gate on the transfer money screen and FOUND one:
`TransferScreen.kt` contains no `rememberGuestGate`/`gate.requireAccount`/`GuestGateDialog`
anywhere (grep = 0); the CTA at :462-485 calls `onTransferClick` directly. But the WEB is
identical: `TransferScreen.tsx:39-176` `handleTransfer` has **no** `requireAccount` either —
both platforms gate only the Wallet quick action ("send money") and rely on the server
`requireNonGuest` from there. Web CTA gates exist exactly where the native ones do:
DepositConfirm (web :307 = native DepositConfirmScreen.kt:475 area) and ConfirmWithdraw
(web :129 = native ConfirmWithdrawScreen.kt:295). The 10-e row's gate-site list omitting
TransferScreen is faithful to both codebases, so REQ-096 VERIFIED stands (client pre-block +
"server double-enforces" framing is accurate).

### A-7 — Absence claims REQ-059/060/142/146 HOLDS — attacks failed.

- **Push/FCM (REQ-059):** full dependency catalog read (`gradle/libs.versions.toml`) — compose/
  core/activity/lifecycle/splash/datastore/coil/retrofit/okhttp/kotlinx-serialization/zxing only;
  `firebase|crashlytics|analytics|google.services` = 0 hits over gradle + app/src; push endpoints
  are declarations with zero call sites (Api.kt:304-311); UnreadManager is polling-only.
- **Deep links (REQ-060):** the manifest's single intent-filter is MAIN/LAUNCHER
  (AndroidManifest.xml:32-35); no VIEW/BROWSABLE/scheme anywhere; `parseDeepLink`
  (ScreenKeys.kt:109-133) is reachable only from notification payloads + pending-link.
- **Firebase/analytics/WorkManager/Hilt/Room (REQ-142):** 0 hits, allowlist confirmed above.
- **Secrets (REQ-146):** `api_key|secret|Bearer |AKIA|sk_live|eyJhbGci|password = "` = 0 hits;
  zero `android.util.Log`/`println` in app/src; only business constants exist
  (FALLBACK_ACCOUNTS — payment counter numbers, not credentials — and the known `app_base_url`
  placeholder). The only x-token carrier is the single Retrofit stack: one `Retrofit.Builder`
  (ApiClient.kt:48), one `ApiClient.create` call (AreenaxApplication.kt:49) — no bypass path.
  (Coil image loads are tokenless, matching the web's tokenless `<img>` — parity, not a leak.)

### A-8 — REQ-062 parity samples — HOLDS, no new drift in the two new screens.

Beyond the already-sampled Home/Wallet/Chat I compared two more, section order + key copy:
- **TournamentDetails:** hero+countdown → summary cards (Prize Pool >0 / Entry Fee always /
  Per Kill >0) → Overview/Rules/Prizes underline tabs → numbered rules ("01","02"…) → Rank
  1/2/3 50/30/20 + Per Kill/Loser rows → Participants → join CTA. Native
  TournamentDetailsScreen.kt:334-355, :565-590, :657, :921-940, :1002-1058, :1211-1229,
  :1567-1570, :1624 = web TournamentDetailsScreen.tsx:253-330, :348, :449, :538-609, :200, :609.
  Shared strings match verbatim incl. "Will be announced" (:1624 = web :609) and "Room ID &
  password will appear before the match starts." (:277 = web :200). No drift.
- **Profile:** card 1 (Edit Profile / Refer & Earn / My Team / Tasks / Theme+switch / About /
  Terms & Conditions / Privacy Policy) and card 2 (Admin Console role-gated / My Stats / My
  Tournaments / Achievements / Leaderboard / Bind Account gated) — same order, same icons, same
  labels, same "Guest user"/"Share"/"QR Code"/"Logout" copy. Native ProfileScreen.kt:214-258,
  :192, :204-207, :330 = web ProfileScreen.tsx:148-205, :227, :232-242, :283-290. The only
  divergence is the already-recorded REQ-130 rewiring (Admin Console → `adminPanel` on native vs
  web `adminDashboard`). No new drift.

### A-9 — Verifier questions (c)/(d)/(e)/(f) resolved.

- **(c) AboutScreen version line:** the actual line is **AboutScreen.kt:110**
  (`val version = settings?.version?.takeIf { it.isNotBlank() } ?: "1.2.0"`) with the render at
  :173 ("Version $version"). The verifier who said :110 is right; STATE.md C4, ledger REQ-048/
  REQ-122 and ledger Appendix A still cite ":106" — a stale cite from before the fix commits
  (doc-only; both RTM part rows already use :110 correctly).
- **(d) "Alphla" spelling:** the WEB renders "Alphla Bank" too — literal
  `FUNDING_BANKS = ["Jazzcash", "Easypaisa", "Sadapay", "Alphla Bank", "Mezan Bank"]`
  (`DepositScreen.tsx`), same in `SelectBankScreen.tsx` METHODS and `BindAccountScreen.tsx`
  BANK_OPTIONS, and in pixel truth `pages/selectbank.html`. Native matches
  (TournamentState.kt:175, BindAccountScreen.kt:496). Parity HOLDS; C1 remains the owner's
  spelling decision, not a verification defect.
- **(e) Midnight Charcoal:** `1C1C1E|3C3C3C|3C3C3E` = **0 hits** in app/src; Midnight Pills
  values all present (Tokens.kt:86-131: 0xFF060608, 0xFF131316…0xFF2A2A2F, 0xFF2F6BFF,
  0xFF4EDEA3, 0xFFFF5449, 0xFF064E3B/0xFF6EE7B7; AppShell.kt glow). REQ-064 VERIFIED HOLDS;
  the mission-brief palette is confirmed stale.
- **(f) qrTeam param:** CONFIRMED dropped (see A-1).

### A-10 — Real-money rows REQ-017–025 / REQ-100 / REQ-102–107 / REQ-136 — attack list, all negative (HOLDS).

- **Guest reachability:** every entry to DEPOSIT/WITHDRAW/CONFIRM_WITHDRAW/TRANSFER_MONEY is
  gated — WalletScreen.kt:298,310,322 (AccountRequiredDialog) and InsufficientBalanceDialog:97
  (reachable only after the guest-gated join). DepositScreen/WithdrawScreen/TransferScreen carry
  no on-screen gate, but neither do their web counterparts (web TransferScreen.tsx has none —
  A-6); logged-out users are force-corrected by `enforceAuthGate` (AppNavigator.kt:108-114,
  invoked on user change AppShell.kt:116-118). No guest path to a money POST found.
- **Balance re-sync after mutations:** deposit → `setUser(copy(balance=res.balance))`
  (DepositConfirmScreen.kt:187-191 = web DepositConfirmScreen.tsx:153-154); withdraw →
  ConfirmWithdrawScreen.kt:112-116; transfer → TransferScreen.kt:148-152; join → local decrement
  + `refreshKey++` refetch (TournamentDetailsScreen.kt:279-280); wallet GET syncs when different
  (WalletScreen.kt:157-163, SessionManager.kt:104-107). All match web `setUser({...u, balance})`.
- **Error paths that lose input:** none found — on "Deposit failed"/"Withdraw failed"/
  "Transfer failed" the screens keep trxId/receipt/recipient/note/amount state (no clearing on
  failure in any of the three POST handlers), `submitting` resets in `finally`-equivalent
  position (:212/:137/:174). Login failure retains the password (LoginScreen.kt:371-393).
- **Double-submit windows:** every money POST is double-guarded — screen-level `submitting`
  flags disable/ignore re-entry (DepositConfirm :163,:289-293; ConfirmWithdraw :102,:293;
  Transfer :123,:475) plus the client InFlightGuard per `METHOD path`
  (ApiClient.kt:95-112 = web api.ts:65-69,87), mapped to a local 409 by safeCall
  (ApiResult.kt:57-58). RequestInFlightException is an IOException thrown in an application
  interceptor and is caught FIRST in safeCall — the mapping is sound.
- **REQ-100 fallback accounts:** native raw-key lookup + fallback map
  (DepositConfirmScreen.kt:99-106,128-130,139-141) is byte-equivalent to the web
  (DepositConfirmScreen.tsx FALLBACK_ACCOUNTS + `map[method]`), and the raw/lower/UPPER/Title
  key expansion is provably SERVER-side (`src/app/api/bootstrap/route.ts:10-28`). C9 conflict
  correctly remains an owner decision, not a status error.
- **REQ-102:** client min/≤balance/bound-account checks (WithdrawScreen.kt:95,114-116,127,
  137-146) + verbatim 409 surfacing via safeCall; second-pending 409 is a real server behavior
  (P2002 handling in `src/app/api/wallet/withdraw/route.ts:13,120-122,162`). HOLDS.
- **REQ-103/104/105/106/107:** spot-checked — returnTo threading, {recipient, amount, note?}
  body, formatMoney/formatBalance ports, state-machine gating: all as cited. HOLDS.

### A-11 — Notes for the owner (no row changes).

1. **Bank selection survives logout on BOTH platforms:** web `store.logout()`
   (src/lib/store.ts:67-74) clears only pending-qr + token/user/unread; sessionStorage
   `areena_bank_method/_selection` survive within the tab. Native logout
   (SessionManager.kt:197-208) likewise keeps the bank keys — parity, but on a shared device
   user A's bank selection is visible to user B. (Also verified: keeping `pending-link` on
   logout IS web parity — REQ-053 HOLDS.)
2. **Stale comment:** UnreadManager.kt:17 says "after FCM receipt" — FCM does not exist
   (REQ-059). Comment-only doc-rot.
3. **DepositConfirm "Account Name" fallback "John Doe"** (DepositConfirmScreen.kt:286) is web
   parity (DepositConfirmScreen.tsx:167 + pages/depositconfirm.html) — not a bug.
4. **minWithdraw defaults:** native 500 (WithdrawScreen.kt:95) = web client 500
   (WithdrawScreen.tsx:53); when the setting is absent the server bootstrap sends 200 and both
   clients override identically. No drift.

---

## PROPOSED ROW CORRECTIONS

(Exact current lines extracted from the part files with `rg -n`; replacements are byte-exact
full-line swaps. Only the STATUS + Evidence/Notes cells change.)

**1. part-feature.md — REQ-008 row (line 33): VERIFIED → PARTIAL**

CURRENT:
```
| REQ-008 | PendingQrBanner on Login/Signup when a QR was scanned pre-auth; route stashed payload after auth (`friends {qrUser}` / `myTeam {qrTeam}`). | VERIFIED | ui/screens/auth/LoginScreen.kt:151 + :589-602 (banner, DataStore-backed), ui/screens/auth/SignupScreen.kt:154 + :482-483; core/ui/AppShell.kt:220-235 (processPendingQr: user QR → navigate(FRIENDS, {qrUser}), team QR → navigate(MY_TEAM, {qrTeam})); core/session/SessionManager.kt:126-134 (DataStore pending-QR) | none (no automated tests in repo) | Verified by fresh code inspection. Routing-with-params implemented exactly as the row says. Downstream nuance: FriendsScreen/MyTeamScreen never READ the qrUser/qrTeam param (grep `env.params` = 0 hits in both files) — the routed id is dropped at the destination (no auto-popup); consistent with the documented native divergence logged under REQ-027. |
```
REPLACEMENT:
```
| REQ-008 | PendingQrBanner on Login/Signup when a QR was scanned pre-auth; route stashed payload after auth (`friends {qrUser}` / `myTeam {qrTeam}`). | PARTIAL | ui/screens/auth/LoginScreen.kt:151 + :589-602 (banner, DataStore-backed), ui/screens/auth/SignupScreen.kt:154 + :482-483; core/ui/AppShell.kt:220-235 (processPendingQr: user QR → navigate(FRIENDS, {qrUser}), team QR → navigate(MY_TEAM, {qrTeam})); core/session/SessionManager.kt:126-134 (DataStore pending-QR) | none (no automated tests in repo) | Attacker 10-f downgrade: banner + stash + navigation are implemented exactly, but BOTH destination screens never read the params (grep `env.params` = 0 hits in FriendsScreen.kt AND MyTeamScreen.kt), so the web's post-QR "Add Friend" / "Team Connect" popups (FriendsScreen.tsx:120-157, MyTeamScreen.tsx:68-105; SPEC/00:343 "open the respective connection popup", SPEC/01:410,460) never appear on native — the payload is consumed then functionally discarded. Navigation half VERIFIED; destination half MISSING. Feeds the REQ-027/REQ-129 owner ruling. |
```

**2. part-business-security-perf.md — REQ-143 row (line 89): VERIFIED → PARTIAL**

CURRENT:
```
| REQ-143 | Client upload caps before send (receipt ≤8 MB, result proof ≤4/8 MB per endpoint, host image ≤5 MB, avatar ≤8 MB), base64 data-URL encoding. | VERIFIED | app/src/main/java/com/areenax/nativeapp/core/ui/PhotoPickers.kt:31-36 (caps),122-131 (in-cap original or downscale-to-cap),70-92 (compressToCap loop),55-56 (data:image/jpeg;base64); consumers: ui/screens/wallet/DepositConfirmScreen.kt:152 (RECEIPT), ui/screens/main/TournamentDetailsScreen.kt:115,163 (details proof 8 MB), core/ui/ResultProofSheet.kt:77 (RESULT_PROOF 4 MB), ui/screens/host/HostCreationScreen.kt:136 (HOST_IMAGE) | none (no automated tests in repo) | "4/8 MB per endpoint" is literally implemented: 4 MB (UploadCaps.RESULT_PROOF) vs 8 MB (details-screen PROOF_MAX_BYTES, SPEC/02 §3). |
```
REPLACEMENT:
```
| REQ-143 | Client upload caps before send (receipt ≤8 MB, result proof ≤4/8 MB per endpoint, host image ≤5 MB, avatar ≤8 MB), base64 data-URL encoding. | PARTIAL | app/src/main/java/com/areenax/nativeapp/core/ui/PhotoPickers.kt:31-36 (caps),122-131 (in-cap original or downscale-to-cap),70-92 (compressToCap loop; last resort :87-91 returns WITHOUT a final size check),55-56 (data:image/jpeg;base64); consumers: ui/screens/wallet/DepositConfirmScreen.kt:152 (RECEIPT), ui/screens/main/TournamentDetailsScreen.kt:115,163 (details proof 8 MB), core/ui/ResultProofSheet.kt:77 (RESULT_PROOF 4 MB — dead sheet, unwired per REQ-128), ui/screens/host/HostCreationScreen.kt:136 (HOST_IMAGE) | none (no automated tests in repo) | Attacker 10-f downgrade: (a) the WEB REJECTS over-cap files with exact copy ("Receipt image too large", DepositConfirmScreen.tsx:93-99; "Screenshot is too large", TournamentDetailsScreen.tsx:164-168) while native SILENTLY DOWNSCALES and never rejects (that copy = 0 hits in app/src) — behavior+copy parity break on real-money upload paths; (b) compressToCap's last resort returns an unverified-size payload, so a pathological image can still exceed the cap before send; (c) the 4 MB proof cap is wired only in dead code (live endpoint uses 8 MB); (d) UploadCaps.AVATAR has zero call sites (no avatar picker exists on EITHER platform — server-only capability). |
```

**3. part-business-security-perf.md — REQ-151 row (line 104): PARTIAL → VERIFIED**

CURRENT:
```
| REQ-151 | Cache bootstrap payload for 60 s (module-level TTL, shared across callers, errors NEVER cached). | PARTIAL | app/src/main/java/com/areenax/nativeapp/ui/screens/main/HomeScreen.kt:90-93 (BOOTSTRAP_TTL_MS=60_000 module cache),110-131 (TTL read path; only Success cached :120-126, errors dropped :127); core/session/SettingsCache.kt:20-45 (cached-first, NO TTL) | none (no automated tests in repo) | The 60 s TTL + errors-never-cached rule is implemented exactly in the HomeScreen module cache. DEVIATION: the shared SettingsCache consumed by OTHER bootstrap consumers (DepositConfirmScreen:139-141, Profile social links, SupportFab/About) returns its cached settings for the whole process lifetime — no 60 s expiry — so those consumers can see settings older than 60 s. See Uncertain rows. |
```
REPLACEMENT:
```
| REQ-151 | Cache bootstrap payload for 60 s (module-level TTL, shared across callers, errors NEVER cached). | VERIFIED | app/src/main/java/com/areenax/nativeapp/ui/screens/main/HomeScreen.kt:90-93 (BOOTSTRAP_TTL_MS=60_000 module cache),110-131 (TTL read path; only Success cached :120-126, errors dropped :127); core/session/SettingsCache.kt:20-45 (cached-first, NO TTL) | none (no automated tests in repo) | Attacker 10-f overturn → VERIFIED: the flagged "deviation" is EXACT WEB PARITY — the web settings helper also caches for process lifetime with no TTL (src/lib/settings.ts:11-30, `if (cached) return…`, errors swallowed) and the 60 s TTL + errors-never-cached bootstrap cache exists on BOTH platforms (HomeScreen). Uncertain-rows question 2 resolved: no TTL change needed for SettingsCache. |
```

---

## New checkpoint questions (owner decisions)

1. **REQ-008 (new gate):** the QR payload is routed but the destination popups never open
   (native) — web opens them. Add the Friends/MyTeam deep-link popups for parity, or log this
   alongside REQ-027/REQ-129 as the final intentional divergence list?
2. **REQ-143 (new gate):** native silently recompresses over-cap uploads where the web rejects
   with exact copy. Which behavior is canonical for the money paths — keep the native
   "convert" UX, or port the web's rejection copy ("Receipt image too large" /
   "Screenshot is too large")? And should the LIVE proof endpoint cap be 8 MB (current) or
   4 MB (the spec's per-endpoint value that today exists only in dead code)?
3. **REQ-058:** server evidence now shows the param-less badge poll still works (route.ts:25)
   but skips the server's own countOnly optimization (route.ts:12-16). Accept the heavier poll
   on native, or require the one-line `?countOnly=1` for parity/perf? (No code changed now.)
4. **REQ-135:** no web screen ever passes `options.idempotencyKey` (grep: 0 call sites) — the
   "caller-overridable" clause is a dormant web capability. Accept always-fresh UUID v4 on
   native as final, or require the override hook?
5. **Shared-device note (A-11.1):** bank selection survives logout on BOTH platforms (web
   sessionStorage parity). Accept, or clear it on logout on native only?
6. **Doc-only:** ledger REQ-048/REQ-122 + Appendix A + STATE.md C4 cite `AboutScreen.kt:106`;
   the real line is :110 (drifted after the fix commits). Fold a cite refresh into the
   RTM assembly commit.
7. **Web-internal conflicts previously parked (Q1/Q2 from 10-d) remain open:** wallet
   quick-action label "Add" (HTML+native) vs "Deposit" (TSX+ledger), and SupportFab on
   ReferEarn/Tasks (native-only). No new evidence from this pass; flagged so they don't get lost.

---

*Verifier: attacker (10-f). Read-only pass — this file is the only artifact written; no
application code, no git operations, no other audit files touched.*
