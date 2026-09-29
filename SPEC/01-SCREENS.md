# 01 — SCREENS (the core build spec)

> One section per screen file under `/home/z/my-project/src/components/screens/**`.
> For every screen: **screen key** (from the `Screen` union in `src/lib/types.ts`), **design
> source** (HTML in `upload/pages_extracted/pages/`), **nav params in/out**, **API calls**
> (method + path + payload + fields consumed — shapes confirmed against the route files),
> **UI structure**, **states**, **interactions**, **shared components**.
> Pixel truth = the HTML file; behavior truth = the TSX. Copy strings exactly as quoted.
>
> Legend: `nav(x)` = `navigate(x)`, `repl(x)` = `replace(x)` (clears stack), `back()` = pop.

---

# A. AUTH (6 files)

## A0. `auth/AuthChrome.tsx` — shared auth primitives (NOT a screen; no screen key)
- **Design source:** none (extracted helper). **Currently imported by NO other file** — kept as
  the auth visual language reference. Native may ignore or adopt its primitives.
- Exports: `AUTH_LOGO_URL` (Google-hosted brand PNG), `AuthBackdrop` (deep-navy→blue aurora:
  radial `rgba(59,130,246,.40)` @50%/58% over linear `#030b1d→#051740→#0a2f7a→#04102e→#020817`),
  `AuthBrand` (logo h-11 + "AREENA X" 1.5rem bold white), `AuthMethodPills` (Email|Phone toggle
  pills: active = `bg-white/[0.16] border-white/65` + glow shadow; inactive `bg-white/[0.06]
  border-white/15 text-white/70`; icons mail/call), `AuthDivider` ("Or" hairline divider
  `text-white/55`), `authInputCls` (h-12 rounded-full `bg-white/[0.07]` border-white/15
  focus `#7db5ff`), `AuthGradientButton` (h-[3.25rem] rounded-full gradient `#5d9bf5→#7db5ff→#9be0ff`,
  text `#071633`, glow shadow), `AuthFooterLinks` ("Terms of Service | Privacy Policy" white/60
  underlined → nav(terms)/nav(privacy)).

## A1. `auth/LoginScreen.tsx` — key `login` — HTML: `index.html`
- **Nav params in:** none. Reads `sessionStorage["areena-pending-qr"]` for the pending-QR banner.
- **Navigates:** setAuth(token,user) → auto `home` (stack cleared). "Create Account" → `nav(signup)`.
- **API:** `POST /auth/login` body `{email?, password}` OR `{phone?, password}` → `{token, user}`.
  Identifier auto-detection: value is a **phone** when digits-only after stripping `[\s\-().]`
  matches `^\+?[0-9]{7,15}$`; for phones it first tries `{phone: digitsWithoutPlus}`, and on
  failure retries with `{phone: valueAsTyped}`; else `{email: value}`.
  Errors → toast destructive "Login failed" + server message.
- **UI (top→bottom, lavender light theme `bg-surface-lavender`):** centered brand logo (h-5rem,
  mt-6) → PendingQrBanner (when a QR was scanned pre-auth) → `Welcome back` (display-lg, center) →
  "Please enter required details." (body-md, on-surface-variant) → label "Email or Phone" +
  rounded-full input (h-12 `bg-surface-container-lavender`, placeholder "Enter your email or phone
  number", autoComplete username) → label "Password" + input with visibility toggle button
  (icons `visibility`/`visibility_off`, filled when visible) → optional red hint line → primary
  button (h-14 rounded-full `bg-primary text-on-primary`, label **"Login"**, spinner + "Logging In"
  while busy) → "or" divider → outlined pill button **"Continue as Guest"** (transparent,
  border-outline, spinner while pending) → "Don't have an account?" (body-lg) + text button
  **"Create Account"** (text-primary). Empty mt-[3rem] slot (the old demo-hint spot).
- **States:** `loggingIn` (button spinner), `guestIn`, inline `hint` errors:
  "Please enter your email or phone number." / "Please enter your password."
- **Interactions:** submit on form; Enter submits; visibility toggle; guest button.
- **Shared:** none (self-contained; the pending-QR banner is a local component — duplicated in
  SignupScreen).

## A2. `auth/SignupScreen.tsx` — key `signup` — HTML: `signup.html`
- **Nav params in:** none. **Out:** `nav(signupStep1, {fullName, gameName})`.
- **API:** `POST /auth/check` body `{gameName}` → `{taken: {gameName?: bool}, messages: {gameName?: string}}`.
  If `taken.gameName` → inline error (server message or "This in-game name is already taken."),
  stays. Check failure is best-effort → navigates anyway (register 409 is the hard stop).
- **UI:** bg `bg-surface-lavender`, main card `bg-surface-container-lowest`; header = brand logo
  (h-5rem) → PendingQrBanner → headings "Create your account" (display-lg) + "Start your journey
  today" (body-lg) → form: label "Full Name" + rounded-full input (placeholder "Enter your full
  name"), label "Game Name" + input ("Enter your game name") + inline `gameNameError`, red `hint`
  → CTA (h-[3.5rem] rounded-full primary, **"Continue"**, spinner + "Checking..." while checking)
  → footer "Already have an account? **Log in**" → `nav(login)`.
- **States:** `hint` = "Please enter your full name and game name." when either empty.
- **Shared:** none.

## A3. `auth/SignupStep1Screen.tsx` — key `signupStep1` — HTML: `signupstep1.html`
- **Params in:** `{fullName?, gameName?}`. **Out:** `nav(signupStep2, {fullName, gameName, phone, gameUid})`; header back → `back()`.
- **API:** `POST /auth/check` body `{phone, gameUid}` → taken/messages for `phone` & `gameUid`
  (defaults: "This phone number is already registered." / "This game UID is already registered.").
- **UI:** small logo (h-8) → back arrow + progress bar (h-1, fill `bg-primary` **1/3**) →
  "Step 1 of 3" (body-md, muted) + "Let's set up your access" (1.25rem semibold) →
  label "Phone Number" + phone row: rounded-full container with static country chip
  (🇵🇰 +92 + `arrow_drop_down`, right hairline) + tel input (placeholder "300 1234567") →
  label "Account UID" + numeric-only input ("Enter your game UID", digits stripped on change) →
  CTA **"Continue"** (spinner "Checking...").
- **States:** `hint` "Please enter your phone number and game UID."; per-field errors under inputs.
- **Shared:** none.

## A4. `auth/SignupStep2Screen.tsx` — key `signupStep2` — HTML: `signupstep2.html`
- **Params in:** `{fullName, gameName, phone, gameUid}`. **Out:** `nav(signupStep3, {..., password})`; back → `back()`.
- **API:** none (client validation only).
- **UI:** progress **2/3** → "Step 2 of 3" + "Create a password" → label "Password" + input
  ("Enter a secure password") with eye toggle → checklist header "Your password must contain at
  least" → 3 live items: "1 letter" (`/[A-Za-z]/`), "1 number or special character (example: #
  ? ! &)" (`/\d|[^A-Za-z0-9\s]/`), "10 characters" — satisfied = `check_circle` + `text-secondary`,
  unsatisfied = `radio_button_unchecked` + muted → CTA **"Continue"**.
- **States:** hint "Your password does not meet all the requirements yet." when `!allValid`.

## A5. `auth/SignupStep3Screen.tsx` — key `signupStep3` — HTML: `signupstep3.html`
- **Params in:** `{fullName, gameName, phone, gameUid, password}`. **Out:** setAuth → home; back → `back()`.
- **API:**
  1. `POST /auth/check` `{email}` (best-effort) → if `taken.email` inline error + toast
     "Email already registered", stay.
  2. `POST /auth/register` body `{fullName, gameName, phone, gameUid, password, email,
     referralCode?}` → `{token, user}` → success toast "Account created" / "Welcome to AREENAX!
     Your journey starts now." → setAuth.
     409 → toast "Already registered" + server message; if field=email → inline emailError +
     show **"Go to Login"** outlined pill → `nav(login)`; other fields → inline hint.
     Other errors → toast "Registration failed".
- **UI:** progress **full** → "Step 3 of 3" + "Final details" → label "Email Address" + email
  input ("name@domain.com", format regex `^\S+@\S+\.\S+$`) → label "Referral Code (Optional)"
  + input ("Enter code if you have one") → **agreement checkbox** (custom 20px rounded box,
  checked = `bg-primary` + white `check`): text "I agree to the Terms & Conditions and Privacy
  Policy, and I consent to create an AREENAX account." — the two links navigate (stopPropagation)
  to `terms`/`privacy` → CTA **"Complete Account"** disabled until `agree` (spinner + same label
  while submitting).
- **States:** hint "Please agree to the Terms & Conditions and Privacy Policy to continue." /
  "Please enter a valid email address."

---
# B. MAIN (5 files)

## B1. `main/HomeScreen.tsx` — key `home` — HTML: `home.html`
- **Nav params in:** none. **Out:** game card (Games tab) → `nav(tournaments, {gameId})`;
  game card (Host tab) → `nav(hostTournament, {gameId, gameName})`.
- **API:** `GET /bootstrap` → `{games: Game[], banners: Banner[], settings: AppSettings}`.
  Module-level 60s TTL cache (`BOOTSTRAP_TTL_MS = 60000`, errors never cached). Also
  `cacheSettings(data.settings)` into the shared settings cache. Games fall back to `[]`.
- **UI:** `bg-areena` ambient (radial top glow). AppBar **tab mode**: left slot = 40px avatar
  circle (initial of gameName, `bg-primary`) + "Welcome," (xs) / gameName (sm bold); right =
  BalanceChip + BellButton. Content (px-4, pb-32):
  1. **Hero banner** — `aspect-[2/1]` rounded-[1.5rem]; first banner with an image (`banners.find(b=>b.image)`)
     as cover, else black gradient placeholder with "AREENAX" + 9-dot grid; 3 pagination dots
     (static, only when `banners.length > 1`).
  2. **Tab switcher** — white pill container (`bg-surface-container-lowest` p-1 rounded-full
     border #e2e2ec): two 50% buttons **"Games"** / **"Host"**; active = `bg-primary text-on-primary`
     (dark: white pill/black text).
  3. **Games grid** — 2 columns, gap-4; each card: 4:3 image (object-cover, top gradient
     overlay), white footer bar with centered bold game name. Tap → tournaments for that game.
  4. **Host tab** — same grid; tap → host flow for that game.
- **States:** loading = centered spinner (w-8 border-2 primary). No dedicated empty state
  (renders empty grid).
- **Interactions:** tab switch (fade-in animation), card taps.
- **Shared:** AppBar (tab, balance, left slot), BellButton, SupportFab (rendered by screen).

## B2. `main/TournamentsScreen.tsx` — key `tournaments` — HTML: `tournament.html`
- **Params in:** `{gameId?}` (filter list per game). **Out:** card tap → `nav(tournamentDetails,
  {tournamentId})`; card results action → `nav(results, {tournamentId})`; room action → opens
  RoomInfoSheet; proof action → `nav(tournamentDetails, {tournamentId})`.
- **API:** `GET /tournaments?status=<UPCOMING|ONGOING|COMPLETED>[&gameId=<id>]` →
  `{tournaments: Tournament & {joined?: boolean}[]}` — refetched on every tab/gameId change.
- **UI:** AppBar tab mode title "Tournaments" + BalanceChip + bell. Main (px-4 pb-24):
  segmented status tabs in a `bg-balance-chip` pill container — **Upcoming / Ongoing / Completed**
  (active = white pill `glass-tab` `text-primary`); list of `TournamentCard` (space-y-4, memoized
  rows).
- **States:** loading spinner; error → EmptyState "Failed to load tournaments." (icon `error`);
  empty → exact HTML empty state: 80px `bg-balance-chip` circle + `trophy` icon (outlined,
  text-primary 4xl) + per-tab text **"No upcoming tournaments."** / **"No ongoing tournaments."** /
  **"No completed tournaments."**.
- **Shared:** AppBar, TournamentCard (via memo row), RoomInfoSheet, SupportFab, EmptyState.

## B3. `main/TournamentDetailsScreen.tsx` — key `tournamentDetails` — HTML: `tournamentdetails.html`
- **Params in:** `{tournamentId}` (required; missing → failed state). **Out:** results action →
  `nav(results, {tournamentId})`; insufficient-balance dialog "Deposit Now" → `nav(deposit)`.
- **API:**
  - `GET /tournaments/:id` → `{tournament}` incl. `entries[]` (user summary each), `myEntry`,
    `joined`, `hasRoom`, `proofActive`, `myProofSubmitted`, `myProofImage`, `host`, `game`,
    room fields (only when joined). Refetch on `refreshKey` change (after join/proof).
  - Join: `POST /tournaments/:id/join` (no body for solo) → `{entry}`; success toast
    "Joined successfully!" / "Room ID & password will appear before the match starts."; local
    balance decrement; refresh. Error containing "insufficient" (case-insensitive) →
    InsufficientBalanceDialog; else toast "Join failed".
  - Result proof: hidden `<input accept="image/*">` → image ≤ **8 MB** → data URL →
    `POST /tournaments/:id/proof` body `{image}` → 409 "already submitted" → lock picker +
    toast "Result Submitted" / "You have already submitted your result for this tournament.";
    success toast "Result Submitted" / "Wait for the admin's approval."
- **UI:** AppBar page mode title "Tournament Details", right = share icon button (AppBarIconButton
  `share` → navigator.share of "«name» on AREENAX" + prize/fee lines + room lines when room open
  and joined; clipboard fallback toast "Tournament details copied to clipboard"). Body (px-4 py-6 pb-28):
  1. **Hero** h-[12.5rem] rounded-[1.5rem], cover image (bannerImage ?? game.image) else dark
     gradient + `stadium` icon; top-left white pill: `schedule` icon + "Registration ends in
     **<countdown>**" (countdown `text-primary` bold) / "Match in progress" / "Match completed".
  2. **Summary cards** 1–3 cols (cells adapt; never render "Rs 0" cells): Prize Pool (`emoji_events`
     filled, only when >0), Entry Fee (`payments`, always), Per Kill (`my_location`, only when >0).
     Card: `bg-surface-container-lowest` rounded-xl border, icon in `bg-primary/10` circle,
     label-md muted + headline-md bold value.
  3. **Tabs** underline style: **Overview / Rules / Prizes** (active `border-b-2 border-primary text-primary`).
  4. **Overview** stat rows (h-[4rem], border-b #edf2f7, icon + label left, value right):
     Mode (`person`), Map (`map`, when set), Players (`group`) with w-24 progress bar +
     "cur/max", Loser Prize (`military_tech`, >0), Perspective (`visibility`, when set), **ID &
     Pass** (`lock`) — joined + room open: "View"/"Hide" toggle revealing Room ID + Room Password
     rows with copy buttons (`content_copy`, toast "Room ID copied to clipboard"); else locked icon.
  5. **Rules** — "Tournament Rules" + copy button (outlined, copies full rules, toast "Rules
     copied to clipboard"); rules split on `\n` → numbered list ("01", "02"… text-primary);
     empty → "No rules provided for this tournament."
  6. **Prizes** — "Prize Distribution": when prizePool>0 rows Rank 1 = 50% ("Winner Trophy",
     trophy icon), Rank 2 = 30%, Rank 3 = 20% (`rankPrize = round(prizePool*pct/100)`); else
     muted row "Prize pool will be announced". Per Kill / Loser Prize rows appended when >0.
  7. **Participants** card — header "Participants" + "«n» / «max»"; rows = initial circle +
     gameName + "UID: «uid»"; empty → "No participants yet. Be the first to join!"
  8. **Floating bottom bar** (fixed, gradient from surface-container-lowest): joined users get a
     white card showing the per-state machine widget (Room ID/Password + copy, or "Room ID &
     Password will be announced before the match starts.", or Results button → results screen,
     or "Result Submitted" chip + "Wait for approval — the admin will review your result", or
     "Match Processing" chip with red ping dot, or "Joined" chip, or CANCELLED/COMPLETED chip).
     Not joined: primary button **"Join Now - Rs «fee»"** / **"Join Now - Free"** (guest-gated via
     `requireAccount("join this tournament")`); slots-full → "Slots Full" chip (`groups` icon);
     completed → label chip.
- **States:** loading = full-screen spinner on `bg-surface-container-low`; failed → AppBar +
  EmptyState "Tournament not found." (icon `error`) + SupportFab.
- **Shared:** AppBar, AppBarIconButton, EmptyState, InsufficientBalanceDialog
  (`open/onOpenChange/entryFee/balance/tournamentName`), SupportFab (offset=false), useRequireAccount,
  tournament-state helpers (`computeTournamentAction`, `isRoomOpen`, `countdownText`).

## B4. `main/MyTournamentScreen.tsx` — key `myTournament` (bottom-nav "Tournament" tab) — HTML: `mytournament.html`
- **Params in:** none (root tab). **Out:** entry tap → COMPLETED ? `nav(results, {tournamentId})`
  : `nav(tournamentDetails, {tournamentId})`; room action → RoomInfoSheet; results action →
  `nav(results)`; proof action → `nav(tournamentDetails)`.
- **API:** `GET /my/tournaments` → `{entries: TournamentEntry[]}` each with embedded live-status
  `tournament` (+ proof fields). **Polls every 30000 ms**; errors only surface when nothing was
  ever loaded. **Guests/logged-out skip the fetch entirely** (render empty state, no 401 risk).
- **UI:** AppBar tab "My Tournaments" + BalanceChip + bell. Filter chips (bg-balance-chip pill):
  **Upcoming / Ongoing / Completed** — client-side filter by `deriveTournamentStatus(tournament)`.
  Cards: `TournamentCard` with `joined=true`, normalized to live status, joinLabel
  "VIEW RESULTS" (completed) else "VIEW DETAILS".
- **States:** loading spinner; error EmptyState "Failed to load tournaments."; empty → 80px
  `bg-surface-container-low` circle (border primary-fixed) + `emoji_events` icon + text:
  list empty ⇒ **"You have not joined any tournaments yet."**, else per-filter "No upcoming/ongoing/
  completed tournaments." + (except Completed tab) primary button **"Browse Games"** (w max-15rem)
  → `nav(home)`.
- **Shared:** AppBar, TournamentCard, RoomInfoSheet, EmptyState.

## B5. `main/ResultsScreen.tsx` — key `results` — HTML: `results.html`
- **Params in:** `{tournamentId}`. **Out:** none (back via AppBar).
- **API:** `GET /tournaments/:id/results` → `{tournament (with entries incl. user), stats:
  {totalKills, topKills, totalPrize, players}}`. 403 for non-participants → treated as failure.
- **UI:** AppBar page "Tournament Results" + default bell. Body (px-4 py-6 pb-32):
  1. **Match Statistics** card (`bar_chart` icon title): grid (3 cols when totalPrize>0 else 2)
     with dividers — "Prize Pool" `Rs. «compact»` (compact = Intl compact 1 decimal), "Players",
     "Top Kills" (text-primary).
  2. **Winners** list — sorted by rank (null→999) then kills desc; rows h-16: rank (text-primary
     w-10), 40px avatar (entry.user.avatar image else initial circle), gameName + "«kills» Kills",
     right `Rs. «prize»` only when >0. **isMe row** (`entry.user.id === user.id`) tinted
     `bg-primary-fixed/40`.
- **States:** loading spinner; failed/no data/empty → EmptyState **"Results not published yet."**
  (icon `trophy`) in both the top slot and the winners list.
- **Bottom sticky bar:** primary button **"Share Results"** (`share` icon) → navigator.share of
  "«name» results on AREENAX" (clipboard fallback toast "Results copied to clipboard").
- **Shared:** AppBar, EmptyState.

---

# C. WALLET (10 files)

## C1. `wallet/WalletScreen.tsx` — key `wallet` (bottom-nav tab) — HTML: `wallet.html`
- **Nav:** quick actions — Deposit → guest gate "make a deposit" then `nav(deposit)`;
  Withdraw → gate "withdraw your winnings" → `nav(withdraw)`; Transfer → gate "send money" →
  `nav(transferMoney)`.
- **API:** `GET /wallet` → `{balance: number, transactions: Transaction[]}`. Syncs
  `user.balance` into the store when different. Error → toast "Couldn't load wallet" + empty txs.
- **UI:** AppBar tab "My Wallet" (NO balance chip — the wallet itself shows it). Main (px-4):
  1. **Wallet card** — `bg-primary` rounded-2xl h-[10rem], decorative blurred circles, label
     "Current Balance" (`text-on-primary/80`), `Rs «formatBalance»` 2rem bold white.
  2. **Quick actions** row (white card): 3 circular buttons — Deposit (`add`, primary-container
     circle), Withdraw (`arrow_upward`), Transfer (`sync_alt`) with labels.
  3. **Transactions** card — header "Transactions" + Filter pill (`tune` icon) toggling an
     inline dropdown: **All / Money In / Money Out** (IN_TYPES = DEPOSIT, PRIZE, REFERRAL_BONUS,
     TASK_REWARD, REFUND).
     Row: 48px type icon circle (money-in = emerald chip class `tx-in-icon` #ecfdf5/#059669,
     dark #064e3b/#6ee7b7; out = `bg-surface-container text-primary`); icon/title per type:
     DEPOSIT→payments "Deposit", TRANSFER→sync_alt "Transfer", WITHDRAW→outbox "Withdraw",
     PRIZE→emoji_events "Prize", ENTRY_FEE→sports_esports "Entry Fee", REFERRAL_BONUS→card_giftcard
     "Referral Bonus", TASK_REWARD→task_alt "Task Reward", REFUND→payments "Refund",
     ADJUSTMENT→tune "Adjustment". `method` overrides ("welcome bonus"→"Welcome Bonus",
     "signup bonus"→"Signup Bonus", "daily task"→"Daily Task", "task reward"→"Task Reward",
     "referral"→"Referral Bonus"). Title + `timeAgo` below; right: signed amount
     (`+`/`-` + formatMoney(abs)) — green when positive, `text-primary` when WITHDRAW-negative,
     else on-surface; below: status dot+label — PENDING "Pending" muted, REJECTED/FAILED
     "Rejected" text-error, COMPLETED "Completed" emerald.
- **States:** tx spinner; empty → EmptyState **"No transactions yet"** (icon `receipt_long`).
- **Shared:** AppBar, SupportFab, useRequireAccount (guest dialog), EmptyState.

## C2. `wallet/DepositScreen.tsx` — key `deposit` — HTML: `deposit.html`
- **Nav:** "Continue to deposit" → `nav(depositConfirm, {amount:number, method:string,
  accountId?:string})`. Back via AppBar.
- **API:** `GET /bank` → `{accounts: BankAccount[]}` (optional, failures ignored);
  `getSettings()` → `minDeposit` (default 100).
- **UI:** radial top glow background. AppBar "Deposit". Funding-source card: 48px rounded-2xl
  icon tile (`account_balance`), method name (headline-md) + bound-account subtitle
  ("«accountTitle» • «maskAccountNumber»") + swap button (`sync_alt`) opening BankSelectSheet
  (title "Choose Funding Bank", search "Search Your Funding Bank", banks + bound accounts).
  Amount display 3rem bold centered. **AmountPad always visible** (keys 1-9, ".", 0, backspace;
  append rules: max 8 significant digits, single "."); primary button **"Continue to deposit"**.
- **Selection persistence:** `sessionStorage["areena_bank_selection"]` = `{method, accountId}`
  (legacy key `areena_bank_method`); default method "Jazzcash". Sheet confirm commits the draft.
  **FUNDING_BANKS = ["Jazzcash", "Easypaisa", "Sadapay", "Alphla Bank", "Mezan Bank"]** (sic "Alphla").
- **Validation:** empty/≤0 → toast "Enter an amount"/"Please enter a valid deposit amount.";
  below min → toast "Amount too low" / "Minimum deposit Rs «min»".
- **Shared:** AppBar, AmountPad, BankSelectSheet.

## C3. `wallet/DepositConfirmScreen.tsx` — key `depositConfirm` — HTML: `depositconfirm.html`
- **Params in:** `{amount:number, method, accountId?}` — invalid amount → `repl(deposit)` effect.
  **Out:** success → `nav(depositSuccess, {amount, method, reference})`.
- **API:** `POST /wallet/deposit` body `{amount, method, trxId, receiptName, image? (data URL),
  accountId?}` → `{success, reference, balance}` → store balance update. Errors → toast
  "Deposit failed". Server rules: min deposit, max deposit, TrxID + receipt required,
  idempotency + rate limiting.
- **UI:** AppBar "Confirm Deposit". Hero: 56px circle `account_balance` + amount 2.25rem bold +
  "Reviewed & approved by admin". Details card (rows with icon + label / bold value):
  Amount, Deposit ID (`#DEP-<random 6 digits>`, client-generated display ref), Account Number
  (from `settings.depositAccounts[method]` via bootstrap — map has raw/lower/upper/title-case
  variants; fallback hardcoded: jazzcash "0300-1234567", easypaisa "0345-7654321", sadapay
  "0123 4567 8901", "alphla bank" "0245 0102 3456", "mezan bank"/"bank transfer" "1234 5678 9012"),
  Account Name (`user.fullName`), optional bound-account row ("«bankName» «masked»"), Our Fee
  "0.00", You Get. **Upload Receipt row (REQUIRED)** — file picker image ≤ 8 MB → data URL;
  shows thumbnail + file name chip + "Tap to change", else "Upload Receipt *" + `upload` icon.
  **TrxID input (REQUIRED)** — rounded-full field with `tag` icon, placeholder "Enter TrxID /
  payment reference". Helper note card (`info` icon): "Send the payment to the account number
  shown above, then upload your payment receipt here."
  Fixed footer: "By confirming, you agree to the deposit terms & conditions." + primary button
  **"Confirm Deposit"** (guest gate "make a deposit"; spinner while submitting).
- **States:** toasts: "Receipt attached" (+file name), "Receipt image too large"/"Please attach
  an image up to 8 MB.", "Invalid receipt file", "Could not read receipt", "Payment receipt
  required"/"Please attach a copy of your payment receipt so we can verify this transaction.",
  "Transaction ID required"/"Please enter the transaction ID (TrxID) shown in your payment
  provider's receipt."
- **Shared:** AppBar, useRequireAccount.

## C4. `wallet/DepositSuccessScreen.tsx` — key `depositSuccess` — HTML: none (created; SuccessAnim design)
- **Params in:** `{amount:number, method:string, reference:string}`. **Out:** back button AND
  "Back to Wallet" → `repl(wallet)`; "Go to Home" → `repl(home)`.
- **API:** none.
- **UI:** AppBar "Deposit Request" with `onBack` overridden to `repl(wallet)`. SuccessAnim:
  title **"Deposit Request Submitted!"**, subtitle "Your deposit is now pending admin approval.
  Your balance will be updated once it is approved." + `StatusChip status="PENDING"`. Summary
  rows: Amount / Method / Reference / Current Balance (live `user.balance`). Buttons: primary
  **"Back to Wallet"**, text **"Go to Home"**.
- **Shared:** AppBar, SuccessAnim, StatusChip.

## C5. `wallet/WithdrawScreen.tsx` — key `withdraw` — HTML: `withdraw.html`
- **Params in:** none. **Out:** "Continue to Withdraw" → `nav(confirmWithdraw, {amount,
  method, account})`; sheet empty-action → `nav(bindAccount, {returnTo:"withdraw"})`.
- **API:** `GET /bank` → accounts; saved account id restored from session storage if still valid;
  `getSettings()` → `minWithdraw` (default 500).
- **UI:** AppBar "Withdraw". Centered pill button (icon + label + `expand_more`): label =
  "«bankName» «masked»" when an account is selected, else "Select account" (accounts exist) or
  "Bind Account" (none; icon `add_card`). Amount display 3rem + blinking cursor bar +
  availability pill: "«formatMoney(balance)» Available balance" + `info`. **Quick chips**
  100 / 500 / 1000 / 5000 (grid-cols-4). AmountPad. Primary button **"Continue to Withdraw"**
  disabled unless an account is selected; helper text below when disabled: "Bind a withdrawal
  account first — tap "Bind Account" above." / "Select a withdrawal account above to continue."
  BankSelectSheet title "Choose Withdrawal Account", `showBanks=false`, emptyAction → Bind Account.
- **Validation:** invalid amount → "Enter an amount"; < min → "Amount too low" /
  "Minimum withdraw Rs «min»"; > balance → "Insufficient balance" / "Available balance is Rs «x»".
- **Shared:** AppBar, AmountPad, BankSelectSheet.

## C6. `wallet/ConfirmWithdrawScreen.tsx` — key `confirmWithdraw` — HTML: `confirmwithdraw.html`
- **Params in:** `{amount, method, account: BankAccount|null}` — invalid amount → `repl(withdraw)`.
  **Out:** success → `nav(withdrawSuccess, {amount, method, reference})`.
- **API:** `POST /wallet/withdraw` body `{amount, method, accountNumber?, accountTitle?}` →
  `{success, reference, balance}` (balance already held server-side). Errors → toast "Withdraw
  failed" (e.g. "Minimum withdrawal is Rs X", "Insufficient balance", "Please select a withdrawal
  account", "You already have a pending withdrawal request." 409).
- **UI:** AppBar "Confirm Withdraw". Hero 48px circle + amount + "Reviewed & approved by admin".
  Summary rows: Amount, Withdraw To ("«bankName» «masked»" or method), Bank, Our Fee "0",
  You Get (icon `payment_arrow_down` rotated 270°). Fixed footer: "By confirming, you agree to
  the risks & terms." + primary **"Confirm Withdraw"** (guest gate "withdraw your winnings").
- **Shared:** AppBar, useRequireAccount.

## C7. `wallet/WithdrawSuccessScreen.tsx` — key `withdrawSuccess` — HTML: none (created)
- **Params in:** `{amount, method, reference}`. **Out:** back + "Back to Wallet" → `repl(wallet)`;
  "Go to Home" → `repl(home)`.
- **UI:** AppBar "Withdrawal Request" (onBack → repl(wallet)). SuccessAnim title
  **"Withdrawal Request Submitted!"** subtitle "Your withdrawal is now pending admin approval.
  The amount has been held from your balance and will be paid once approved." + StatusChip PENDING.
  Rows: Amount / Method / Reference / **New Balance**. Buttons as depositSuccess.
- **Shared:** AppBar, SuccessAnim, StatusChip.

## C8. `wallet/TransferScreen.tsx` — key `transferMoney` — HTML: `transfermoney.html`
- **Params in:** none. **Out:** success → `nav(transferSuccess, {amount, receiver, reference})`.
- **API:** `POST /wallet/transfer` body `{recipient (uid or email), amount, note?}` →
  `{success, receiver: string (gameName), reference, balance}`. Errors: "Enter a valid recipient
  and amount", "Maximum transfer amount is Rs X.", "Insufficient balance", "Recipient not found.
  Check UID or email." (404), "You cannot transfer to yourself", "Transfers to guest accounts
  are not allowed." → toast "Transfer failed".
- **UI:** AppBar "Transfer Money". Cards: **SourceAccountCard** (initial circle, fullName,
  "**** **** «uid»", balance right); **RecipientCard** "Send to:" — when empty: person_search
  input "Enter UID or email"; else avatar chip + recipient + "Player UID / email" + **Change**
  button; **InputSection** "Enter amount" 4xl figure + cursor; **Note** textarea "What is this
  for?" (label "Note (optional)"); AmountPad; primary CTA **"Transfer Money"**.
- **Validation:** recipient required ("Recipient required"/"Enter the player UID or email to send
  money."), amount ≤0, amount > balance.
- **Shared:** AppBar, AmountPad.

## C9. `wallet/TransferSuccessScreen.tsx` — key `transferSuccess` — HTML: none (created)
- **Params in:** `{amount, receiver, reference}`. **Out:** back + "Back to Wallet" → `repl(wallet)`;
  "Go to Home" → `repl(home)`.
- **UI:** AppBar "Transfer Successful" (onBack → repl(wallet)). SuccessAnim title
  **"Transfer Successful!"** subtitle "You sent Rs «amount» to «receiver»". Rows: Amount /
  Recipient / Reference. Buttons as depositSuccess (no StatusChip).
- **Shared:** AppBar, SuccessAnim.

## C10. `wallet/SelectBankScreen.tsx` — key `selectBank` — HTML: `selectbank.html`
- **Legacy screen** (superseded by BankSelectSheet on Deposit/Withdraw/Bind flows) but still
  routable. Reads `GET /bank` for accounts; METHODS = Jazzcash/Easypaisa/Sadapay/Alphla Bank/
  Mezan Bank; search field filters both lists; selecting a bank row sets `selected`; **Confirm**
  writes `sessionStorage["areena_bank_method"]` then `back()`.
- **UI:** renders a dimmed mock of the Deposit screen behind a bottom sheet (h-[85vh],
  rounded-t-[1.75rem], drag handle, "Choose Funding Bank" title, search pill, rows with
  account_balance icon tiles + green check circle, "Your Accounts" section showing raw
  accountNumber). Empty → EmptyState "No banks found" (icon `account_balance`).
- **Shared:** AppBar, EmptyState.

---

# D. SOCIAL (8 files)

## D1. `social/MyTeamScreen.tsx` — key `myTeam` — HTML: `myteam.html` (no-team) + `teamcreationdone.html` (with-team)
- **Params in:** `{qrTeam?}` — a deep-linked team id opens the **Team Connect popup** (resolve
  via `GET /qr/lookup?teamId=`; popup dismissed-forever per id via local state). Popup shows
  `TeamResultCard` + "Go to My Team" link when member/requested.
- **Out:** "Create Team" → guest gate "create a team" → `nav(teamCreation)`; QR button → QrSheet
  (target "team", initialTab "mine").
- **API:** `GET /team` → `{team: Team|null, stats}` (screen uses team). Owner also fetches
  `GET /team/join-requests` → `{requests: TeamJoinRequestRow[], myRequest?}`.
  Leave: `DELETE /team` → `{disbanded?, left?}` → toast "Team disbanded" / "You left the team" →
  reload. Respond: `POST /team/join-requests/:id` body `{action:"accept"|"decline"}` →
  `{success, status}` → toast "Member added!" / "Request declined" → reload both.
  Popup join: `POST /team/join-requests` body `{teamId}` → toast "Join request sent to the team
  owner!". Lookup fail 404 → toast "Could not open team"/"No team found with this QR code."
- **UI:** radial gradient background (primary-fixed-dim top-right + emerald bottom-left).
  AppBar "My Team".
  - **No team:** centered "No Team Yet" (headline-md) + "Create your squad team to join Squad
    tournaments!" (body-lg, max-w) + primary **"Create Team"** button (`add` icon, max-w-15rem).
  - **With team:** team name hero (2rem extrabold UPPERCASE) → owner-only **Join Requests (n)**
    card (rows: initial circle, gameName, "UID: «uid»", timeAgo; Accept primary pill / Decline
    muted pill) → **Team Members** header + QR icon button (40px white circle) → members card
    (rows: 48px initial circle, gameName, "UID:", OWNER → "Leader" outlined chip) → info note
    "Note: To add or remove team members, or to delete your team, please contact Customer
    Support." → destructive text button **"Leave Team"** / **"Leave Team (Disband)"** (owner;
    `logout` icon; "Leaving…" while busy).
- **States:** loading spinner; error → EmptyState "Could not load your team. Pull to retry."
  (icon `groups`) + **"Try Again"** pill button.
- **Shared:** AppBar, EmptyState, QrSheet (+ TeamResultCard), useRequireAccount.

## D2. `social/TeamCreationScreen.tsx` — key `teamCreation` — HTML: `teamcreation.html`
- **Params in:** none. **Out:** success → `nav(teamCreationDone, {teamName})`.
- **API:** `POST /team` body `{name, memberUids: string[] (trimmed, empty-filtered)}` →
  `{team: {..., invited}}`. "already in team" → 400 error toast "Failed to create team".
- **UI:** AppBar "My Team". Sections: **Team Name** (shield SVG icon label, rounded-full input
  maxLength 24, placeholder "e.g. Phoenix Squad", error state red border + "Please enter a team
  name to continue."); **LEADER (YOU)** card (crown icon filled; leader card with left 4px
  primary border, initial circle + ring, gameName + green presence dot, "UID:", "Leader" chip
  bg-primary-fixed); **Squad Members** ("3 Players Required" chip; 3 numbered "Player 1/2/3 UID"
  inputs, placeholder "Enter player UID"); note "All members must be from the same country and
  will receive an in-app invite."; CTA **"Create Team"** (paper-plane SVG icon, "Creating Team…"
  spinner). Guest gate "create a team".
- **Shared:** AppBar, useRequireAccount.

## D3. `social/TeamCreationDoneScreen.tsx` — key `teamCreationDone` — HTML: `teamcreationdone.html`
- **Params in:** `{teamName?}` (fallback "Your Team"). **Out:** AppBar back → `repl(myTeam)`.
- **API:** `GET /team` → team (falls back to params name on failure).
- **UI:** dual radial gradient background; AppBar "My Team"; team hero (2rem extrabold uppercase);
  **Team Members** card identical to MyTeam rows (Leader chip for OWNER); same customer-support
  note. Loading spinner; members empty → EmptyState "Your team is being set up." (icon `groups`).
- **Shared:** AppBar, EmptyState.

## D4. `social/FriendsScreen.tsx` — key `friends` (bottom-nav tab) — HTML: `freinds.html`
- **Params in:** `{qrUser?}` — deep-linked player id opens the **Add Friend popup** (resolve via
  `GET /qr/lookup?uid=`). **Out:** friend card → `nav(chat, {friendId})`; popup Message →
  `nav(chat, {friendId})` (after closing popup); QR button → QrSheet (target "user", initialTab "scan").
- **API:**
  - `GET /friends` → `{friends: Friend[]}` (Friend = user + friendshipId + lastMessage,
    lastMessageAt, unread).
  - `GET /friends/requests` → `{received: FriendRequestRow[], sent: FriendRequestRow[],
    receivedPending: number}`.
  - Add by UID: `POST /friends/requests` body `{uid, source:"UID"|"QR"}` →
    `{request:{id,status}}` or `{request, autoAccepted:true}` → toast "You are now friends!" /
    "Request sent to UID «uid»"; errors: 404 "Player not found"/`No player found with UID «uid»`,
    400 "You cannot add yourself as a friend", 409 "Already connected" (alreadyFriends flag →
    default-variant toast) / "Friend request already sent".
  - Respond: `POST /friends/requests/:id` body `{action:"accept"|"decline"|"cancel"}` →
    `{success, status}` → toasts "Friend added!" / "Request declined" / "Request canceled".
  - Accept All loops accept per row → toast "Added «n» friend(s)!" / "No requests accepted".
- **UI:** AppBar tab "Friends" with right = `person_add` + `qr_code_2` AppBarIconButtons + bell.
  Search pill (placeholder "Search by username or UID...", clear X). Segmented tabs:
  **All (n) / • Online (n) / Requests [badge]** (active white pill). Meta row: "«onlineCount»
  Online • «total» Total" + "Recent Activity". Online = lastMessageAt within 30 min.
  - Friend card: 44px initial circle (+green online dot), gameName, "UID:", chat_bubble circle
    button; tap → chat.
  - Requests tab: "Requests Received (n pending)" + **Accept All** link; received cards
    (identity + via "QR code"/"UID" + UID + timeAgo; Accept/Decline; ACCEPTED → green
    "Accepted — added to friends" row; DECLINED → "Request declined"); "Requests Sent (n)" cards
    (Pending chip + Cancel link; Accepted/Declined chips); both empty → EmptyState
    **"No requests yet."** (icon `person_add`); single-sided empty → "Nothing here yet."
  - Add tab (via header person_add): "Add by Player UID" card ("Enter a player tag to send an
    instant squad invite", numeric input "e.g. 782104", **Send** button "Sending…") +
    "Suggested Players" demo list (TactiCool_Dan "5 mutual friends", Vortex_Recon "Played with
    yesterday" — local Add → "Sent" state only).
  - Friend-tab empty states: none → **"No friends yet."** (icon `group`); search miss →
    `No friends match "«q»".` (icon `search`); online empty → **"No friends online right now."**
    (icon `schedule`); load error → "Could not load friends. Please try again." (icon `group`).
  - Add Friend popup (qrUser): white rounded card with `UserResultCard` + relation line
    ("This is your own QR code." / "You and X are friends." / "Your friend request is waiting for
    a response." / "X sent you a friend request." / "Send a friend request to connect.").
- **States:** loading spinner (friends + requests separately).
- **Shared:** AppBar, AppBarIconButton, BellButton, EmptyState, QrSheet (+UserResultCard),
  useRequireAccount.

## D5. `social/ChatScreen.tsx` — key `chat` — HTML: `chat.html`
- **Params in:** `{friendId}`. **Out:** none.
- **API:** `GET /friends/:friendId/messages` → `{friend: FriendUser, messages: ChatMessage[]}` —
  initial load + **poll every 3000 ms** (skips state write when length+last id unchanged; poll
  errors silent). Send: `POST /friends/:friendId/messages` body `{text}` → `{message: ChatMessage}`
  appended.
- **UI:** AppBar page with title = friend.gameName ("Chat" fallback). Fixed "Today, 5:42 PM"
  divider pill (static, from HTML). Messages list (independently scrollable, auto-scroll to
  bottom): friend bubbles left (white `bg-surface-container-lowest` rounded-2xl rounded-bl-sm,
  7px avatar initial, time under) vs my bubbles right (`bg-primary text-on-primary` rounded-br-sm,
  time + `done`/`done_all` read icon below). Input bar pinned bottom: rounded-full container,
  "Type a message..." + emoji button (appends 😊) + primary circular `send` FAB. Enter sends.
- **States:** loading spinner; load failure → `wifi_off` icon + "Could not load this chat.";
  empty → `forum` icon + "Say hi to «gameName» — start the squad talk!"; send error → toast
  "Failed to send".
- **Shared:** AppBar.

## D6. `social/ReferEarnScreen.tsx` — key `referEarn` — HTML: `refer&earn.html`
- **Params in:** none. **Out:** none.
- **API:** `GET /referrals` → `{referralCode, bonus, referrals: ReferralInfo[] (each bonusEarned),
  totalEarned, count}`. Error → toast "Failed to load referrals".
- **UI:** AppBar "My Referrals" over a primary-tinted radial top. Stats grid (2 cards):
  "Total Referrals" + count, "My Code" + code (both headline values text-primary). Primary
  button **"Copy Referral Code"** (`content_copy` icon; on copy → check icon + **"Copied!"** for
  2s; failure toast "Could not copy code"). "Referred Users" card (header + "«count» users" chip):
  rows = initial circle, gameName, "Joined «timeAgo»", right "+Rs «bonusEarned»" (text-secondary);
  empty → "No referrals yet. Share your code to invite friends!" (centered, body-lg, max-w-15rem).
- **Shared:** AppBar.

## D7. `social/TasksScreen.tsx` — key `tasks` (Profile sub-page; back arrow required) — HTML: `tasks.html`
- **Params in:** none. **Out:** none.
- **API:** `GET /tasks` → `{tasks: Task[]}` (id, title, description, icon (Material Symbol name),
  reward, type, claimed, completed). Claim: `POST /tasks` body `{taskId}` → `{success, reward}` →
  toast "+Rs «reward»" / "Task reward added to your wallet" + balance update + reload. 409 dup →
  toast "Claim failed". Error → toast "Failed to load tasks".
- **UI:** AppBar "Tasks" (page mode). Right-aligned earned-today pill: "Rs «sum of claimed
  rewards»" (`bg-surface-container-low text-primary`). Task rows (white rounded-2xl cards):
  48px rounded-2xl icon tile (Material symbol from task.icon, text-primary), title + description,
  right "+Rs «reward»" chip (`bg-secondary/15 text-secondary`) + Claim button (primary pill,
  "Claiming…" while busy) OR claimed state (filled `check_circle` + **"Claimed"** text-secondary).
  Empty: 80px rounded-2xl tile with `assignment` icon + **"No tasks available."** (lg semibold) +
  "Check back later for new tasks to complete and earn rewards." Guest gate "claim task rewards".
- **Shared:** AppBar, useRequireAccount.

## D8. `social/NotificationsScreen.tsx` — key `notifications` — HTML: `notification.html`
- **Params in:** none. **Out:** deep-link "Open" → `navigate(parseDeepLink(n.link))`.
- **API:**
  - `GET /notifications` → `{notifications: AppNotification[], unread}` (also setUnread).
  - Tap-to-read: `PATCH /notifications` body `{ids:[id]}` → `{success, unread}` (optimistic
    local isRead first).
  - Delete selected: `DELETE /notifications` body `{ids:[...]}` → `{success, unread}` → toast
    "Deleted «n» notification(s)".
- **UI:** AppBar "Notifications" — normal state right = BellButton; **selection mode** right =
  "Select All" text button + delete AppBarIconButton (red icon). Background radial glow. Single
  white card list with hairline dividers (ml-20 mr-4). Row: 48px type icon circle
  (`bg-surface-container-high`, icon per type — TOURNAMENT `emoji_events`, PAYMENT `payments`,
  SUCCESS `check_circle`, WARNING `warning`, INFO `info`, default `notifications`) + title +
  `timeAgo` + message (2-line clamp until tapped; tapped rows expand fully and STAY expanded) +
  unread **dot** (w-2 bg-primary — the only read/unread visual difference; rows never dim) +
  expanded deep-link "Open →" button when `n.link` parses. Selection indicator circle
  (`check_circle`/`radio_button_unchecked`).
- **Interactions:** tap = expand + background mark-read (if unread); tap on expanded deep-linkable
  row navigates; **long-press 1s** (cancel on >12px move) enters selection mode and selects the
  row; in selection mode tap toggles selection; Select All selects everything; delete removes
  only selected.
- **States:** loading spinner; empty → EmptyState **"No notifications yet."** (icon `notifications`).
- **Shared:** AppBar, AppBarIconButton, BellButton, EmptyState.

---
# E. PROFILE (11 files)

## E1. `profile/ProfileScreen.tsx` — key `profile` (bottom-nav tab) — HTML: `profile.html`
- **Params in:** none. **Out (menu):** editProfile, referEarn, myTeam, tasks, about, terms,
  privacy, myStats, myTournament, achievements, leaderboard, bindAccount (guest gate "bind your
  bank account"), adminDashboard (privileged roles only); QR → QrSheet; Logout.
- **API:** `POST /auth/logout` (best-effort) then local `logout()` (wipes pending-qr too).
  `getSettings()` for social links.
- **UI:** `bg-ambient` background. AppBar tab "Profile" (no balance chip).
  1. **Header card** — 96px initial avatar in a primary-container ring, gameName + "PK" superscript
     chip, email line (guests: **"Guest user"**), "UID: «uid»", two pill buttons: **Share**
     (navigator.share: "Join me on AREENAX! My UID is «uid» — use my referral code «code» to get
     a welcome bonus!"; clipboard fallback toast "Copied!") and **QR Code** (`qr_code_2` → QrSheet).
  2. **Menu card 1** (icon rows w/ chevron_right): Edit Profile (`person`), Refer & Earn
     (`group_add`), My Team (`diversity_3`), Tasks (`checklist`), Theme (`dark_mode`) with a
     custom Switch (track/knob; toggles `dark` class + persists `areena-theme`), About (`help`),
     Terms & Conditions (`gavel`), Privacy Policy (`shield`).
  3. **Menu card 2:** [Admin Console (`admin_panel_settings`) — only when role ∈
     ADMIN_PANEL_ROLES], My Stats (`bar_chart`), My Tournaments (`sports_esports`), Achievements
     (`emoji_events`), Leaderboard (`leaderboard`), Bind Account (`account_balance`, guest-gated).
  4. **Social icons row** (only configured ones): Instagram `https://instagram.com/`+handle,
     Telegram `https://t.me/`+, Discord `https://discord.gg/`+, YouTube `https://youtube.com/`+
     (remote icon PNGs; native must bundle icon assets).
  5. **Logout button** (primary pill, `logout` icon, label **"Logout"**).
- **Shared:** AppBar, SupportFab, QrSheet, useRequireAccount.

## E2. `profile/EditProfileScreen.tsx` — key `editProfile` — HTML: `editprofile.html`
- **Params in:** none. **Out:** after save → `back()`. Logout-all → logout().
- **API:**
  - Save profile: `PATCH /me` body `{fullName, gameName, email (string|null), password?}` →
    `{user}` → toast "Profile updated"/"Your changes have been saved." → back.
  - Change password: `PATCH /me` body `{currentPassword, password}` → `{user}` → toast
    "Password changed"/"Your password has been updated." (fields cleared).
    400 "Current password is incorrect" → inline field error + toast "Password change failed".
  - `POST /auth/logout` body `{all:true}` → toast "Logged out from all devices." → logout().
- **UI:** dual radial gradient bg. AppBar "Edit Profile". **Profile Info card**: Full Name,
  Email, In-Game Name fields (rounded-full inputs) + validation ("Full name is required",
  "In-game name is required", "Enter a valid email address") + primary **"Save Changes"**.
  **Change Password card**: Current/New/Confirm password fields (visibility toggles; rules:
  new ≥6 chars, matches confirm) + outlined **"Change Password"** (`key` icon) + divider +
  outlined **"Log out from all devices"** (`logout` icon) with caption "Ends every active session".
  Guest gate "edit your profile" on both actions.
- **Shared:** AppBar, useRequireAccount.

## E3. `profile/MyStatsScreen.tsx` — key `myStats` — HTML: `mystats.html`
- **Params in:** none. **Out:** leaderboard card → `nav(leaderboard)`.
- **API:** `GET /stats` → `{stats: {totalMatches, wins, kills, earnings, winRate, top3,
  leaderboardRank, kd}}` (route returns top3 & kd too; the web consumes most fields). Error →
  toast "Failed to load stats".
- **UI:** AppBar "My Stats" over radial ambient. **Win Rate hero card**: "Win Rate" (headline-lg
  text-primary), "«wins» Wins out of «totalMatches» Total Tournaments", progress bar (h-3, fill
  = winRate%) + "«winRate»%" label. **Stats grid (2×2):** Total Matches (`sports_esports`,
  bg-surface-variant), Total Wins (`emoji_events`, secondary-container/30), Total Kills
  (`my_location`, tertiary-container/20), Total Earnings (`payments`, "Rs «formatMoney»",
  primary-container/20). **Leaderboard card** (whole card tappable): gold medal icon
  (`military_tech`, #D4AF37), "Leaderboard #«rank»", "Times ranked Top 1 globally", right chip
  "«top3» Times" + chevron.
- **Shared:** AppBar.

## E4. `profile/LeaderboardScreen.tsx` — key `leaderboard` — HTML: `leaderboard.html`
- **Params in:** none. **Out:** none.
- **API:** parallel `GET /leaderboard` → `{leaderboard: LeaderboardRow[]}` and
  `GET /stats` (for `leaderboardRank` when I'm not listed; stats failure tolerated). Error →
  toast "Failed to load leaderboard".
- **UI:** AppBar "Leaderboard" + two blurred radial blobs (primary-container tints).
  **Podium:** center #1 — 112px initial circle (border-4 primary), floating gold crown SVG
  (#F59E0B), rank badge "1", gameName, points pill (`bolt` filled + bold) in `bg-primary/10`;
  sides #2 (left) / #3 (right) — 80px circles, rank badges, points with bolt.
  **Ranked list card** (rounded-t-[2rem]): rows for `rank > 3`: rank number, 40px initial circle,
  gameName, right points (Locale.US grouping). **isMe row** = highlighted (left 4px primary bar,
  `bg-surface-container-low`, name shown as **"You"**, text-primary) with no divider around it.
  When I'm NOT in the list: bottom "You" row with `myRank` from stats ("—" when unknown).
- **States:** loading spinner; empty → 80px `bg-primary-fixed/60` circle + `leaderboard` icon +
  **"No leaderboard data yet"**.
- **Shared:** AppBar.

## E5. `profile/AchievementsScreen.tsx` — key `achievements` — HTML: `achievements.html`
- **Params in:** none. **Out:** none (detail bottom-sheet in place).
- **API:** `GET /achievements` → `{achievements: Achievement[] (id,title,description,icon,
  category,target,reward,progress,unlocked,unlockedAt), unlockedCount, total}`. Error → toast
  "Failed to load achievements".
- **UI:** AppBar "Achievements". **Summary card**: "«percent»% Completed" (headline-lg-mobile
  text-primary), "«unlocked» of «total» Unlocked", right circular SVG progress ring (r=16,
  stroke 4, dashoffset 100-percent) with filled `emoji_events` center. **Category chips**
  (horizontal scroll): All / Tournaments (TOURNAMENTS) / Kills (KILLS) / Earnings (EARNINGS) /
  Social (SOCIAL) — active primary pill. **Achievement cards**: icon tile (unlocked =
  secondary-container + `check_circle`; in-progress = primary-container + task `icon`; locked =
  surface-container-highest + `lock`), title + description, progress bar (progress/target) when
  in progress, chips "Unlocked"/"Locked", chevron; unlocked cards get a soft emerald glow blob.
  Tap card → **detail bottom sheet** (`rounded-t-[1.75rem]`, bg-surface-container): big icon tile,
  title/description, Progress bar + "progress/target", Reward "Rs «formatMoney»", status chip,
  close X (Escape closes).
- **States:** loading spinner; category empty → "No achievements in this category yet.";
  footer caption "Tap on any achievement card to view full details and reward eligibility."
- **Shared:** AppBar, AppBarIconButton (in-sheet close).

## E6. `profile/BindAccountScreen.tsx` — key `bindAccount` — HTML: `bindaccount.html`
- **Params in:** `{returnTo?}` ("withdraw" when coming from Withdraw). **Out:** success →
  `nav(bindAccountSuccess, {bankName, accountTitle, accountNumber, returnTo?})`.
- **API:** `GET /bank` → saved accounts; `POST /bank` body `{bankName, accountTitle,
  accountNumber}` → `{account: BankAccount}` → toast "Account bound"/"«bankName» linked
  successfully.". Error → toast "Failed to bind account".
- **UI:** AppBar "Bind Account". **Saved Accounts** list (bank tile icon, bankName + "Primary"
  chip when isPrimary, accountTitle, "•••• «last4»"). Form card: **Bank Name** — opens
  BankSelectSheet (title "Select Bank", search "Search Your Bank", banks only, confirm);
  **Account Holder Name** input ("Enter account holder name", `person` leading icon);
  **Account Number / IBAN** input (`credit_card` icon); errors: "Select your bank", "Account
  holder name is required", "Account number is required", "Enter a valid account number" (<8).
  Footnote (`info`): "Ensure your account details match your official legal documents for
  successful verification." CTA **"Bind Account Now"**. Guest gate "bind your bank account".
- **Shared:** AppBar, BankSelectSheet, useRequireAccount.

## E7. `profile/BindAccountSuccessScreen.tsx` — key `bindAccountSuccess` — HTML: `bindaccountsuccess.html`
- **Params in:** `{bankName, accountTitle, accountNumber, returnTo?}`. doneTarget =
  returnTo==="withdraw" ? `withdraw` : `wallet`. **Out:** back + primary CTA → `repl(doneTarget)`;
  "Back to Home" → `repl(home)`.
- **API:** none.
- **UI:** AppBar "Bank Account" (onBack → repl(doneTarget)). Success hero: 80px tinted circle +
  48px primary circle with white `check`, **"Account Linked!"** (26px bold), subtitle
  "Your «bankName» account is now active for instant withdrawals." Summary card: bank icon tile +
  bankName + **"Verified"** chip (bg-secondary/15) + "Primary Withdrawal Method"; rows:
  Account Holder, Account Number ("•••• «last4»"), Linked Date (today, "Oct 25, 2025" format),
  Instant Payouts ("Enabled" + green dot). Buttons: primary **"Continue to Withdraw"** /
  **"Go to Wallet"** (`arrow_forward`), secondary **"Back to Home"**.
- **Shared:** AppBar.

## E8. `profile/AdminPanelScreen.tsx` — key `adminPanel` — HTML: none (native admin section)
- **Access:** programmatic only (no REGISTRY caller navigates here in the current build — the
  Profile "Admin Console" row routes to `adminDashboard`; keep this screen for the mobile admin
  UI). Gate: `ADMIN_PANEL_ROLES.includes(user.role)` else EmptyState **"Admin access required."**
  (icon `lock`). Loading spinner; load error → EmptyState with message.
- **API:** `GET /admin/settings` → `{settings: Record<string,string>}` — 403 → denied state.
  Read keys: appDownloadUrl, minDeposit, minWithdraw, referralBonus, welcomeBonus,
  welcomeBonusUserEnabled ("true"/"false"), welcomeBonusGuestEnabled, welcomeBonusGuest,
  commission, version, whatsapp, instagram, telegram, youtube, discord, aboutMission,
  depositAccounts, fourEyesEnabled. Save: `POST /admin/settings` body `{settings: <form>}` →
  `{success, updated}` → toast "Settings saved" / "Save failed".
- **Section layout (role-gated, per server RBAC 59-c):**
  - **GamesAdminSection** (SUPER_ADMIN) — see E10.
  - **Payment Requests** (FINANCE_ADMIN+): `GET /admin/requests` →
    `{requests: PaymentRequest[]}` (id, type DEPOSIT|WITHDRAW, amount, method, reference, note,
    createdAt, reviewedById, approvedById, user{fullName,gameName,uid,email,phone,balance}).
    Header count badge + refresh button. Rows: direction icon circle (deposit green/arrow_downward,
    withdraw blue/arrow_upward), "Deposit/Withdrawal Request", "«gameName» • UID «uid»",
    "«method» • «timeAgo»", signed amount, note bubble, "Ref: «reference» • Wallet: Rs «balance»";
    deposits get **View Receipt** → `GET /admin/requests/:id` → `{id, image}` (data URL, cached)
    full-screen viewer. Four-eyes chip when reviewedById && !approvedById ("Reviewed — awaiting
    second approval"). Actions `POST /admin/requests/:id` body `{action:"approve"|"reject"}` →
    toasts "Request approved"/"The user has been notified and their balance updated." /
    "Request rejected"/"The user has been notified." (Approve label = "Final Approve" when
    fourEyesEnabled). Empty: **"No pending payment requests."**
  - **Tournament Management** (MODERATOR+) — `GET /admin/tournaments` →
    `{tournaments: AdminTournament[]}` (…+ entries[] with user summaries + disabled +
    resultsPublishedAt). Parallel `GET /admin/proofs` → `{proofs: AdminProofLite[]}` keyed
    "tournamentId:userId". Expandable rows (status line "«status» • cur/max players • Disabled •
    Results out/Room set"). Expanded:
    * **Match Status** chips Upcoming/Live/Cancelled (COMPLETED is publish-only; status route
      400s it) → `POST /admin/tournaments/:id/status` body `{status}` → toast "Status set to «s»".
    * **Result Proof Mode** toggle → `POST /admin/tournaments/:id/disable` body
      `{disabled: !current}` → toasts "Tournament disabled"/"Joined players now see Submit Result
      Proof instead of Match in Progress." / "Tournament enabled"/"Players are back on the normal
      match flow."
    * **Room ID & Password** form (+ datetime-local expiry) → `POST /admin/tournaments/:id/room`
      body `{roomId, roomPassword, roomExpiresAt: ISO|null}` → toast "Room details saved"/"Joined
      players can now see them on the card." Validation "Room ID and Password are required".
    * **Results** editor: per entry Rank/Kills/Prize inputs; **auto-prize** on rank/kills change:
      rank1=50%, r2=30%, r3=20% of prizePool, else loserPrize; + kills×perKill (floor rounding).
      "View Proof" chip per player with a proof → `GET /admin/proofs/:id` → `{id, image}` viewer.
      Publish (hidden after published; "Results published" chip + read-only inputs +
      **Final Standings** sorted list) → `POST /admin/tournaments/:id/results` body
      `{entries:[{entryId, rank, kills, prize}]}` → toast "Results published"/"Prizes credited and
      players notified." Empty entries: "No participants joined yet."
  - **ResultProofsAdminSection** (MODERATOR+) — see E11.
  - **Settings sections** (fields render for every panel role; non-permitted keys skipped
    server-side; section headers SUPER_ADMIN-only): App Distribution (appDownloadUrl), Wallet
    Limits & Bonuses (minDeposit, minWithdraw, referralBonus, User/Guest welcome-bonus toggles +
    amounts, commission, depositAccounts textarea "Name: number | Name: number"), App (version),
    Support & Social (whatsapp, instagram, telegram, youtube, discord, aboutMission textarea).
  - **Security & Audit** (SUPER_ADMIN) — tabs Events/Audit + "Run reconciliation" →
    `GET /admin/security?type=events|audit&take=50` → `{ok, items}`; `?type=reconcile` →
    `{ok, items: drifts, checkedCount}`. Event rows: action + ALLOW/DENY chip + "risk «n»" +
    "«formatDateTime» • endpoint • reason". Audit rows: action + actorRole chip + datetime •
    targetType • targetId. Reconcile: "All «n» balances match the ledger." or drift lines.
    Empty: "No security events yet." / "No audit entries yet."
  - Bottom **Save Settings** CTA (all roles) with "Saving..." spinner.
- **Shared:** AppBar, EmptyState.

## E9. `profile/AdminTournamentsScreen.tsx` — key: none (**NOT registered in AppShell — dead code**)
- Superseded by the TournamentsAdminSection inside AdminPanelScreen. Documented for archaeology:
  room form with `roomVisibleMinutes` (older contract), publish flow `POST /admin/tournaments/:id/room`
  body `{roomId, roomPassword, minutes?, publish}`, results `POST /admin/tournaments/:id/results`,
  status chips, sorted by status. **Native: do NOT build; use E8's section instead.**

## E10. `profile/GamesAdminSection.tsx` — section component (rendered inside AdminPanelScreen; no key)
- **API:** `GET /admin/games` → `{games: Game[]}` (with modes/maps/perspectives arrays).
  Create: `POST /admin/games` body `{name, image}` → toast "Game created".
  Update: `POST /admin/games/:id` body (`{isActive}` or `{matchDurationMinutes}`) → toasts
  "Game deactivated"/"Game activated"/"Match duration updated". Delete: `DELETE /admin/games/:id`
  → "Game deleted". Config ops: `POST /admin/games/:id/config` body `{kind:"mode"|"map"|
  "perspective", op:"add"|"delete", ...}` (mode add: `{name, slots, sub}`) → toasts "Match type
  added"/"Match type removed"/"Map added"/"Map removed"/"Perspective added"/"Perspective removed".
- **UI:** "Games Management" header + explainer "Games, match types (with fixed slots), maps and
  perspectives defined here feed the Host tournament creation flow automatically."; create row
  (name + image URL + "Add Game"); game rows (thumbnail, name, "«m» match types · «maps» maps ·
  «p» perspectives" + "· inactive", Activate/Deactivate, configure `edit`/`expand_less`, delete);
  expanded **GameConfigEditor**: "Match in Progress Duration (minutes)" (1..10080 numeric +
  Save), Match Types list (icon + name + sub + "«slots» slots" + delete X) + add form
  (Name/Fixed slots/Subtitle/Add Match Type), Maps chips + add, Perspectives chips + add.
  Empty: "No games yet — add the first one above."
- All failures → toasts "Failed to load games"/"Create failed"/"Update failed"/"Delete failed"/
  "Operation failed".

## E11. `profile/ResultProofsAdminSection.tsx` — section component (no key)
- **API:** `GET /admin/proofs` → `{proofs: AdminProof[]}` (id, createdAt, user{gameName,fullName,uid},
  tournament{id,name,mode,status,game?}). Images fetched per id from `GET /admin/proofs/:id` →
  `{id, image}` (data URL, cached; prefetched for thumbnails).
- **UI:** "Result Proofs" header + refresh; explainer "Match result screenshots uploaded by
  joined players from disabled tournaments. Tap one to view it full size."; scrollable rows
  (48px thumbnail / broken_image placeholder / spinner, "«gameName» • UID «uid»",
  "«game.name» • «tournament.name» • «mode»", timeAgo, chevron) → full-screen viewer dialog
  (black/80 backdrop, white card, close X, image object-contain or "Could not load image").
  Empty: **"No result proofs submitted yet."** Error toast "Couldn't load result proofs".

---

# F. HOST (5 files)

## F1. `host/HostTournamentScreen.tsx` — key `hostTournament` — HTML: `hosttournament.html`
- **Params in:** `{gameId?, gameName?}` (auto-selected from Home Host grid). **Out:** card tap →
  `nav(hostTournamentDetails, {tournamentId})`; FAB/empty CTA → guest gate "host your own
  tournament" → `nav(hostTournamentCreation, {gameId?, gameName?})`.
- **API:** `GET /my/hosted` → `{tournaments: Tournament[]}` (live status derived server-side).
  Client-side scope by gameId + filter.
- **UI:** AppBar page, title = gameName ?? "Host Tournament" + bell. Scrollable tab pill row:
  **Upcoming / Ongoing / Completed / Created** (empty texts: "No upcoming tournaments." /
  "No ongoing tournaments." / "No completed tournaments." / "You haven't hosted any tournaments
  yet."). **HostCard** = exact `hosttournamentcard.html` replica: h-48 banner (bannerImage ??
  game.image) + bottom gradient + top-right mode chip + bottom-left `schedule` + formatDateTime;
  body: host initial + name + "Tournament Lead", right "Tournament Status" + statusLabel
  (Open/Live/Completed/Cancelled); Total Prize (when >0) / Entry Fee grid; "Filled Slots" +
  "cur / max Players" + progress bar; full-width "JOIN NOW ›" button (display only). Empty
  (Created tab) shows a primary **"Create Tournament"** button; a floating add FAB (bottom-right,
  48px primary circle) always offers creation.
- **Shared:** AppBar, useRequireAccount.

## F2. `host/HostCreationScreen.tsx` — key `hostTournamentCreation` — HTML: `hosttournamentcreation.html`
- **Params in:** `{gameId?, gameName?}`. **Out:** success → `nav(hostTournamentSuccess,
  {tournamentId, name})`.
- **API:**
  - `GET /games/config` → `{games: Game[]}` — games with Admin-managed `modes[]` (name, slots
    FIXED, sub, icon), `maps[]`, `perspectives[]`. Auto-selects the tapped game (or first) and
    silently applies its first mode/map/perspective.
  - `POST /tournaments` body `{gameId, name, mode, map, perspective, entryFee, prizePool,
    loserPrize, maxPlayers, rules, startTime: ISO, image? (data URL ≤5MB)}` → `{tournament}` →
    toast "Tournament hosted!"/"Your tournament is now live for players to join.".
- **UI:** AppBar title = selected game name. Form card "Create your Tournament": Tournament Name
  ("e.g. Creator Battle"), **Date & Time** (readonly field → DateTimePickerModal; display format
  "Oct 25, 2025, 11:06 AM"), **Match Type** (opens bottom sheet listing Admin modes with icon +
  name + sub; selection sets FIXED slots; empty: "No match types configured for this game yet."),
  **Entry Fee (Rs.)** numeric (default "50"), **Slots** (disabled input, auto-filled), **Rules**
  (readonly field → centered modal with textarea "Enter tournament rules, terms, and conditions
  here..." + Cancel/"Save Rules"), **Tournament Image (optional)** row (add_photo_alternate /
  thumbnail + name chip + "Tap to change" + X remove; ≤5 MB, data:image only).
  **CALCULATIONS PREVIEW** card (dashed primary border): live economics —
  `collection = fee × slots`; `prizePool = round(collection*0.5)` ("Winner Prize Pool (50%)");
  rank1/2/3 = pool×50%/30%/20%; `loserPrize = round(collection*0.5/(slots-1))` ("Refund per
  Loser"). CTA **"Host Tournament"** (guest gate "host your own tournament"; "Hosting..." spinner).
- **Validation:** "Tournament name is required", "Date & time is required", "Match type is
  required", "Entry fee must be 0 or more"; game-not-ready toast "Game not ready"/"Games are
  still loading — please try again in a moment."
- **Shared:** AppBar, DateTimePickerModal, useRequireAccount.

## F3. `host/HostSuccessScreen.tsx` — key `hostTournamentSuccess` — HTML: `hosttournamentsuccess.html`
- **Params in:** `{tournamentId?, name?}`. **Out:** "View Tournament Details" →
  `nav(hostTournamentDetails, {tournamentId})`; "Created Tournaments" → `nav(hostTournamentCard)`.
- **API:** `GET /tournaments/:id` (summary fields; failures keep params fallback).
- **UI:** AppBar "Success". Bouncing glowing check hero (w-32 primary circle, `check_circle`
  filled 6xl, blur glow ring). "Tournament Hosted Successfully!" + "Your tournament "«name»" has
  been created and is now live for players to join." (name bold). **Tournament Summary** card:
  Name / Entry Fee ("Rs. «formatMoney»") / Slots (fallback 48) / Match Type (fallback "Solo",
  text-primary). Buttons as above.
- **Shared:** AppBar.

## F4. `host/HostCardsScreen.tsx` — key `hostTournamentCard` — HTML: `hosttournamentcard.html`
- Simple "my created tournaments" list: AppBar "My Tournaments"; `GET /my/hosted` → list of
  HostCard replicas (same card as F1) → tap `nav(hostTournamentDetails, {tournamentId})`;
  empty → trophy empty state; loading spinner. (147 lines; use HostTournamentScreen as the
  richer superset — native can merge both.)

## F5. `host/HostDetailsScreen.tsx` — key `hostTournamentDetails` — HTML: `hosttournamentdetails.html`
- **Params in:** `{tournamentId}`. **Out:** not-found button "My Tournaments" → `nav(hostTournament)`.
- **API:** `GET /tournaments/:id` → `{tournament}` (with entries[]). This is the HOST's view of
  their own tournament (results access allowed by the API for the host).
- **UI:** AppBar "Tournament Details" + bell on `bg-surface-container-low` page with inner
  `bg-background` card. **Hero** (h-[12.5rem]) with countdown pill "Registration ends in …".
  **Summary cards** (Prize Pool/Entry Fee/Per Kill — same adaptive rules as B3). **Tabs**
  Overview / Rules / Prizes (same content as B3: mode/map/players/loserPrize/perspective rows,
  rules numbered list with copy, prize distribution 50/30/20). **ID & Pass row** — HOST variant:
  "Copy room info" button copies "Room ID: x\nRoom Password: y" → "Copied!" flash state (2s) or
  toast "Room info not set yet"/"Room ID & password will be available before match start." A
  **Room Info** card section shows Room ID/Password rows with per-field copy buttons
  (flash "Copied!") and rules copy. **Participants** card — count header + rows (initial, name,
  "UID:", teamName when present) or "No participants yet." variant. Clipboard helper falls back
  to `document.execCommand("copy")` (native: ClipboardManager).
- **Shared:** AppBar, EmptyState.

---

# G. INFO (4 files)

## G1. `info/AboutScreen.tsx` — key `about` — HTML: none ("about.html" not among extracted pages; matches design language)
- **Params in:** none. **Out:** Privacy Policy → `nav(privacy)`; Terms & Conditions → `nav(terms)`;
  social links → browser intents.
- **API:** `GET /bootstrap` → settings (whatsapp/telegram/youtube/instagram/discord).
- **UI:** AppBar "About". App identity block: 96px rounded-[2rem] logo tile, "Areenax Tournaments"
  (xl bold), "Version 1.0.0" (sm muted). **Our Mission** card — `settings.aboutMission` from
  bootstrap is NOT actually bound here (renders FALLBACK_MISSION text: "We are building the
  ultimate gaming tournament platform where players from around the world can compete, win
  prizes, and connect with a global community of gamers. Our platform supports multiple games,
  fair competitions, and transparent prize distribution."). **Follow Us** card — WhatsApp
  (`https://wa.me/<digits>` green chip), Telegram / YouTube / Instagram / Discord chips (remote
  icons; hide/neutralize when unset). **Legal links** card — Privacy Policy (shield, green
  circle) and Terms & Conditions (gavel) rows with chevrons. Footer "© 2026 Areenax Tournament
  App. All rights reserved."
- **Shared:** AppBar.

## G2. `info/TermsScreen.tsx` — key `terms` — HTML: `terms.html` (PUBLIC pre-auth)
- **Params in:** none. **Out:** none. AppBar "Terms & Conditions" — right = BellButton ONLY when
  signed in (`user ? <BellButton/> : null` — avoids a 401 bounce for visitors).
- **UI:** "Legal Document" eyebrow + "User Agreement" (headline-lg-mobile) + "Please read these
  terms carefully before using our services."; white floating card "Last updated: March 2026" +
  10 static sections (Acceptance of Terms, Eligibility (18+), Account Rules, Tournament
  Participation, Wallet & Payments, Referral Program, Prohibited Conduct (bullet list),
  Termination, Limitation of Liability, Changes to Terms). Static copy — port verbatim.

## G3. `info/PrivacyScreen.tsx` — key `privacy` — HTML: `privacy.html` (PUBLIC pre-auth)
- Same pattern as Terms; AppBar "Privacy Policy" + conditional bell. 8 static sections
  (Information We Collect, How We Use Your Information, Data Security, Payment Information,
  Information Sharing, Cookies, Your Rights, Contact Us — contact "support@gamingapp.com").
  Note: text mentions Argon2id/JWT (historical copy) — keep verbatim.

## G4. `info/OfflineScreen.tsx` — key `offline` — HTML: `offline.html`
- **Behavior:** auto-recovery — on mount and on every `online` event, if `navigator.onLine` →
  `back()` (returns to the screen that was pushed under it; AppShell pushed `offline` when the
  browser went offline).
- **UI:** light gradient `#f0f4f8→#dbe4ec` (fixed, not token-driven); 256px ghost illustration
  (remote PNG — native must bundle an offline illustration) + blurred ground shadow ellipse;
  "OOPSS!" (display-lg text-primary); body "Something went wrong. Try refreshing the page or
  checking your connection. We'll see you in a moment."; primary button **"Try again"** →
  `window.location.reload()` (native: retry/refresh current screen).
- **Shared:** none.

---

## Appendix: screen → HTML design-source map (quick reference)

| Screen key | HTML file | Screen key | HTML file |
|---|---|---|---|
| login | index.html | myTeam | myteam.html + teamcreationdone.html |
| signup | signup.html | teamCreation | teamcreation.html |
| signupStep1/2/3 | signupstep1/2/3.html | teamCreationDone | teamcreationdone.html |
| home | home.html | friends | freinds.html |
| tournaments | tournament.html | chat | chat.html |
| tournamentDetails | tournamentdetails.html | referEarn | refer&earn.html |
| myTournament | mytournament.html | tasks | tasks.html |
| results | results.html | notifications | notification.html |
| wallet | wallet.html | profile | profile.html |
| deposit | deposit.html | editProfile | editprofile.html |
| depositConfirm | depositconfirm.html | myStats | mystats.html |
| depositSuccess | — (created) | achievements | achievements.html |
| withdraw | withdraw.html | leaderboard | leaderboard.html |
| confirmWithdraw | confirmwithdraw.html | bindAccount | bindaccount.html |
| withdrawSuccess | — (created) | bindAccountSuccess | bindaccountsuccess.html |
| transferMoney | transfermoney.html | adminPanel | — (native admin UI) |
| transferSuccess | — (created) | hostTournament | hosttournament.html |
| selectBank | selectbank.html | hostTournamentCreation | hosttournamentcreation.html |
| about | — (created) | hostTournamentSuccess | hosttournamentsuccess.html |
| terms | terms.html | hostTournamentCard | hosttournamentcard.html |
| privacy | privacy.html | hostTournamentDetails | hosttournamentdetails.html |
| offline | offline.html | admin* sections | — (native admin UI) |
