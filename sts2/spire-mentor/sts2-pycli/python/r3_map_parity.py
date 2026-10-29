#!/usr/bin/env python3
"""R3.2: structural map parity between scraped corpus runs and the
Rust map generator.

The scraped `.run` files record only the visited *path* through each
act (a sequence of (map_point_type, room_model_id) tuples), NOT the
full grid with (col, row) coordinates. So we can't do a byte-for-byte
(col, row) → point_type diff. Instead we check:

  For each scraped run at v0.103.2:
    1. Build the Rust Act-1 map with the same C#-hashed seed.
    2. Enumerate all start→boss paths through the Rust map.
    3. Assert the scraped visited-type sequence (minus Neow at idx 0)
       appears as the room-type sequence of at least one Rust path.

If yes: Rust's map_generator + RNG hashing of the string seed agrees
with the C# game for that seed. If no: structural divergence.

Bead `sts2-rs-5hsqp` (R3.2).
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path

import sts2_rs
from run_files import LEGACY_SCRAPER_ROOT, codex_root, glob_runs, load_run

# Vendored, clone-portable corpus (sts2-rs/data/run-corpus/, git-lfs).
PRIMARY_ROOT = Path(__file__).resolve().parents[2] / "sts2-rs/data/run-corpus/files"


def _cache_dir() -> Path:
    """Writable projection-cache dir: STS2_PARITY_CACHE_DIR, else the legacy
    scraper-repo location while it exists, else gitignored logs/parity-cache."""
    env = os.environ.get("STS2_PARITY_CACHE_DIR")
    if env:
        return Path(env)
    legacy = LEGACY_SCRAPER_ROOT / "data" / "parity-cache"
    if legacy.is_dir():
        return legacy
    return Path(__file__).resolve().parents[1] / "logs" / "parity-cache"


CACHE_DIR = _cache_dir()
CHAR_ALIASES = {
    "the-ironclad": ["the-ironclad", "ironclad"],
    "the-silent": ["the-silent", "silent"],
    "the-defect": ["the-defect", "defect"],
    "the-necrobinder": ["the-necrobinder", "necrobinder"],
    "the-regent": ["the-regent", "regent"],
}


def _cache_path(character_dir: str, use_codex: bool) -> Path:
    suffix = "primary+codex" if use_codex else "primary"
    return CACHE_DIR / f"v103_{character_dir}_{suffix}.jsonl"


def _project_run(d: dict) -> dict | None:
    """Project a full .run dict down to just the fields parity needs."""
    if d.get("build_id") != "v0.103.2":
        return None
    if len(d.get("players", [])) > 1:
        return None
    if not _act1_path_complete(d):
        return None
    mph0 = d.get("map_point_history", [[]])[0]
    return {
        "seed": d.get("seed"),
        "ascension": d.get("ascension", 0),
        "win": bool(d.get("win")),
        "was_abandoned": bool(d.get("was_abandoned")),
        "scraped_seq": [r["map_point_type"] for r in mph0[1:]],
    }


def build_cache(character_dir: str, use_codex: bool, force: bool = False) -> Path:
    out_path = _cache_path(character_dir, use_codex)
    if out_path.exists() and not force:
        return out_path
    CACHE_DIR.mkdir(parents=True, exist_ok=True)
    roots = [PRIMARY_ROOT] + ([codex_root()] if use_codex else [])
    aliases = CHAR_ALIASES.get(character_dir, [character_dir])
    files: list[Path] = []
    for root in roots:
        for alias in aliases:
            files.extend(glob_runs(root / alias))
    print(f"building cache from {len(files)} files → {out_path.name}")
    seen: set[str] = set()
    n = 0
    tmp = out_path.with_suffix(".tmp")
    with tmp.open("w") as fh:
        for i, f in enumerate(files):
            if i % 1000 == 0 and i:
                print(f"  scanned {i}/{len(files)} kept={n}")
            try:
                d = load_run(f)
            except Exception:
                continue
            proj = _project_run(d)
            if proj is None:
                continue
            if proj["seed"] in seen:
                continue
            seen.add(proj["seed"])
            fh.write(json.dumps(proj) + "\n")
            n += 1
    tmp.rename(out_path)
    print(f"cached {n} v0.103.2 SP runs → {out_path}")
    return out_path


def _act1_path_complete(d: dict) -> bool:
    """Act-1 map_point_history is complete iff the player reached the act-1
    boss — either by winning, or by progressing into act 2+."""
    mph = d.get("map_point_history") or []
    if not mph:
        return False
    if len(mph) >= 2:
        return True
    last = mph[0][-1] if mph[0] else None
    return bool(last) and last.get("map_point_type") == "boss"


def find_v103_runs(
    character_dir: str,
    n: int,
    ascension: int | None = None,
    use_codex: bool = False,
    require_win: bool = True,
    rebuild_cache: bool = False,
):
    """Find v0.103.2 single-player runs with a complete act-1 path. Reads
    from a cached projection (built lazily on first call)."""
    cache = build_cache(character_dir, use_codex, force=rebuild_cache)
    out = []
    with cache.open() as fh:
        for line in fh:
            r = json.loads(line)
            if require_win and (not r["win"] or r["was_abandoned"]):
                continue
            if ascension is not None and r["ascension"] != ascension:
                continue
            out.append(r)
            if len(out) >= n:
                break
    return out


def scraped_act1_room_type_sequence(d: dict) -> list[str]:
    """Return the visited (map_point_type) sequence for act 1, skipping
    the Neow ancient node at index 0. The boss is the final entry."""
    return [r["map_point_type"] for r in d["map_point_history"][0][1:]]


def rust_paths(act_map: list[dict]) -> list[list[str]]:
    """Enumerate all start→boss paths through the Rust act map. Returns
    a list of room-type sequences (one per path)."""
    by_coord = {(p["col"], p["row"]): p for p in act_map}
    # Starting nodes = all nodes at row 1.
    starts = [p for p in act_map if p["row"] == 1]
    # Boss = single node at the top row (max row).
    max_row = max(p["row"] for p in act_map)
    boss_nodes = [p for p in act_map if p["row"] == max_row and p["point_type"] == "boss"]
    if not boss_nodes:
        # No explicit boss node — final-row node serves.
        boss_nodes = [p for p in act_map if p["row"] == max_row]
    boss_coords = {(b["col"], b["row"]) for b in boss_nodes}

    paths: list[list[str]] = []

    def walk(node: dict, acc: list[str]):
        acc = [*acc, node["point_type"]]
        if (node["col"], node["row"]) in boss_coords:
            paths.append(acc)
            return
        children = [by_coord[c, r] for (c, r) in node["children"] if (c, r) in by_coord]
        if not children:
            paths.append(acc)
            return
        for child in children:
            walk(child, acc)

    for start in starts:
        walk(start, [])
    return paths


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--character-dir", default="the-ironclad")
    ap.add_argument("-n", "--num", type=int, default=5)
    ap.add_argument(
        "--ascension",
        type=int,
        default=None,
        help="Restrict to a specific ascension level. Default: any.",
    )
    ap.add_argument("-v", "--verbose", action="store_true")
    ap.add_argument("--codex", action="store_true", help="Also ingest files-spire-codex corpus")
    ap.add_argument(
        "--no-require-win", action="store_true", help="Accept runs that died after act 1"
    )
    ap.add_argument(
        "--per-ascension", action="store_true", help="Print per-ascension match summary"
    )
    ap.add_argument(
        "--rebuild-cache", action="store_true", help="Force rebuild of the corpus cache"
    )
    args = ap.parse_args()

    targets = find_v103_runs(
        args.character_dir,
        args.num,
        args.ascension,
        use_codex=args.codex,
        require_win=not args.no_require_win,
        rebuild_cache=args.rebuild_cache,
    )
    if not targets:
        print("no targets")
        return 1
    print(f"corpus size: {len(targets)}")

    matched = 0
    matched_by_asc: dict[int, int] = {}
    total_by_asc: dict[int, int] = {}
    mismatches: list[tuple[str, list[str], list[list[str]]]] = []
    for d in targets:
        seed = d["seed"]
        ascension = d["ascension"]
        scraped_seq = d["scraped_seq"]
        rm = sts2_rs.RunManager(string_seed=seed, character="Ironclad", ascension_level=ascension)
        am = rm.act_map()
        if am is None:
            print(f"  {seed}: Rust act_map is None — skipping")
            continue
        paths = rust_paths(am)
        # Boss "?" handling: scraped sequence may have 'unknown' nodes
        # that Rust resolves at room-entry time. For structural parity
        # we compare the *map_point_type* sequence, which preserves
        # 'unknown'. So both sides should still have 'unknown' for ?
        # nodes — this works.
        path_strs = ["/".join(p) for p in paths]
        scraped_str = "/".join(scraped_seq)
        is_match = scraped_str in path_strs
        marker = "MATCH" if is_match else "MISMATCH"
        print(f"  [{marker}] {seed}  scraped_path_len={len(scraped_seq)}  rust_paths={len(paths)}")
        if args.verbose or not is_match:
            print(f"    scraped: {scraped_str}")
            if not is_match:
                # Show the closest Rust path by common prefix
                best = max(paths, key=lambda p: sum(1 for a, b in zip(p, scraped_seq) if a == b))
                print(f"    best Rust path: {'/'.join(best)}")
                print(f"    Rust map size: {len(am)} nodes")
                # Distribution diff
                from collections import Counter

                cs = Counter(scraped_seq)
                cr = Counter(p["point_type"] for p in am)
                print(f"    scraped type counter: {dict(cs)}")
                print(f"    Rust map all-nodes counter: {dict(cr)}")
        total_by_asc[ascension] = total_by_asc.get(ascension, 0) + 1
        if is_match:
            matched += 1
            matched_by_asc[ascension] = matched_by_asc.get(ascension, 0) + 1
        else:
            mismatches.append((seed, scraped_seq, paths))

    print(f"\n{matched}/{len(targets)} runs have a matching Rust path.")
    if args.per_ascension:
        print("\nper-ascension:")
        print(f"{'Asc':>4} {'match':>6} {'total':>6} {'pct':>6}")
        for asc in sorted(total_by_asc):
            t = total_by_asc[asc]
            m = matched_by_asc.get(asc, 0)
            pct = 100.0 * m / t if t else 0
            print(f"{asc:>4} {m:>6} {t:>6} {pct:>5.1f}%")
    return 0 if matched == len(targets) else 1


if __name__ == "__main__":
    sys.exit(main())
