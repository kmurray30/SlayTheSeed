#!/usr/bin/env python
"""Point a character at a newly trained champion, everywhere, in one step.

Rewrites that character's entry in ``data/ppo/champions.toml`` (the source of
truth) and regenerates the two derived regions. Everything else -- the Python
resolver, the frozen advisor bundle, the taxonomy view -- reads the manifest, so
nothing else needs touching.

The old failure this replaces: four hand-kept lists, where forgetting one left
a lane silently serving a retired net (PR #339 shipped one to production).

Usage:
    python scripts/promote_champion.py silent ppo_fullrun_silent_asc_it400.onnx
    just promote-champion silent ppo_fullrun_silent_asc_it400.onnx
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

_PYCLI = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(_PYCLI / "python"))

import champions  # noqa: E402
import sync_champions  # noqa: E402  (sibling script, same dir)


def _replace_key(section: str, key: str, value: str) -> str:
    """Set ``key = "value"`` inside one manifest section's text."""
    pattern = re.compile(rf"^{re.escape(key)}\s*=.*$", re.M)
    line = f'{key} = "{value}"'
    if pattern.search(section):
        return pattern.sub(line, section, count=1)
    # Append after the header line so a manifest missing `ckpt` still works.
    lines = section.splitlines()
    return "\n".join([lines[0], line, *lines[1:]])


def _features_of(ckpt: Path) -> str:
    """Read the encoder schema this net was TRAINED at, out of its own cfg.

    Derived, never passed by hand. A gated feature rewrites an *existing*
    ``cat`` slot rather than appending a column, so a net trained with one and
    served without it has an identical graph and identical tensor shapes --
    nothing crashes, and the mismatch cannot be detected from the loaded model
    (GH #545). A hand-maintained field would be exactly the "four hand-kept
    lists" failure this script exists to remove, one layer down.

    A checkpoint with no ``cfg`` predates the field and is default-schema,
    which is what every champion promoted before 2026-08 was trained at.
    """
    import torch  # local: keeps the manifest tooling importable without torch

    # weights_only=True suffices -- cfg is a plain dict, and unpickling
    # arbitrary objects to read metadata is not worth it.
    ck = torch.load(ckpt, map_location="cpu", weights_only=True)
    cfg = ck.get("cfg") if isinstance(ck, dict) else None
    features = str((cfg or {}).get("features") or "") if isinstance(cfg, dict) else ""
    return ",".join(sorted(f for f in features.replace(",", " ").split() if f))


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("character", help="manifest key, e.g. 'silent'")
    ap.add_argument("onnx", help="filename under data/ppo/, e.g. ppo_fullrun_silent_asc_it400.onnx")
    args = ap.parse_args(argv)

    character = champions.normalize(args.character)
    ppo_dir = champions.ppo_dir()
    onnx = ppo_dir / args.onnx
    if not onnx.exists():
        raise SystemExit(f"{onnx} does not exist -- commit the net first")
    if onnx.suffix != ".onnx":
        raise SystemExit(f"{args.onnx} is not an .onnx export")
    ckpt = onnx.with_suffix(".pt")
    if not ckpt.exists():
        raise SystemExit(
            f"{ckpt.name} is missing. The torch checkpoint is committed alongside "
            f"every champion; naming it explicitly is what stops the .pt and the "
            f".onnx drifting to different iterations."
        )

    manifest = champions.manifest_path()
    text = manifest.read_text(encoding="utf-8")
    header = f"[{character}]"
    start = text.find(f"\n{header}\n")
    if start < 0:
        raise SystemExit(
            f"{manifest.name} has no [{character}] section. A brand-new character "
            f"needs its entry (and a CharacterId mapping in sync_champions.py) "
            f"added by hand -- promotion only moves an existing one."
        )
    start += 1
    nxt = text.find("\n[", start)
    stop = len(text) if nxt < 0 else nxt + 1

    section = text[start:stop]
    previous = champions.get(character)
    section = _replace_key(section, "onnx", onnx.name)
    section = _replace_key(section, "ckpt", ckpt.name)
    section = _replace_key(section, "sha256", champions.sha256_of(onnx))
    section = _replace_key(section, "features", _features_of(ckpt))
    manifest.write_text(text[:start] + section + text[stop:], encoding="utf-8", newline="\n")
    champions.reset_cache()

    was = previous.onnx if previous else "<none>"
    print(f"champions.toml: {character} {was} -> {onnx.name}")
    feats = _features_of(ckpt)
    was_feats = (previous.features if previous else "") or "<default>"
    if feats != ((previous.features if previous else "") or ""):
        print(f"champions.toml: {character} features {was_feats} -> {feats or '<default>'}")
    rc = sync_champions.main([])
    if rc:
        return rc

    print("\nStill needs a human:")
    print(f"  - data/ppo/{onnx.stem}.provenance.md (what gate promoted it, vs which baseline)")
    print(f"  - the `gate` line in [{character}] -- it is still the OLD net's summary")
    print(
        "  - synergy's card taxonomy now clusters the NEW net; re-run any analysis "
        f"that cited {was}"
    )
    print("\nThen: just check-champions && just test")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
