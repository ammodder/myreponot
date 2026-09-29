# Tournament App — Master Implementation Plan

> **Status:** In Progress
> **Started:** 2026-09-27

---

## Implementation Approach

Following the mandatory three-step workflow for every item:
1. **DIAGNOSE FIRST** — Inspect existing code before making changes
2. **IMPLEMENT** — Make targeted changes based on diagnosis
3. **RUN AND VERIFY** — Test each change incrementally

---

## PART A — General Frontend Fixes (App-Wide)

### A1: Global App Performance & Startup Optimization
- [ ] Profile `Application.onCreate()` and identify non-critical init
- [ ] Move heavy initialization off startup path using `Lazy`/deferred patterns
- [ ] Generate Baseline Profile for ART pre-compilation
- [ ] Verify Splash Screen API usage
- [ ] Audit Composables for unstable lambdas/objects
- [ ] Add `@Stable`/`@Immutable` annotations where needed
- [ ] Convert inline-computed values to `remember { derivedStateOf { ... } }`
- [ ] Audit all `LazyColumn`/`LazyRow` for stable keys
- [ ] Verify shared OkHttp client with connection pooling
- [ ] Convert sequential API calls to parallel `async`/`awaitAll`
- [ ] Add HTTP caching for non-real-time endpoints
- [ ] Verify Coil image loading with proper sizing
- [ ] Confirm Coil memory + disk cache enabled
- [ ] Enable R8 full mode + resource shrinking
- [ ] Add macrobenchmark or Firebase Performance Monitoring

**Status:** Not Started
**Target:** Cold start < 1.5–2s, navigation < 300ms, 60fps scrolling

---

### A2: App-Wide Broken Layout & Half-Background Audit
- [ ] Grep for `verticalArrangement = Arrangement.Center` on top-level screens
- [ ] Grep for hardcoded/inconsistent background modifiers
- [ ] Produce full flagged-screen list
- [ ] Apply fix pattern to every flagged screen
- [ ] Centralize button text alignment in shared components

**Status:** Not Started

---

### A3: Navigation Fix — Profile Button
- [ ] Read [BottomNav.kt](file:///C:/AreenaxNativeAndroid/app/src/main/java/com/areenax/app/core/ui/BottomNav.kt)
- [ ] Change primary-tab handler from `navigate()` to `replace()`
- [ ] Check ProfileScreen for auto-navigation side effects
- [ ] Test: Profile tap goes directly without intermediate screens

**Status:** Not Started

---

### A4: Notification Badge Layout
- [ ] Read AppBar.kt's BellButton implementation
- [ ] Change badge from fixed `.size(18.dp)` to `widthIn(min = 18.dp)`
- [ ] Test with 1, 2, and 9+ digit counts

**Status:** Not Started

---

### A5: Push Notifications — Functional Fix
- [ ] Diagnose FCM token generation and backend receipt
- [ ] Verify `POST_NOTIFICATIONS` runtime permission (API 33+)
- [ ] Check backend trigger → delivery path
- [ ] Verify messaging service registration in manifest
- [ ] Confirm notification channel setup (API 26+)
- [ ] Test foreground and background/killed-app delivery

**Status:** Not Started
**Priority:** High (0% functioning)

---

### A6: Deposit Page
- [ ] Read [DepositScreen.kt](file:///C:/AreenaxNativeAndroid/app/src/main/java/com/areenax/app/ui/screens/wallet/DepositScreen.kt)
- [ ] Fix bank selector white-bar glitch
- [ ] Replace full-page navigation with `BankSelectSheet` bottom sheet
- [ ] Audit spacing/alignment/information display
- [ ] Profile deposit submission call
- [ ] Parallelize independent calls
- [ ] Add loading/skeleton state

**Status:** Not Started

---

### A7: Confirmation & Status Screens — Redesign
- [ ] Read [DepositConfirmScreen.kt](file:///C:/AreenaxNativeAndroid/app/src/main/java/com/areenax/app/ui/screens/wallet/DepositConfirmScreen.kt)
- [ ] Redesign Deposit Confirmation screen
- [ ] Redesign Withdrawal Confirmation screen
- [ ] Redesign Bind Account Success/Pending screens

**Status:** Not Started

---

### A8: Tournament Creation — Image Upload Logic
- [ ] Read tournament creation flow
- [ ] Remove image upload picker from user flow
- [ ] Bind tournament image to `game.image` automatically
- [ ] Verify/build admin Game default image management

**Status:** Not Started

---

### A9: Screen Revisit Re-Loading Bug (App-Wide)
- [ ] Diagnose shared root cause (navigation scoping, ViewModel lifecycle)
- [ ] Check if ViewModels are scoped to navigation entry vs wider scope
- [ ] Identify if fetch calls fire unconditionally in `init`/`LaunchedEffect(Unit)`
- [ ] Apply fix at shared/base level
- [ ] Spot-check representative screens across app
- [ ] Verify fresh data still loads when genuinely stale

**Status:** Not Started
**Priority:** High (affects entire app)

---

## PART B — 48-Slot Winner Prize Tournament System

> **Scope:** Admin Host Tournament ONLY — does not affect User-created tournaments

### B1: Game & Match Type Management
- [ ] Diagnose existing Game records and Match Type structure
- [ ] Verify Game has unique DB ID
- [ ] Verify Match Types belong to specific `game_id`
- [ ] Confirm deactivation behavior
- [ ] Build Create/Edit/Activate/Deactivate if missing
- [ ] Add backend validation for `match_type_id` → `game_id` pairing

**Status:** Not Started

---

### B2: Tournament Data Model (Host Tournament Only)
- [ ] Diagnose existing tournament table/model
- [ ] Add/confirm: `created_by_type`, `created_by_user_id`
- [ ] Add/confirm: `total_slots=48`, `free_slots=1`, `max_paid_participants=47`
- [ ] Add/confirm: `free_slot_number` (nullable until assigned)
- [ ] Add/confirm: `entry_fee`
- [ ] Add/confirm slot-level structure
- [ ] Add DB constraint preventing duplicate slot occupancy

**Status:** Not Started

---

### B3: Random Free Slot Assignment
- [ ] Implement server-side random selection (1–48)
- [ ] Store at creation time
- [ ] Lock permanently (never regenerate)
- [ ] Test across multiple tournaments

**Status:** Not Started

---

### B4: Free Slot Join Logic
- [ ] Implement "Join — Rs. X" vs "Join Free" button logic
- [ ] Backend determines free slot
- [ ] Free join: no deduction, `is_free_entry=true`
- [ ] Paid join: deduct fee, `is_free_entry=false`
- [ ] Prevent duplicate free slot claims

**Status:** Not Started

---

### B5: Admin Visibility — Free Slot & Free User
- [ ] Read [AdminTournamentsScreen.kt](file:///C:/AreenaxNativeAndroid/app/src/main/java/com/areenax/app/ui/screens/profile/AdminTournamentsScreen.kt)
- [ ] Show Free Slot number in admin details
- [ ] Show Free User name/ID
- [ ] Show Entry Status (FREE/PAID) + Amount for all participants

**Status:** Not Started

---

### B6: Collected Entry Pool
- [ ] Implement `collected_entry_pool = 47 × entry_fee`
- [ ] Exclude free participant from calculation

**Status:** Not Started

---

### B7: Centralized Calculation Service
- [ ] Create/locate `TournamentPrizeCalculationService`
- [ ] Implement all prize calculations in ONE place
- [ ] Consolidate any duplicated formula code
- [ ] Reuse for: Preview, Admin view, Details, Settlement

**Status:** Not Started

---

### B8: Top-9 Prize Calculation

- [ ] Implement fixed percentages (1st: 10%, 2nd: 8.510638%, 3rd: 7.234043%, 4th: 6.382979%, 5th: 5.531915%, 6th: 5.106383%, 7th: 4.680851%, 8th: 4.255319%, 9th: 3.404255%)
- [ ] Round UP to next Rs. 5 for each prize
- [ ] Calculate `top9_prize_pool = SUM(final_top9_prizes)`
- [ ] Validate against Rs. 50 reference case (pool Rs. 2,350 → Rs. 1,295 total)

**Status:** Not Started

---

### B9: Consolation ("Loser Prize") Calculation
- [ ] Implement 39 participants, 35.574468% total, ≈0.912166% each
- [ ] Round each UP to whole rupee
- [ ] Calculate `consolation_pool = final_consolation × 39`
- [ ] Display as "Loser Prize" in UI

**Status:** Not Started

---

### B10: Prize Pool Display Rule (Post-Creation)

- [ ] Audit all "Prize Pool" displays
- [ ] Show ONLY Total `top9_prize_pool` as Prize pool on calculation preview card when user create tournaments in host games/overview
- [ ] Never show collected\_entry\_pool, consolation\_pool, or remaining\_balance

**Status:** Not Started

---

### B11: Remaining Balance

- [ ] Implement `remaining_balance = collected − top9 − consolation`
- [ ] Reject settlement if negative
- [ ] Remaining balance is the company balance so don't expose it into in user panel anywhere only admin keep track this

**Status:** Not Started

---

### B12: Calculation Preview (Creation Screen)

- [ ] Read [HostTournamentScreen.kt](file:///C:/AreenaxNativeAndroid/app/src/main/java/com/areenax/app/ui/screens/host/HostTournamentScreen.kt)
- [ ] Implement live preview updating on Entry Fee change
- [ ] Display Match type, Total slots, Entry fee, prize pool(total top 9 calculated prize according to entry fee and our rules) and Loser prize (`consolation prize`)
- [ ] Show final rounded values only

**Status:** Not Started

---

### B13: Free-User Result Rules
- [ ] Implement Rank #1 → Refund Mode (all paid get entry fee back)
- [ ] Implement Rank #2–#9 → normal distribution
- [ ] Implement Rank #10+ → normal consolation
- [ ] Label refund as "Refund", not "Loser Prize"

**Status:** Not Started

---

### B14: Settlement Order & Idempotency
- [ ] Implement 6-step settlement order
- [ ] Ensure idempotency (no double payments)
- [ ] Reuse existing idempotency patterns

**Status:** Not Started

---

### B15: Admin Host Tournament Creation Form
- [ ] Read existing admin creation form
- [ ] Lock slots at 48 (not editable)
- [ ] Admin selects: Game, Match Type, Name, Entry Fee, Date, Time
- [ ] Derive Top-9 prizes from B7 service (never manual entry)

**Status:** Not Started

---

### B16: Wallet & Join Transaction Safety
- [ ] Implement paid join transaction with full validation
- [ ] Implement free join (no deduction)
- [ ] Extend existing join transaction code
- [ ] Guard against all duplicate/negative scenarios

**Status:** Not Started

---

### B17: Financial Transaction Audit Trail
- [ ] Implement transaction logging for all operations
- [ ] Record: transaction_id, user_id, tournament_id, amount, type, etc.
- [ ] Reuse existing transaction-logging pattern

**Status:** Not Started

---

### B18: Immutability After Participation

- [ ] Lock Entry Fee when tournament is created/hosted
- [ ] Lock Slot Count when tournament is created/hosted
- [ ] Lock Free Slot when tournament is created/hosted
- [ ] Lock Top-9/Consolation percentages when tournament is created/hosted

**Status:** Not Started

---

### B19: Backend Security & Validation
- [ ] Server-side validation for all financial operations
- [ ] Role checks for admin operations
- [ ] Transaction/row locking for concurrent joins
- [ ] CSRF/rate-limiting

**Status:** Not Started

---

## Verification Checklist

### Part A Verification
- [ ] Cold start 5x, consistently under ~2s
- [ ] Profile tab direct navigation (no intermediate screens)
- [ ] All flagged screens: no half-background, proper alignment
- [ ] Deposit flow with bank sheet
- [ ] Push notifications (foreground + background)
- [ ] Notification badge with 1, 2, 10+ unread
- [ ] Three redesigned confirmation screens
- [ ] User tournament creation (no image upload)
- [ ] Screen revisit test (multiple screens)

### Part B Verification
- [ ] 5+ tournaments with different free slot numbers
- [ ] Free slot join (no deduction)
- [ ] Normal slot join (correct deduction)
- [ ] Admin details view (all fields)
- [ ] Rs. 50 calculation preview
- [ ] Post-creation prize pool display
- [ ] Settlement: free user Rank #1 (refund mode)
- [ ] Settlement: free user Rank #5 (normal)
- [ ] Settlement: free user Rank #20 (consolation)
- [ ] Settlement idempotency test
- [ ] Negative balance rejection test
- [ ] User tournaments unaffected

---

## Notes

- Deep link intent filter added to `AndroidManifest.xml` ✓
- App successfully runs on emulator ✓
- FCM token generation confirmed ✓
- Current focus: Systematic implementation of master plan
