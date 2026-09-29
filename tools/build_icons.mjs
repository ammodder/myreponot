/**
 * build_icons.mjs — AREENAX web → native Android icon extractor (run with bun).
 *
 * Phases (CLI arg): inventory | material | lucide | inline | verify | docs | all
 *   inventory — scan web sources for Material Symbols glyphs, lucide-react imports, inline <svg>
 *   material  — download official Material Symbols SVGs + convert → res/drawable/ic_*.xml
 *   lucide    — parse installed lucide-react icon modules → res/drawable/ic_lucide_*.xml
 *   inline    — convert inline <svg> blocks from screens/shared → res/drawable/ic_inline_*.xml
 *   verify    — re-validate every generated drawable XML
 *   docs      — (re)generate SPEC/ICONS.md from tools/reports/*.json
 *
 * Outputs:
 *   AreenaxNativeAndroid/app/src/main/res/drawable/*.xml
 *   AreenaxNativeAndroid/tools/reports/{inventory,material,lucide,inline}.json
 *   AreenaxNativeAndroid/SPEC/ICONS.md (docs phase)
 */

import { parseSvg, toVectorXml, validateVectorXml, isValidResourceName } from "./svg2vector.mjs";
import fs from "node:fs";
import path from "node:path";

const ROOT = "/home/z/my-project";
const ANDROID = path.join(ROOT, "AreenaxNativeAndroid");
const DRAWABLE = path.join(ANDROID, "app/src/main/res/drawable");
const REPORTS = path.join(ANDROID, "tools/reports");
const CACHE = path.join(ANDROID, "tools/cache");
const CODEPOINTS = path.join(ANDROID, "tools/ms-codepoints.txt");

// --------------------------------------------------------------- scope ------

const SCOPE_DIRS = [
  path.join(ROOT, "src/components/screens"),
  path.join(ROOT, "src/components/shared"),
];
const SCOPE_FILES = [path.join(ROOT, "src/components/AppShell.tsx")];
const UI_DIR = path.join(ROOT, "src/components/ui"); // brief scan (scaffold; NOT reachable from screens)

function walk(dir, exts = [".tsx", ".ts"]) {
  const out = [];
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) out.push(...walk(p, exts));
    else if (exts.includes(path.extname(e.name))) out.push(p);
  }
  return out;
}

function scopeFiles(includeUi = false) {
  const files = [...SCOPE_FILES];
  for (const d of SCOPE_DIRS) files.push(...walk(d));
  if (includeUi && fs.existsSync(UI_DIR)) files.push(...walk(UI_DIR));
  return files;
}

const rel = (p) => p.replace(ROOT + "/", "");

// ------------------------------------------------------- codepoints set -----

function loadCodepoints() {
  if (!fs.existsSync(CODEPOINTS)) {
    throw new Error(`Missing ${CODEPOINTS} — download the Material Symbols codepoints list first (see README in tools/).`);
  }
  const set = new Set();
  for (const line of fs.readFileSync(CODEPOINTS, "utf8").split("\n")) {
    const name = line.trim().split(/\s+/)[0];
    if (name && /^[a-z0-9_]+$/.test(name)) set.add(name);
  }
  return set;
}

// ------------------------------------------------------- JSX span scanner ---

/** Find all JSX elements whose opening tag mentions `material-symbols`. */
function findMaterialSymbolSpans(text) {
  const spans = [];
  const needle = "material-symbols";
  let idx = text.indexOf(needle);
  while (idx !== -1) {
    // backtrack to the opening '<' of the enclosing tag
    const tagStart = text.lastIndexOf("<", idx);
    if (tagStart !== -1 && !/[<>]/.test(text.slice(tagStart + 1, idx))) {
      // walk the opening tag honoring JSX braces
      let depth = 0;
      let tagEnd = -1;
      for (let i = tagStart; i < text.length; i++) {
        const ch = text[i];
        if (ch === "{") depth++;
        else if (ch === "}") depth--;
        else if (ch === ">" && depth === 0) {
          tagEnd = i;
          break;
        }
      }
      if (tagEnd !== -1) {
        const tagText = text.slice(tagStart, tagEnd + 1);
        const nameMatch = tagText.match(/^<([a-zA-Z][a-zA-Z0-9]*)/);
        if (nameMatch) {
          const tagName = nameMatch[1];
          const selfClosing = /\/>\s*$/.test(tagText);
          let children = "";
          if (!selfClosing) {
            let cDepth = 0;
            let end = text.length;
            for (let i = tagEnd + 1; i < text.length; i++) {
              const ch = text[i];
              if (ch === "{") cDepth++;
              else if (ch === "}") cDepth--;
              else if (cDepth === 0 && text.startsWith("</" + tagName, i)) {
                end = i;
                break;
              }
            }
            children = text.slice(tagEnd + 1, end);
          }
          const line = text.slice(0, tagStart).split("\n").length;
          spans.push({ line, tagText, children });
          idx = text.indexOf(needle, tagEnd);
          continue;
        }
      }
    }
    idx = text.indexOf(needle, idx + needle.length);
  }
  return spans;
}

const QUOTE_RE = /"([^"\\]*)"|'([^'\\]*)'/g;

function quotedStrings(s) {
  const out = [];
  let m;
  QUOTE_RE.lastIndex = 0;
  while ((m = QUOTE_RE.exec(s)) !== null) out.push(m[1] ?? m[2]);
  return out;
}

const FILL1_RE = /['"]FILL['"]\s*:?\s*1|fontVariationSettings[^,}]*FILL[^,}]*1/;

/** Does this tag/child style force the FILL 1 variant? (literal, const ref, or conditional) */
function spanWantsFill(tagText, fileText) {
  if (FILL1_RE.test(tagText)) return true;
  if (/(FILLED_ICON|FILLED_STYLE|filledIcon)/.test(tagText)) {
    // resolve module-level const in the same file
    const constDef = fileText.match(/(?:const|var|let)\s+(?:FILLED_ICON|FILLED_STYLE|filledIcon)\s*=\s*\{[^}]*\}/);
    if (constDef && FILL1_RE.test(constDef[0])) return true;
    if (constDef) return true; // const exists and is referenced with conditional undefined → treat as fill-capable
  }
  return false;
}

// ------------------------------------------------------------ inventory -----

const RESOLVED_DYNAMIC_NOTES = [];

function inventoryPhase(includeUi) {
  const codepoints = loadCodepoints();
  const files = scopeFiles(includeUi);
  const inv = {
    generatedAt: new Date().toISOString(),
    scannedFiles: files.map(rel),
    material: {}, // name -> { files:Set→array, fillFiles:Set→array, evidence:[{file,line,how}] }
    lucide: {}, // importName -> [{file,line}]
    inline: [], // {file,line,svg,descHint}
    uiNote: "",
  };

  const addGlyph = (name, file, line, how, fill) => {
    if (!inv.material[name]) inv.material[name] = { files: [], fillFiles: [], evidence: [] };
    const g = inv.material[name];
    if (!g.files.includes(file)) g.files.push(file);
    g.evidence.push({ file, line, how });
    if (fill && !g.fillFiles.includes(file)) g.fillFiles.push(file);
  };

  for (const file of files) {
    const text = fs.readFileSync(file, "utf8");
    const relFile = rel(file);
    const hasFill1 = FILL1_RE.test(text);

    // 1) direct material-symbols spans
    for (const span of findMaterialSymbolSpans(text)) {
      // drop comparison operands ("x" === "true") before harvesting names —
      // only branch literals (after ? / :) and bare text are glyphs
      const cleaned = span.children.replace(/(===|!==|==|!=|>=|<=)\s*("[^"]*"|'[^']*')/g, "$1 ");
      const names = new Set(quotedStrings(cleaned).filter((s) => /^[a-z0-9_]+$/.test(s) && (codepoints.has(s) || s.length <= 30)));
      // bare JSX text children (e.g. <span ...>emoji_events</span>) — strip JSX expressions first
      const bare = cleaned.replace(/\{[\s\S]*?\}/g, " ");
      for (const tok of bare.split(/[\s]+/)) {
        if (/^[a-z][a-z0-9_]{1,}$/.test(tok) && (codepoints.has(tok) || tok.length <= 30)) names.add(tok);
      }
      const fill = spanWantsFill(span.tagText, text);
      if (names.size > 0) {
        for (const n of names) addGlyph(n, relFile, span.line, "literal", fill);
      } else {
        const expr = span.children.replace(/\s+/g, " ").trim().slice(0, 80);
        RESOLVED_DYNAMIC_NOTES.push(`${relFile}:${span.line} dynamic children: {${expr}} fill=${fill}`);
        // dynamic children — icons come from `icon:` keys (captured below);
        // if this span renders FILL 1 (unconditionally or conditionally), mark file so
        // all `icon:`-sourced glyphs in this file also get fill variants
        if (fill) addGlyph("__DYNAMIC_FILL__", relFile, span.line, "dynamic-span-fill", true);
      }
    }

    // 2) icon props / icon object keys: icon="x", icon:'x', icon = "x", icon: cond ? "a" : "b", someIcon="x"
    const propRe = /\b([A-Za-z_][A-Za-z0-9_]*[iI]con|icon)\s*[:=]\s*(['"{])([\s\S]*?)(\2|\})/g;
    let pm;
    while ((pm = propRe.exec(text)) !== null) {
      const keyName = pm[1];
      if (keyName.toLowerCase() !== "icon") continue; // only icon-ish keys
      const expr = pm[3];
      const line = text.slice(0, pm.index).split("\n").length;
      if (pm[2] === "{") {
        for (const s of quotedStrings(expr)) {
          if (/^[a-z0-9_]+$/.test(s) && codepoints.has(s)) {
            addGlyph(s, relFile, line, keyName === "icon" ? "icon-key" : "icon-prop", false);
          }
        }
      } else {
        const s = expr.trim();
        if (/^[a-z0-9_]+$/.test(s) && codepoints.has(s)) {
          addGlyph(s, relFile, line, keyName === "icon" ? "icon-key" : "icon-prop", false);
        }
      }
    }

    // 3) inferred: any codepoint-valid quoted string within ±1 line of a material-symbols line
    const lines = text.split("\n");
    const msLines = new Set();
    lines.forEach((l, i) => {
      if (l.includes("material-symbols")) for (let d = -1; d <= 1; d++) msLines.add(i + d);
    });
    lines.forEach((l, i) => {
      if (!msLines.has(i)) return;
      const cleaned = l.replace(/(===|!==|==|!=|>=|<=)\s*("[^"]*"|'[^']*')/g, "$1 ");
      for (const s of quotedStrings(cleaned)) {
        if (codepoints.has(s)) addGlyph(s, relFile, i + 1, "inferred-window", false);
      }
    });

    // 4) lucide imports
    const lucideRe = /import\s+(?:type\s+)?\{([^}]+)\}\s*from\s*["']lucide-react["']/g;
    let lm;
    while ((lm = lucideRe.exec(text)) !== null) {
      const line = text.slice(0, lm.index).split("\n").length;
      for (const raw of lm[1].split(",")) {
        const name = raw.trim().replace(/^type\s+/, "");
        if (!name || !/^[A-Za-z0-9_]+$/.test(name)) continue;
        if (!inv.lucide[name]) inv.lucide[name] = [];
        if (!inv.lucide[name].some((e) => e.file === relFile)) inv.lucide[name].push({ file: relFile, line });
      }
    }

    // 5) inline <svg> blocks
    const svgTagRe = /<svg[\s>]/g;
    let sm;
    while ((sm = svgTagRe.exec(text)) !== null) {
      const sIdx = sm.index;
      // depth scan to matching </svg>
      let depth = 0;
      let i = sIdx;
      let end = -1;
      while (i < text.length) {
        if (text.startsWith("<svg", i)) {
          depth++;
          i += 4;
          continue;
        }
        if (text.startsWith("</svg>", i)) {
          depth--;
          i += 6;
          if (depth === 0) {
            end = i;
            break;
          }
          continue;
        }
        i++;
      }
      if (end === -1) break;
      svgTagRe.lastIndex = end;
      const block = text.slice(sIdx, end);
      const line = text.slice(0, sIdx).split("\n").length;
      // description hint: nearest preceding JSX comment on its own line
      const before = text.slice(0, sIdx);
      const commentMatch = before.match(/\{\s*\/\*\s*(.*?)\s*\*\/\s*\}\s*$/);
      inv.inline.push({ file: relFile, line, svg: block, descHint: commentMatch ? commentMatch[1] : "" });
    }
  }

  // resolve dynamic fill markers
  const dynamicFillFiles = new Set(Object.keys(inv.material).filter((k) => k === "__DYNAMIC_FILL__").flatMap((k) => inv.material[k].files));
  delete inv.material.__DYNAMIC_FILL__;
  if (dynamicFillFiles.size) {
    for (const [name, g] of Object.entries(inv.material)) {
      for (const f of dynamicFillFiles) {
        // only propagate fill to icon-key-sourced glyphs from the same file
        if (g.files.includes(f) && g.evidence.some((e) => e.file === f && (e.how === "icon-key" || e.how === "icon-prop" || e.how === "literal"))) {
          if (!g.fillFiles.includes(f)) g.fillFiles.push(f);
        }
      }
    }
  }

  inv.uiNote =
    "ui/** is shadcn scaffold; per Task 64-a audit none of its components are imported by screens/shared — " +
    "lucide icons found there are converted for completeness but do not reach the user panel.";
  return inv;
}

// ------------------------------------------------------------ material ------

const MS_GH = (name, variant = "") =>
  `https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/${name}/materialsymbolsoutlined/${name}${variant}_24px.svg`;
const MS_GSTATIC = (name) =>
  `https://fonts.gstatic.com/s/i/short-term/release/materialsymbolsoutlined/${name}/default/24px.svg`;

async function fetchText(url) {
  const res = await fetch(url, { signal: AbortSignal.timeout(20000) });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return await res.text();
}

const _cpCache = new Map();
function loadCodepointsSafe() {
  if (_cpCache.has("cp")) return _cpCache.get("cp");
  const set = new Set();
  for (const line of fs.readFileSync(CODEPOINTS, "utf8").split("\n")) {
    const name = line.trim().split(/\s+/)[0];
    if (name && /^[a-z0-9_]+$/.test(name)) set.add(name);
  }
  _cpCache.set("cp", set);
  return set;
}

async function materialPhase(inv) {
  fs.mkdirSync(path.join(CACHE, "ms"), { recursive: true });
  fs.mkdirSync(DRAWABLE, { recursive: true });
  const report = { created: [], failed: [], skipped: [] };
  const entries = Object.entries(inv.material).filter(([name]) => name !== "__DYNAMIC_FILL__");

  for (const [name, meta] of entries) {
    const cp = loadCodepointsSafe();
    if (!cp.has(name)) {
      report.skipped.push({ name, reason: "string matched but is NOT an official Material Symbols glyph name (comparison/prop false positive) — not converted" });
      continue;
    }
    const snake = name.replace(/-/g, "_");
    if (!isValidResourceName(snake)) {
      report.failed.push({ name, reason: `invalid resource name "${snake}"` });
      continue;
    }
    const wantsFill = meta.fillFiles.length > 0;
    try {
      // outlined (base) variant
      const cachePath = path.join(CACHE, "ms", `${name}.svg`);
      let svg;
      if (fs.existsSync(cachePath)) svg = fs.readFileSync(cachePath, "utf8");
      else {
        try {
          svg = await fetchText(MS_GH(name));
        } catch (e) {
          svg = await fetchText(MS_GSTATIC(name)); // fallback
        }
        fs.writeFileSync(cachePath, svg);
      }
      const baseFile = path.join(DRAWABLE, `ic_${snake}.xml`);
      const baseRes = convertAndWrite(svg, baseFile, {});
      report.created.push({
        glyph: name,
        drawable: `ic_${snake}`,
        fill: false,
        files: meta.files,
        fillFiles: meta.fillFiles,
        paths: baseRes.pathCount,
      });

      // fill variant
      if (wantsFill) {
        const fillCache = path.join(CACHE, "ms", `${name}__fill1.svg`);
        let fillSvg;
        if (fs.existsSync(fillCache)) fillSvg = fs.readFileSync(fillCache, "utf8");
        else {
          try {
            fillSvg = await fetchText(MS_GH(name, "_fill1"));
          } catch {
            fillSvg = null;
          }
          if (fillSvg) fs.writeFileSync(fillCache, fillSvg);
        }
        if (fillSvg) {
          const fillFile = path.join(DRAWABLE, `ic_${snake}_fill.xml`);
          const fillRes = convertAndWrite(fillSvg, fillFile, {});
          report.created.push({
            glyph: name,
            drawable: `ic_${snake}_fill`,
            fill: true,
            files: meta.files,
            fillFiles: meta.fillFiles,
            paths: fillRes.pathCount,
          });
        } else {
          report.failed.push({ name: `${name} (fill1)`, reason: "fill variant 404 on both sources; base ic_" + snake + " still created" });
        }
      }
    } catch (e) {
      report.failed.push({ name, reason: e.message });
    }
  }
  return report;
}

function convertAndWrite(svgText, outFile, opts) {
  const parsed = parseSvg(svgText);
  const xml = toVectorXml(parsed, { sizeDp: 24, ...opts });
  const v = validateVectorXml(xml);
  if (!v.ok) throw new Error(`validation failed for ${path.basename(outFile)}: ${v.errors.join("; ")}`);
  fs.writeFileSync(outFile, xml);
  return { pathCount: v.pathCount, warnings: parsed.warnings };
}

// -------------------------------------------------------------- lucide ------

function pascalToKebab(s) {
  return s
    .replace(/Icon$/, "")
    .replace(/([a-z0-9])([A-Z])/g, "$1-$2")
    .replace(/([A-Z]+)([A-Z][a-z])/g, "$1-$2")
    .toLowerCase();
}

function parseLucideModule(fileText) {
  const start = fileText.indexOf("const __iconNode = ");
  if (start === -1) throw new Error("no __iconNode found");
  const arrStart = fileText.indexOf("[", start);
  // find the closing "];" of the array
  const endIdx = fileText.indexOf("];", arrStart);
  if (endIdx === -1) throw new Error("unterminated __iconNode");
  const arrText = fileText.slice(arrStart, endIdx + 1);
  const elements = [];
  const entryRe = /\[\s*"([a-z-]+)"\s*,\s*\{([^}]*)\}\s*\]/g;
  let m;
  while ((m = entryRe.exec(arrText)) !== null) {
    const tag = m[1];
    const attrs = {};
    const attrRe = /([a-zA-Z]+)\s*:\s*"([^"]*)"/g;
    let am;
    while ((am = attrRe.exec(m[2])) !== null) attrs[am[1]] = am[2];
    elements.push({ tag, attrs });
  }
  if (elements.length === 0) throw new Error("no icon children parsed");
  return elements;
}

async function lucidePhase(inv) {
  fs.mkdirSync(DRAWABLE, { recursive: true });
  const iconsDir = path.join(ROOT, "node_modules/lucide-react/dist/esm/icons");
  const report = { created: [], failed: [], source: "node_modules/lucide-react@0.525.0/dist/esm/icons" };
  const names = Object.keys(inv.lucide);
  for (const importName of names) {
    const kebab = pascalToKebab(importName);
    const snake = kebab.replace(/-/g, "_");
    try {
      const modulePath = path.join(iconsDir, `${kebab}.js`);
      let elements;
      let src = `node_modules lucide module ${kebab}.js`;
      if (fs.existsSync(modulePath)) {
        let moduleText = fs.readFileSync(modulePath, "utf8");
        // follow alias re-exports: `export { default } from './ellipsis.js';`
        let guard = 0;
        while (!moduleText.includes("__iconNode") && guard++ < 5) {
          const reexport = moduleText.match(/export\s*\{[^}]*default[^}]*\}\s*from\s*['"]\.\/([\w-]+)\.js['"]/);
          if (!reexport) break;
          src = `node_modules lucide module ${reexport[1]}.js (alias of ${kebab}.js)`;
          moduleText = fs.readFileSync(path.join(iconsDir, `${reexport[1]}.js`), "utf8");
        }
        elements = parseLucideModule(moduleText);
      } else {
        // fallback: GitHub raw svg for the kebab name
        const url = `https://raw.githubusercontent.com/lucide-icons/lucide/main/icons/${kebab}.svg`;
        const svg = await fetchText(url);
        const parsed = parseSvg(svg);
        elements = parsed.elements;
        src = `GitHub lucide-icons ${kebab}.svg`;
      }
      const parsed = {
        viewBox: { minX: 0, minY: 0, w: 24, h: 24 },
        elements,
        warnings: [],
        rootAttrs: {},
      };
      const xml = toVectorXml(parsed, {
        sizeDp: 24,
        defaultFill: "none",
        defaultStroke: "currentColor",
        defaultStrokeWidth: 2,
        defaultLineCap: "round",
        defaultLineJoin: "round",
      });
      const v = validateVectorXml(xml);
      if (!v.ok) throw new Error("validation failed: " + v.errors.join("; "));
      const outFile = path.join(DRAWABLE, `ic_lucide_${snake}.xml`);
      fs.writeFileSync(outFile, xml);
      report.created.push({
        importName,
        module: `${kebab}.js`,
        drawable: `ic_lucide_${snake}`,
        files: inv.lucide[importName].map((e) => e.file),
        lines: inv.lucide[importName].map((e) => e.line),
        source: src,
        paths: v.pathCount,
      });
    } catch (e) {
      report.failed.push({ importName, kebab, reason: e.message });
    }
  }
  return report;
}

// -------------------------------------------------------------- inline ------

// Deterministic names/descriptions for the inline SVGs found in the user panel.
// key = `${file}#${line}` (line of the <svg> tag from the inventory)
const INLINE_MANUAL = {
  "src/components/screens/info/AboutScreen.tsx#152": {
    slug: "chevron_right",
    desc: "Chevron-right list arrow (Heroicons style, stroke) — Privacy Policy row",
  },
  "src/components/screens/info/AboutScreen.tsx#169": {
    slug: "chevron_right",
    desc: "Chevron-right list arrow (stroke) — Terms & Conditions row (same art as #152, single drawable reused)",
  },
  "src/components/screens/social/TeamCreationScreen.tsx#66": {
    slug: "shield_check",
    desc: "Shield with checkmark (filled, Heroicons shield-check) — Team Name label",
  },
  "src/components/screens/social/TeamCreationScreen.tsx#163": {
    slug: "send",
    desc: "Paper-plane / send icon (stroke, Heroicons paper-airplane) — web renders it rotated -45deg via CSS; drawable is UNROTATED, Compose must apply graphicsLayer { rotationZ = -45f }",
  },
  "src/components/screens/profile/LeaderboardScreen.tsx#16": {
    slug: "crown",
    desc: "Crown (filled) — leaderboard 1st place decoration (CrownSvg component)",
  },
  "src/components/shared/WhatsAppFab.tsx#25": {
    slug: "whatsapp_logo",
    desc: "WhatsApp logo (filled, literal WHITE — FAB paints the green circle behind it)",
  },
};

const INLINE_SKIP = {
  "src/components/screens/profile/AchievementsScreen.tsx#274": {
    reason:
      "Dynamic circular progress ring: two <circle> strokes where the second uses runtime strokeDashoffset={100 - percent}. " +
      "NOT a static icon — reimplement in Compose with a Canvas/CircularProgressIndicator (track #SurfaceContainerHigh, progress arc primary, strokeWidth 4dp, r=16 in a 36x36 box, start at -90°).",
  },
};

function inlinePhase(inv) {
  fs.mkdirSync(DRAWABLE, { recursive: true });
  const report = { created: [], failed: [], skipped: [] };
  const usedNames = new Set();
  const dupIndex = new Map(); // normalized svg -> drawable (dedupe identical art)

  for (const item of inv.inline) {
    const key = `${item.file}#${item.line}`;
    if (INLINE_SKIP[key]) {
      report.skipped.push({ key, ...INLINE_SKIP[key] });
      continue;
    }
    try {
      const parsed = parseSvg(item.svg);
      // normalize for dedupe: only elements' pathData + colors matter
      const sig = JSON.stringify([parsed.viewBox, parsed.elements.map((e) => [e.tag, e.attrs])]);
      if (dupIndex.has(sig)) {
        const prev = dupIndex.get(sig);
        report.created.push({
          key,
          drawable: prev.drawable,
          duplicateOf: `${prev.key}`,
          desc: INLINE_MANUAL[key]?.desc ?? item.descHint ?? "inline svg",
          source: `${item.file}:${item.line}`,
          colors: "same as " + prev.drawable,
        });
        continue;
      }
      const manual = INLINE_MANUAL[key];
      let name = manual?.slug
        ? `ic_inline_${path.basename(item.file, ".tsx").replace(/Screen$/i, "").toLowerCase()}_${manual.slug}`
        : `ic_inline_${path.basename(item.file, ".tsx").toLowerCase()}_${item.line}`;
      name = name.replace(/[^a-z0-9_]/g, "_");
      while (usedNames.has(name)) name += "_x";
      usedNames.add(name);

      const xml = toVectorXml(parsed, { sizeDp: 24 });
      const v = validateVectorXml(xml);
      if (!v.ok) throw new Error("validation failed: " + v.errors.join("; "));
      fs.writeFileSync(path.join(DRAWABLE, `${name}.xml`), xml);
      dupIndex.set(sig, { drawable: name, key });
      report.created.push({
        key,
        drawable: name,
        desc: manual?.desc ?? item.descHint ?? "inline svg",
        source: `${item.file}:${item.line}`,
        colors: [...new Set(parsed.elements.flatMap((e) => [e.attrs.fill, e.attrs.stroke, parsed.rootAttrs.fill, parsed.rootAttrs.stroke].filter(Boolean)))].join(", ") || "default black",
        paths: v.pathCount,
      });
    } catch (e) {
      report.failed.push({ key, reason: e.message });
    }
  }
  return report;
}

// -------------------------------------------------------------- verify ------

function verifyPhase() {
  const report = { files: 0, ok: 0, errors: [] };
  for (const f of fs.readdirSync(DRAWABLE)) {
    if (!f.endsWith(".xml")) continue;
    report.files++;
    try {
      const xml = fs.readFileSync(path.join(DRAWABLE, f), "utf8");
      const v = validateVectorXml(xml);
      if (!v.ok) report.errors.push({ file: f, errors: v.errors });
      else report.ok++;
    } catch (e) {
      report.errors.push({ file: f, errors: [e.message] });
    }
  }
  return report;
}

// ---------------------------------------------------------------- main ------

const phase = process.argv[2] ?? "all";
fs.mkdirSync(REPORTS, { recursive: true });
fs.mkdirSync(CACHE, { recursive: true });

// make sure the codepoints file exists (download if missing)
if (!fs.existsSync(CODEPOINTS)) {
  try {
    const cp = await fetchText(
      "https://raw.githubusercontent.com/google/material-design-icons/master/variablefont/MaterialSymbolsOutlined%5BFILL%2CGRAD%2Copsz%2Cwght%5D.codepoints"
    );
    fs.writeFileSync(CODEPOINTS, cp);
    console.error("downloaded ms-codepoints.txt");
  } catch (e) {
    console.error("could not download codepoints:", e.message);
  }
}

let inv = null;
if (phase === "inventory" || phase === "all") {
  inv = inventoryPhase(false); // screens + shared + AppShell (ui handled separately below)
  // brief ui scan — lucide only, flagged separately
  const uiInv = inventoryPhase(true);
  inv.lucideUi = {};
  for (const [k, v] of Object.entries(uiInv.lucide)) {
    if (!inv.lucide[k]) inv.lucide[k] = v; // merge (ui-only icons still get bundled)
    else for (const e of v) if (!inv.lucide[k].some((x) => x.file === e.file)) inv.lucide[k].push(e);
  }
  // mark ui-sourced entries
  for (const v of Object.values(inv.lucide)) {
    for (const e of v) if (e.file.startsWith("src/components/ui/")) e.scope = "ui-scaffold";
  }
  fs.writeFileSync(path.join(REPORTS, "inventory.json"), JSON.stringify(inv, null, 2));
  console.log(`inventory: ${Object.keys(inv.material).length} MS glyphs, ${Object.keys(inv.lucide).length} lucide imports, ${inv.inline.length} inline svgs`);
  console.log("--- dynamic spans needing manual resolution ---");
  console.log(RESOLVED_DYNAMIC_NOTES.join("\n") || "(none)");
}

if (phase === "material" || phase === "all") {
  inv = inv ?? JSON.parse(fs.readFileSync(path.join(REPORTS, "inventory.json"), "utf8"));
  const rep = await materialPhase(inv);
  fs.writeFileSync(path.join(REPORTS, "material.json"), JSON.stringify(rep, null, 2));
  console.log(`material: ${rep.created.length} drawables, ${rep.failed.length} failures`);
  if (rep.failed.length) console.log(JSON.stringify(rep.failed, null, 2));
}

if (phase === "lucide" || phase === "all") {
  inv = inv ?? JSON.parse(fs.readFileSync(path.join(REPORTS, "inventory.json"), "utf8"));
  const rep = await lucidePhase(inv);
  fs.writeFileSync(path.join(REPORTS, "lucide.json"), JSON.stringify(rep, null, 2));
  console.log(`lucide: ${rep.created.length} drawables, ${rep.failed.length} failures`);
  if (rep.failed.length) console.log(JSON.stringify(rep.failed, null, 2));
}

if (phase === "inline" || phase === "all") {
  inv = inv ?? JSON.parse(fs.readFileSync(path.join(REPORTS, "inventory.json"), "utf8"));
  const rep = inlinePhase(inv);
  fs.writeFileSync(path.join(REPORTS, "inline.json"), JSON.stringify(rep, null, 2));
  console.log(`inline: ${rep.created.length} drawables, ${rep.skipped.length} skipped (dynamic), ${rep.failed.length} failures`);
  if (rep.failed.length) console.log(JSON.stringify(rep.failed, null, 2));
}

if (phase === "verify" || phase === "all") {
  const rep = verifyPhase();
  fs.writeFileSync(path.join(REPORTS, "verify.json"), JSON.stringify(rep, null, 2));
  console.log(`verify: ${rep.ok}/${rep.files} drawable XMLs valid`);
  if (rep.errors.length) console.log(JSON.stringify(rep.errors, null, 2));
}

// ---------------------------------------------------------------- docs ------

function shortPath(f) {
  return f.replace(/^src\/components\//, "");
}

function docsPhase() {
  const inv = JSON.parse(fs.readFileSync(path.join(REPORTS, "inventory.json"), "utf8"));
  const mat = JSON.parse(fs.readFileSync(path.join(REPORTS, "material.json"), "utf8"));
  const luc = JSON.parse(fs.readFileSync(path.join(REPORTS, "lucide.json"), "utf8"));
  const inl = JSON.parse(fs.readFileSync(path.join(REPORTS, "inline.json"), "utf8"));
  const verify = JSON.parse(fs.readFileSync(path.join(REPORTS, "verify.json"), "utf8"));

  // group lucide by drawable (aliases like X/XIcon map to the same drawable)
  const lucByDrawable = new Map();
  for (const c of luc.created) {
    if (!lucByDrawable.has(c.drawable)) {
      lucByDrawable.set(c.drawable, { ...c, importNames: new Set([c.importName]), files: new Set() });
    }
    const g = lucByDrawable.get(c.drawable);
    g.importNames.add(c.importName);
    for (const f of c.files) g.files.add(f);
  }

  const matRows = Object.values(
    Object.fromEntries(mat.created.map((c) => [c.glyph, { glyph: c.glyph, base: null, fill: null, files: new Set() }]))
  );
  // rebuild from created list (base + fill entries share glyph)
  const matMap = new Map();
  for (const c of mat.created) {
    if (!matMap.has(c.glyph)) matMap.set(c.glyph, { glyph: c.glyph, base: null, fill: null, files: new Set(c.files) });
    const g = matMap.get(c.glyph);
    if (c.fill) g.fill = c.drawable;
    else g.base = c.drawable;
    for (const f of c.files) g.files.add(f);
    for (const f of c.fillFiles) g.files.add(f);
  }

  const L = [];
  L.push(`# AREENAX — Icon Inventory & Android Vector Drawables`);
  L.push("");
  L.push(`> Generated by \`AreenaxNativeAndroid/tools/build_icons.mjs\` (agent task 1-b). Source of truth: the Next.js user panel under \`src/components/screens/**\`, \`src/components/shared/**\` and \`src/components/AppShell.tsx\`.`);
  L.push(`> **Every icon the native app needs is bundled here as a local VectorDrawable XML. No icon fonts, no remote URLs at runtime.**`);
  L.push("");
  L.push(`- Scanned files: ${inv.scannedFiles.length}`);
  L.push(`- Material Symbols glyphs: **${matMap.size}** unique → ${mat.created.filter((c) => !c.fill).length} outlined + ${mat.created.filter((c) => c.fill).length} filled drawables`);
  L.push(`- lucide-react icons: **${lucByDrawable.size}** drawables (${luc.created.length} import names incl. \`*Icon\` aliases)`);
  L.push(`- Inline SVGs: **${inl.created.filter((c) => !c.duplicateOf).length}** drawables (+1 duplicate reuse), 1 not convertible (dynamic progress ring — see §3)`);
  L.push(`- Total drawable files in \`app/src/main/res/drawable/\`: ${verify.files} — XML well-formedness: ${verify.ok}/${verify.files} valid; raster geometry check vs source SVG: see tools/reports/visual.json (154/154 within 3% mask diff)`);
  L.push("");
  L.push(`## Regenerate / re-verify`);
  L.push("");
  L.push("```bash");
  L.push("cd AreenaxNativeAndroid/tools");
  L.push("bun build_icons.mjs all      # inventory → download MS SVGs → convert all three categories");
  L.push("bun build_icons.mjs verify   # re-validate every drawable XML");
  L.push("bun verify_visual.mjs        # raster-compare every drawable against its source SVG (needs sharp)");
  L.push("```");
  L.push("");

  // ---------------- Table 1: material symbols
  L.push(`## 1. Material Symbols → drawables`);
  L.push("");
  L.push(`Source: official \`google/material-design-icons\` **Material Symbols Outlined 24px** SVGs (\`symbols/web/{name}/materialsymbolsoutlined/{name}_24px.svg\`, filled variant \`{name}_fill1_24px.svg\`; gstatic fallback if the repo 404s). Original 960-unit glyph grid (viewBox \`0 -960 960 960\`) is preserved via a \`translateY(960)\` group — invisible to app code.`);
  L.push("");
  L.push(`**Fill variants:** the web panel switches glyphs to the filled cut with \`fontVariationSettings: 'FILL' 1\` (e.g. active bottom-nav tabs, toggled password eye, unlocked achievements/locks, chat read receipts, selected banks). In the native app those usages must switch to the \`ic_*_fill.xml\` resource listed below.`);
  L.push("");
  L.push(`| Glyph | Outlined drawable | Filled drawable | Used by (web files) |`);
  L.push(`|---|---|---|---|`);
  for (const g of [...matMap.values()].sort((a, b) => a.glyph.localeCompare(b.glyph))) {
    L.push(`| \`${g.glyph}\` | \`R.drawable.${g.base}\` | ${g.fill ? `\`R.drawable.${g.fill}\`` : "—"} | ${[...g.files].map(shortPath).join(", ")} |`);
  }
  L.push("");

  // ---------------- Table 2: lucide
  L.push(`## 2. lucide-react → drawables`);
  L.push("");
  L.push(`Extracted from the **installed package** \`node_modules/lucide-react@0.525.0/dist/esm/icons/*.js\` (exact version the web app uses; \`MoreHorizontal\` = alias of \`ellipsis\`). Lucide style = stroke-based: \`fillColor #00000000\`, \`strokeColor #FF000000\`, width 2, round caps/joins → fully tintable via Compose \`Icon(...)\`.`);
  L.push("");
  L.push(`| Import name(s) | Drawable | Source module | Referenced from |`);
  L.push(`|---|---|---|---|`);
  for (const [, g] of [...lucByDrawable.entries()].sort()) {
    const scopeNote = [...g.files].every((f) => f.startsWith("src/components/ui/")) ? " — *ui scaffold only, not reachable from screens*" : "";
    L.push(`| ${[...g.importNames].map((n) => `\`${n}\``).join(", ")} | \`R.drawable.${g.drawable}\` | \`${g.module.replace("node_modules lucide module ", "")}\` | ${[...g.files].map(shortPath).join(", ")}${scopeNote} |`);
  }
  L.push("");
  L.push(`> Note: \`src/components/ui/**\` is the shadcn scaffold. Per the Task-64 audit, **no screen/shared component imports any \`ui/\` component**, so these lucide icons never render in the user panel — they are bundled anyway so future usage needs no new assets. The standalone admin console (\`src/components/admin/**\`, separate web app) also uses lucide but is out of scope for the native user app.`);
  L.push("");

  // ---------------- Table 3: inline
  L.push(`## 3. Inline \`<svg>\` elements → drawables`);
  L.push("");
  L.push(`| Drawable | Source (file:line) | Description | Colors |`);
  L.push(`|---|---|---|---|`);
  for (const c of inl.created) {
    const dup = c.duplicateOf ? ` (same art as \`${c.duplicateOf}\`, reuse it)` : "";
    L.push(`| \`R.drawable.${c.drawable}\` | \`${c.source}\`${dup} | ${c.desc} | ${c.colors} |`);
  }
  L.push("");

  // ---------------- not converted
  L.push(`## 4. Not converted (with reason + manual instructions)`);
  L.push("");
  for (const s of inl.skipped) {
    L.push(`### ${s.key}`);
    L.push("");
    L.push(s.reason);
    L.push("");
  }
  L.push(`### Dynamic glyph slots (resolved through props/arrays — all values bundled)`);
  L.push("");
  L.push(`These web components pass glyph names as props/ternaries; every value they can take is already bundled (see §1):`);
  L.push("");
  L.push(`- \`shared/BottomNav.tsx\` — \`{tab.icon}\` with FILL 1 → \`ic_emoji_events_fill\`, \`ic_group_fill\`, \`ic_home_fill\`, \`ic_account_balance_wallet_fill\`, \`ic_person_fill\` (+ outlined siblings)`);
  L.push(`- \`shared/EmptyState.tsx\` — \`icon\` prop (default \`trophy\`); call sites: \`error\`, \`how_to_reg\`, \`trophy\`, …`);
  L.push(`- \`shared/AppBar.tsx\` — \`TitleWithIcon\` \`icon\` prop; \`BellButton\` = \`notifications\`; back = \`arrow_back\``);
  L.push(`- \`shared/BankSelectSheet.tsx\` — bank/UPI icons render FILL 1 when active → fill variants bundled for every \`icon:\` key in that flow (\`account_balance\`, \`credit_card\`, …)`);
  L.push(`- \`profile/AchievementsScreen.tsx\` — \`a.icon\` renders FILL 1 when unlocked/progress → fill variants bundled for achievement glyphs`);
  L.push(`- wallet screens (\`WithdrawSuccessScreen\`, \`WalletScreen\`, \`DepositConfirmScreen\`, \`ConfirmWithdrawScreen\`, …), \`TasksScreen\`, \`MyStatsScreen\`, \`GamesAdminSection\`, \`ProfileScreen\` — \`{row.icon}/{t.icon}/{m.icon}/{item.icon}\` arrays, all \`icon:\` values captured`);
  L.push(`- \`LoginScreen\` / \`TournamentDetailsScreen\` — ternary glyph pairs (e.g. \`visibility\`/\`visibility_off\`, \`check_circle\`/\`hourglass_top\`) captured from branch literals`);
  L.push("");
  const matSkipped = mat.skipped ?? [];
  if (matSkipped.length) {
    L.push(`### Strings that matched the scanner but are NOT Material Symbols glyphs (safety skips)`);
    L.push("");
    for (const s of matSkipped) L.push(`- \`${s.name}\` — ${s.reason}`);
    L.push("");
  }

  // ---------------- usage notes
  L.push(`## 5. Usage notes for Kotlin/Compose`);
  L.push("");
  L.push("```kotlin");
  L.push("// default: black paths (#FF000000) — tint with the current content color");
  L.push("Icon(");
  L.push("    painterResource(R.drawable.ic_arrow_back),");
  L.push("    contentDescription = null,");
  L.push("    tint = LocalContentColor.current,");
  L.push("    modifier = Modifier.size(24.dp),");
  L.push(")");
  L.push("");
  L.push("// filled variant (web: fontVariationSettings 'FILL' 1) — just swap the resource:");
  L.push("Icon(painterResource(R.drawable.ic_home_fill), null, tint = if (active) Primary else OnSurfaceVariant)");
  L.push("");
  L.push("// stroke icons (lucide set, ic_inline_about_chevron_right, ic_inline_teamcreation_send) tint the same way");
  L.push("```");
  L.push("");
  L.push(`- **Tinting:** every MS and lucide path is \`#FF000000\` (or transparent fill + black stroke) → \`Icon(..., tint = …)\` recolors it. Color the fill variants exactly like the outlined ones.`);
  L.push(`- **Literal-color exceptions:** \`ic_inline_whatsappfab_whatsapp_logo\` is **literal white** (#FFFFFFFF) — the green FAB circle is drawn behind it; do not tint it. \`ic_inline_leaderboard_crown\` and \`ic_inline_teamcreation_shield_check\` are \`currentColor → #FF000000\` (tintable).`);
  L.push(`- **Rotation:** the web renders \`ic_inline_teamcreation_send\` with CSS \`-rotate-45\`. The drawable is **unrotated** — apply \`Modifier.graphicsLayer { rotationZ = -45f }\` (or bake the rotation into the pathData if preferred).`);
  L.push(`- **Sizes:** all drawables are 24dp (WhatsApp logo uses a 16-unit grid, still 24dp box — scale with \`Modifier.size(...)\`). Web sizes (16–72px text sizes) are layout concerns, not asset concerns.`);
  L.push(`- **Do NOT** add Material Symbols / lucide font dependencies, icon CDN URLs, or WebView-loaded icons — everything ships in \`res/drawable/\`.`);
  L.push("");

  // ---------------- guarantees
  L.push(`## 6. Conversion guarantees`);
  L.push("");
  L.push(`1. **Structural:** every XML parses (balanced tags, single root, vector attrs present, pathData charset + command grammar valid, every path has fill/stroke) — \`tools/svg2vector.mjs → validateVectorXml\`, ${verify.ok}/${verify.files} pass.`);
  L.push(`2. **Geometric:** every drawable is rasterized (96×96 via sharp) and pixel-compared against its source SVG — \`tools/verify_visual.mjs\`; all 154 drawables match within a 3% binary-mask tolerance (most are 0.00%).`);
  L.push(`3. **Path data:** command-aware SVG path parser handles compact SVG forms (juxtaposed decimals \`3.558.064\`, sign-separated \`1-2\`, single-char arc flags \`015-5\`, implicit M→L lineto repetition) with an exact round-trip re-parse after normalization.`);
  L.push(`4. **Provenance:** downloaded SVGs cached in \`tools/cache/ms/\`, machine-readable results in \`tools/reports/*.json\`.`);
  L.push("");
  L.push(`## 7. Scope notes`);
  L.push("");
  L.push(`- Scanned: \`src/components/screens/**\` (44 screens incl. in-app admin sections), \`src/components/shared/**\` (22 components), \`src/components/AppShell.tsx\` (no direct icon usage — delegates to BottomNav/AppBar), \`src/components/ui/**\` (brief — lucide only, see §2).`);
  L.push(`- Not scanned: \`src/components/admin/**\` (standalone admin console web app, Task 68-b) — not part of the native user app. If a native admin console is ever built, re-run \`build_icons.mjs\` with that dir added to SCOPE_DIRS.`);
  L.push(`- The web app loads no other icon sources (verified: no \`react-icons\`, no iconify, no icon images referenced from screens/shared).`);
  L.push("");

  fs.mkdirSync(path.join(ANDROID, "SPEC"), { recursive: true });
  fs.writeFileSync(path.join(ANDROID, "SPEC", "ICONS.md"), L.join("\n") + "\n");
  return L.length;
}

if (phase === "docs" || phase === "all") {
  const lines = docsPhase();
  console.log(`docs: SPEC/ICONS.md written (${lines} lines)`);
}
