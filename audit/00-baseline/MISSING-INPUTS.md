# MISSING INPUTS — what I need from the owner (2026-09-19)

## A. URGENT — caused by the sandbox rollback (needs your answer, not files)
1. **Master Command verbatim text.** The original word-for-word file was lost. I rebuilt the
   rules from my conversation record (`audit/MASTER_RULES.md`) but it is NOT verbatim.
   → Please PASTE the Master Command text here as a message and I will restore it exactly.
2. **Do you still have the last downloaded `AreenaxNativeAndroid.zip`?** (the ~1.09 MB file,
   sha256 starting 570c457f). It contained the FINAL app (Tasks 6-11, incl. the finished
   "Midnight Charcoal" dark theme). Those app-side changes are gone from this workspace.
   - If YES: send/point me to it and I can restore the final app source from it (with your approval).
   - If NO: the Task 6-11 app changes (dark-theme polish etc.) must be re-applied from the
     recorded specs — only with your approval, and only in Phase 2, not now.

## B. Information needed for verification (paste as text when you have it)
3. A build/test machine or Android Studio setup (with emulator or a phone) — until then every
   runtime claim stays NOT VERIFIED. (Alternatively: approve that I keep using the JDK+SDK I
   bootstrapped here for compile-only checks.)
4. Signing material for release: keystore file details are SECRET — do NOT paste secrets here;
   only tell me whether you have one and where you keep it.
5. The live backend domain (to replace the placeholder `app_base_url` in strings.xml at deploy time).
6. Staging test credentials: one test account per role (user, moderator, finance admin, super admin)
   + a test tournament you control. Paste only THROWAWAY test credentials, never real ones.
7. Decision on push notifications: the app currently has NO Firebase/FCM code at all (verified in
   manifest + dependencies). If you want push later, that is a new feature (ask-first list).

## C. Decisions already pending from before (C1-C6, from the conversation record — re-confirm)
- C1 "Alphla Bank" spelling: keep (web parity) or fix to "Allied/Alphla"? Your call.
- C2 Results-screen wording parity with web.
- C3 Chat online-status + date separators are static (not live).
- C4 About-screen version: server-driven (`settings.version`, fallback "1.2.0") vs versionName
  "2.0.0" — which version story is correct? (Corrected 2026-09-19: earlier note claiming a
  hardcoded "1.0.0" was wrong — AboutScreen.kt:106.)
- C5 Friend-recommendation demo data vs the no-demo-data rule.
- C6 No account-deletion flow exists (Google Play policy risk for release).
- C7 (new) Approve the minimal compile-blocker fixes (see 02-findings/PHASE0-BLOCKERS.md) —
  without them the app cannot build at all.
- C9 (new) Deposit fallback account numbers are HARDCODED when `settings.depositAccounts` is
  empty — a real-money path. Confirm the fallbacks or remove them.

## D. Rule reminder
I never ask you to upload files — paste everything as text in the chat. Secrets never go into
code, logs, reports, or the chat.
