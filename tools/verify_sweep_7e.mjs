// Task 7-e security sweep — static hygiene verification:
//  (D1) char-aware brace/paren/bracket balance on every .kt file
//  (D2) every R.drawable.*/R.string.*/R.font.*/R.color.* reference resolves
//       to a file in app/src/main/res
//  (D3) ScreenKeys route keys are all consumed by AppNavHost when()s (spot check)
import { readdirSync, readFileSync, statSync, existsSync } from "node:fs";
import { join, relative } from "node:path";

const ROOT = "/home/z/my-project/AreenaxNativeAndroid";
const APP = join(ROOT, "app/src/main");

function walk(dir, out = []) {
  for (const e of readdirSync(dir)) {
    const p = join(dir, e);
    const s = statSync(p);
    if (s.isDirectory()) walk(p, out);
    else out.push(p);
  }
  return out;
}

// ---- strip comments + string/char literals (char-aware) --------------------
function stripForBalance(src) {
  let out = "";
  let i = 0;
  const n = src.length;
  let mode = "code"; // code | line | block | str | tstr | chr
  while (i < n) {
    const c = src[i];
    const d = src[i + 1];
    if (mode === "code") {
      if (c === "/" && d === "/") { mode = "line"; out += "  "; i += 2; continue; }
      if (c === "/" && d === "*") { mode = "block"; out += "  "; i += 2; continue; }
      if (c === '"') {
        // triple-quoted?
        if (src.slice(i, i + 3) === '"""') { mode = "tstr"; out += "   "; i += 3; continue; }
        mode = "str"; out += " "; i += 1; continue;
      }
      if (c === "'") { mode = "chr"; out += " "; i += 1; continue; }
      out += c; i += 1; continue;
    }
    if (mode === "line") {
      if (c === "\n") { mode = "code"; out += c; } else out += " ";
      i += 1; continue;
    }
    if (mode === "block") {
      if (c === "*" && d === "/") { mode = "code"; out += "  "; i += 2; continue; }
      out += c === "\n" ? "\n" : " ";
      i += 1; continue;
    }
    if (mode === "str" || mode === "tstr") {
      const term = mode === "str" ? '"' : '"""';
      if (c === "\\") { out += "  "; i += 2; continue; } // escape swallows next char
      if (mode === "str" && c === '"') { mode = "code"; out += " "; i += 1; continue; }
      if (mode === "tstr" && src.slice(i, i + 3) === '"""') { mode = "code"; out += "   "; i += 3; continue; }
      // NOTE: Kotlin "$expr" interpolation inside strings can contain braces —
      // count them (they DO produce real code braces in the language grammar
      // sense; unbalanced ones would be a compile error anyway).
      if (c === "$" && d === "{" && mode === "str") {
        // inline interpolation — emit nothing but keep scanning as code? keep simple:
        out += "  "; i += 2; continue;
      }
      out += c === "\n" ? "\n" : " ";
      i += 1; continue;
    }
    if (mode === "chr") {
      if (c === "\\") { out += "  "; i += 2; continue; }
      if (c === "'") { mode = "code"; out += " "; i += 1; continue; }
      out += " "; i += 1; continue;
    }
  }
  return out;
}

const ktFiles = walk(APP).filter((f) => f.endsWith(".kt"));
let bad = 0;
for (const f of ktFiles) {
  const stripped = stripForBalance(readFileSync(f, "utf8"));
  const counts = { "{": 0, "}": 0, "(": 0, ")": 0, "[": 0, "]": 0 };
  for (const ch of stripped) if (ch in counts) counts[ch] += 1;
  const pairs = [["{", "}"], ["(", ")"], ["[", "]"]];
  const unbalanced = pairs.filter(([o, cl]) => counts[o] !== counts[cl]);
  if (unbalanced.length) {
    bad++;
    console.log(`UNBALANCED ${relative(ROOT, f)} — ` +
      unbalanced.map(([o, cl]) => `${o}=${counts[o]} ${cl}=${counts[cl]}`).join(", "));
  }
}
console.log(`D1 brace balance: ${ktFiles.length} kt files, ${bad} unbalanced`);

// ---- D2 resource references -------------------------------------------------
const resDir = join(APP, "res");
const drawableRes = new Set(
  walk(join(resDir, "drawable")).map((f) => relative(join(resDir, "drawable"), f).replace(/\.(xml|png)$/, "")),
);
const stringRes = new Set();
const fontRes = new Set(walk(join(resDir, "font")).map((f) => f.split("/").pop().replace(/\.ttf$/, "")));
const colorsXml = readFileSync(join(resDir, "values", "colors.xml"), "utf8");
for (const m of colorsXml.matchAll(/<color name="([^"]+)"/g)) stringRes; // noop guard
const colorRes = new Set([...colorsXml.matchAll(/name="([^"]+)"/g)].map((m) => m[1]));
const stringsXml = readFileSync(join(resDir, "values", "strings.xml"), "utf8");
const strRes = new Set([...stringsXml.matchAll(/<string name="([^"]+)"/g)].map((m) => m[1]));

let badRefs = 0;
for (const f of ktFiles) {
  const text = readFileSync(f, "utf8");
  for (const m of text.matchAll(/R\.drawable\.(\w+)/g)) {
    if (!drawableRes.has(m[1])) { badRefs++; console.log(`MISSING drawable ${m[1]} ← ${relative(ROOT, f)}`); }
  }
  for (const m of text.matchAll(/R\.string\.(\w+)/g)) {
    if (!strRes.has(m[1])) { badRefs++; console.log(`MISSING string ${m[1]} ← ${relative(ROOT, f)}`); }
  }
  for (const m of text.matchAll(/R\.font\.(\w+)/g)) {
    if (!fontRes.has(m[1])) { badRefs++; console.log(`MISSING font ${m[1]} ← ${relative(ROOT, f)}`); }
  }
}
// manifest + res→res refs
const manifest = readFileSync(join(APP, "AndroidManifest.xml"), "utf8");
for (const m of manifest.matchAll(/@drawable\/([\w.]+)/g)) {
  if (!drawableRes.has(m[1])) { badRefs++; console.log(`MISSING manifest drawable ${m[1]}`); }
}
for (const m of manifest.matchAll(/@string\/([\w.]+)/g)) {
  if (!strRes.has(m[1])) { badRefs++; console.log(`MISSING manifest string ${m[1]}`); }
}
const themes = readFileSync(join(resDir, "values", "themes.xml"), "utf8");
for (const m of themes.matchAll(/@color\/([\w.]+)/g)) {
  if (!colorRes.has(m[1])) { badRefs++; console.log(`MISSING theme color ${m[1]}`); }
}
for (const m of themes.matchAll(/@drawable\/([\w.]+)/g)) {
  if (!drawableRes.has(m[1])) { badRefs++; console.log(`MISSING theme drawable ${m[1]}`); }
}
console.log(`D2 resource refs: ${badRefs} missing`);

// ---- D3 ScreenKeys ↔ AppNavHost --------------------------------------------
const JAVA = join(APP, "java/com/areenax/nativeapp");
const navHost = readFileSync(join(JAVA, "core/nav/AppNavHost.kt"), "utf8");
const screenKeys = readFileSync(join(JAVA, "core/nav/ScreenKeys.kt"), "utf8");
const keys = [...screenKeys.matchAll(/const val (\w+) = "(\w+)"/g)].map((m) => ({ name: m[1], key: m[2] }));
let missingRoutes = 0;
for (const k of keys) {
  // routes use the ScreenKeys.X constants (not string literals) in the when()
  if (!navHost.includes(`ScreenKeys.${k.name}`)) { missingRoutes++; console.log(`ROUTE NOT IN NAVHOST: ${k.key}`); }
}
console.log(`D3 routes: ${keys.length} ScreenKeys, ${missingRoutes} not routed`);

// ---- D4 screen composables signature check ---------------------------------
let screenFns = 0;
const screenFiles = ktFiles.filter((f) => f.includes("/ui/screens/"));
for (const f of screenFiles) {
  const text = readFileSync(f, "utf8");
  for (const m of text.matchAll(/fun (\w+Screen)\(env: NavEnv/g)) screenFns++;
}
console.log(`D4 screen fns with (env: NavEnv): ${screenFns} across ${screenFiles.length} files`);
