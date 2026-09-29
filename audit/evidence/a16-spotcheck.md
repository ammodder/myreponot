# A16 defender (Task 19-c) spot-check evidence — 2026-09-22T08:45Z

(a) prisma/seed.ts version claim (R3b: 1.2.0 → 1.0.0)
119:    ["version", "1.0.0"],
  → CONFIRMED (seed.ts:119 = "1.0.0", no 1.2.0 remains: 0 hits)

(b) Tokens.kt 0xFF count (claim ≈108)
108
  → CONFIRMED (exact 108)

(c) heading() sites (claim 17 new across addendum; grep ≥17)
20
  per-file breakdown in a16-spotcheck-heading-files.txt
  new-site math: total 20 − pre-existing AppBar.kt:97(1) + FriendsScreen.kt:733/806(2, A9-02) = 17 new exactly
  → CONFIRMED (20 total ≥ 17; 17-new math exact; 7 files contain heading(), 5 newly modified)

(d) 0xFF[0-9A-Fa-f]{6} outside Tokens.kt/Shadows.kt/ChatScreen.kt (claim 0 code hits)
0
  → CONFIRMED (0 hits; ChatScreen.kt:80 is the sanctioned skip, verified still the only ChatScreen hex: 1)
