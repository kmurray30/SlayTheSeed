"""Compare A0 monster HP bands: C# decomp v0.107.1 (our build) vs sansb v0.111.0.

usage: python3 -I hp_diff.py MONSTER_MODELS_DIR SANSB_HP_BANDS_JSON

MONSTER_MODELS_DIR is the v0.107.1 decompilation's monster-model class
directory (one <Monster>.cs file per monster).
"""
import json
import re
import sys
from pathlib import Path

decomp = Path(sys.argv[1])
sansb = json.loads(Path(sys.argv[2]).read_text())["hp_bands"]

W1_KINDS = [
    "SHRINKER_BEETLE", "FUZZY_WURM_CRAWLER", "NIBBIT", "LEAF_SLIME_S", "TWIG_SLIME_S",
    "LEAF_SLIME_M", "TWIG_SLIME_M", "CUBEX_CONSTRUCT", "FLYCONID", "FOGMOG", "EYE_WITH_TEETH",
    "INKLET", "MAWLER", "ASSASSIN_RUBY_RAIDER", "AXE_RUBY_RAIDER", "BRUTE_RUBY_RAIDER",
    "CROSSBOW_RUBY_RAIDER", "TRACKER_RUBY_RAIDER", "SLITHERING_STRANGLER", "SNAPPING_JAXFRUIT",
    "VINE_SHAMBLER", "BYGONE_EFFIGY", "BYRDONIS", "PHROG_PARASITE", "WRIGGLER", "VANTOM",
    "CEREMONIAL_BEAST", "KIN_FOLLOWER", "KIN_PRIEST",
]


def snake(name: str) -> str:
    return re.sub(r"(?<=[a-z0-9])(?=[A-Z])", "_", name).upper()


def a0(expr: str, props: dict) -> int | None:
    expr = expr.strip().rstrip(";")
    m = re.fullmatch(r"-?\d+", expr)
    if m:
        return int(expr)
    m = re.search(r"GetValueIfAscension\(AscensionLevel\.\w+,\s*(-?\d+),\s*(-?\d+)\)", expr)
    if m:
        return int(m.group(2))
    if expr in props:
        return props[expr]
    return None


rows = []
for path in sorted(decomp.glob("*.cs")):
    text = path.read_text(errors="replace")
    mm = re.search(r"public override int MinInitialHp\s*=>\s*([^;]+);", text)
    mx = re.search(r"public override int MaxInitialHp\s*=>\s*([^;]+);", text)
    if not mm or not mx:
        continue
    kind = snake(path.stem)
    lo = a0(mm.group(1), {})
    hi = a0(mx.group(1), {"MinInitialHp": lo})
    rows.append((kind, lo, hi))

ours = {k: (lo, hi) for k, lo, hi in rows}
print(f"{'kind':26} {'v0.107.1 (C#)':>14} {'sansb v0.111.0':>15}  diff")
for kind in W1_KINDS:
    o = ours.get(kind)
    s = sansb.get(kind)
    s_t = tuple(s) if isinstance(s, list) else s
    flag = "" if o == s_t else "  <-- DIFFERENT"
    print(f"{kind:26} {str(o):>14} {str(s_t):>15}{flag}")
