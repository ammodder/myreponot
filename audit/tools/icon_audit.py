#!/usr/bin/env python3
"""
icon_audit.py — AREENAX PHASE 2 dedicated icon audit agent (owner decision Q9).

Read-only audit tool. The ONLY thing it ever writes is under the audit evidence
tree (JSON/log outputs + the --staging directory of `fetch-missing`).
It NEVER writes into app/src/main/res — downloading icons INTO the app is a
fix-phase step (owner-approved later).

Subcommands
-----------
  scan-native   Inventory app res/drawable* + res/mipmap* (name, type, bytes,
                density dir, vector vs raster), extract resource references from
                Kotlin code (R.drawable./R.mipmap.) + AndroidManifest (@drawable/),
                classify every file: hard-referenced / dynamically reachable via
                core/ui/AreenaxIcon.kt `iconResId(name, filled)` convention /
                unreachable. Report unreferenced files and referenced-but-missing.
                Also detects inline Canvas/graphicsLayer-drawn icons in Kotlin.

  scan-web      Extract Material Symbols glyph names used by the web panel
                (literal <span class="material-symbols-outlined|rounded">name</span>
                usage, ±2-line inferred window around such spans, data-driven
                `icon: "name"` slots, lucide-react imports) + glyph usage with
                FILL'1' style (bottom-nav tabs, FILLED_ICON const) + the design
                HTML pages (upload/pages_extracted/pages) + remote-loaded image
                URLs (social icons in ProfileScreen).

  cross-check   Map web glyph names <-> native drawables using the discovered
                mapping rule (see MAPPING RULE below) and the runtime resolver
                table parsed out of AreenaxIcon.kt (aliases + reserved fill set).
                Output MISSING (web uses, native lacks), EXTRA (native has,
                web never uses — split into reachable-by-data / dead),
                MISMATCHED-STYLE (web renders FILL'1', native would render
                outlined, or vice versa).

  fetch-missing Download the MISSING glyph SVGs from the official Google
                material-design-icons GitHub repo into an audit staging dir
                ONLY. Features: 3-attempt retry with exponential backoff,
                md5-based cache (re-download skipped when md5 still matches),
                manifest.json (source URL, sha256, target res name).
                URL pattern (verified live):
                https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/<name>/materialsymbolsoutlined/<name>_24px.svg
                (fill variant: <name>_fill1_24px.svg). License: Apache-2.0.

MAPPING RULE (learned from tools/build_icons.mjs + tools/reports/*.json +
core/ui/AreenaxIcon.kt)
  web Material Symbol glyph "some_name"            -> res/drawable/ic_some_name.xml
  web glyph rendered with fontVariationSettings FILL 1 -> ic_some_name_fill.xml
  lucide-react import "SomeIcon" (kebab "some-icon")   -> ic_lucide_some_icon.xml
  inline <svg> block in web screen X                   -> ic_inline_<screen>_<slug>.xml
  launcher icon                                        -> drawable/areenax_logo.png
  Runtime resolver (AreenaxIcon.kt): glyph -> "ic_<base>" (or "ic_<base>_fill"
  when filled=true and base ∈ reserved fill set); aliases:
  warning→error, tournament→tune, qr_code_scanner→qr_code_2, quiz→assignment,
  chevron-right→chevron_right; unknown names fall back to ic_help.

Usage examples
--------------
  python3 icon_audit.py scan-native
  python3 icon_audit.py scan-web
  python3 icon_audit.py cross-check [--include-design]
  python3 icon_audit.py fetch-missing --staging audit/evidence/phase2/icon-staging
"""

from __future__ import annotations

import argparse
import datetime
import glob
import hashlib
import json
import os
import re
import sys
import time
import urllib.request
import urllib.error

# ----------------------------------------------------------------- defaults --
DEF_ANDROID = "/home/z/audit-wt/AreenaxNativeAndroid"
DEF_WEBROOT = "/home/z/my-project"
DEF_EVID = os.path.join(DEF_ANDROID, "audit/evidence/phase2")
GH_BASE = ("https://raw.githubusercontent.com/google/material-design-icons/"
           "master/symbols/web/{name}/materialsymbolsoutlined/{name}{variant}_24px.svg")
LICENSE = "Apache-2.0 (google/material-design-icons)"

WEB_SCOPE_DIRS = ["src/components/screens", "src/components/shared"]
WEB_SCOPE_FILES = ["src/components/AppShell.tsx"]
WEB_HTML_DIR = "upload/pages_extracted/pages"


def now() -> str:
    return datetime.datetime.now().astimezone().isoformat(timespec="seconds")


def write_json(path: str, data) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, sort_keys=False)
        f.write("\n")


def sha256_file(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


def md5_bytes(data: bytes) -> str:
    return hashlib.md5(data).hexdigest()


# ============================================================ scan-native ====
RES_DIR_RE = re.compile(r"^(drawable|mipmap)")
REF_KT_RE = re.compile(r"R\.(drawable|mipmap)\.([A-Za-z0-9_]+)")
REF_ATTR_RE = re.compile(r"@(drawable|mipmap)/([A-Za-z0-9_]+)")
INLINE_HINT_RE = re.compile(
    r"Canvas\s*\(|drawPath\s*\(|rotationZ\s*=|\.rotate\s*\(\s*-?\d+(?:\.\d+)?f\s*\)"
)


def native_inventory(android: str) -> dict:
    res = os.path.join(android, "app/src/main/res")
    dirs = sorted(
        d for d in os.listdir(res)
        if RES_DIR_RE.match(d) and os.path.isdir(os.path.join(res, d))
    )
    files = []
    for d in dirs:
        full = os.path.join(res, d)
        for fn in sorted(os.listdir(full)):
            p = os.path.join(full, fn)
            if not os.path.isfile(p):
                continue
            stem, ext = os.path.splitext(fn)
            kind = "other"
            if ext == ".xml":
                try:
                    head = open(p, encoding="utf-8", errors="replace").read(600)
                    kind = "vector" if "<vector" in head else "xml-other"
                except OSError:
                    kind = "unreadable"
            elif ext == ".png":
                kind = "png"
            elif ext == ".webp":
                kind = "webp"
            files.append({
                "resource": stem,
                "dir": d,
                "file": os.path.relpath(p, android),
                "ext": ext.lstrip("."),
                "bytes": os.path.getsize(p),
                "kind": kind,
            })
    return {"res_dir": res, "dirs": dirs, "files": files}


def extract_refs(android: str) -> dict:
    java = os.path.join(android, "app/src/main/java")
    refs = {"drawable": {}, "mipmap": {}}
    for p in glob.glob(os.path.join(java, "**/*.kt"), recursive=True):
        text = open(p, encoding="utf-8", errors="replace").read()
        for m in REF_KT_RE.finditer(text):
            line = text[:m.start()].count("\n") + 1
            refs[m.group(1)].setdefault(m.group(2), []).append(
                f"{os.path.relpath(p, android)}:{line}")
    manifest = os.path.join(android, "app/src/main/AndroidManifest.xml")
    man = open(manifest, encoding="utf-8", errors="replace").read() if os.path.exists(manifest) else ""
    for m in REF_ATTR_RE.finditer(man):
        refs[m.group(1)].setdefault(m.group(2), []).append("AndroidManifest.xml")
    for p in glob.glob(os.path.join(android, "app/src/main/res/values/*.xml")):
        text = open(p, encoding="utf-8", errors="replace").read()
        for m in REF_ATTR_RE.finditer(text):
            refs[m.group(1)].setdefault(m.group(2), []).append(os.path.relpath(p, android))
    return refs


def parse_resolver(android: str) -> dict:
    """Parse core/ui/AreenaxIcon.kt — aliases + reserved fill set + fallback."""
    p = os.path.join(android, "app/src/main/java/com/areenax/nativeapp/core/ui/AreenaxIcon.kt")
    out = {"file": os.path.relpath(p, android), "aliases": {}, "reserved_fill": [], "fallback": None}
    if not os.path.exists(p):
        return out
    text = open(p, encoding="utf-8", errors="replace").read()
    for m in re.finditer(r'"([A-Za-z0-9_-]+)"\s*->\s*"([A-Za-z0-9_]+)"', text):
        out["aliases"][m.group(1)] = m.group(2)
    rm = re.search(r"val\s+reserved\s*=\s*setOf\(([^)]*)\)", text, re.S)
    if rm:
        out["reserved_fill"] = re.findall(r'"([A-Za-z0-9_]+)"', rm.group(1))
    fm = re.search(r"R\.drawable\.(\w+)\s*$", text, re.M)
    if fm:
        out["fallback"] = fm.group(1)
    return out


def detect_inline_icons(android: str) -> list:
    """Static detection of icons drawn in Kotlin code (Canvas/graphicsLayer/rotate)."""
    hits = []
    java = os.path.join(android, "app/src/main/java")
    for p in glob.glob(os.path.join(java, "**/*.kt"), recursive=True):
        rel = os.path.relpath(p, android)
        text = open(p, encoding="utf-8", errors="replace").read()
        for i, line in enumerate(text.splitlines(), 1):
            if INLINE_HINT_RE.search(line):
                hits.append({"file": rel, "line": i, "snippet": line.strip()[:120]})
    return hits


def cmd_scan_native(args) -> int:
    android = args.android
    inv = native_inventory(android)
    refs = extract_refs(android)
    resolver = parse_resolver(android)
    inline = detect_inline_icons(android)

    have = {f["resource"] for f in inv["files"]}
    ref_draw = set(refs["drawable"])
    ref_mip = set(refs["mipmap"])

    missing = sorted((ref_draw | ref_mip) - have)
    unreferenced = sorted(n for n in have if n not in ref_draw and n not in ref_mip)

    codepoints = load_codepoints(android)
    aliases = resolver.get("aliases", {})
    reserved = set(resolver.get("reserved_fill", []))

    def base_of(name: str):
        m = re.fullmatch(r"ic_([a-z0-9_]+?)(_fill)?", name)
        if not m:
            return None
        return m.group(1), m.group(2) == "_fill"

    reachable, dead = [], []
    for n in unreferenced:
        b = base_of(n)
        if b is None:
            dead.append({"name": n, "why": "no ic_ prefix (bundled asset)"})
            continue
        base, is_fill = b
        rev = {v: k for k, v in aliases.items()}
        glyph = rev.get(base, base)
        if is_fill:
            ok = glyph in codepoints and base in reserved
            entry = {"name": n, "glyph_known": glyph in codepoints,
                     "in_reserved_fill_set": base in reserved}
            if ok:
                entry["via"] = f"iconResId('{glyph}', filled=true)"
            else:
                entry["why"] = ("fill variant exists but glyph not in AreenaxIcon.kt reserved set "
                                "and not hard-referenced -> resolver never returns it")
            reachable.append(entry)
        else:
            ok = glyph in codepoints or base in aliases.values() or base in {"areenax_logo"}
            reachable.append({"name": n, "glyph_known": glyph in codepoints,
                              "via": f"iconResId('{glyph}')" if ok else None})
            if not ok:
                reachable[-1]["why"] = "glyph name not in Material Symbols codepoints set -> resolver can never receive it (except via server data)"

    report = {
        "tool": "icon_audit.py scan-native",
        "generated_at": now(),
        "android_root": android,
        "read_only": True,
        "counts": {
            "res_dirs": inv["dirs"],
            "mipmap_dirs": [d for d in inv["dirs"] if d.startswith("mipmap")],
            "files_total": len(inv["files"]),
            "xml": sum(1 for f in inv["files"] if f["ext"] == "xml"),
            "png": sum(1 for f in inv["files"] if f["ext"] == "png"),
            "vector_drawables": sum(1 for f in inv["files"] if f["kind"] == "vector"),
            "by_dir": {d: sum(1 for f in inv["files"] if f["dir"] == d) for d in inv["dirs"]},
            "hard_ref_drawable": len(ref_draw),
            "hard_ref_mipmap": len(ref_mip),
        },
        "manifest_icon": sorted(set(re.findall(r'android:(?:icon|roundIcon)="([^"]+)"', open(
            os.path.join(android, "app/src/main/AndroidManifest.xml"), encoding="utf-8", errors="replace").read()))),
        "resolver": resolver,
        "files": inv["files"],
        "referenced_but_missing": missing,
        "unreferenced_files": unreferenced,
        "unreferenced_classification": {
            "dynamically_reachable": [r for r in reachable if r.get("via")],
            "never_resolvable": [r for r in reachable if not r.get("via")] + dead,
        },
        "inline_drawn_icons_kotlin": inline,
    }
    out = os.path.join(args.evid, "icon-scan-native.json")
    write_json(out, report)

    # human log
    lines = [
        f"scan-native @ {now()}",
        f"res dirs: {inv['dirs']}",
        f"mipmap dirs: {report['counts']['mipmap_dirs'] or 'NONE (launcher icon is drawable/areenax_logo.png, see manifest_icon)'}",
        f"files: {report['counts']['files_total']} (xml {report['counts']['xml']}, png {report['counts']['png']}, vector {report['counts']['vector_drawables']})",
        f"hard R.drawable refs: {len(ref_draw)} | hard R.mipmap refs: {len(ref_mip)}",
        f"manifest icon: {report['manifest_icon']}",
        f"referenced-but-missing: {missing or 'NONE'}",
        f"unreferenced (hard): {len(unreferenced)}",
        f"  dynamically reachable via iconResId(): {len(report['unreferenced_classification']['dynamically_reachable'])}",
        f"  never resolvable (dead weight): {len(report['unreferenced_classification']['never_resolvable'])}",
        f"inline-drawn icon sites (Canvas/graphicsLayer/rotate): {len(inline)}",
    ]
    for r in report["unreferenced_classification"]["never_resolvable"]:
        lines.append(f"    DEAD {r['name']} :: {r.get('why', r.get('why'))}")
    log = "\n".join(lines)
    with open(os.path.join(args.evid, "icon-scan-native.log"), "w") as f:
        f.write(log + "\n")
    print(log)
    return 0


# ============================================================== scan-web =====
SPAN_RE = re.compile(
    r"<span[^>]*material-symbols-(?:outlined|rounded)[^>]*>([\s\S]{0,120}?)<",
)
FILL_HINT_RE = re.compile(r"FILL['\"]?\s*1|FILLED_ICON")
DYNAMIC_CHILD_RE = re.compile(r"^\s*\{\s*(\w+)(?:\.\w+)*\s*\}\s*$")
LUCIDE_RE = re.compile(r'import\s+(?:type\s+)?\{([^}]+)\}\s*from\s*["\']lucide-react["\']')
ICON_SLOT_RE = re.compile(r'(?<![\w])icon(?:Name)?\s*[:=]\s*"([a-z0-9_]+)"')
ICON_TERNARY_RE = re.compile(
    r'\bicon\s*[:=][^"\n]*"([a-z0-9_]+)"\s*:\s*"([a-z0-9_]+)"')
QUOTED_RE = re.compile(r'"([a-z][a-z0-9_]{1,30})"')
CMP_CLEAN_RE = re.compile(r'(===|!==|==|!=|>=|<=)\s*("[^"]*"|\'[^\']*\')')
HTML_SPAN_RE = re.compile(r"material-symbols[^>]*>\s*([a-z][a-z0-9_]{1,30})\s*<")
URL_RE = re.compile(r'https://[^\s"\'<>)]+')


def load_codepoints(android: str) -> set:
    p = os.path.join(android, "tools/ms-codepoints.txt")
    if not os.path.exists(p):
        return set()
    out = set()
    for line in open(p, encoding="utf-8", errors="replace"):
        token = line.split()
        if token:
            out.add(token[0])
    return out


def web_scope_files(webroot: str) -> list:
    out = []
    for d in WEB_SCOPE_DIRS:
        out += sorted(glob.glob(os.path.join(webroot, d, "**/*.tsx"), recursive=True))
        out += sorted(glob.glob(os.path.join(webroot, d, "**/*.ts"), recursive=True))
    for f in WEB_SCOPE_FILES:
        p = os.path.join(webroot, f)
        if os.path.exists(p):
            out.append(p)
    return out


def cmd_scan_web(args) -> int:
    webroot = args.webroot
    android = args.android
    codepoints = load_codepoints(android)

    glyphs = {}   # name -> {"uses": n, "files": {rel: [lines]}, "fill_files": [...], "sources": [...]}
    lucide = {}   # importName -> [relfiles]

    def add(name, rel, line, source, fill=False):
        g = glyphs.setdefault(name, {"uses": 0, "files": {}, "fill_files": [], "sources": set()})
        g["uses"] += 1
        g["files"].setdefault(rel, []).append(line)
        g["sources"].add(source)
        if fill:
            g["fill_files"].append(f"{rel}:{line}")

    for p in web_scope_files(webroot):
        rel = os.path.relpath(p, webroot)
        text = open(p, encoding="utf-8", errors="replace").read()
        lines = text.splitlines()
        ms_lines = set()
        file_dynamic_fill = False  # era rule: a FILL'1' span with dynamic {x.icon}
        # children marks every icon-key glyph in this file for a fill variant
        # literal spans — whole-file regex so MULTI-LINE tags are caught too
        for m in SPAN_RE.finditer(text):
            line_no = text[:m.start()].count("\n") + 1
            inner = m.group(1)
            fill = bool(FILL_HINT_RE.search(m.group(0)))
            dm = DYNAMIC_CHILD_RE.match(inner)
            if dm:  # {tab.icon} etc. — glyph comes from data; note fill coupling
                if fill:
                    file_dynamic_fill = True
                ms_lines.add(line_no)
                continue
            if inner.lstrip().startswith("{"):
                # JSX expression inside the span (e.g. {seen ? "done_all" : "done"})
                expr = CMP_CLEAN_RE.sub(r"\1 ", inner)  # drop mode strings like m === "email"
                for tok in QUOTED_RE.findall(expr):
                    if tok in codepoints:
                        add(tok, rel, line_no, "literal-span-expression", fill)
                ms_lines.add(line_no)
                continue
            add(inner.strip(), rel, line_no, "literal-span", fill)
            ms_lines.add(line_no)
        for i, line in enumerate(lines, 1):
            # data-driven icon slots: icon: "x", icon="x", icon: cond ? "a" : "b"
            for m in ICON_SLOT_RE.finditer(line):
                if m.group(1) in codepoints:
                    add(m.group(1), rel, i, "data-slot",
                        file_dynamic_fill and "FILL" not in line)
                    ms_lines.add(i)
            for m in ICON_TERNARY_RE.finditer(line):
                for tok in m.groups():
                    if tok in codepoints:
                        add(tok, rel, i, "data-slot", file_dynamic_fill)
                        ms_lines.add(i)
        # inferred window: quoted glyph-like tokens within ±1 line of a LITERAL
        # material-symbols class line (era parity: icon-slot lines excluded, and
        # comparison-operator strings dropped, to avoid route-name/type noise)
        lit_lines = set()
        for m in SPAN_RE.finditer(text):
            lit_lines.add(text[:m.start()].count("\n") + 1)
        for i in lit_lines:
            for j in range(max(1, i - 1), min(len(lines), i + 1) + 1):
                cleaned = CMP_CLEAN_RE.sub(r"\1 ", lines[j - 1])
                for m in QUOTED_RE.finditer(cleaned):
                    tok = m.group(1)
                    if tok in codepoints:
                        add(tok, rel, j, "inferred-window")
        for m in LUCIDE_RE.finditer("\n".join(lines)):
            for name in re.findall(r"([A-Za-z0-9_]+?)(?:Icon)?\s*,", m.group(1) + ","):
                lucide.setdefault(name, []).append(rel)

    # design HTML pages
    html_glyphs = {}
    html_dir = os.path.join(webroot, WEB_HTML_DIR)
    for p in sorted(glob.glob(os.path.join(html_dir, "*.html"))):
        rel = os.path.relpath(p, webroot)
        text = open(p, encoding="utf-8", errors="replace").read()
        for m in HTML_SPAN_RE.finditer(text):
            line = text[:m.start()].count("\n") + 1
            html_glyphs.setdefault(m.group(1), {"files": {}})["files"].setdefault(rel, []).append(line)

    # remote-loaded images in profile screens (social icons etc.)
    remote_images = []
    for p in sorted(glob.glob(os.path.join(webroot, "src/components/screens/profile/*.tsx"))):
        rel = os.path.relpath(p, webroot)
        for i, line in enumerate(open(p, encoding="utf-8", errors="replace").read().splitlines(), 1):
            for m in URL_RE.finditer(line):
                u = m.group(0)
                if "googleusercontent" in u or "lh3." in u:
                    remote_images.append({"file": rel, "line": i, "url": u[:160], "role": "remote social icon"})

    report = {
        "tool": "icon_audit.py scan-web",
        "generated_at": now(),
        "web_root": webroot,
        "scope_dirs": WEB_SCOPE_DIRS + WEB_SCOPE_FILES,
        "counts": {
            "files_scanned": len(web_scope_files(webroot)),
            "glyphs_distinct": len(glyphs),
            "glyphs_literal_only": sum(1 for g in glyphs.values() if g["sources"] == {"literal-span"}),
            "glyphs_with_fill_usage": sum(1 for g in glyphs.values() if g["fill_files"]),
            "lucide_imports": len(lucide),
            "html_pages_glyphs": len(html_glyphs),
            "remote_image_urls": len(remote_images),
        },
        "glyphs": {k: {**v, "sources": sorted(v["sources"])} for k, v in sorted(glyphs.items())},
        "lucide": lucide,
        "html_design_pages": html_glyphs,
        "remote_images": remote_images,
        "fill_rule_note": "web switches a glyph to the FILLED cut via fontVariationSettings 'FILL' 1 "
                          "(FILLED_ICON const in JoinTeamSheet/RoomSheet/BankSelectSheet, inline style in "
                          "BottomNav/TournamentCard) — bottom-nav tabs always FILL 1 (REQ-076).",
    }
    out = os.path.join(args.evid, "icon-scan-web.json")
    write_json(out, report)

    fills = sorted(k for k, v in report["glyphs"].items() if v["fill_files"])
    lines = [
        f"scan-web @ {now()}",
        f"files scanned: {report['counts']['files_scanned']}",
        f"distinct Material Symbol glyphs (live panel scope): {report['counts']['glyphs_distinct']}",
        f"  with FILL'1' usage: {len(fills)} -> {fills}",
        f"lucide-react imports: {report['counts']['lucide_imports']}",
        f"design-page (HTML) distinct glyphs: {report['counts']['html_pages_glyphs']}",
        f"remote image URLs (profile socials): {report['counts']['remote_image_urls']}",
    ]
    log = "\n".join(lines)
    with open(os.path.join(args.evid, "icon-scan-web.log"), "w") as f:
        f.write(log + "\n")
    print(log)
    return 0


# ============================================================ cross-check ====
def resolver_target(glyph: str, filled: bool, aliases: dict, reserved: set) -> str:
    base = aliases.get(glyph, glyph).replace("-", "_")
    if filled and base in reserved:
        return f"ic_{base}_fill"
    return f"ic_{base}"


def cmd_cross_check(args) -> int:
    android, webroot = args.android, args.webroot
    nat = native_inventory(android)
    refs = extract_refs(android)
    resolver = parse_resolver(android)
    web = json.load(open(os.path.join(args.evid, "icon-scan-web.json"))) \
        if os.path.exists(os.path.join(args.evid, "icon-scan-web.json")) else None
    if web is None:
        print("scan-web output missing — run scan-web first", file=sys.stderr)
        return 2

    have = {f["resource"] for f in nat["files"]}
    ref_draw = set(refs["drawable"])
    aliases = resolver.get("aliases", {})
    reserved = set(resolver.get("reserved_fill", []))

    glyphs = web["glyphs"]
    html_glyphs = web["html_design_pages"]

    missing, missing_fill, mismatch = [], [], []
    for name, g in glyphs.items():
        target = resolver_target(name, False, aliases, reserved)
        if target not in have:
            missing.append({"glyph": name, "target_res": target,
                            "used_at": [f"{f}:{ln}" for f, lns in g["files"].items() for ln in lns[:5]]})
            continue
        if g["fill_files"]:
            ftarget = resolver_target(name, True, aliases, reserved)
            hard = f"ic_{aliases.get(name, name)}_fill" in ref_draw
            if ftarget not in have:
                missing_fill.append({"glyph": name, "target_res": ftarget,
                                     "web_fill_at": g["fill_files"][:6]})
            elif not hard and aliases.get(name, name) not in reserved:
                mismatch.append({
                    "glyph": name, "web": "FILL'1'", "native_renders": "outlined",
                    "reason": f"{ftarget}.xml exists but '{aliases.get(name, name)}' is NOT in "
                              f"AreenaxIcon.kt reserved set and no static R.drawable.{ftarget} ref -> "
                              f"iconResId(name, filled=true) returns the OUTLINED drawable",
                    "web_fill_at": g["fill_files"][:6]})

    missing_design = []
    for name, h in html_glyphs.items():
        if name in glyphs:
            continue
        target = resolver_target(name, False, aliases, reserved)
        if target not in have:
            missing_design.append({"glyph": name, "target_res": target,
                                   "design_pages": sorted(h["files"].keys())})

    # EXTRA: native drawables the web panel never names
    web_names = set(glyphs) | {aliases.get(g, g) for g in glyphs}
    extra = []
    for f in nat["files"]:
        n = f["resource"]
        if n in ref_draw:
            continue
        m = re.fullmatch(r"ic_([a-z0-9_]+?)(_fill)?", n)
        if m and m.group(1) in web_names | set(aliases.values()) | set(reserved):
            continue  # dynamically reachable for data-driven slots
        extra.append(n)

    # fill drawables web never fills
    extra_fill_never_used = sorted(
        n for n in have
        if re.fullmatch(r"ic_[a-z0-9_]+_fill", n)
        and re.sub(r"_fill$", "", n)[3:] not in {aliases.get(g, g) for g, v in glyphs.items() if v["fill_files"]}
        and n not in ref_draw
    )

    report = {
        "tool": "icon_audit.py cross-check",
        "generated_at": now(),
        "mapping_rule": "web glyph 'x' -> res/drawable/ic_x.xml; FILL'1' usage -> ic_x_fill.xml; "
                        "lucide import -> ic_lucide_<snake>.xml; inline <svg> -> ic_inline_<screen>_<slug>.xml; "
                        "runtime resolver AreenaxIcon.kt iconResId(name, filled) with alias table + reserved fill set",
        "resolver": resolver,
        "missing_live": missing,
        "missing_fill_variant": missing_fill,
        "mismatched_style": mismatch,
        "missing_design_pages_only": missing_design,
        "extra_native_not_in_web": extra,
        "extra_fill_never_used_by_web_or_code": extra_fill_never_used,
        "counts": {
            "web_glyphs": len(glyphs),
            "native_drawables": len(have),
            "missing_live": len(missing),
            "missing_fill_variant": len(missing_fill),
            "mismatched_style": len(mismatch),
            "missing_design_pages_only": len(missing_design),
            "extra_native_not_in_web": len(extra),
            "extra_fill_never_used_by_web_or_code": len(extra_fill_never_used),
        },
        "design_scope_note": "missing_design_pages_only = glyphs found only in upload/pages_extracted/pages "
                             "(design mockups), not in the live panel src — candidates are design-intent, "
                             "lower priority than live-panel missing.",
        "admin_scope_note": "src/components/admin/** is a separate web console, out of scope for the native "
                            "user app per SPEC/ICONS.md — glyphs used only there (e.g. fact_check, settings, "
                            "security, replay) are NOT counted missing.",
    }
    out = os.path.join(args.evid, "icon-cross-check.json")
    write_json(out, report)

    # markdown tables for the findings doc
    md = ["# cross-check tables (generated — do not edit by hand)", ""]
    md.append(f"Mapping rule: {report['mapping_rule']}")
    md.append("")
    md.append(f"Web glyphs scanned: {len(glyphs)} · Native drawables: {len(have)} "
              f"(hard-referenced in Kotlin: {len(ref_draw)})")
    for title, key, rows in [
        ("MISSING (live panel uses, native lacks)", "missing_live", missing),
        ("MISSING fill variant (web FILL'1', no ic_*_fill)", "missing_fill_variant", missing_fill),
        ("MISMATCHED-STYLE (web FILL'1' -> native renders outlined)", "mismatched_style", mismatch),
        ("MISSING (design pages only)", "missing_design_pages_only", missing_design),
        ("EXTRA (native has, web never names, code never refs)", "extra_native_not_in_web", [{"name": n} for n in extra]),
        ("EXTRA fill variants (web never fills, code never refs)", "extra_fill_never_used_by_web_or_code", [{"name": n} for n in extra_fill_never_used]),
    ]:
        md.append(f"\n## {title} — {len(rows)}\n")
        if rows:
            if "glyph" in rows[0]:
                md.append("| glyph | native target | where (web) / reason |")
                md.append("|---|---|---|")
                for r in rows:
                    where = r.get("used_at") or r.get("web_fill_at") or r.get("design_pages") or r.get("reason") or ""
                    md.append(f"| `{r['glyph']}` | `{r.get('target_res', r.get('native_renders', ''))}` | {where} |")
            else:
                md.append(", ".join("`" + r["name"] + "`" for r in rows))
    md_text = "\n".join(md) + "\n"
    with open(os.path.join(args.evid, "icon-cross-check.md"), "w") as f:
        f.write(md_text)

    lines = [
        f"cross-check @ {now()} (include-design={args.include_design})",
        f"web glyphs {len(glyphs)} vs native drawables {len(have)}; hard code refs {len(ref_draw)}",
        f"MISSING live: {len(missing)} {[m['glyph'] for m in missing][:20]}",
        f"MISSING fill: {len(missing_fill)} {[m['glyph'] for m in missing_fill][:20]}",
        f"MISMATCHED-STYLE: {len(mismatch)} {[m['glyph'] for m in mismatch][:20]}",
        f"MISSING design-only: {len(missing_design)} {[m['glyph'] for m in missing_design][:20]}",
        f"EXTRA native-not-in-web: {len(extra)}",
        f"EXTRA fill never used: {len(extra_fill_never_used)}",
    ]
    log = "\n".join(lines)
    with open(os.path.join(args.evid, "icon-cross-check.log"), "w") as f:
        f.write(log + "\n")
    print(log)
    return 0


# =========================================================== fetch-missing ===
def fetch_url(url: str, attempts: int = 3, base_delay: float = 1.0) -> tuple[bytes | None, str]:
    """3 attempts, exponential backoff 1s/2s/4s. Returns (data, status_detail)."""
    last = ""
    for attempt in range(1, attempts + 1):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "areenax-icon-audit/1.0 (audit staging)"})
            with urllib.request.urlopen(req, timeout=30) as r:
                return r.read(), f"ok attempt={attempt}"
        except (urllib.error.URLError, urllib.error.HTTPError, OSError, TimeoutError) as e:
            last = f"attempt {attempt} failed: {type(e).__name__}: {e}"
            print(f"    {last}", flush=True)
            if attempt < attempts:
                delay = base_delay * (2 ** (attempt - 1))
                print(f"    backing off {delay:.0f}s before retry…", flush=True)
                time.sleep(delay)
    return None, f"failed after {attempts} attempts ({last})"


def cmd_fetch_missing(args) -> int:
    staging = os.path.abspath(args.staging)
    svg_dir = os.path.join(staging, "svg")
    os.makedirs(svg_dir, exist_ok=True)
    cache_path = os.path.join(staging, "cache-index.json")
    man_path = os.path.join(staging, "manifest.json")
    log_path = os.path.join(args.evid, "icon-fetch.log")

    cc_path = os.path.join(args.evid, "icon-cross-check.json")
    cc = json.load(open(cc_path)) if os.path.exists(cc_path) else None
    if cc is None:
        print("cross-check output missing — run cross-check first", file=sys.stderr)
        return 2

    jobs = []
    for m in cc["missing_live"] + cc["missing_design_pages_only"]:
        jobs.append({"glyph": m["glyph"], "variant": "", "target_res": f"ic_{m['glyph'].replace('-', '_')}"})
    for m in cc["missing_fill_variant"]:
        jobs.append({"glyph": m["glyph"], "variant": "_fill1", "target_res": f"ic_{m['glyph']}_fill"})
    if args.include_demo and not jobs:
        # Explicitly-labeled DEMO (only when the real missing list is empty):
        # exercise cache+retry against two REAL design-page-missing glyphs.
        jobs = [{"glyph": g, "variant": "", "target_res": f"ic_{g}"}
                for g in ("sports_handball", "qr_code")]
        print("NOTE: real missing list is empty — running explicitly-labeled demo job set "
              "(sports_handball, qr_code from design pages) to prove cache+retry.")

    cache = {}
    if os.path.exists(cache_path):
        cache = json.load(open(cache_path))

    items, downloaded, cache_hits, failures = [], 0, 0, 0
    lines = [f"fetch-missing @ {now()} staging={staging} jobs={len(jobs)}"]
    for j in jobs:
        name = j["glyph"]
        url = GH_BASE.format(name=name, variant=j["variant"])
        fname = f"{name}{j['variant']}_24px.svg"
        fpath = os.path.join(svg_dir, fname)
        entry = {"glyph": name, "variant": j["variant"] or "outlined", "url": url,
                 "file": os.path.relpath(fpath, staging), "target_res_name": j["target_res"],
                 "license": LICENSE}
        prev = cache.get(url)
        if prev and os.path.exists(fpath) and prev.get("md5") == md5_bytes(open(fpath, "rb").read()):
            entry.update(status="cache-hit", sha256=prev.get("sha256") or sha256_file(fpath),
                         md5=prev["md5"], bytes=os.path.getsize(fpath), attempts=0)
            cache_hits += 1
            lines.append(f"CACHE HIT  {name}{j['variant']} (md5 match, 0 network)")
        else:
            data, detail = fetch_url(url)
            if data is None:
                entry.update(status="failed", detail=detail, attempts=3)
                failures += 1
                lines.append(f"FAILED     {name}{j['variant']} :: {detail}")
            else:
                with open(fpath, "wb") as f:
                    f.write(data)
                entry.update(status="downloaded", sha256=sha256_file(fpath), md5=md5_bytes(data),
                             bytes=len(data), attempts=1, fetched_at=now())
                cache[url] = {"md5": entry["md5"], "sha256": entry["sha256"], "file": entry["file"],
                              "fetched_at": entry["fetched_at"]}
                downloaded += 1
                lines.append(f"DOWNLOADED {name}{j['variant']} {len(data)} B sha256={entry['sha256'][:16]}…")
        items.append(entry)

    manifest = {
        "tool": "icon_audit.py fetch-missing",
        "generated_at": now(),
        "staging_dir": staging,
        "app_res_touched": False,
        "note": "Staging ONLY. Fix phase (owner-approved) copies each svg into app/src/main/res/drawable "
                "as <target_res_name>.xml after svg2vector conversion — NOT done in PHASE 2.",
        "source": "https://github.com/google/material-design-icons (Material Symbols Outlined 24px)",
        "license": LICENSE,
        "retry_policy": "3 attempts, exponential backoff 1s/2s/4s",
        "cache_policy": "md5 of stored file vs cache-index.json; matching md5 => 0 network calls",
        "summary": {"jobs": len(jobs), "downloaded": downloaded, "cache_hits": cache_hits,
                    "failed": failures},
        "items": items,
    }
    write_json(man_path, manifest)
    write_json(cache_path, cache)
    write_json(os.path.join(args.evid, "icon-fetch-manifest.json"), manifest)
    log = "\n".join(lines + [f"SUMMARY downloaded={downloaded} cache_hits={cache_hits} failed={failures}"])
    with open(log_path, "w") as f:
        f.write(log + "\n")
    print(log)
    return 0 if failures == 0 else 1


# ==================================================================== main ===
def main(argv=None) -> int:
    ap = argparse.ArgumentParser(description="AREENAX PHASE 2 icon audit tool (read-only for app code)")
    ap.add_argument("--android", default=DEF_ANDROID)
    ap.add_argument("--webroot", default=DEF_WEBROOT)
    ap.add_argument("--evid", default=DEF_EVID)
    sub = ap.add_subparsers(dest="cmd", required=True)

    sub.add_parser("scan-native", help="inventory res/drawable*+mipmap*, code refs, unreferenced/missing")
    sub.add_parser("scan-web", help="extract Material Symbols glyph usage from web panel + design HTML")

    cc = sub.add_parser("cross-check", help="web<->native mapping: MISSING/EXTRA/MISMATCHED-STYLE")
    cc.add_argument("--include-design", action="store_true",
                    help="also report design-page-only glyphs (default: reported separately anyway)")

    fm = sub.add_parser("fetch-missing", help="download missing SVGs into staging (retry+cache+manifest)")
    fm.add_argument("--staging", required=True)
    fm.add_argument("--include-demo", action="store_true",
                    help="if real missing list is empty, run a labeled 2-glyph demo to prove cache+retry")

    args = ap.parse_args(argv)
    return {"scan-native": cmd_scan_native,
            "scan-web": cmd_scan_web,
            "cross-check": cmd_cross_check,
            "fetch-missing": cmd_fetch_missing}[args.cmd](args)


if __name__ == "__main__":
    sys.exit(main())
