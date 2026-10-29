#!/usr/bin/env python3
"""Diff two decompiled game trees for a version port, with decompiler noise removed.

Built for the v0.107.1 -> v0.111.0 port (GH #786). The ILSpy output of two game
builds differs in thousands of places that are not gameplay changes: `//IL_xxxx`
offset comments, brace layout, and a few API renames that touch every card
(`FromCard(this)` -> `FromCard(this, cardPlay)`). Those drown the real changes,
so each file is normalised before comparison:

* drop `//IL_` lines, blank lines and brace/paren-only lines;
* drop `///` XML doc comments and `using` directives (import churn, e.g. `using Godot;`);
* `this, cardPlay)` -> `this)` -- covers `FromCard(this, cardPlay)` and the
  same new trailing argument on `FromOsty`/`CreatureCmd.Damage`;
* `(List<Variant>)null` -> `null`;
* strip ILSpy's new `global::` qualifier;
* collapse runs of whitespace.

Only the gameplay-relevant namespaces are compared (`INCLUDE`); everything else
is reported as skipped with a file count, so the scope is auditable.

Usage (from sts2-wiki/; `-I` keeps the decomp dir off sys.path)::

    uv run python -I scripts/port_inventory.py OLD NEW list  # OLD/NEW: decompiled/<ver>
    uv run python -I scripts/port_inventory.py OLD NEW diff [--ns Models.Cards] [--file X.cs]
    uv run python -I scripts/port_inventory.py OLD NEW skipped
    uv run python -I scripts/port_inventory.py OLD NEW check INVENTORY.md

`check` parses the inventory's markdown tables and asserts that every
non-skipped added/removed/changed file has exactly one classified row (matched by
the `Decomp ref` column, `<Namespace>/<File>.cs:<line>` with everything up to
and including `.Core.` dropped from the namespace directory name). It prints the
coverage line and exits 1 on any gap or stray row.
"""

from __future__ import annotations

import argparse
import difflib
import re
import sys
from dataclasses import dataclass
from pathlib import Path

# Namespace directories are `<Vendor>.<Game>.Core.<Sub>`; everything up to and
# including this marker is dropped to get the short namespace.
CORE = ".Core."

# Namespaces compared, relative to `<Vendor>.<Game>.Core.`. An entry ending in ".*" also matches
# every sub-namespace; otherwise the match is exact.
INCLUDE: tuple[str, ...] = (
    "Models",
    "Models.*",
    "Hooks",
    "Combat",
    "Combat.*",
    "Commands",
    "Commands.Builders",
    "GameActions",
    "Entities.*",
    "Rooms",
    "Rewards",
    "Runs",
    "Map",
    "Odds",
    "Factories",
    "Random",
    "Helpers",
    "Helpers.*",
    "CardSelection",
    "MonsterMoves",
    "MonsterMoves.*",
    "Events",
    "Events.Custom.*",
    # Not in the original #786 list but gameplay-bearing: damage/block flags,
    # the dynamic vars that cards compute their numbers through, and the
    # unlock state that gates card/relic pools.
    "ValueProps",
    "Localization.DynamicVars",
    "Unlocks",
)

# Sub-namespace tokens that are never gameplay, even under an included root
# (e.g. Models.Cards.Mocks, GameActions.Multiplayer, Entities.UI).
EXCLUDE_TOKENS: tuple[str, ...] = (
    "UI",
    "Nodes",
    "Saves",
    "Multiplayer",
    "Platform",
    "Debug",
    "DevConsole",
    "AutoSlay",
    "Mocks",
    "Mock",
    "Audio",
    "Vfx",
)

_IL = re.compile(r"^\s*//\s*IL_")
# ILSpy sometimes glues an `//IL_xxxx: ...` comment onto the end of a code line.
_IL_TRAIL = re.compile(r"//\s*IL_.*$")
_XMLDOC = re.compile(r"^\s*///")
_BRACE_ONLY = re.compile(r"^[\s{}();,]*$")
_WS = re.compile(r"\s+")
_USING = re.compile(r"^\s*using [A-Za-z0-9_.=\s]+;\s*$")
_SUBS: tuple[tuple[re.Pattern[str], str], ...] = (
    # API drift: the card play is now threaded into every card-sourced
    # command (`FromCard(this, cardPlay)`, `FromOsty(osty, this, cardPlay)`,
    # `CreatureCmd.Damage(..., this, cardPlay)`). What it carries is an S6
    # sweep item; per file it is noise.
    (re.compile(r"\bthis,\s*cardPlay\)"), "this)"),
    (re.compile(r"\(List<Variant>\)null"), "null"),
    # ILSpy now qualifies its synthesized collection types with `global::`.
    (re.compile(r"\bglobal::"), ""),
)


# Comparison-only normalisation. The v0.111.0 build was decompiled against
# Godot's C# bindings, so ILSpy dropped the `(Node)(object)x` up-casts,
# `Variant.op_Implicit(...)` wrappers and `(EaseType)1` enum casts it emitted
# for v0.107.1. None of that is a gameplay change; a line that differs only in
# these is treated as equal (the displayed diff keeps the real text).
_GODOT_TYPES = (
    "Node|Node2D|Control|CanvasItem|GodotObject|Texture2D|CompressedTexture2D|Resource|"
    "Material|ShaderMaterial|SceneTree|Vector2\\[\\]|Variant\\[\\]|Action|Callable|"
    "IDisposable|Rect2|TextureRect|Label|Tween|PackedScene"
)
_KEY_SUBS: tuple[tuple[re.Pattern[str], str], ...] = (
    (re.compile(r"\((?:" + _GODOT_TYPES + r")\??\)"), ""),
    (re.compile(r"\(object\)"), ""),
    (re.compile(r"\b(?:NodePath|Variant|StringName)\.op_Implicit\b"), ""),
    (re.compile(r"\(ref (\w+)\)"), r"\1"),
    (re.compile(r"\(double\)|\(float\)"), ""),
    # ILSpy temporaries are renumbered whenever surrounding code changes.
    (re.compile(r"\b(val|num|flag|text|list|item|array|enumerable|card|cardModel)\d+\b"), r"\1"),
    (re.compile(r"[()]"), ""),
    (re.compile(r"\s+"), ""),
)


def short_ns(ns_dir: str) -> str:
    return ns_dir.split(CORE, 1)[1] if CORE in ns_dir else ns_dir


def is_included(ns: str) -> bool:
    """`ns` is the short namespace (e.g. `Models.Cards`)."""
    if any(tok in EXCLUDE_TOKENS for tok in ns.split(".")):
        return False
    for pat in INCLUDE:
        if pat.endswith(".*"):
            root = pat[:-2]
            if ns.startswith(root + "."):
                return True
        elif ns == pat:
            return True
    return False


@dataclass
class Line:
    no: int  # 1-indexed line number in the raw file
    text: str  # lightly normalised, for display
    key: str  # aggressively normalised, for comparison only


def normalise(path: Path) -> list[Line]:
    out: list[Line] = []
    raw = path.read_text(encoding="utf-8-sig", errors="replace").splitlines()
    for i, line in enumerate(raw, 1):
        if _IL.match(line) or _USING.match(line) or _XMLDOC.match(line):
            continue
        line = _IL_TRAIL.sub("", line)
        if _BRACE_ONLY.match(line):
            continue
        s = line
        for pat, rep in _SUBS:
            s = pat.sub(rep, s)
        s = _WS.sub(" ", s).strip()
        k = s
        for pat, rep in _KEY_SUBS:
            k = pat.sub(rep, k)
        out.append(Line(i, s, k))
    return out


@dataclass
class Entry:
    ns: str  # short namespace
    name: str  # file name
    status: str  # added | removed | changed
    line: int  # first changed line (new file; old file for removed)
    diff: list[str]

    @property
    def ref(self) -> str:
        return f"{self.ns}/{self.name}"


def _cs_files(root: Path) -> dict[str, Path]:
    """Map `<short ns>/<file>.cs` -> path for every .cs one level under a ns dir."""
    files: dict[str, Path] = {}
    for d in sorted(p for p in root.iterdir() if p.is_dir()):
        if CORE not in d.name:
            continue
        for f in sorted(d.glob("*.cs")):
            files[f"{short_ns(d.name)}/{f.name}"] = f
    return files


@dataclass
class Stats:
    raw_changed: int = 0  # in-scope files whose bytes differ
    noise_only: int = 0  # ... of which equal after normalisation


def collect(old: Path, new: Path) -> tuple[list[Entry], dict[str, int], Stats]:
    """Return (in-scope entries, skipped ns -> touched-file count, stats)."""
    of, nf = _cs_files(old), _cs_files(new)
    entries: list[Entry] = []
    skipped: dict[str, int] = {}
    stats = Stats()
    for key in sorted(set(of) | set(nf)):
        ns, name = key.split("/", 1)
        if key not in of:
            status = "added"
        elif key not in nf:
            status = "removed"
        else:
            status = "changed"
        if status == "changed":
            if of[key].read_bytes() == nf[key].read_bytes():
                continue
            a, b = normalise(of[key]), normalise(nf[key])
            at, bt = [x.key for x in a], [x.key for x in b]
            if is_included(ns):
                stats.raw_changed += 1
            if at == bt:
                if is_included(ns):
                    stats.noise_only += 1
                continue
        if not is_included(ns):
            skipped[ns] = skipped.get(ns, 0) + 1
            continue
        if status == "added":
            b = normalise(nf[key])
            entries.append(Entry(ns, name, status, b[0].no if b else 1, []))
            continue
        if status == "removed":
            a = normalise(of[key])
            entries.append(Entry(ns, name, status, a[0].no if a else 1, []))
            continue
        sm = difflib.SequenceMatcher(a=at, b=bt)
        first = None
        diff: list[str] = []
        for tag, i1, i2, j1, j2 in sm.get_opcodes():
            if tag == "equal":
                continue
            if first is None:
                first = b[j1].no if j1 < len(b) else (b[-1].no if b else 1)
            diff.append(
                f"@@ old {a[i1].no if i1 < len(a) else '-'} new {b[j1].no if j1 < len(b) else '-'}"
            )
            diff.extend(f"- {a[i].text}" for i in range(i1, i2))
            diff.extend(f"+ {b[j].no}: {b[j].text}" for j in range(j1, j2))
        entries.append(Entry(ns, name, status, first or 1, diff))
    return entries, skipped, stats


def cmd_list(entries: list[Entry], stats: Stats) -> None:
    by_ns: dict[str, list[Entry]] = {}
    for e in entries:
        by_ns.setdefault(e.ns, []).append(e)
    for ns in sorted(by_ns):
        es = by_ns[ns]
        counts = {s: sum(1 for e in es if e.status == s) for s in ("added", "removed", "changed")}
        print(f"## {ns}  ({len(es)}: {counts})")
        for e in es:
            print(
                f"  {e.status:8} {e.ref}:{e.line}  (+{sum(1 for d in e.diff if d[0] == '+')}"
                f"/-{sum(1 for d in e.diff if d[0] == '-')})"
            )
    kinds = {s: sum(1 for e in entries if e.status == s) for s in ("added", "removed", "changed")}
    print(
        f"TOTAL {len(entries)} in-scope files ({kinds}); {stats.raw_changed} in-scope files "
        f"differ byte-wise, {stats.noise_only} of them only in decompiler noise"
    )


def cmd_diff(entries: list[Entry], ns: str | None, file: str | None, max_lines: int) -> None:
    for e in entries:
        if ns and e.ns != ns:
            continue
        if file and e.name != file:
            continue
        print(f"=== {e.status} {e.ref}:{e.line}")
        body = e.diff
        if len(body) > max_lines:
            body = body[:max_lines] + [f"... ({len(e.diff) - max_lines} more lines)"]
        for d in body:
            print(d)


def cmd_skipped(skipped: dict[str, int]) -> None:
    for ns in sorted(skipped):
        why = "excluded" if any(t in EXCLUDE_TOKENS for t in ns.split(".")) else "out of scope"
        print(f"{ns}\t{skipped[ns]}\t{why}")
    print(f"TOTAL\t{sum(skipped.values())} files in {len(skipped)} namespaces")


_ROW = re.compile(r"^\|(?P<cells>.*)\|\s*$")
_REF = re.compile(r"`?(?P<ref>[A-Za-z0-9_.]+/[A-Za-z0-9_.\-]+\.cs)(?::\d+(?:-\d+)?)?`?")
KINDS = ("churn", "value", "behaviour", "new", "removed")


def cmd_check(entries: list[Entry], inventory: Path) -> int:
    want = {e.ref: e for e in entries}
    seen: dict[str, str] = {}
    errors: list[str] = []
    kinds: dict[str, int] = {k: 0 for k in KINDS}
    for lineno, line in enumerate(inventory.read_text().splitlines(), 1):
        m = _ROW.match(line)
        if not m:
            continue
        cells = [c.strip() for c in m.group("cells").split("|")]
        if len(cells) != 4 or cells[1] not in KINDS:
            continue
        r = _REF.search(cells[3])
        if not r:
            errors.append(f"line {lineno}: no decomp ref in {cells[3]!r}")
            continue
        ref = r.group("ref")
        if ref in seen:
            errors.append(f"line {lineno}: duplicate row for {ref}")
            continue
        seen[ref] = cells[1]
        kinds[cells[1]] += 1
        e = want.get(ref)
        if e is None:
            errors.append(f"line {lineno}: {ref} is not a changed file in scope")
            continue
        if e.status == "added" and cells[1] != "new":
            errors.append(f"line {lineno}: {ref} is added but classified {cells[1]}")
        if e.status == "removed" and cells[1] != "removed":
            errors.append(f"line {lineno}: {ref} is removed but classified {cells[1]}")
        if e.status == "changed" and cells[1] in ("new", "removed"):
            errors.append(f"line {lineno}: {ref} is a changed file but classified {cells[1]}")
    missing = sorted(set(want) - set(seen))
    for ref in missing:
        errors.append(f"unclassified: {want[ref].status} {ref}:{want[ref].line}")
    covered = len(set(seen) & set(want))
    for err in errors:
        print(err)
    print(
        f"coverage: {covered}/{len(want)} non-skipped changed files classified "
        f"({'OK' if not errors else f'{len(errors)} problems'}); "
        + ", ".join(f"{k} {v}" for k, v in kinds.items())
    )
    return 0 if not errors else 1


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawTextHelpFormatter)
    ap.add_argument("old", type=Path)
    ap.add_argument("new", type=Path)
    sub = ap.add_subparsers(dest="cmd", required=True)
    sub.add_parser("list")
    sub.add_parser("skipped")
    d = sub.add_parser("diff")
    d.add_argument("--ns")
    d.add_argument("--file")
    d.add_argument("--max-lines", type=int, default=400)
    c = sub.add_parser("check")
    c.add_argument("inventory", type=Path)
    args = ap.parse_args()
    entries, skipped, stats = collect(args.old, args.new)
    if args.cmd == "list":
        cmd_list(entries, stats)
    elif args.cmd == "skipped":
        cmd_skipped(skipped)
    elif args.cmd == "diff":
        cmd_diff(entries, args.ns, args.file, args.max_lines)
    elif args.cmd == "check":
        return cmd_check(entries, args.inventory)
    return 0


if __name__ == "__main__":
    sys.exit(main())
