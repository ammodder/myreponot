# REQUIREMENTS_LEDGER.md (rebuilt 2026-09-20, TASK-005 10-a)

> **Provenance.** The original requirements ledger was LOST in the 2026-09-19 sandbox rollback
> (see `audit/STATE.md` "Environment event"). This ledger is REBUILT from three sources:
> (1) the distilled app contract `AreenaxNativeAndroid/SPEC/00-05 + ICONS.md`;
> (2) native-era worklog entries (`/home/z/my-project/worklog.md` lines 376–end — owner
> requirements of that era, including the frozen web-panel decisions the native app ports 1:1);
> (3) [reconstructed] owner decisions/constraints in the audit docs (`STATE.md` owner-decision
> log + gates C1–C9, `00-baseline/*`). REQ numbering RESTARTS at REQ-001; no attempt is made to
> recover the lost ledger's numbering. Owner-verbatim text unavailable for reconstructed items
> is tagged `[reconstructed]`.
>
> **Format.** One row per requirement: ID | imperative requirement sentence | source cite
> (SPEC file:line, worklog:LINE, or [reconstructed] audit doc) | notes. Notes carry inline
> conflict tags: CANDIDATE-CONFLICT (owner gate / parity-vs-app divergence), AMBIGUITY
> (uncertain or self-contradictory source), [reconstructed] (non-verbatim provenance).
> Six sections in fixed order: FEATURE, UI, BUSINESS RULE, SECURITY, PERFORMANCE, ADMIN-DATA.
> Web parity target (blanket "1:1 replication of the web panel"): the 50 screen files under
> `/home/z/my-project/src/components/screens/**` (49 ported + dead-code AuthChrome.tsx excluded).
>
> Every row is written so a later verifier can check it against the app source by file:line.

---

## SECTION 1 — FEATURE (screens, flows, system capabilities)

| REQ | Requirement | Source | Notes |
|---|---|---|---|
| REQ-001 | Ship the whole user panel as ONE pure-Kotlin + Jetpack Compose Android app with ZERO WebView — every screen a 1:1 port (design, buttons, functions, animations) of the frozen web panel. | worklog:2867; SPEC/05:36-39 | Blanket requirement; parity detail in UI rows. |
| REQ-002 | Show the Login screen with ONE unified identifier field (email or phone), password with visibility toggle, "Login" primary CTA, inline hints and destructive "Login failed" toast. | SPEC/01:29-51; worklog:1245 | Owner removed the Email/Phone tabs (era decision). |
| REQ-003 | Provide "Continue as Guest" on Login → `POST /auth/guest` returning `isGuest=true` user, with its own spinner. | SPEC/01:44-46; SPEC/00:61-63 | Guest lives below the Login button (worklog:1208). |
| REQ-004 | Show the Signup screen (Full Name + Game Name) with best-effort `POST /auth/check {gameName}` availability; taken name → inline error + stay. | SPEC/01:53-65 | register 409 is the hard stop. |
| REQ-005 | Show Signup Step 1 with static 🇵🇰 +92 country chip, phone input, and DIGITS-ONLY game-UID field; both checked via `POST /auth/check`. | SPEC/01:67-78; worklog:1323 | Digits-only UID was an explicit owner fix. |
| REQ-006 | Show Signup Step 2 with a live 3-rule password checklist (1 letter; 1 number/special; 10 characters) gating "Continue". | SPEC/01:80-88 | |
| REQ-007 | Show Signup Step 3 (email + optional referral code + agreement checkbox that disables "Complete Account" until checked); `POST /auth/register`; 409 email → inline error + "Go to Login" pill. | SPEC/01:90-109; FFR:199-200 | "Go to Login" branch is LIVE on native, unreachable on web — AMBIGUITY (parity question). |
| REQ-008 | Render the PendingQrBanner on Login/Signup when a QR was scanned pre-auth, and route the stashed payload after auth (`friends {qrUser}` / `myTeam {qrTeam}`). | SPEC/00:339-344; SPEC/01:30 | Native stores pending QR in DataStore. |
| REQ-009 | Show Home: AppBar tab mode (avatar+welcome left, BalanceChip+BellButton right), hero banner with gradient/wordmark fallback, Games/Host pill tabs, 2-column games grid routing to tournaments/host flow. | SPEC/01:114-135 | Bootstrap via 60s TTL cache (see PERF). |
| REQ-010 | Show Tournaments with Upcoming/Ongoing/Completed segmented tabs that EACH refetch `GET /tournaments?status=…[&gameId]`, TournamentCard list, Room→RoomInfoSheet, Results→results. | SPEC/01:137-151; worklog:2819 | Per-tab snapshot/error memory is frozen behavior. |
| REQ-011 | Show Tournament Details: hero + countdown pill, 1–3-col summary cards (Prize Pool >0 only, Entry Fee always, Per Kill >0 only), Overview/Rules/Prizes tabs, numbered rules, 50/30/20 prize rows, Participants card. | SPEC/01:153-204 | "Never render Rs 0 cells" rule (worklog:1776). |
| REQ-012 | Join a tournament from Details via guest-gated "Join Now - Rs «fee»/Free"; on "insufficient" error open InsufficientBalanceDialog → "Deposit Now" → deposit. | SPEC/01:160-164,197-199; SPEC/03:110-118 | |
| REQ-013 | Submit a ONE-TIME result proof from Details: image ≤8 MB → `POST /tournaments/:id/proof`; 409 already-submitted locks the picker with "Result Submitted" / "Wait for the admin's approval." | SPEC/01:164-167; SPEC/02:146-149; worklog:1340 | Owner rule: submit-once, single image. |
| REQ-014 | Reveal Room ID & Password (joined + room open) with per-row copy buttons and toasts; AppBar share of tournament text with clipboard fallback. | SPEC/01:168-171,182-184 | |
| REQ-015 | Show My Tournaments listing ONLY tournaments the caller joined, polling `GET /my/tournaments` every 30 s, skipping the fetch for guests/logged-out, with 3 client-side filter tabs and "Browse Games" hidden on the Completed tab. | SPEC/01:206-222; worklog:999,1014,1027,1119 | "ONLY joined" + "no All tab" + "no Browse on Completed" were explicit owner orders. |
| REQ-016 | Show Results: Match Statistics grid (compact prize notation), winners sorted rank→kills with isMe tint, "Results not published yet." empty state, sticky "Share Results". | SPEC/01:224-240 | Wording parity = owner gate C2 [reconstructed] STATE.md. |
| REQ-017 | Show Wallet: primary balance card (2-decimal Rs), three guest-gated quick actions (Deposit/Withdraw/Transfer), transactions card with All/Money In/Money Out filter, typed icons and signed amounts with status dot+label. | SPEC/01:246-272 | Wallet card is the ONLY 2-decimal money surface (worklog:1170). |
| REQ-018 | Show Deposit: funding-source card + swap via BankSelectSheet, 3rem amount display, ALWAYS-VISIBLE AmountPad, "Continue to deposit" → depositConfirm with `{amount, method, accountId?}`. | SPEC/01:274-290; worklog:781 | Inline keypad restored per owner (Task 14). |
| REQ-019 | Show Confirm Deposit: amount hero, details rows (Deposit ID `#DEP-…`, Account Number from `settings.depositAccounts`, Account Name, fee "0.00", You Get), MANDATORY receipt upload ≤8 MB and TrxID input, helper note card, "Confirm Deposit". | SPEC/01:292-318; worklog:783 | Receipt+TrxID mandatory = owner order; CANDIDATE-CONFLICT C9 on fallback accounts (see REQ-102). |
| REQ-020 | Show Deposit Success: SuccessAnim "Deposit Request Submitted!" + PENDING StatusChip + Amount/Method/Reference/Current Balance rows; back and "Back to Wallet" → `repl(wallet)`, "Go to Home" → `repl(home)`. | SPEC/01:320-329 | |
| REQ-021 | Show Withdraw: account pill ("Select account"/"Bind Account"), 3rem amount + availability pill, quick chips 100/500/1000/5000, AmountPad, CTA disabled until account selected with helper text. | SPEC/01:331-346 | min withdraw from settings (default 500). |
| REQ-022 | Show Confirm Withdraw: summary rows (Amount, Withdraw To, Bank, Our Fee "0", You Get) and "Confirm Withdraw" → `POST /wallet/withdraw` (amount HELD server-side). | SPEC/01:348-359; SPEC/02:92-97 | |
| REQ-023 | Show Withdraw Success: "Withdrawal Request Submitted!" + held-from-balance subtitle + PENDING chip + New Balance row; same repl navigation as depositSuccess. | SPEC/01:361-368 | |
| REQ-024 | Show Transfer Money: SourceAccountCard, recipient picker (UID or email) with Change, amount section, optional Note, AmountPad, "Transfer Money" with client validations. | SPEC/01:370-384 | |
| REQ-025 | Show Transfer Success: "Transfer Successful!" + "You sent Rs «amount» to «receiver»" + Amount/Recipient/Reference rows; repl navigation. | SPEC/01:386-392 | |
| REQ-026 | Keep the legacy `selectBank` screen routable (dimmed Deposit mock + bottom sheet, confirm writes legacy bank-method selection, `back()`). | SPEC/01:394-403 | Superseded by BankSelectSheet but still routed — AMBIGUITY (dead-ish route kept for parity). |
| REQ-027 | Show My Team in two states: no-team ("No Team Yet" + guest-gated "Create Team") and with-team (UPPERCASE hero, members with Leader chip, customer-support note, destructive Leave / owner "Leave Team (Disband)" → `DELETE /team`). | SPEC/01:409-435; worklog:2794 | CANDIDATE-CONFLICT: native OMITS the owner join-requests inbox + team-QR deep-link popup that SPEC/01:416-427 requires (worklog:2804, FFR orphan 9). |
| REQ-028 | Show Team Creation: name (max 24, error state), LEADER (YOU) card with presence dot, numbered Player-UID inputs, same-country note, "Create Team" → `POST /team {name, memberUids}`. | SPEC/01:437-449; worklog:2795 | AMBIGUITY: native adds a Tag field + 1–3 add/remove UID rows; web spec has fixed 3 UID inputs. |
| REQ-029 | Show Team Creation Done with team hero + members card; back → `repl(myTeam)`. | SPEC/01:451-457; worklog:2796 | AMBIGUITY: native renders SuccessAnim "Team Created!"; web replicates teamcreationdone.html only. |
| REQ-030 | Show Friends: search pill, segmented All(n)/• Online(n)/Requests[badge] tabs, meta row, friend cards (online dot, last message, unread badge) → chat, requests received/sent with Accept All / Cancel, Add-by-UID view via header person_add, header qr_code_2 → QrSheet. | SPEC/01:459-499; worklog:2797 | |
| REQ-031 | Show Chat: friend-titled AppBar with static online indicator, messages list auto-scrolled, friend/mine bubble styling with done/done_all receipts, pinned input bar ("Type a message…", emoji appends 😊, send FAB, IME Send). | SPEC/01:501-516; worklog:2798 | CANDIDATE-CONFLICT C3: divider + online status are STATIC (see REQ-121). |
| REQ-032 | Show Refer & Earn: stats (Total Referrals, My Code), copy with 2 s "Copied!" state, share via Android chooser with clipboard fallback, referred-users list with "+Rs bonusEarned". | SPEC/01:518-528; worklog:2799 | |
| REQ-033 | Show Tasks (Profile sub-page with back arrow): earned-today pill, task rows (server icon, +Rs reward chip), guest-gated Claim → `POST /tasks` → "+Rs «reward»" toast + balance update, claimed state with check. | SPEC/01:530-543; worklog:2800 | |
| REQ-034 | Show Notifications: type-icon rows, tap = expand in place + background mark-read (rows never navigate away), unread dot, expanded deep-link "Open →", mark-all-read action. | SPEC/01:545-567; worklog:1100,2801 | CANDIDATE-CONFLICT: SPEC/01:553-565 also requires long-press selection mode + Select All + delete-selected; native replaced it with mark-all-read (worklog:2804) — needs owner ruling. |
| REQ-035 | Show Profile: header card (avatar ring, gameName+PK chip, email/"Guest user", UID, Share + QR Code pills), two menu cards, dark-mode Switch persisting the choice, social icons row (configured only), guest-gated Bind Account, "Logout" pill. | SPEC/01:572-594 | |
| REQ-036 | Show Edit Profile: profile info save via `PATCH /me`, change-password card (current/new/confirm, inline wrong-current error), "Log out from all devices" (`POST /auth/logout {all:true}`) → logout. | SPEC/01:596-612 | |
| REQ-037 | Show My Stats: Win Rate hero (progress bar + %), 2×2 stats grid, tappable Leaderboard card with rank + "«top3» Times" chip. | SPEC/01:614-627 | |
| REQ-038 | Show Leaderboard: podium top-3 with crown SVG + bolt points, ranked list with isMe highlight ("You", left primary bar), out-of-list "You" row using stats rank. | SPEC/01:629-644 | |
| REQ-039 | Show Achievements: summary card with "«percent»% Completed" + circular progress ring (Compose-drawn), category chips (All/Tournaments/Kills/Earnings/Social), achievement cards with progress bars, tap → detail bottom sheet. | SPEC/01:646-664; ICONS.md:181-183 | Progress ring is NOT a drawable — manual Canvas implementation (ICONS.md §4). |
| REQ-040 | Show Bind Account: saved-accounts list, Bank Name via BankSelectSheet ("Select Bank"), holder + account number inputs with validation (<8 → invalid), footnote, "Bind Account Now" → `POST /bank`. | SPEC/01:666-680 | |
| REQ-041 | Show Bind Account Success: "Account Linked!" + Verified chip + holder/number("•••• last4")/Linked Date/Instant Payouts rows; doneTarget = withdraw (when returnTo) else wallet. | SPEC/01:682-694 | |
| REQ-042 | Show the in-app Admin Panel screen (`adminPanel`): role-gated sections with "Admin access required." EmptyState for non-panel roles; loading/error states. | SPEC/01:696-755 | Section contracts in ADMIN-DATA rows. |
| REQ-043 | Show Host Tournament: game-scoped tabs (Upcoming/Ongoing/Completed/Created), HostCard replicas (banner, mode chip, status label, prize/fee, slots progress, display-only JOIN NOW), empty-state "Create Tournament" + floating add FAB — creation guest-gated. | SPEC/01:797-813 | |
| REQ-044 | Show Host Tournament Creation fed ONLY by `GET /games/config`: name, Date & Time via modal picker, Match Type sheet from admin modes (FIXED slots auto-fill a disabled Slots field), Entry Fee, Rules modal, optional image ≤5 MB, CALCULATIONS PREVIEW card, "Host Tournament". | SPEC/01:815-840; worklog:1061,1067,1082 | Owner: prize pool/per-kill/loser-prize inputs REMOVED (admin economics); game auto-selected from Host grid. |
| REQ-045 | Show Host Success: bouncing glowing check hero, "Tournament Hosted Successfully!", summary card (Name/Entry Fee/Slots/Match Type), View Details + Created Tournaments buttons. | SPEC/01:842-851 | |
| REQ-046 | Provide the "my created tournaments" list screen (`hostTournamentCard`, HostCard list → hostTournamentDetails). | SPEC/01:853-857 | SPEC notes native may merge with F1 — AMBIGUITY (kept as its own route key). |
| REQ-047 | Show Host Tournament Details: host's own view with countdown hero, summary cards, Overview/Rules/Prizes, "Copy room info" (+ per-field copy) with "Room info not set yet" fallback, Participants card. | SPEC/01:859-874 | |
| REQ-048 | Show About: identity block with brand logo, server-driven version line `settings.version ?: "1.2.0"`, FALLBACK_MISSION text, Follow Us chips (wa.me/t.me/etc., hidden when unset), legal links, "© 2026 Areenax Tournament App. All rights reserved." | SPEC/01:880-894; [reconstructed] STATE.md gates C4 | CANDIDATE-CONFLICT C4: server/fallback version 1.2.0 (AboutScreen.kt:106) vs versionName 2.0.0. |
| REQ-049 | Show Terms & Conditions as PUBLIC pre-auth screen with 10 verbatim static sections and "Last updated: March 2026"; bell only when signed in. | SPEC/01:896-903 | |
| REQ-050 | Show Privacy Policy as PUBLIC pre-auth screen with 8 verbatim static sections (contact "support@gamingapp.com"); bell only when signed in. | SPEC/01:905-909 | |
| REQ-051 | Show the Offline screen ("OOPSS!" + exact body + "Try again") with AUTO-RECOVERY: pop back when connectivity returns; "Try again" retries the current screen. | SPEC/01:911-920; FFR G04 | |
| REQ-052 | On app start show the splash (logo + spinner), read the persisted session, and if a token exists validate via `GET /me` → Home, else → Login; a 401 here auto-logs out. | SPEC/00:80-86 | |
| REQ-053 | Logout: best-effort `POST /auth/logout` (Edit-Profile variant `{all:true}` revokes ALL sessions), then wipe token/user/unread/nav AND any stashed pending QR. | SPEC/00:71-78; SPEC/01:604 | |
| REQ-054 | Replicate the web navigation state machine exactly: 46 route keys, `navigate` pushes onto a stack capped at 25, `replace` clears the stack, `goBack` pops and falls back to Home (logged-in) / Login; unknown key → Login. | SPEC/00:88-114,145; SPEC/05:216-219 | |
| REQ-055 | Force-correct any non-public screen to Login when no user; show BottomNav ONLY on the 6 logged-in roots (home, tournaments, myTournament, friends, wallet, profile). | SPEC/00:112-115; SPEC/05:224-226 | PUBLIC_SCREENS = auth 5 + terms + privacy. |
| REQ-056 | Provide the full QR system: display ("My QR" deep-link QR, ZXing 512px), scan (camera → parse → `GET /qr/lookup` → user/team result cards), and manual numeric-UID fallback — all three paths reach the same lookup. | SPEC/00:312-352; SPEC/03:240-271 | Camera optional — manual fallback required (BASELINE:73). |
| REQ-057 | Monitor connectivity: on loss navigate Offline, on regain pop it (ConnectivityManager NetworkCallback). | SPEC/00:160-163; FFR S02 | |
| REQ-058 | Show the unread badge ONLY inside BellButton (red, "9+" cap, 2px white ring), refreshed on navigation via the 30 s-throttled `GET /notifications?countOnly=1`. | SPEC/00:201-213; FFR S03 | |
| REQ-059 | Ship v2 WITHOUT Firebase/FCM push (unread via polling only); treat push notifications as a NEW feature requiring owner approval. | [reconstructed] MISSING-INPUTS.md:23-24; BASELINE:80 | [reconstructed] owner decision pending. |
| REQ-060 | Ship with NO manifest deep-link intent filters; the `parseDeepLink` grammar (tournament:/host:/wallet/myTournament/notifications/home/myStats/results) is reachable only from in-app notification payloads and pending-link processing. | BASELINE:69-73; FFR:173-177 | Pairing must not be broken one-sided. |
| REQ-061 | Freeze release identity: versionCode 2, versionName "2.0.0", applicationId com.areenax.nativeapp, minSdk 24 / target+compile 35; first debug APK (12,796,733 B, sha256 5ff0d2b5…) published. | BASELINE:84-85; worklog:2994; [reconstructed] STATE.md Phase-0 outcome | Lost Task 6–11 app-side changes are NOT in this baseline (STATE.md:26-27). |

## SECTION 2 — UI (design system, components, visual parity)

| REQ | Requirement | Source | Notes |
|---|---|---|---|
| REQ-062 | Replicate the web panel 1:1 — pixel truth = extracted HTML pages, behavior truth = the TSX; copy strings EXACTLY as quoted (no rewording). | SPEC/01:8; SPEC/05:378; worklog:2790,2802 | Blanket parity requirement (web target: src/components/screens/**). |
| REQ-063 | Implement the light theme exactly: background #f8f9ff, primary #004ac6, on-primary #fff, containers/fixed tones, secondary #006c49, tertiary #ad0033, error #ba1a1a, outlines, lavender surfaces, balance-chip #f0f4ff. | SPEC/04:15-100 | |
| REQ-064 | Implement the dark "Midnight Pills" theme exactly: canvas #060608 w/ top glow, containers #131316→#2a2a2f, primary #2f6bff (white on-primary), secondary #4edea3, error #ff5449, dark tx-in pair #064e3b/#6ee7b7. | SPEC/04:104-154 | The lost Task-11 "Midnight Charcoal" (#1C1C1E/#3C3C3C) is NOT in this source — do not restore without owner approval (FFR:187-190). |
| REQ-065 | Paint the dark ambient canvas ONCE behind the NavHost (AppScaffold); screens draw transparent roots (light-only screen gradients via drawBehind). | SPEC/04:147-153; SPEC/05:105-110,449 | |
| REQ-066 | Bundle Hanken Grotesk variable font and implement the 9-step type scale (display-lg 32/40 … label-sm 12/600) plus recurring one-offs (hero amounts, toast title/description). | SPEC/04:158-178 | |
| REQ-067 | Apply the shape system: pill (rounded-full) ALL text fields and buttons, 24dp cards, 32dp top sheets, 28dp dialogs, 12/16dp tiles. | SPEC/04:182-196 | |
| REQ-068 | Apply the shadow set: card-shadow, nav-shadow, fab/primary glow, premium/glass-tab, toast shadow. | SPEC/04:198-212 | |
| REQ-069 | Apply spacing conventions: 16dp gutters, pb-24 nav screens / pb-28-32 floating-bar screens, 64dp AppBar. | SPEC/04:214-218 | |
| REQ-070 | Replicate per-screen ambient gradients: auth aurora (reference), home .bg-areena, profile .bg-ambient, wallet radial tops, MyTeam/Referrals/Leaderboard blobs, offline linear gradient, banner scrims, success glow. | SPEC/04:227-243 | |
| REQ-071 | Replicate motion: screen transition fade+x-slide (SPEC 0.18 s; native host uses 260 ms), sheets slide-up 250 ms + backdrop fade, press-scale 0.95–0.98, fade-in tab content, ping live-dot, host-success bounce. | SPEC/04:247-259; SPEC/00:156; SPEC/05:220-222 | AMBIGUITY: 180 ms (SPEC/00:156) vs 260 ms (SPEC/05:221) transition timing. |
| REQ-072 | Persist the theme toggle in DataStore and drive the app theme from it — NOT from the system setting. | SPEC/00:174-176; SPEC/04:104-105; FFR S08 | |
| REQ-073 | Render every icon from 154 locally-bundled VectorDrawables (115 outlined + 20 filled Material Symbols, 14 lucide, 5 inline) — NO icon fonts, no remote URLs; use fill variants where the web sets FILL 1; apply -45° rotation to the send icon; keep WhatsApp logo literal-white; draw the achievements ring manually. | ICONS.md:1-10,170-195,197-218; SPEC/05:331-359 | AMBIGUITY: worklog:2867 says "155" drawables; verified count is 154 (ICONS.md:10, BASELINE:26). |
| REQ-074 | Provide the AppBar system: page mode (back circle + centered title + right actions, null-right spacer) and tab mode (left slot + right cluster); BalanceChip renders when `balance=true`. | SPEC/03:30-49 | Native screens pass BellButton explicitly (worklog:2830a). |
| REQ-075 | Provide BellButton (40px white circle, outlined bell, red badge w/ 9+ cap) and BalanceChip ("Rs «formatMoney»" pill → wallet) and AppBarIconButton (40px white circle). | SPEC/03:40-47 | |
| REQ-076 | Provide BottomNav with EXACT tab order myTournament, friends, home, wallet, profile; icon-only (no labels/dots), FILL 1 icons, active `text-primary`, tap no-op when already active. | SPEC/03:51-60 | |
| REQ-077 | Provide EmptyState: centered 80px circle + outlined Material icon (default `trophy`) + message, used at every listed screen with the web's exact copy. | SPEC/03:62-69 | |
| REQ-078 | Provide StatusChip (Pending/Rejected/Completed variants) and SuccessAnim (glowing check badge, title 26px, subtitle max-w 290px). | SPEC/03:71-84 | |
| REQ-079 | Provide SupportFab: 56dp primary circle + support_agent, opens `https://wa.me/<settings digits>` (no number → About); placed on home/tournaments/wallet/profile (offset) and tournamentDetails (no offset); never on auth/chat/admin. | SPEC/03:86-93; FFR S04 | |
| REQ-080 | Provide AccountRequiredDialog ("Account Required" / "You're exploring AREENAX as a guest." / feature line / Create Account → signup / Continue as Guest) behind `useRequireAccount`-equivalent gate. | SPEC/03:99-108; SPEC/00:63-68 | |
| REQ-081 | Provide InsufficientBalanceDialog ("Insufficient Balance", needs Rs entryFee, your balance, "Deposit at least Rs «shortfall»", Deposit Now → deposit, Not Now). | SPEC/03:110-118 | |
| REQ-082 | Provide AmountPad (3-col, 1-9, ".", 0, backspace) ALWAYS VISIBLE inline on deposit/withdraw/transfer, with append rules max-8 significant digits + single ".". | SPEC/03:120-126; SPEC/05:264-266; worklog:781 | Owner restored inline keyboards (Task 14). |
| REQ-083 | Provide BankSelectSheet in 3 configurations: "Choose Funding Bank" (banks+accounts), "Choose Withdrawal Account" (accounts only + Bind Account empty action), "Select Bank" (banks only) — sticky Confirm, search, green active checks. | SPEC/03:136-152 | |
| REQ-084 | Provide DateTimePickerModal replicating the hostcreation picker (calendar grid, HH:MM + AM/PM, Cancel / "Set Date & Time" → ISO). | SPEC/03:154-164; worklog:1061 | |
| REQ-085 | Provide TournamentCard with the full state-machine action set (JOIN NOW / JOINED / ROOM ID & PASSWORD / RESULTS / SUBMIT RESULT PROOF or "Result Submitted" / MATCH PROCESSING ping / SLOTS FULL / CANCELLED-COMPLETED), banner scrim, host row, stats grid, slots progress. | SPEC/03:166-190; worklog:965 | |
| REQ-086 | Provide RoomInfoSheet: fresh `GET /tournaments/:id` per open + refresh, "Room details not shared yet" state, copy rows w/ toasts, "Visible until «formatDateTime»" expiry footer. | SPEC/03:192-201 | |
| REQ-087 | Provide QrSheet with My QR / Scan QR tabs, 176px ZXing QR of the deep link, share texts w/ clipboard fallback, scan viewfinder + camera-error fallback to manual UID, UserResultCard/TeamResultCard with relation-aware actions. | SPEC/03:240-271 | |
| REQ-088 | Provide the toast system: SINGLE visible toast, auto-dismiss 5 s, default/destructive variants with exact colors, slide-in from top, timer bar — copy matches the web per call site. | SPEC/00:181-199; FFR S06 | Do NOT use platform Snackbar. |
| REQ-089 | Use AreenaxSpinner as the standard loading indicator (32dp default) on every loading state. | SPEC/05:257,457 | |
| REQ-090 | Render ALL avatars as initial-circles (first char of gameName, bg-primary) when no image; host initial on cards; Coil for remote images with same fallback. | SPEC/00:298-301 | |
| REQ-091 | Keep the auth visual language: lavender root, h-5rem brand logo, rounded-full lavender-filled inputs, h-14 primary CTAs; app colors (NOT the rejected Floxly clone); login logo styled like signup's. | SPEC/01:37-46; worklog:1208,1227 | Owner rolled back the Task-38 Floxly redesign (Task 39). |

## SECTION 3 — BUSINESS RULE

| REQ | Requirement | Source | Notes |
|---|---|---|---|
| REQ-092 | Never change behavior of a frozen feature without owner approval — every code change re-checks the Feature Freeze Register (110 frozen rows). | FFR:5-8; [reconstructed] MASTER_RULES.md F.2 | Blanket no-drift rule. |
| REQ-093 | Auto-detect login identifier: digits-only after stripping `[\s\-().]` matching `^\+?[0-9]{7,15}$` → `{phone}` (retry as-typed on failure), else `{email}`. | SPEC/01:32-35; SPEC/02:35-39 | |
| REQ-094 | Enforce the password policy at registration: client checklist (letter, number/special, 10 chars) AND server policy (10+ chars, letter + digit/symbol, breached list). | SPEC/01:84-87; SPEC/02:50 | |
| REQ-095 | Enforce gameName uniqueness: pre-flight check best-effort, register 409 is the hard stop (owner-ordered unique gameName). | SPEC/01:55-57; worklog:1340 | |
| REQ-096 | Block guests from account-dependent actions with the AccountRequiredDialog (deposit, withdraw, transfer, join, claim task, host, create team, bind, edit profile, add friends, join request) — server `requireNonGuest` double-enforces. | SPEC/00:61-69; worklog:1615 | |
| REQ-097 | On ANY HTTP 401 wipe the session (token/user/pending stores) and route to Login. | SPEC/00:55-59; SPEC/05:404 | |
| REQ-098 | Enforce deposit minimum from settings (`minDeposit`, default 100) client-side ("Amount too low" / "Minimum deposit Rs «min»") and server-side (min/max caps). | SPEC/01:278,288-289; SPEC/02:88-90 | |
| REQ-099 | Require BOTH a payment receipt image (≤8 MB) and a TrxID before Confirm Deposit validates (owner-ordered mandatory receipt + TrxID). | worklog:778,783; SPEC/02:84 | |
| REQ-100 | Resolve the deposit account number from `settings.depositAccounts` with raw/lower/UPPER/Title-case key variants, FALLING BACK to hardcoded numbers (jazzcash 0300-1234567, easypaisa 0345-7654321, sadapay 0123 4567 8901, alphla 0245 0102 3456, mezan/bank transfer 1234 5678 9012). | SPEC/01:302-304; [reconstructed] STATE.md gates C9 | CANDIDATE-CONFLICT C9: real-money hardcoded fallback — owner must confirm or remove. |
| REQ-101 | Keep FUNDING_BANKS exactly `["Jazzcash","Easypaisa","Sadapay","Alphla Bank","Mezan Bank"]` — the "Alphla" spelling is BINDING for web parity. | SPEC/01:287; SPEC/05:201; FFR:183-184 | CANDIDATE-CONFLICT C1: spelling parity vs correctness — owner to decide. |
| REQ-102 | Enforce withdraw rules: minimum (`minWithdraw`, default 500), amount ≤ balance, require a BOUND account, server rejects a second pending withdrawal (409) and HOLDS the amount immediately. | SPEC/01:344-345; SPEC/02:92-97 | |
| REQ-103 | Route withdraw→bindAccount with `returnTo:"withdraw"` so success returns the user to withdraw; bind success "Continue to Withdraw" honors the same returnTo. | SPEC/01:332-334,683-685 | |
| REQ-104 | Enforce transfer rules: recipient by UID or email, block self-transfer and guest recipients, server max cap, 404 "Recipient not found. Check UID or email." | SPEC/02:99-104; SPEC/01:373-376 | |
| REQ-105 | Sync wallet balance into the session store on `GET /wallet` when different, and decrement locally on join success (refetch after join/proof via refreshKey). | SPEC/01:250-251,159-163; worklog:2820 | |
| REQ-106 | Format money exactly: `formatMoney` 0–2 decimals grouping for general amounts, `formatBalance` always 2 decimals for the wallet card only (owner: integer rows elsewhere); `Rs ` prefix. | SPEC/00:25,218-228; worklog:1170 | |
| REQ-107 | Gate joins server+client: insufficient fee → dialog path, slots full, match started, already joined; errors surfaced verbatim. | SPEC/02:133-138; SPEC/01:162-164 | |
| REQ-108 | Compute host economics by the fixed rule: collection = fee × slots; prizePool = round(collection×0.5); rank1/2/3 = 50/30/20% of pool; loserPrize = round(collection×0.5/(slots−1)); perKill never sent by the panel (server default 0) — validated server-side. | SPEC/01:833-836; worklog:1067,1591 | Owner confirmed calc-preview logic (Task 47). |
| REQ-109 | Drive EVERY tournament card from the single join-state machine (Join → Joined → Room ID & Password → Match status → Results, + Slots Full) with admin-set room credentials + expiry and results publishing. | worklog:965,1132; SPEC/03:166-190 | |
| REQ-110 | Derive tournament status TIME-AWARE everywhere (start time → Ongoing, admin results → Completed) and show only the 3 status tabs over joined tournaments (no "All" tab). | worklog:1042,1049 | |
| REQ-111 | Strip room credentials from any surface for non-joined users (server already strips; client must not fabricate or persist them). | SPEC/02:116,123; worklog:1542 | |
| REQ-112 | Enforce result-proof window: admin disable OR duration elapsed → proof mode takes precedence; ONE-time submission with 409 lock; RESULTS button appears automatically after publish. | worklog:1149,1340; SPEC/02:146-157 | |
| REQ-113 | Take the match-in-progress duration from the admin-configured per-game `matchDurationMinutes` (games config), not a client constant. | worklog:1149; SPEC/02:313-315 | |
| REQ-114 | Keep notification interactions: tap = expand in place + auto read (no navigation, no visibility loss); delete deletes only what is selected (native: mark-all-read path) — never silent mass deletion. | worklog:1100,1114 | Divergence conflict recorded at REQ-034. |
| REQ-115 | Refresh chat by polling every 3 s, skipping the state write when the list+last-id is unchanged, poll errors silent. | SPEC/00:380; SPEC/01:503-505 | |
| REQ-116 | Apply friend-request rules: auto-accept when the other party already sent one; block self-add; 409 already-friends / already-sent; cancel sender-only; Accept All loops per-row with summary toasts. | SPEC/02:209-219; SPEC/01:468-475 | |
| REQ-117 | Derive friend "Online" as lastMessageAt within 30 minutes; render the chat date divider and online status STATICALLY ("Today, 5:42 PM" pill). | SPEC/01:479,507; [reconstructed] STATE.md gates C3 | CANDIDATE-CONFLICT C3: static vs live — owner to decide. |
| REQ-118 | Keep the Friends "Suggested Players" DEMO list (TactiCool_Dan / Vortex_Recon, local Add → "Sent") exactly as the design shows. | SPEC/01:489-490; [reconstructed] STATE.md gates C5 | CANDIDATE-CONFLICT C5: demo data vs no-demo-data rule. |
| REQ-119 | Enforce team rules: max 4 members, ONE team per user, owner-only accept/decline of join requests, any-member leave / owner leave = disband. | SPEC/02:174-194; worklog:2131 | |
| REQ-120 | Claim tasks idempotently once per period (`POST /tasks` 409 on duplicate) crediting wallet + TASK_REWARD transaction + notification. | SPEC/02:260-262 | |
| REQ-121 | Keep the referral system: code from `GET /referrals`, per-referral bonus from settings, copy/share with fallbacks, referred list with bonusEarned. | SPEC/02:264-266; SPEC/01:518-528 | |
| REQ-122 | Show the About version as SERVER-DRIVEN `settings.version` with fallback "1.2.0" (AboutScreen.kt:106) — do NOT hardcode versionName. | [reconstructed] STATE.md gates C4; FFR:146,205 | CANDIDATE-CONFLICT C4: server 1.2.0 story vs versionName 2.0.0. |
| REQ-123 | Keep version informational only — NO forced-update logic anywhere. | SPEC/00:31 | |
| REQ-124 | Accept that NO account-deletion flow exists in the app (release blocker candidate). | [reconstructed] STATE.md gates C6; FFR:206 | CANDIDATE-CONFLICT C6: Play policy risk — owner decision required. |
| REQ-125 | Keep `app_base_url` (strings.xml) as the ONLY deployment knob; the current placeholder `https://YOUR-AREENAX-DOMAIN.example.com` MUST be replaced before any deploy. | SPEC/05:440-445; BASELINE:96-97; FFR:185-186 | Deployment blocker, not a code change. |
| REQ-126 | Never fabricate data while offline: bootstrap errors are never cached; MyTournament errors surface only when nothing was ever loaded; each screen's error/empty states are used verbatim. | SPEC/01:118-119,212; worklog:2819 | |
| REQ-127 | Render optional info ONLY when configured — never "Rs 0"/"—" placeholders; Entry Fee is ALWAYS shown (0 = meaningful free entry). | worklog:1776; SPEC/01:174-175,1535 | Owner-confirmed Part-B rule. |
| REQ-128 | Keep the dead shared sheets UNWIRED (JoinTeamSheet, SlotPickerSheet, RoomSheet, AmountKeyboard*, ResultProofSheet, TeamJoinSheet as live contracts) — do not wire or delete them without owner approval. | SPEC/03:11-14; FFR:178-182 | FFR orphan 2. |
| REQ-129 | Surface the native-vs-SPEC divergence on MyTeam (no join-requests inbox, no team-QR popup) and Notifications (no long-press selection/delete) as owner decisions — they are frozen intentional divergences per the build record. | SPEC/01:416-427,553-565; worklog:2804; FFR:201-204 | CANDIDATE-CONFLICT: SPEC requires both; app omits both. |
| REQ-130 | Route the Profile "Admin Console" row to the adminPanel flow on native (web routes to the desktop AdminDashboard); keep `adminDashboard` key mapped per the native audit decision. | FFR:191-194; SPEC/00:399-403 | AMBIGUITY: intentional divergence vs 1:1 parity. |
| REQ-131 | Apply the in-app deep-link grammar `parseDeepLink` to notification links (`tournament:<id>`, `host:<id>`, wallet, myTournament, notifications, home, myStats, results) with URL-decode tolerance. | BASELINE:69-71; SPEC/00:170-173 | In-app only (see REQ-060). |
| REQ-132 | Persist only what the web persists: token+user under the session store; bank selection, pending QR, pending link, theme as named stores; everything else session-scoped. | SPEC/00:39-44,357-364 | |

## SECTION 4 — SECURITY

| REQ | Requirement | Source | Notes |
|---|---|---|---|
| REQ-133 | Attach `x-token: <token>` to every authenticated request (and `Content-Type: application/json` to all). | SPEC/00:46-48; SPEC/02:6-7 | |
| REQ-134 | Store the session token + user in DataStore ("areena-app" equivalent) — the ONLY persisted credentials. | SPEC/00:39-44 | AMBIGUITY: SPEC/00:42 offers EncryptedSharedPreferences as alternative; native uses plain DataStore — security-review item. |
| REQ-135 | Send an `Idempotency-Key` (UUID v4, caller-overridable) on EVERY non-GET call so the server can dedupe replays. | SPEC/00:49-51; SPEC/02:8-9 | |
| REQ-136 | Block concurrent duplicate non-GET calls per method+path client-side (second call → local 409 "Request already in progress"). | SPEC/00:52-53; SPEC/05:403-404 | |
| REQ-137 | Enforce guest restrictions on the server (`requireNonGuest`) for all money/host/team/social mutations — the client dialog is a pre-block, not the gate. | SPEC/00:69; worklog:1615 | |
| REQ-138 | Mask password input with visibility toggle; the API never returns password material (publicUser projection only). | SPEC/01:41-42; SPEC/02:15-17 | |
| REQ-139 | Mask bank account numbers in UI via `maskAccountNumber` ("PK••••••••6702" style); raw numbers only where the design shows them (SelectBank "Your Accounts"). | SPEC/00:262-266; SPEC/01:401 | |
| REQ-140 | Restrict room credentials to joined users (server strips for others; client renders conditionally and logs nothing). | SPEC/02:116,123 | |
| REQ-141 | Keep the permission set to INTERNET + CAMERA + ACCESS_NETWORK_STATE only, with camera `uses-feature required=false` (manual-UID fallback). | BASELINE:72-73; FFR S12 | |
| REQ-142 | Ship with NO Firebase, no analytics, no trackers, no WorkManager/Hilt/Room — dependency allowlist as frozen. | BASELINE:76-80 | Push addition = ask-first (REQ-059). |
| REQ-143 | Enforce client upload caps before send (receipt ≤8 MB, result proof ≤4/8 MB per endpoint, host image ≤5 MB, avatar ≤8 MB) with base64 data-URL encoding. | SPEC/00:307-310; SPEC/05:184-187; FFR S10 | |
| REQ-144 | Parse QR payloads strictly (accept `u:/user:/t:/team:` and `?qr=` deep links; anything else → "This is not an AREENAX QR code." — no navigation on invalid). | SPEC/00:315-318; SPEC/03:255-258 | |
| REQ-145 | Keep release builds minified (R8 + resource shrinking); no keystore exists — release signing is an owner-only external blocker. | BASELINE:83; [reconstructed] STATE.md blockers | |
| REQ-146 | Never place secrets in code, logs, or reports (no secrets exist in the app source; backend secrets stay server-side). | [reconstructed] MASTER_RULES.md F.5 | |

## SECTION 5 — PERFORMANCE

| REQ | Requirement | Source | Notes |
|---|---|---|---|
| REQ-147 | Poll chat messages every 3000 ms only while the chat screen is active (auto-cancelled on dispose) with identical-result skip. | SPEC/00:380; SPEC/05:412-418; worklog:2808 | |
| REQ-148 | Poll My Tournaments every 30000 ms; guests/logged-out skip the fetch entirely. | SPEC/00:381; SPEC/01:212; worklog:2821 | |
| REQ-149 | Throttle unread checks to one fetch per 30 s via the single UnreadManager (mounted BellButtons must not each hit the API). | SPEC/00:207-213; SPEC/05:431-432 | |
| REQ-150 | Tick countdown timers at 1000 ms only on the details/host-details screens (drives countdown text, room-open check, state machine). | SPEC/00:384-385; worklog:2820 | |
| REQ-151 | Cache the bootstrap payload for 60 s (module-level TTL, shared across callers, errors NEVER cached). | SPEC/00:388; SPEC/01:118-119 | |
| REQ-152 | Load remote images through Coil with initial-circle/gradient fallbacks; downscale picked images to the endpoint cap before base64 upload. | SPEC/00:292-310; BASELINE:47 | |
| REQ-153 | Keep navigation lightweight: single Activity, in-memory state-machine router with AnimatedContent transitions; no Navigation-Compose library. | SPEC/05:216-222; BASELINE:37-40 | |
| REQ-154 | Consume the slimmed list payloads (no `myProofImage` in list endpoints; admin lists slimmed; heavy images fetched per-id in viewers only). | worklog:1832,1846 | Web-side fix the native DTOs mirror. |
| REQ-155 | Run NO background workers/services/receivers — all polling is screen-lifecycle-scoped. | BASELINE:67; SPEC/05:409-436 | |

## SECTION 6 — ADMIN-DATA (admin panel, settings, server-driven data)

| REQ | Requirement | Source | Notes |
|---|---|---|---|
| REQ-156 | Gate the Admin Panel by `ADMIN_PANEL_ROLES` (MODERATOR, FINANCE_ADMIN, SUPER_ADMIN, legacy ADMIN) with server RBAC per section; client gate shows "Admin access required.". | SPEC/00:396-403; SPEC/01:696-700 | |
| REQ-157 | Provide Payment Requests administration: list with direction icons, user summary (gameName/UID/balance), note bubble, "View Receipt" full-screen data-URL viewer (`GET /admin/requests/:id`), four-eyes "Reviewed — awaiting second approval" chip, approve/reject with exact toasts, "Final Approve" label when fourEyesEnabled. | SPEC/01:709-721; SPEC/02:337-339 | |
| REQ-158 | Provide Tournament Management: Match Status chips (COMPLETED → publish-only, 400 otherwise), Result-Proof-Mode disable toggle with exact toasts, Room ID/Password form + datetime expiry, results editor with auto-prize on rank/kills entry (50/30/20 + loserPrize + kills×perKill floor), per-player "View Proof" viewer, publish-once (409 after publish) with Final Standings. | SPEC/01:721-741; SPEC/02:340-344; worklog:1170 | |
| REQ-159 | Provide Result Proofs administration: list of submitted screenshots (user + tournament + mode + timeAgo), thumbnails prefetched via `GET /admin/proofs/:id`, full-screen viewer, empty state "No result proofs submitted yet.". | SPEC/01:782-791 | |
| REQ-160 | Provide Games Management: create/update(activate/deactivate, match duration)/delete games; per-game config ops add/delete for modes (name + FIXED slots + sub), maps, perspectives — feeding the host creation flow. | SPEC/01:763-780; worklog:1061 | |
| REQ-161 | Provide the Settings editor with per-key server authority: appDownloadUrl, minDeposit, minWithdraw, referralBonus, welcome-bonus toggles/amounts (user+guest), commission, version, whatsapp/instagram/telegram/youtube/discord, aboutMission, depositAccounts ("Name: number | …"), fourEyesEnabled; Save → `{success, updated}` toast. | SPEC/01:702-706,743-747; worklog:1832 | |
| REQ-162 | Provide Security & Audit (SUPER_ADMIN): Events tab (action + ALLOW/DENY + risk + endpoint + reason), Audit tab (action + actorRole + target), and "Run reconciliation" returning drift list or "All «n» balances match the ledger.". | SPEC/01:748-753; SPEC/02:349-350 | |
| REQ-163 | Consume `GET /bootstrap` settings app-wide: minDeposit/minWithdraw (wallet validation), version (About), whatsapp (SupportFab/About), social links (Profile/About), appDownloadUrl (PendingQrBanner), referralBonus, depositAccounts (depositConfirm). | SPEC/02:23-33; SPEC/01:702-706 | SettingsCache is the single cached source. |
| REQ-164 | Feed the host creation flow exclusively from `GET /games/config` (admin-managed modes with FIXED slots, maps, perspectives, matchDurationMinutes) — no client-side constants. | SPEC/02:312-315; worklog:1061,1067 | |
| REQ-165 | Keep `adminDashboard` as a distinct 46th route key (native: bound to the rewired admin tournaments flow; NOT the web desktop console). | SPEC/01:757-761; FFR:191-194 | AMBIGUITY: see REQ-130. |

---

## Appendix A — Conflict & ambiguity index (for TASK-005 10-c..10-f)

| Tag | REQ | One-line issue |
|---|---|---|
| CANDIDATE-CONFLICT C1 | REQ-101 | "Alphla Bank" spelling: web parity (binding) vs correctness — owner gate open. |
| CANDIDATE-CONFLICT C3 | REQ-117, REQ-031 | Chat date divider + online status rendered static vs live. |
| CANDIDATE-CONFLICT C4 | REQ-048, REQ-122 | About version server-driven (`settings.version` fallback "1.2.0", AboutScreen.kt:106) vs versionName 2.0.0. |
| CANDIDATE-CONFLICT C5 | REQ-118 | Friends "Suggested Players" demo data vs no-demo-data rule. |
| CANDIDATE-CONFLICT C6 | REQ-124 | No account-deletion flow (Play policy risk). |
| CANDIDATE-CONFLICT C9 | REQ-100, REQ-019 | Hardcoded deposit fallback account numbers when `settings.depositAccounts` empty (real-money path). |
| CANDIDATE-CONFLICT (no gate yet) | REQ-027, REQ-129 | Native omits MyTeam join-requests inbox + team-QR popup that SPEC/01 requires (worklog:2804). |
| CANDIDATE-CONFLICT (no gate yet) | REQ-034, REQ-129 | Native omits Notifications long-press selection/delete that SPEC/01 requires; mark-all-read instead. |
| CANDIDATE-CONFLICT C2 | REQ-016 | Results-screen wording parity with web (owner gate open). |
| AMBIGUITY | REQ-071 | Screen transition 180 ms (SPEC/00:156) vs 260 ms (SPEC/05:221). |
| AMBIGUITY | REQ-073 | Drawable count 154 (ICONS.md/BASELINE) vs "155" (worklog:2867). |
| AMBIGUITY | REQ-007 | Email-409 "Go to Login" live on native, unreachable on web. |
| AMBIGUITY | REQ-026 | selectBank legacy screen still routable though superseded. |
| AMBIGUITY | REQ-028, REQ-029 | Native TeamCreation adds Tag + dynamic UID rows; TeamCreationDone uses SuccessAnim (web: HTML replica only). |
| AMBIGUITY | REQ-130, REQ-165 | adminDashboard key routed to adminPanel flow on native vs web desktop console. |
| AMBIGUITY | REQ-134 | Session token in plain DataStore vs EncryptedSharedPreferences alternative (SPEC/00:42). |

## Appendix B — Assumptions made during the rebuild

1. Worklog lines 376–end were treated as the "native-era" owner-requirement pool per the tasking
   (it contains both the frozen web-panel owner decisions that the native app ports 1:1 and the
   native build records themselves).
2. SPEC/ requirements are treated as binding even where the native build intentionally diverged;
   divergences are tagged CANDIDATE-CONFLICT, not silently dropped.
3. Backend-only worklog tasks (e.g. 49-B, 51-a) are cited only where a native-consumed contract
   or user-visible behavior is defined.
4. Rows cited to STATE.md / MISSING-INPUTS.md / FFR / BASELINE / MASTER_RULES carry
   [reconstructed] where those docs are themselves post-rollback reconstructions rather than
   verbatim owner text.
5. The Task-5-era build identity (versionCode 2 / 2.0.0, Midnight Pills dark theme, 154 drawables)
   is assumed to be the audit baseline, since the lost Task 6–11 "Midnight Charcoal" changes are
   provably absent from the current source (STATE.md:26-27, FFR:187-190).
6. REQ numbering restarts at REQ-001; the lost ledger's numbering is not recovered or mapped.
