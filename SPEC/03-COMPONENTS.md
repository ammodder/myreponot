> ⚠️ **STATUS 2026-09-22 (R1 final-audit pass):** the push/security-vault stack this doc may reference — PushManager, AreenaxMessagingService, SecretVault, KeystoreCipher, CryptoStore, IntegrityGuard/Config/LockScreen, TransportPolicy, PinningConfig, AppConfigGate/Store, SupabaseConfigFetcher, ServerSetupScreen, `fcm_*` resources, `app/proguard-dicts/*`, `vault-gen.gradle.kts`, WhatsAppFab — was **REVERTED from the app tree** (owner ordered no Firebase; see audit/03-fixes/FIX_PLAN.md R1). The verifier tools `verify_build_7b.py`, `verify_vault_7a.mjs`, `verify_integrity_7c.mjs`, `verify_config_6a.mjs`, `gen_proguard_dicts.mjs` were removed with it — use `tools/check.sh`, `tools/verify_sweep_7e.mjs`, `tools/verify_visual.mjs`. Treat affected sections as historical.

# 03 — SHARED COMPONENTS (design-system contract for the native module)

> Source: `/home/z/my-project/src/components/shared/*.tsx` + `AppShell.tsx` + the guest-guard
> hook. Each entry documents the **props interface**, **visual design** (token references —
> full values in `04-DESIGN-TOKENS.md`), **behavior**, and **which screens use it**.
> All bottom sheets share one motion language: backdrop fade 0.2s (black/60 unless noted),
> sheet slide-up 0.25s easeOut, drag handle (w-12 h-1.5 rounded-full `bg-outline-variant/50`),
> `rounded-t-[2rem]` (some 24–32px variants noted), `bg-surface-container-lowest`,
> safe-area bottom padding, tap-outside closes (Escape on web).
>
> ⚠️ **Dead-but-documented:** `JoinTeamSheet`, `RoomSheet`, `AmountKeyboardSheet`,
> `AmountKeyboard(+AmountField)`, `SlotPickerSheet` are currently NOT imported by any screen
> (superseded by inline flows). They remain part of the visual language — native may implement
> the live subset and treat these as reference.

---

## AppShell (`src/components/AppShell.tsx`) — the app scaffold
- **Props:** none (reads store).
- **Behavior:** hydration splash (logo 7rem + primary spinner on `bg-areena`); session restore
  (`GET /me` → setUser; auth-screen correction → home); `initPush()` once; online/offline
  listener → `nav(offline)`; `?qr=` / `?n=` capture → pending stores; pending-QR / pending-link
  routing after auth; scroll-to-top per screen; screen transition (fade+slide-x 18px, 0.18s);
  `showNav = user && NAV_SCREENS.has(screen)` → BottomNav; desktop-only backdrop dressing
  (≥768px, irrelevant on native); adminDashboard rendered outside the frame for
  ADMIN_PANEL_ROLES.
- **Native equivalent:** single-Activity Compose host: SplashScreen API, splash-to-login/home
  router, ConnectivityManager gate, deep-link handlers, NavHost with the same transitions.

## 1. AppBar (+ BellButton, BalanceChip, AppBarIconButton)
- **Props:** `{title?, mode?: "page"|"tab" = "page", balance? = false, right?: ReactNode,
  onBack?: () => void, left?: ReactNode}`.
  - `mode="page"`: [◀ back 40px white circle `bg-surface-container-lowest card-shadow` +
    `arrow_back` icon 1.375rem `text-on-surface-variant`] + centered title
    (`headline-lg-mobile` bold, truncate, flex-1) + right actions; `right === null` renders an
    empty 40px spacer (legal pages pre-auth). `onBack` defaults to nav-stack goBack.
  - `mode="tab"`: left slot (title or custom node, `headline-lg-mobile` bold) + right cluster
    (`ml-auto`).
  - `balance` → renders **BalanceChip** before the right node.
- **BellButton** `{size?: "md"|"sm"}`: 40px (sm 36px) white circle, outlined `notifications`
  icon 22px, red unread badge (min-w 18px, h 18px, `bg-error`, `text-on-error` 10px bold,
  "9+" cap, 2px white ring) hidden when unread=0; self-fetches `/notifications?countOnly=1`
  with the 30s module throttle; tap → `nav(notifications)`.
- **BalanceChip:** pill `bg-balance-chip` + `border-outline-variant/40` + card-shadow, px-3
  py-1.5 rounded-full, text `Rs «formatMoney(balance)»` (text-primary sm semibold); tap →
  `nav(wallet)`.
- **AppBarIconButton** `{icon, label, onClick}`: 40px white circle + any Material symbol.
- **Used by:** every screen except Chat input-only bits; SupportFab pages listed below.
- Header height h-16 (64px), px-4, sticky top, transparent bg.

## 2. BottomNav
- **Props:** none (store-driven).
- **Tabs:** Tournament (`emoji_events`, screen `myTournament`), Friends (`group`, `friends`),
  Home (`home`), Wallet (`account_balance_wallet`, `wallet`), Profile (`person`, `profile`) —
  ORDER: myTournament, friends, home, wallet, profile.
- **Visual:** fixed bottom, centered to the 480px shell, `rounded-t-[1.5rem]`, `nav-shadow`,
  `bg-surface-container-lowest` (dark: top hairline `outline-variant`), px-2 py-3, icons
  1.5rem **FILL 1**; active `text-primary`, inactive `text-on-surface-variant`; **no labels, no
  dots**; tap → `navigate(screen)` (no-op when already active).
- **Used by:** AppShell only (6 root screens).

## 3. EmptyState
- **Props:** `{message: string, icon? = "trophy"}`.
- **Visual:** centered column (`-mt-20`, min-h 300px): 80px circle `bg-surface-container-low`
  with the outlined Material symbol 4xl `text-primary` (FILL 0, wght 400) + message
  (`text-on-surface-variant` base font-medium, center).
- **Used by:** Tournaments (error), MyTournament (error), Results, Wallet, Friends, MyTeam,
  TeamCreationDone, Notifications, TournamentDetails/HostDetails (not-found), AdminPanelScreen
  (denied/error), SelectBankSheet/BankSelectSheet ("No banks found").

## 4. StatusChip
- **Props:** `{status: string, className?}`.
- **Visual:** pill rounded-full px-2.5 py-0.5 `text-label-sm` font-medium + 0.875rem icon —
  PENDING: `bg-surface-container-high` `text-on-surface-variant`, icon `schedule`, label
  **"Pending"**; REJECTED/FAILED: `bg-error/10` `text-error`, icon `close`, **"Rejected"**;
  else: `bg-secondary/15` `text-secondary`, icon `check`, **"Completed"**.
- **Used by:** DepositSuccess, WithdrawSuccess (PENDING, enlarged via className).

## 5. SuccessAnim
- **Props:** `{title: string, subtitle?: string, children?}`.
- **Visual:** glowing check badge — radial primary glow (112px blur), sparkle dots, tint ring
  (`bg-primary/15 border-primary/20` p-4 rounded-full) around a 48px `bg-primary` circle with
  white filled `check`; title 26px bold (`tracking-tight`); subtitle 14px muted max-w-[290px].
- **Used by:** DepositSuccess, WithdrawSuccess, TransferSuccess (+ children slots for chips).

## 6. SupportFab
- **Props:** `{offset?: boolean = true}` (true → `bottom-24` above nav; false → `bottom-6`).
- **Visual:** fixed right `max(16px, calc(50%-224px))`, 56px `bg-primary` circle, white
  `support_agent` icon 3rem, `shadow-fab`, scale press.
- **Behavior:** reads cached settings → opens `https://wa.me/<digits>`; no WhatsApp number →
  `nav(about)`.
- **Used by:** Home, Tournaments, Wallet, Profile, TournamentDetails (offset=false),
  (per worklog also TournamentDetails only on sub-pages). NOT on: auth, chat, admin.

## 7. WhatsAppFab — DEPRECATED
- **Props:** `{offset? = false, href? = "#"}`. Green circle with inline WhatsApp SVG.
- **Not used by any screen anymore** (replaced by SupportFab). Native: skip.

## 8. AccountRequiredDialog (guest gate)
- **Props:** `{open, onOpenChange, feature?}` — rendered via `useRequireAccount()` which returns
  `{requireAccount(featureName?): boolean, guestDialog: element}`.
- **Visual:** centered modal (black/60 backdrop, scale-in), white card `rounded-[1.75rem]`
  max-w-[320px] p-6 center: 64px `bg-primary-fixed` circle with `account_circle` 2rem
  text-primary, title **"Account Required"** (headline-md bold), "You're exploring AREENAX as a
  guest." then "Create a free account to «feature»." (body-md muted), primary pill
  **"Create Account"** → close + `nav(signup)`, text button **"Continue as Guest"** → dismiss.
- **Used by:** every guest-gated screen (wallet trio, deposit confirm, tasks, team, bind,
  edit profile, friends, QrSheet, tournament details join).

## 9. InsufficientBalanceDialog
- **Props:** `{open, onOpenChange, entryFee, balance, tournamentName?}`.
- **Visual:** centered modal like the guest dialog; 64px `bg-error-container` circle with
  `account_balance_wallet`; title **"Insufficient Balance"**; lines: "You need Rs «entryFee» to
  join «tournamentName|this tournament»." / "Your balance: **Rs «balance»**" / when shortfall>0:
  "Deposit at least **Rs «shortfall»** to continue." (shortfall text-primary); primary
  **"Deposit Now"** → close + `nav(deposit)`; text **"Not Now"** → dismiss.
- **Used by:** TournamentDetailsScreen (on join 409/insufficient). (JoinTeamSheet references an
  older prop shape — dead code.)

## 10. AmountPad
- **Props:** `{onKey(key: "1".."9"|"0"|"."|"backspace"), className?}`.
- **Visual:** 3-col grid gap-3 max-w-[320px]; keys h-16 rounded-2xl
  `bg-surface-container-lowest` border `outline-variant/30` + subtle shadow; digits
  `headline-lg`; backspace = Material icon `backspace` muted; key order 1-9, ".", 0, backspace.
- **Used by:** DepositScreen, WithdrawScreen, TransferScreen (always visible), AmountKeyboard
  (dead).

## 11. AmountKeyboard (+ AmountField) — DEAD (reference)
- Slide-up keypad panel docked bottom (rounded-t-[28px], handle + label + `keyboard_hide`
  button, invisible outside-tap catcher, no dim). AmountField = tappable big figure w/ active
  border + cursor. Not wired to screens.

## 12. AmountKeyboardSheet — DEAD (reference)
- Same pad inside a dimmed (black/60) bottom sheet, rounded-t-[24px], explicit **"Done"** button.

## 13. BankSelectSheet
- **Props:** `{open, onClose, title, searchPlaceholder? = "Search bank", banks?: string[],
  accounts?: BankAccount[], accountsLabel? = "Your Accounts", showBanks? = true,
  showAccounts? = true, selectedId: string|null ("bank:<name>"|"acc:<id>"),
  onSelect(id, {bankName, account?}), onConfirm(), confirmLabel? = "Confirm",
  emptyAction?: {icon, title, description, buttonLabel, onClick}}`.
- **Visual:** sheet rounded-t-[2rem] max-h-[85vh]; header title centered (headline-md) + search
  pill (h-12 rounded-full `bg-surface-container-lavender`, `search` icon); rows = 48px rounded-xl
  icon tile (`account_balance` for banks / `credit_card` for accounts, tile
  `bg-surface-container-lowest border outline-variant/30`, FILLED icon when active) + name +
  subtitle ("«accountTitle» • «maskAccountNumber(accountNumber)»") + green check circle
  (`bg-secondary text-on-secondary`, 24px, active only); sticky Confirm footer (h-[3.5rem]
  primary pill, disabled until selection). Empty-with-action → dashed card (icon, title,
  description, primary button w/ icon). Filter-miss → EmptyState "No banks found".
- **Used by:** DepositScreen ("Choose Funding Bank" — banks + accounts), WithdrawScreen
  ("Choose Withdrawal Account" — accounts only + Bind Account emptyAction), BindAccountScreen
  ("Select Bank" — banks only).

## 14. DateTimePickerModal
- **Props:** `{open, value: string (ISO, "" when unset), onClose, onChange(iso)}`.
- **Visual (replica of hosttournamentcreation.html picker):** centered dialog rounded-[24px];
  header `bg-primary-container text-on-primary` p-5 — "Select Date & Time" eyebrow + close X,
  month-year (headline-lg bold) + chevron_left/chevron_right month nav (clamped); calendar grid
  on `bg-surface-container-lowest` — weekday header S M T W T F S, 40px day cells (selected =
  `bg-primary-container text-on-primary` bold); time section on `bg-surface-container-low` —
  "Time" label, HH:MM numeric inputs (h-12 rounded-xl w-20, 12-hour clamp 1-12 / 0-59) +
  AM/PM segmented toggle (active `bg-primary-container`); footer Cancel (outlined) +
  **"Set Date & Time"** (`bg-primary-container text-on-primary`) → emits ISO.
- **Used by:** HostCreationScreen.

## 15. TournamentCard
- **Props:** `{tournament: Tournament, onClick?, joinLabel? = "JOIN NOW", joined? = false,
  onAction?: (action: TournamentCardAction) => void}` — action kinds: join / slots-full /
  joined / room / live / proof / completed / results (state machine =
  `lib/tournament-state.ts computeTournamentAction`).
- **Visual:** article `bg-surface-container-lowest` rounded-[1.5rem] border
  `outline-variant/20`, press scale .99:
  1. Banner h-48 (bannerImage ?? game.image) + bottom black/70 gradient + top-right mode chip
     (`bg-primary text-on-primary` rounded-full px-4 py-1.5 12px bold) + bottom-left
     `schedule` + formatDateTime (white 13px semibold).
  2. Header row: 40px initial circle (host gameName) + name (bold 16px) + "Tournament Lead"
     (11px uppercase) | right: "Tournament Status" (11px) + statusLabel ("Open"/"Live"/status,
     text-primary bold 16px).
  3. Stats grid (borders top/bottom): Total Prize (only when >0, text-primary) / Entry Fee
     (always, spans 2 when no prize).
  4. Empty 2-col grid (HTML artifact, 16px spacing).
  5. "Filled Slots" + "cur / max Players" (text-primary bold) + h-2 progress
     (`bg-primary/20` light, `bg-primary` dark).
  6. Bottom action h-14 rounded-full 16px bold uppercase: JOIN NOW (primary + `chevron_right`) /
     JOINED chip (balance-chip + check_circle) / ROOM ID & PASSWORD (primary + `key`) / RESULTS
     (primary + `emoji_events`) / SUBMIT RESULT PROOF (primary + `upload`) or "Result Submitted"
     chip / MATCH PROCESSING chip (balance-chip + red ping dot) / SLOTS FULL (surface-high +
     `groups`) / CANCELLED/COMPLETED (surface + `hourglass_top`). Room/results/proof buttons
     stopPropagation → onAction.
- **Used by:** TournamentsScreen, MyTournamentScreen (+ HostTournamentCard has a local replica).

## 16. RoomInfoSheet (LIVE room popup)
- **Props:** `{open, onClose, tournamentId: string|null, tournamentName?}`.
- **Behavior:** fetches `GET /tournaments/:id` fresh on every open (credentials only returned
  for joined users); refresh button re-fetches (epoch). States: spinner; failure
  "Could not load room details. Please try again."; no credentials → `schedule` icon +
  "Room details not shared yet" + "The admin will share the Room ID & Password here before the
  match starts."; success → two rows (`bg-surface-container-lavender`, label-md muted label +
  bold break-all value + copy circle `bg-primary/10 text-primary`) → toasts "Room ID copied" /
  "Password copied"; expiry footer "Visible until «formatDateTime»" (`timer_off`).
- **Used by:** TournamentsScreen, MyTournamentScreen.

## 17. RoomSheet — DEAD (reference)
- Props `{open, onClose, tournamentName, mode?, roomId, roomPassword, visibleUntil, onExpired?}`.
- Live 1s countdown chip "Visible for «m:ss»", expired state ("Room details expired"/"The
  visibility window has ended. Check the Results instead.") → onExpired() callback. Copy toasts
  "«label» copied to clipboard".

## 18. JoinTeamSheet — DEAD (reference; the documented team-join contract)
- Props `{open, onClose, tournament, onJoined}`. 4 steps (choose → manual/team → confirm) with
  `modeTeamSize()` (duo/2v2=2, squad/4v4=4); manual UIDs verified live via `GET /players/lookup`
  (debounced 450ms; rejects own UID + duplicates); team path picks teammates from `GET /team`;
  confirm shows roster + fee breakdown (entryFee × size) and polls balance every 4s;
  join = `POST /tournaments/:id/join {members:[{uid}]}`; insufficient → dialog. Not currently
  mounted by any screen (the details screen joins solo; 48-slot and 1v1/2v2/4v4 joins would use
  SlotPickerSheet/TeamJoinSheet semantics).

## 19. TeamJoinSheet
- **Props:** `{open, onClose, mode: string ("1v1"|"2v2"|"4v4"), playersPerSide: 1|2|4, entryFee,
  balance, joining, onConfirm({useTeam?: boolean, memberUids?: string[]})}`.
- **Behavior:** joiner becomes CAPTAIN and pays entryFee × playersPerSide; 1v1 → confirm only;
  2v2/4v4 → option buttons "Use Your Team" (`diversity_3`) / "Enter Manually" (`edit_note`);
  fetches `GET /team` on open; team preview validates size ≥ playersPerSide (chip
  "«count»/«playersPerSide» players", error "This format needs N players — your team has M.
  Enter members manually instead."); manual = (playersPerSide−1) numeric UID fields (all
  required — "Player «i+2» UID is required."); footer shows "Entry fee Rs «fee» × N players",
  total (red when insufficient), "Your balance", warning "Insufficient balance — deposit first
  to join with your side."; confirm disabled while insufficient; button labels
  "Join — Rs «total»" (solo) / "Pay Rs «total» & Join".
- **Used by:** none currently mounted (Team Battle join path) — implement native-side with the
  TournamentDetails join flow.

## 20. SlotPickerSheet — DEAD (reference)
- Props `{open, onClose, totalSlots, freeSlotNumber|null, takenSlots: number[], entryFee,
  joining, onConfirm(slotNumber)}`. 6-per-row slot grid (h-11 rounded-xl; taken = disabled +
  check; free = secondary tint + "Free" micro-label; selected = primary + scale 1.05); footer
  "Slot #n selected" / "Tap a slot to select" + price or "Join Free"; confirm
  "Select a Slot" → "Join — Rs. «fee»" / "Join Free".

## 21. QrSheet (THE QR modal)
- **Props:** `{open, onClose, target: "user"|"team", teamId?, teamName?, initialTab? =
  "mine"|"scan"}`. Exported sub-components: `UserResultCard {data: QrUserLookup, busy,
  onAddFriend, onMessage}`, `TeamResultCard {data: QrTeamLookup, busy, onJoin}`.
- **Tabs:** segmented pill (My QR / Scan QR; active = white pill text-primary).
- **Mine tab:** QR image 176px (generated locally: deep link `«base»/?qr=u%3A<uid>` or
  `t%3A<teamId>`, 512px, margin 2, dark #0b1c30 on white; native: ZXing) in a bordered rounded
  tile; user: gameName + "UID: «uid»" + "Scan to add me as a friend"; team: teamName +
  "Scan to view & join my team"; outlined **Share** button (navigator.share text:
  "Add me on AREENAX! Scan my QR or use my UID «uid»." / "Join my team «name» on AREENAX! Scan
  this QR." → clipboard fallback "Copied!"); info row ("Anyone who scans this QR will be taken
  straight to your profile to add you." / "...straight to your team to send a join request.").
- **Scan tab:** square viewfinder (camera; native ML Kit/ZXing) with QR watermark; hint pill
  "Align the QR code within the frame"; camera-error state (`no_photography`,
  "Camera not available" + "You can still enter a Player UID manually below."); resolving
  spinner "Looking up…"; decoded → parse (accepts `u:/user:/t:/team:` payloads and full
  `?qr=` deep links) → `GET /qr/lookup` → result card + "Scan something else" link;
  invalid → toast "Invalid QR code"/"This is not an AREENAX QR code."; 404 → "No player/team
  found for this code."
  - **UserResultCard:** initial circle, gameName, "UID:", fullName; relation actions: self →
    info chip "This is your own QR code"; friend → disabled "Friends ✓" + **Message** →
    `nav(chat,{friendId})`; request_sent → disabled "Request Sent"; else primary **Add Friend** /
    **Accept Request** (guest gate "add friends") → `POST /friends/requests {uid, source:"QR"}`
    → toasts "You are now friends!" / "Friend request sent!".
  - **TeamResultCard:** rounded initial tile, UPPERCASE name + tag chip, "Owner: «gameName» •
    UID «uid»", "«memberCount»/«maxMembers» Members"; isMember → "You're in this team" chip;
    joinRequested → disabled "Request Sent"; else primary **Request to Join** (guest gate
    "join a team") → `POST /team/join-requests {teamId}` → "Join request sent to the team
    owner!".
  - Manual fallback (hidden while a result card shows): "Or enter a Player UID" + numeric field
    ("e.g. 782104") + primary **Find** (`search` icon) → treats input as `u:<uid>`.
- **Used by:** ProfileScreen (user, scan), FriendsScreen (user, scan), MyTeamScreen (team, mine).

## 22. ResultProofSheet — reference (details screen implements inline)
- **Props:** `{open, onClose, tournamentId, submittedAt?, onUploaded}`.
- Gallery picker (accept image/*, ≤4MB → data URL) with preview card ("Screenshot ready to
  upload" + Remove), submitted banner ("**Proof submitted** · «datetime» — upload a new
  screenshot to replace it."), dashed "Choose from Gallery" tile ("PNG or JPG screenshot · max
  4MB"), submit `POST /tournaments/:id/result-proof {image}` → toasts "Result proof submitted"/
  "The admin can now see your screenshot in the Admin Panel." (update: "Result proof updated");
  button labels "Upload Screenshot" / "Update Screenshot" / "Uploading…".

---

## Icon-font note (Material Symbols)
The web uses the **Material Symbols** variable font in two variants — `material-symbols-outlined`
(default FILL 0) and `material-symbols-rounded` (FILL 1) — with per-instance
`fontVariationSettings` overrides (`'FILL' 1` etc.). Icon names appearing in the panel
(non-exhaustive): arrow_back, arrow_forward, arrow_drop_down, chevron_left, chevron_right,
expand_more, expand_less, close, check, check_circle, cancel, radio_button_unchecked,
notifications, schedule, timer, timer_off, lock, lock_open, key, content_copy, share, delete,
refresh, search, info, error, warning, add, remove, visibility, visibility_off, person, group,
groups, group_add, person_add, person_search, diversity_3, emoji_events, trophy, leaderboard,
bar_chart, my_location, military_tech, payments, account_balance, account_balance_wallet,
credit_card, wallet, outbox, sync_alt, add_card, receipt_long, task_alt, card_giftcard,
sports_esports, tournament→tune, assignment, checklist, dark_mode, help, gavel, shield, logout,
admin_panel_settings, qr_code_2, qr_code_scanner→(scan tab uses qr_code_2), no_photography,
add_photo_alternate, cloud_upload, upload, screenshot_monitor, how_to_reg, star, crown, bolt,
done, done_all, chat_bubble, forum, wifi_off, send, sentiment_satisfied, support_agent,
account_circle, hourglass_top, payment_arrow_down, stadium, grid_view, image, broken_image,
balance, keyboard, keyboard_hide, touch_app, download, calendar_month, description, edit,
calculate, toggle_on, toggle_off, arrow_downward, arrow_upward, swap_horiz, expand_more.
Native: bundle Material Symbols Outlined+Rounded variable fonts (same names work) or map to
Material Icons equivalents; honor FILL for the listed filled instances.

## Shared-component usage matrix

| Component | Screens |
|---|---|
| AppBar (+Bell/Balance/Icon buttons) | all screens (mode/props vary per 01-SCREENS.md) |
| BottomNav | AppShell: home, tournaments, myTournament, friends, wallet, profile |
| EmptyState | tournaments, myTournament, results, wallet, friends, myTeam, teamCreationDone, notifications, tournamentDetails, hostDetails, adminPanel, bank sheets |
| StatusChip | depositSuccess, withdrawSuccess |
| SuccessAnim | depositSuccess, withdrawSuccess, transferSuccess |
| SupportFab | home, tournaments, wallet, profile, tournamentDetails |
| AccountRequiredDialog | all guest-gated flows (via useRequireAccount) |
| InsufficientBalanceDialog | tournamentDetails |
| AmountPad | deposit, withdraw, transfer |
| BankSelectSheet | deposit, withdraw, bindAccount |
| DateTimePickerModal | hostTournamentCreation |
| TournamentCard | tournaments, myTournament (HostCards local replica) |
| RoomInfoSheet | tournaments, myTournament |
| QrSheet (+User/TeamResultCard) | profile, friends, myTeam |
| TeamJoinSheet / JoinTeamSheet / SlotPickerSheet / RoomSheet / AmountKeyboard* | not mounted (reference contracts) |
