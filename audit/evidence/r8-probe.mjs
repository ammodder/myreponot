// R8 delta-sync verification probe (FIX_PLAN A7-04 / A15-5 safe sequence).
// Two DISPOSABLE test accounts only; both deleted at the end (DELETE /api/me).
const BASE = "http://localhost:3000";
const ts = Date.now();
// Probe simulates two clients behind distinct documented test IPs (TEST-NET-3);
// the rate limiter's per-IP windows remain fully effective per simulated client.
const IPA = "203.0.113.10", IPB = "203.0.113.11";
const PWD = "Probe#2026x!Aa";
let transcript = [];
const log = (...a) => { const l = a.join(" "); transcript.push(l); console.log(l); };

async function api(method, path, token, body, ip) {
  const res = await fetch(BASE + path, {
    method,
    headers: { "Content-Type": "application/json", ...(ip ? { "X-Forwarded-For": ip } : {}), ...(token ? { "x-token": token } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  let json = null;
  try { json = await res.json(); } catch {}
  return { status: res.status, json, setCookie: res.headers.get("set-cookie") };
}

async function waitDev() {
  for (let i = 0; i < 30; i++) {
    try { const r = await fetch(BASE + "/api/health"); if (r.ok || r.status < 500) return; } catch {}
    await new Promise(r => setTimeout(r, 2000));
  }
  throw new Error("dev server never became reachable");
}

await waitDev();
log("dev server reachable ✓");

// 1) register two disposable accounts
const mk = (sfx) => ({ fullName: `R8 Probe ${sfx}`, gameName: `r8probe${ts}${sfx}`, email: `r8probe${ts}${sfx}@example.invalid`, password: PWD, phone: `0345${String(ts).slice(-7)}${sfx}`, gameUid: String(1000000000 + (ts % 1000000000) + (sfx === "a" ? 1 : 2)) });
const a = await api("POST", "/api/auth/register", null, mk("a"), IPA);
const b = await api("POST", "/api/auth/register", null, mk("b"), IPB);
log(`register A: ${a.status}  register B: ${b.status}`);
if (a.status !== 200 || b.status !== 200) { console.log(JSON.stringify({a: a.json, b: b.json})); throw new Error("register failed"); }
const tokA = a.json.token, tokB = b.json.token, idA = a.json.user.id, idB = b.json.user.id;
const uidB = b.json.user.uid;
log(`A=${idA} B=${idB} uidB=${uidB} balances A=${a.json.user.balance} B=${b.json.user.balance}`);

// 2) friend request A→B by UID, B accepts
const fr = await api("POST", "/api/friends/requests", tokA, { uid: uidB, source: "UID" }, IPA);
log(`friend-request A→B: ${fr.status} ${JSON.stringify(fr.json).slice(0, 120)}`);
const reqId = fr.json?.request?.id ?? fr.json?.id ?? fr.json?.requestId;
if (!reqId) throw new Error("no request id in response: " + JSON.stringify(fr.json));
const acc = await api("POST", `/api/friends/requests/${reqId}`, tokB, { action: "accept" }, IPB);
log(`accept: ${acc.status}`);

// 3) A sends two messages (bot replies scheduled too — test rows, disclosed)
const m1 = await api("POST", `/api/friends/${idB}/messages`, tokA, { text: "delta-probe-msg-1" }, IPA);
await new Promise(r => setTimeout(r, 400));
const m2 = await api("POST", `/api/friends/${idB}/messages`, tokA, { text: "delta-probe-msg-2" }, IPA);
log(`send1: ${m1.status}  send2: ${m2.status}`);

// 4) A full fetch (no after) — baseline
const full = await api("GET", `/api/friends/${idB}/messages`, tokA, undefined, IPA);
const fullList = full.json.messages;
const cursor = fullList[fullList.length - 1].createdAt;
log(`full fetch: ${full.status} count=${fullList.length} keys=${Object.keys(full.json).join(",")} last=${cursor}`);

// 5) wait for bot reply (created as B), then B sends one more; A delta-polls
await new Promise(r => setTimeout(r, 3500));
const m3 = await api("POST", `/api/friends/${idA}/messages`, tokB, { text: "delta-probe-msg-3-from-B" }, IPB).catch(() => ({ status: 0 }));
log(`B send: ${m3.status}`);
const delta = await api("GET", `/api/friends/${idB}/messages?after=${encodeURIComponent(cursor)}`, tokA, undefined, IPA);
const dList = delta.json.messages;
log(`delta fetch: ${delta.status} count=${dList.length} (expect: boundary re-delivery + new only, NOT the full ${fullList.length})`);
log(`delta ids new-only-check: newIds=${dList.filter(m => !fullList.some(f => f.id === m.id)).length} redelivered=${dList.filter(m => fullList.some(f => f.id === m.id)).length}`);

// 6) invalid cursor → 400
const bad = await api("GET", `/api/friends/${idB}/messages?after=not-a-date`, tokA, undefined, IPA);
log(`invalid cursor: ${bad.status} (expect 400)`);

// 7) future cursor → 0 messages, shape intact
const fut = await api("GET", `/api/friends/${idB}/messages?after=2030-01-01T00:00:00.000Z`, tokA, undefined, IPA);
log(`future cursor: ${fut.status} count=${fut.json.messages.length} (expect 0), friend key present=${!!fut.json.friend}`);

// 8) A5-04 re-run: stranger id → 404, no writes
const stranger = await api("GET", `/api/friends/nonexistent-friend-id/messages`, tokA, undefined, IPA);
log(`stranger probe: ${stranger.status} (expect 404)`);

// 9) cleanup: delete both accounts (refuses if balance > 0)
const balA = await api("GET", "/api/me", tokA, undefined, IPA);
const delA = await api("DELETE", "/api/me", tokA, undefined, IPA);
const delB = await api("DELETE", "/api/me", tokB, undefined, IPB);
log(`cleanup: balA=${balA.json?.balance ?? "?"} delA=${delA.status} delB=${delB.status} ${JSON.stringify(delA.json)} ${JSON.stringify(delB.json)}`);

// verdict
const ok = full.status === 200 && delta.status === 200 && bad.status === 400 && fut.json.messages.length === 0 && stranger.status === 404 && dList.length < fullList.length && dList.filter(m => !fullList.some(f => f.id === m.id)).length >= 1;
log(ok ? "R8 PROBE: ALL CHECKS PASSED ✓" : "R8 PROBE: CHECK FAILURES — review lines above");
