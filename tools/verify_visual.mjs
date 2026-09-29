/**
 * verify_visual.mjs — end-to-end geometry check for generated drawables.
 *
 * For every ic_*.xml in res/drawable:
 *   1. reconstruct an SVG from the VectorDrawable (viewport + group translate + paths)
 *   2. fetch/build the ORIGINAL source SVG
 *        - ic_{ms_name}.xml        → tools/cache/ms/{name}.svg (downloaded official glyph)
 *        - ic_{ms_name}_fill.xml   → tools/cache/ms/{name}__fill1.svg
 *        - ic_lucide_{name}.xml    → GitHub raw lucide-icons/lucide main icons/{name}.svg
 *        - ic_inline_*.xml         → the raw <svg> block from the source file (inventory report)
 *   3. rasterize both at 96×96 with sharp, compare binary masks
 *
 * Exit code 0 iff every drawable matches within tolerance.
 *
 *   bun verify_visual.mjs
 */

import fs from "node:fs";
import path from "node:path";

const ROOT = "/home/z/my-project";
const ANDROID = path.join(ROOT, "AreenaxNativeAndroid");
const DRAWABLE = path.join(ANDROID, "app/src/main/res/drawable");
const CACHE = path.join(ANDROID, "tools/cache");
const REPORTS = path.join(ANDROID, "tools/reports");
const SIZE = 96;

const sharp = (await import("sharp")).default;

async function fetchText(url) {
  const res = await fetch(url, { signal: AbortSignal.timeout(20000) });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return await res.text();
}

// --- tiny VectorDrawable XML parser (enough for our own output) ---

function parseVectorXml(xml) {
  const vw = parseFloat(xml.match(/android:viewportWidth="([\d.]+)"/)[1]);
  const vh = parseFloat(xml.match(/android:viewportHeight="([\d.]+)"/)[1]);
  const tx = xml.match(/android:translateX="(-?[\d.]+)"/);
  const ty = xml.match(/android:translateY="(-?[\d.]+)"/);
  const paths = [];
  const re = /<path\b([\s\S]*?)\/>/g;
  let m;
  while ((m = re.exec(xml)) !== null) {
    const body = m[1];
    const attr = (n) => (body.match(new RegExp(`android:${n}="([^"]*)"`)) ?? [])[1];
    paths.push({
      d: attr("pathData"),
      fillColor: attr("fillColor"),
      strokeColor: attr("strokeColor"),
      strokeWidth: attr("strokeWidth"),
      cap: attr("strokeLineCap"),
      join: attr("strokeLineJoin"),
    });
  }
  return { vw, vh, tx: tx ? parseFloat(tx[1]) : 0, ty: ty ? parseFloat(ty[1]) : 0, paths };
}

// fillColor "#AARRGGBB" → "#RRGGBB"
function hex6(aarrggbb) {
  if (!aarrggbb || aarrggbb === "#00000000") return null;
  if (aarrggbb === "#FF000000") return "#000000";
  return "#" + aarrggbb.slice(3).toLowerCase();
}

function vectorToSvgStrict(vec) {
  const parts = [];
  parts.push(`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${vec.vw} ${vec.vh}" width="${vec.vw}" height="${vec.vh}">`);
  const t = vec.tx || vec.ty ? ` transform="translate(${vec.tx},${vec.ty})"` : "";
  parts.push(`<g${t}>`);
  for (const p of vec.paths) {
    const attrs = [`d="${p.d}"`];
    const fill = hex6(p.fillColor);
    attrs.push(`fill="${fill ?? "none"}"`);
    const stroke = hex6(p.strokeColor);
    if (stroke) {
      attrs.push(`stroke="${stroke}"`);
      if (p.strokeWidth) attrs.push(`stroke-width="${p.strokeWidth}"`);
      if (p.cap) attrs.push(`stroke-linecap="${p.cap}"`);
      if (p.join) attrs.push(`stroke-linejoin="${p.join}"`);
    }
    parts.push(`<path ${attrs.join(" ")}/>`);
  }
  parts.push("</g></svg>");
  return parts.join("");
}

async function rasterMask(svgText) {
  // render the svg centered into SIZE×SIZE (aspect-fit) as black-on-transparent, return binary mask
  const buf = await sharp(Buffer.from(svgText), { density: 300 })
    .resize(SIZE, SIZE, { fit: "contain", background: { r: 0, g: 0, b: 0, alpha: 0 } })
    .png()
    .toBuffer();
  const { data, info } = await sharp(buf).ensureAlpha().raw().toBuffer({ resolveWithObject: true });
  const mask = new Uint8Array(SIZE * SIZE);
  let on = 0;
  for (let i = 0; i < SIZE * SIZE; i++) {
    const a = data[i * info.channels + 3];
    if (a > 64) {
      mask[i] = 1;
      on++;
    }
  }
  return { mask, on };
}

function diffPct(a, b) {
  let diff = 0;
  for (let i = 0; i < a.length; i++) if (a[i] !== b[i]) diff++;
  return (diff / a.length) * 100;
}

// --- original source resolution ---

/** Raw <svg> blocks extracted from .tsx are JSX: camelCase attrs must become SVG kebab-case
 *  before librsvg can rasterize them faithfully. */
function jsxToSvg(block) {
  return block
    .replace(/\bstrokeWidth=/g, "stroke-width=")
    .replace(/\bstrokeLinecap=/g, "stroke-linecap=")
    .replace(/\bstrokeLinejoin=/g, "stroke-linejoin=")
    .replace(/\bfillRule=/g, "fill-rule=")
    .replace(/\bfillOpacity=/g, "fill-opacity=")
    .replace(/\bstrokeOpacity=/g, "stroke-opacity=")
    .replace(/\bclassName="[^"]*"/g, "")
    .replace(/\bstrokeDasharray="[^"]*"/g, 'stroke-dasharray="1 0"');
}

async function originalSvgFor(file) {
  const name = file.replace(/\.xml$/, "");
  if (name.startsWith("ic_lucide_")) {
    const kebab = name.replace("ic_lucide_", "").replace(/_/g, "-");
    // lucide renames/aliases: more-horizontal is now ellipsis on GitHub main
    const aliases = { "more-horizontal": "ellipsis" };
    const file = aliases[kebab] ?? kebab;
    return await fetchText(`https://raw.githubusercontent.com/lucide-icons/lucide/main/icons/${file}.svg`);
  }
  if (name.startsWith("ic_inline_")) {
    const inv = JSON.parse(fs.readFileSync(path.join(REPORTS, "inventory.json"), "utf8"));
    const inlineRep = JSON.parse(fs.readFileSync(path.join(REPORTS, "inline.json"), "utf8"));
    const entry = inlineRep.created.find((c) => c.drawable === name);
    if (!entry) throw new Error("no inline report entry");
    if (entry.duplicateOf) {
      const orig = inlineRep.created.find((c) => c.drawable === entry.duplicateOf || c.key === entry.duplicateOf);
      const key = orig?.key ?? entry.key;
      const item = inv.inline.find((x) => `${x.file}#${x.line}` === key);
      if (item) return jsxToSvg(item.svg);
    }
    const item = inv.inline.find((x) => `${x.file}#${x.line}` === entry.key);
    if (!item) throw new Error("inline item not found: " + entry.key);
    return jsxToSvg(item.svg);
  }
  // material symbols
  if (name.endsWith("_fill")) {
    const glyph = name.replace(/^ic_/, "").replace(/_fill$/, "");
    return fs.readFileSync(path.join(CACHE, "ms", `${glyph}__fill1.svg`), "utf8");
  }
  const glyph = name.replace(/^ic_/, "");
  return fs.readFileSync(path.join(CACHE, "ms", `${glyph}.svg`), "utf8");
}

// --- main ---

const files = fs.readdirSync(DRAWABLE).filter((f) => f.endsWith(".xml") && f.startsWith("ic_"));
const results = { pass: [], fail: [], skipped: [] };
for (const file of files) {
  try {
    const xml = fs.readFileSync(path.join(DRAWABLE, file), "utf8");
    const vec = parseVectorXml(xml);
    const drawableSvg = vectorToSvgStrict(vec);
    const origSvg = await originalSvgFor(file);
    const a = await rasterMask(drawableSvg);
    const b = await rasterMask(origSvg);
    if (a.on === 0 && b.on === 0) {
      results.fail.push({ file, reason: "both masks empty" });
      continue;
    }
    if (a.on === 0 || b.on === 0) {
      results.fail.push({ file, reason: `one mask empty (drawable px=${a.on}, source px=${b.on})` });
      continue;
    }
    const d = diffPct(a.mask, b.mask);
    const rec = { file, diffPct: +d.toFixed(2), px: a.on };
    if (d <= 3.0) results.pass.push(rec);
    else results.fail.push({ ...rec, reason: "mask diff above 3%" });
  } catch (e) {
    results.skipped.push({ file, reason: e.message });
  }
}
fs.writeFileSync(path.join(REPORTS, "visual.json"), JSON.stringify(results, null, 2));
console.log(`visual verify: ${results.pass.length} pass, ${results.fail.length} FAIL, ${results.skipped.length} skipped (no source) / ${files.length}`);
for (const f of results.fail) console.log("FAIL", f.file, f.diffPct ?? "", f.reason);
for (const s of results.skipped) console.log("SKIP", s.file, "-", s.reason.slice(0, 90));
if (results.fail.length) process.exit(1);
