/**
 * svg2vector.mjs — SVG → Android VectorDrawable XML converter (zero-dependency, run with bun/node).
 *
 * Library exports:
 *   parseSvg(text)                  → { viewBox:{minX,minY,w,h}, elements:[...], warnings:[...] }
 *   shapeToPathData(el)             → path `d` string for circle/ellipse/rect/line/polyline/polygon
 *   toVectorXml(parsed, opts)       → full XML string (groups wrap viewBox offsets, colors normalized)
 *   validateVectorXml(xml)          → { ok, errors } well-formedness + pathData charset check
 *   svgToVectorXml(svgText, opts)   → one-shot: text → validated XML (throws on failure)
 *   isValidResourceName(name)
 *
 * CLI:
 *   bun svg2vector.mjs <input.svg> <output.xml> [sizeDp]
 *
 * Rules implemented:
 *   - currentColor → #FF000000 (so Compose Icon(painterResource(...)) tint works)
 *   - named colors (white/black/...) and #rgb/#rrggbb hex → #AARRGGBB (alpha from fill-opacity)
 *   - fill="none" → android:fillColor="#00000000"; stroke default round caps/joins NOT assumed
 *   - viewBox offsets (e.g. Material Symbols "0 -960 960 960") → paths wrapped in
 *     <group android:translateX/translateY> since android:viewport* has no offset
 *   - circle/ellipse/rect/line/polyline/polygon → arc/line pathData
 *   - numbers normalized (no scientific notation), pathData charset validated
 */

// ---------- number helpers ----------

function normNum(v) {
  const n = typeof v === "number" ? v : parseFloat(v);
  if (!Number.isFinite(n)) return "0";
  let s = n.toFixed(3);
  s = s.replace(/\.?0+$/, ""); // strip trailing zeros / dot
  if (s === "" || s === "-") s = "0";
  return s;
}

const NUM_TOKEN_RE = /-?(?:\d*\.\d+|\d+\.?)(?:[eE][+-]?\d+)?/g;

/**
 * Command-aware pathData normalizer.
 * CRITICAL: SVG pathData permits compact forms that a plain number regex corrupts:
 *   - juxtaposed decimals  "3.558.064" = 3.558 then 0.064
 *   - sign separation      "1-2"       = 1 then -2
 *   - single-char arc flags "a5 5 0 015-5" = largeArc 0, sweep 1, x 5, y -5
 * This parser walks the command grammar (M L H V C S Q T A Z, per-command arg counts,
 * one-char A flags) and re-emits fully space-separated rounded numbers, verified by an
 * exact round-trip re-parse.
 */
const PATH_ARGC = { M: 2, L: 2, H: 1, V: 1, C: 6, S: 4, Q: 4, T: 2, A: 7, Z: 0 };

function parsePathSegments(d) {
  const SEP = /[\s,]/;
  const isDigit = (c) => c >= "0" && c <= "9";
  let i = 0;
  const n = d.length;
  function skipSep() {
    while (i < n && SEP.test(d[i])) i++;
  }
  function readNumber() {
    skipSep();
    const start = i;
    if (d[i] === "+" || d[i] === "-") i++;
    while (i < n && isDigit(d[i])) i++;
    if (d[i] === ".") {
      i++;
      while (i < n && isDigit(d[i])) i++;
    }
    if (d[i] === "e" || d[i] === "E") {
      i++;
      if (d[i] === "+" || d[i] === "-") i++;
      while (i < n && isDigit(d[i])) i++;
    }
    if (start === i) {
      throw new Error(`pathData: number expected at offset ${i} near "${d.slice(Math.max(0, i - 12), i + 12)}"`);
    }
    return d.slice(start, i);
  }
  function readFlag() {
    skipSep();
    if (d[i] !== "0" && d[i] !== "1") {
      throw new Error(`pathData: arc flag expected at offset ${i} near "${d.slice(Math.max(0, i - 12), i + 12)}"`);
    }
    return d[i++];
  }
  const segs = [];
  let cmd = null;
  while (true) {
    skipSep();
    if (i >= n) break;
    if (/[a-zA-Z]/.test(d[i])) cmd = d[i++];
    if (!cmd) throw new Error(`pathData: command expected at offset ${i}`);
    const up = cmd.toUpperCase();
    if (up === "Z") {
      segs.push({ cmd, args: [] });
      cmd = null; // Z takes no args; a following number without a command is an error
      continue;
    }
    if (!PATH_ARGC[up]) throw new Error(`pathData: unknown command "${cmd}"`);
    const args = [];
    if (up === "A") {
      args.push(readNumber(), readNumber(), readNumber(), readFlag(), readFlag(), readNumber(), readNumber());
    } else {
      for (let k = 0; k < PATH_ARGC[up]; k++) args.push(readNumber());
    }
    segs.push({ cmd, args });
    // SVG spec: coordinate pairs following a moveto are IMPLICIT relative/absolute LINETOs
    // ("M18 6 6 18" = M 18,6 L 6,18) — switch the pending command so repeats don't re-moveto.
    if (cmd === "M") cmd = "L";
    else if (cmd === "m") cmd = "l";
  }
  return segs;
}

function normalizePathData(d) {
  const segs = parsePathSegments(d);
  let out = "";
  for (const s of segs) {
    out += s.cmd;
    for (const a of s.args) out += " " + normNum(a);
  }
  // exact round-trip verification
  const back = parsePathSegments(out);
  const eq =
    segs.length === back.length &&
    segs.every(
      (s, k) =>
        s.cmd === back[k].cmd &&
        s.args.length === back[k].args.length &&
        s.args.every((v, x) => Math.abs(parseFloat(v) - parseFloat(back[k].args[x])) < 6e-4)
    );
  if (!eq) throw new Error("pathData normalization round-trip mismatch");
  return out;
}

// ---------- color helpers ----------

const NAMED_COLORS = {
  white: "#FFFFFFFF",
  black: "#FF000000",
  red: "#FFFF0000",
  green: "#FF008000",
  blue: "#FF0000FF",
  yellow: "#FFFFFF00",
  orange: "#FFFFA500",
  purple: "#FF800080",
  gray: "#FF808080",
  grey: "#FF808080",
};

/** Convert an SVG color + opacity to Android #AARRGGBB. Returns null for none. */
function toAndroidColor(color, opacity = 1) {
  if (color == null) return null;
  const c = String(color).trim().toLowerCase();
  if (c === "" || c === "none") return null;
  if (c === "transparent") return "#00000000";
  if (c.startsWith("url(")) return { gradient: true }; // marker — caller warns
  let rrggbb = null;
  let alphaHex = "FF";
  if (c === "currentcolor") {
    rrggbb = "000000"; // currentColor → opaque black (tintable via Icon())
  } else if (NAMED_COLORS[c]) {
    const full = NAMED_COLORS[c].slice(1); // RRGGBBAA
    alphaHex = full.slice(0, 2);
    rrggbb = full.slice(2);
  } else if (c.startsWith("#")) {
    let hex = c.slice(1);
    if (hex.length === 3) hex = hex.split("").map((ch) => ch + ch).join("");
    if (hex.length === 6) {
      rrggbb = hex;
    } else if (hex.length === 8) {
      rrggbb = hex.slice(2); // #RRGGBBAA (SVG order)
      alphaHex = hex.slice(0, 2);
    } else {
      return "#FF000000"; // unknown → opaque black
    }
  } else {
    return "#FF000000"; // unrecognized (rgb() etc.) → opaque black + caller may warn
  }
  let alpha = parseInt(alphaHex, 16);
  if (opacity < 1) alpha = Math.round(alpha * opacity);
  alpha = Math.max(0, Math.min(255, alpha));
  return "#" + alpha.toString(16).padStart(2, "0").toUpperCase() + rrggbb.toUpperCase();
}

// ---------- viewBox ----------

function parseViewBox(svgText) {
  const m = svgText.match(/viewBox\s*=\s*"([^"]+)"/i) || svgText.match(/viewBox\s*=\s*'([^']+)'/i);
  if (!m) return { minX: 0, minY: 0, w: 24, h: 24 };
  const parts = m[1].trim().split(/[\s,]+/).map(Number);
  if (parts.length !== 4 || parts.some((n) => !Number.isFinite(n))) {
    return { minX: 0, minY: 0, w: 24, h: 24 };
  }
  return { minX: parts[0], minY: parts[1], w: parts[2], h: parts[3] };
}

// ---------- pathData builders for shapes ----------

function ptsToPathData(points, close) {
  const nums = points.trim().split(/[\s,]+/).map(Number).filter(Number.isFinite);
  if (nums.length < 4) return null;
  let d = `M ${normNum(nums[0])},${normNum(nums[1])}`;
  for (let i = 2; i + 1 < nums.length; i += 2) d += ` L ${normNum(nums[i])},${normNum(nums[i + 1])}`;
  return close ? d + " Z" : d;
}

/** Convert a non-path shape element to pathData. Returns null when unsupported/empty. */
function shapeToPathData(el) {
  const a = el.attrs;
  switch (el.tag) {
    case "circle": {
      const cx = parseFloat(a.cx ?? 0), cy = parseFloat(a.cy ?? 0), r = parseFloat(a.r);
      if (!Number.isFinite(r) || r <= 0) return null;
      return (
        `M ${normNum(cx - r)},${normNum(cy)} ` +
        `a ${normNum(r)},${normNum(r)} 0 1,0 ${normNum(2 * r)},0 ` +
        `a ${normNum(r)},${normNum(r)} 0 1,0 ${normNum(-2 * r)},0 Z`
      );
    }
    case "ellipse": {
      const cx = parseFloat(a.cx ?? 0), cy = parseFloat(a.cy ?? 0);
      const rx = parseFloat(a.rx), ry = parseFloat(a.ry);
      if (!Number.isFinite(rx) || !Number.isFinite(ry) || rx <= 0 || ry <= 0) return null;
      return (
        `M ${normNum(cx - rx)},${normNum(cy)} ` +
        `a ${normNum(rx)},${normNum(ry)} 0 1,0 ${normNum(2 * rx)},0 ` +
        `a ${normNum(rx)},${normNum(ry)} 0 1,0 ${normNum(-2 * rx)},0 Z`
      );
    }
    case "rect": {
      const x = parseFloat(a.x ?? 0), y = parseFloat(a.y ?? 0);
      const w = parseFloat(a.width), h = parseFloat(a.height);
      if (!Number.isFinite(w) || !Number.isFinite(h) || w <= 0 || h <= 0) return null;
      let rx = a.rx != null ? parseFloat(a.rx) : null;
      let ry = a.ry != null ? parseFloat(a.ry) : null;
      if (rx != null && ry == null) ry = rx;
      if (ry != null && rx == null) rx = ry;
      if (rx == null || rx <= 0 || ry <= 0) {
        return `M ${normNum(x)},${normNum(y)} h ${normNum(w)} v ${normNum(h)} h ${normNum(-w)} Z`;
      }
      rx = Math.min(rx, w / 2);
      ry = Math.min(ry, h / 2);
      return (
        `M ${normNum(x + rx)},${normNum(y)} h ${normNum(w - 2 * rx)} ` +
        `a ${normNum(rx)},${normNum(ry)} 0 0 1 ${normNum(rx)},${normNum(ry)} ` +
        `v ${normNum(h - 2 * ry)} ` +
        `a ${normNum(rx)},${normNum(ry)} 0 0 1 ${normNum(-rx)},${normNum(ry)} ` +
        `h ${normNum(-(w - 2 * rx))} ` +
        `a ${normNum(rx)},${normNum(ry)} 0 0 1 ${normNum(-rx)},${normNum(-ry)} ` +
        `v ${normNum(-(h - 2 * ry))} ` +
        `a ${normNum(rx)},${normNum(ry)} 0 0 1 ${normNum(rx)},${normNum(-ry)} Z`
      );
    }
    case "line": {
      const x1 = parseFloat(a.x1), y1 = parseFloat(a.y1), x2 = parseFloat(a.x2), y2 = parseFloat(a.y2);
      if (![x1, y1, x2, y2].every(Number.isFinite)) return null;
      return `M ${normNum(x1)},${normNum(y1)} L ${normNum(x2)},${normNum(y2)}`;
    }
    case "polyline":
      return ptsToPathData(a.points ?? "", false);
    case "polygon":
      return ptsToPathData(a.points ?? "", true);
    default:
      return null;
  }
}

// ---------- tiny XML scanner ----------

/** Parse SVG text into a flat element list (recursing through <g> transforms). */
export function parseSvg(svgText) {
  const warnings = [];
  const vb = parseViewBox(svgText);
  const elements = [];

  // strip comments, doctype, prelude
  const text = svgText
    .replace(/<\?[\s\S]*?\?>/g, "")
    .replace(/<!DOCTYPE[\s\S]*?>/gi, "")
    .replace(/<!--[\s\S]*?-->/g, "");

  const tagRe = /<(\/?)([a-zA-Z][a-zA-Z0-9:_-]*)((?:\s+[^<>]*?)?)(\/?)>/g;
  const stack = [];
  let m;
  let rootAttrs = {};
  while ((m = tagRe.exec(text)) !== null) {
    const [, closing, rawTag, rawAttrs, selfClose] = m;
    const tag = rawTag.toLowerCase().replace(/^svg:/, "");
    if (closing) {
      const open = stack.pop();
      if (open !== tag && tag !== "svg") {
        warnings.push(`mismatched closing tag </${tag}> (expected </${open}>)`);
      }
      continue;
    }
    const attrs = {};
    const attrRe = /([a-zA-Z_:][a-zA-Z0-9_:.-]*)\s*=\s*("([^"]*)"|'([^']*)')/g;
    let am;
    // JSX (inline <svg> inside .tsx) uses camelCase presentation attributes — map to SVG kebab-case
    const JSX_ATTR_MAP = {
      strokewidth: "stroke-width",
      strokelinecap: "stroke-linecap",
      strokelinejoin: "stroke-linejoin",
      strokedasharray: "stroke-dasharray",
      strokedashoffset: "stroke-dashoffset",
      fillopacity: "fill-opacity",
      strokeopacity: "stroke-opacity",
      fillrule: "fill-rule",
      cliprule: "clip-rule",
    };
    while ((am = attrRe.exec(rawAttrs)) !== null) {
      let key = am[1].toLowerCase().replace(/^svg:/, "");
      if (JSX_ATTR_MAP[key]) key = JSX_ATTR_MAP[key];
      attrs[key] = am[3] ?? am[4] ?? "";
    }
    if (!closing && tag === "svg" && stack.length === 0) rootAttrs = attrs;
    if (selfClose || voidElems.has(tag)) {
      collectElement(tag, attrs, elements, warnings, stack);
    } else {
      collectElement(tag, attrs, elements, warnings, stack);
      stack.push(tag);
    }
  }
  if (stack.length) warnings.push(`unclosed tag(s): ${stack.join(", ")}`);
  if (elements.length === 0) warnings.push("no drawable elements found in SVG");
  return { viewBox: vb, elements, warnings, rootAttrs };
}

const VOID_ELEMS = new Set(["path", "circle", "ellipse", "rect", "line", "polyline", "polygon", "stop", "use", "image"]);
const voidElems = VOID_ELEMS;

function collectElement(tag, attrs, out, warnings, ancestorTags) {
  const inDefs = ancestorTags.includes("defs") || ancestorTags.includes("clippath") || ancestorTags.includes("mask");
  if (inDefs) return; // defs content ignored
  if (tag === "path") {
    if (!attrs.d) {
      warnings.push("<path> without d attribute skipped");
      return;
    }
    out.push({ tag: "path", attrs });
  } else if (["circle", "ellipse", "rect", "line", "polyline", "polygon"].includes(tag)) {
    out.push({ tag, attrs });
  } else if (tag === "g") {
    if (attrs.transform && /matrix/i.test(attrs.transform)) {
      warnings.push("<g transform=matrix(...)> not supported — geometry may be off");
    }
    // g itself contributes nothing; children handled by scanner + transform recorded on out marker
    if (attrs.transform) out.push({ tag: "_gtransform", attrs });
  } else if (["title", "desc", "metadata", "style", "defs", "clippath", "mask", "svg", "lineargradient", "radialgradient", "filter"].includes(tag)) {
    if (tag === "lineargradient" || tag === "radialgradient") {
      warnings.push("gradient found — not convertible, fill will fall back / be skipped");
    }
    return;
  } else {
    warnings.push(`unsupported element <${tag}> skipped`);
  }
}

// ---------- path attribute translation ----------

function attrNum(attrs, name, def) {
  const v = attrs[name];
  if (v == null || v === "") return def;
  const n = parseFloat(v);
  return Number.isFinite(n) ? n : def;
}

function translatePathAttrs(attrs, warnings, ctx) {
  const out = [];
  const fillRaw = attrs.fill !== undefined ? attrs.fill : ctx.defaultFill;
  const strokeRaw = attrs.stroke !== undefined ? attrs.stroke : ctx.defaultStroke;
  const fillOp = attrNum(attrs, "fill-opacity", 1) * (attrNum(attrs, "opacity", 1));
  const strokeOp = attrNum(attrs, "stroke-opacity", 1) * (attrNum(attrs, "opacity", 1));

  const fillColor = toAndroidColor(fillRaw, fillOp);
  const strokeColor = toAndroidColor(strokeRaw, strokeOp);

  if (fillColor && fillColor.gradient) {
    warnings.push("gradient fill not supported — path dropped");
    return null;
  }
  if (strokeColor && strokeColor.gradient) {
    warnings.push("gradient stroke not supported — path dropped");
    return null;
  }

  const hasStroke = !!strokeColor;
  const hasFill = !!fillColor; // null means fill:none
  if (!hasStroke && !hasFill) return null; // invisible

  if (hasFill) out.push(`android:fillColor="${fillColor}"`);
  else out.push(`android:fillColor="#00000000"`);

  const fillRule = (attrs["fill-rule"] || "").toLowerCase();
  if (fillRule === "evenodd") out.push(`android:fillType="evenOdd"`);

  if (hasStroke) {
    out.push(`android:strokeColor="${strokeColor}"`);
    const sw = attrs["stroke-width"] != null ? attrNum(attrs, "stroke-width", 1) : (ctx.defaultStrokeWidth ?? 1);
    out.push(`android:strokeWidth="${normNum(sw)}"`);
    const cap = (attrs["stroke-linecap"] || ctx.defaultLineCap || "").toLowerCase();
    if (["round", "square", "butt"].includes(cap)) out.push(`android:strokeLineCap="${cap}"`);
    const join = (attrs["stroke-linejoin"] || ctx.defaultLineJoin || "").toLowerCase();
    if (["round", "bevel", "miter"].includes(join)) out.push(`android:strokeLineJoin="${join}"`);
    const miter = attrs["stroke-miterlimit"];
    if (miter != null && Number.isFinite(parseFloat(miter))) out.push(`android:strokeMiterLimit="${normNum(miter)}"`);
  }
  return out;
}

// ---------- vector XML assembly ----------

function escapeAttr(v) {
  return String(v).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}

/**
 * Build VectorDrawable XML.
 * opts: { sizeDp=24, defaultStrokeWidth, defaultLineCap, defaultLineJoin, extraRootAttrs: [] }
 * parsed: result of parseSvg()
 */
export function toVectorXml(parsed, opts = {}) {
  const { viewBox, elements } = parsed;
  const sizeDp = opts.sizeDp ?? 24;
  const scale = sizeDp / Math.max(viewBox.w, viewBox.h);
  const wDp = normNum(Math.max(1, Math.round(viewBox.w * scale * 100) / 100));
  const hDp = normNum(Math.max(1, Math.round(viewBox.h * scale * 100) / 100));

  const root = parsed.rootAttrs ?? {};
  const ctx = {
    defaultFill: opts.defaultFill ?? root.fill ?? "black",
    defaultStroke: opts.defaultStroke ?? root.stroke, // undefined → none
    defaultStrokeWidth:
      opts.defaultStrokeWidth ?? (root["stroke-width"] != null ? attrNum(root, "stroke-width", 1) : undefined),
    defaultLineCap: opts.defaultLineCap ?? root["stroke-linecap"],
    defaultLineJoin: opts.defaultLineJoin ?? root["stroke-linejoin"],
  };

  const needsOffset = viewBox.minX !== 0 || viewBox.minY !== 0;
  const body = [];
  for (const el of elements) {
    if (el.tag === "_gtransform") continue;
    let d;
    if (el.tag === "path") d = normalizePathData(el.attrs.d.trim());
    else d = shapeToPathData(el);
    if (!d) continue;
    const attrs = translatePathAttrs(el.attrs, parsed.warnings, ctx);
    if (!attrs) continue;
    body.push(`  <path\n      android:pathData="${escapeAttr(d)}"\n      ${attrs.join("\n      ")}/>`);
  }
  const out = [];
  out.push(`<vector xmlns:android="http://schemas.android.com/apk/res/android"`);
  out.push(`    android:width="${wDp}dp"`);
  out.push(`    android:height="${hDp}dp"`);
  out.push(`    android:viewportWidth="${normNum(viewBox.w)}"`);
  out.push(`    android:viewportHeight="${normNum(viewBox.h)}">`);
  if (needsOffset) {
    out.push(`  <group`);
    if (viewBox.minX !== 0) out.push(`      android:translateX="${normNum(-viewBox.minX)}"`);
    out.push(`      android:translateY="${normNum(-viewBox.minY)}">`);
  }
  for (const b of body) out.push(b);
  if (needsOffset) out.push(`  </group>`);
  out.push(`</vector>`);
  parsed._emitted = body.length;
  return out.join("\n") + "\n";
}

// ---------- validation ----------

const PATH_CMD_RE = /^[MmLlHhVvCcSsQqTtAaZz]*$/;
const PATHDATA_ALLOWED = /^[MmLlHhVvCcSsQqTtAaZz0-9eE,.\-\s+]+$/;

/** Well-formedness + VectorDrawable constraint checks. */
export function validateVectorXml(xml) {
  const errors = [];
  // 1. tag balance
  const tagRe = /<(\/?)([a-zA-Z][a-zA-Z0-9:_-]*)((?:\s+[^<>]*?)?)(\/?)>/g;
  const stack = [];
  let m;
  let sawRoot = false;
  while ((m = tagRe.exec(xml)) !== null) {
    const [, closing, tag, , selfClose] = m;
    if (closing) {
      const open = stack.pop();
      if (open !== tag) errors.push(`tag mismatch: </${tag}> closes <${open ?? "nothing"}>`);
    } else if (!selfClose) {
      stack.push(tag);
      sawRoot = true;
    }
  }
  if (stack.length) errors.push(`unclosed tag(s): ${stack.join(", ")}`);
  if (!sawRoot) errors.push("no root element");
  // 2. raw ampersands / stray angle brackets in attribute values
  const ampRe = /&(?!amp;|lt;|gt;|quot;|apos;|#)/g;
  if (ampRe.test(xml)) errors.push("raw & (unescaped entity) present");
  // 3. vector root attributes
  if (!/xmlns:android="http:\/\/schemas\.android\.com\/apk\/res\/android"/.test(xml)) {
    errors.push("missing xmlns:android declaration");
  }
  if (!/android:viewportWidth="[\d.]+"/.test(xml)) errors.push("missing android:viewportWidth");
  if (!/android:viewportHeight="[\d.]+"/.test(xml)) errors.push("missing android:viewportHeight");
  if (!/android:width="\d+(\.\d+)?dp"/.test(xml)) errors.push("missing android:width");
  if (!/android:height="\d+(\.\d+)?dp"/.test(xml)) errors.push("missing android:height");
  // 4. per-path checks
  const pathRe = /<path\b([\s\S]*?)\/>/g;
  let pm;
  let pathCount = 0;
  while ((pm = pathRe.exec(xml)) !== null) {
    pathCount++;
    const inner = pm[1];
    const dMatch = inner.match(/android:pathData="([^"]*)"/);
    if (!dMatch) {
      errors.push(`path #${pathCount}: missing pathData`);
      continue;
    }
    const d = dMatch[1];
    if (!PATHDATA_ALLOWED.test(d)) {
      errors.push(`path #${pathCount}: pathData has illegal characters`);
      continue;
    }
    const cmds = d.replace(/[^MmLlHhVvCcSsQqTtAaZz]/g, "");
    if (!PATH_CMD_RE.test(cmds)) {
      errors.push(`path #${pathCount}: invalid command sequence`);
    }
    if (!inner.match(/android:(fillColor|strokeColor)=/)) {
      errors.push(`path #${pathCount}: neither fillColor nor strokeColor`);
    }
  }
  if (pathCount === 0) errors.push("no <path> elements emitted");
  return { ok: errors.length === 0, errors, pathCount };
}

// ---------- one-shot ----------

/**
 * Convert SVG text → validated VectorDrawable XML. Throws with all warnings on failure.
 * opts: { sizeDp, defaultFill, defaultStroke, defaultStrokeWidth, defaultLineCap, defaultLineJoin }
 */
export function svgToVectorXml(svgText, opts = {}) {
  const parsed = parseSvg(svgText);
  const xml = toVectorXml(parsed, opts);
  const v = validateVectorXml(xml);
  if (!v.ok) {
    throw new Error("VectorDrawable validation failed:\n  - " + v.errors.join("\n  - ") + (parsed.warnings.length ? "\nWarnings:\n  - " + parsed.warnings.join("\n  - ") : ""));
  }
  return { xml, warnings: parsed.warnings, pathCount: v.pathCount, emitted: parsed._emitted, viewBox: parsed.viewBox };
}

export function isValidResourceName(name) {
  return /^[a-z][a-z0-9_]*$/.test(name);
}

// ---------- CLI ----------

if (import.meta.main) {
  const [, , input, output, sizeArg] = process.argv;
  if (!input || !output) {
    console.error("Usage: bun svg2vector.mjs <input.svg> <output.xml> [sizeDp=24]");
    process.exit(1);
  }
  const fs = await import("node:fs");
  const svg = fs.readFileSync(input, "utf8");
  try {
    const { xml, warnings, pathCount } = svgToVectorXml(svg, { sizeDp: sizeArg ? parseInt(sizeArg, 10) : 24 });
    fs.writeFileSync(output, xml);
    console.log(`OK ${output} (${pathCount} path(s))${warnings.length ? " warnings: " + warnings.join("; ") : ""}`);
  } catch (e) {
    console.error("FAILED:", e.message);
    process.exit(1);
  }
}
