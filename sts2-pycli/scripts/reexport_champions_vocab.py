#!/usr/bin/env python3
"""Re-export every champion over a widened PPO vocabulary, under a new filename.

GH #799 (v0.111.0 port, S10). The card / relic / potion vocabularies now span
both supported game builds, so every champion's ``emb_card`` / ``emb_relic`` /
``emb_potion`` table has to grow before the Rust height guard
(``sts2-runai::ppo::net::check_cat_embedding_rows``) will load it. Same
mechanism as ``rebake_champion_vocab.py`` (GH #544, boss vocab), two
differences:

* it widens whatever table grew, not just ``emb_boss``;
* it writes ``<stem><suffix>.onnx`` (plus a byte-identical ``<stem><suffix>.pt``,
  which git stores as the same blob) instead of overwriting, so the net the
  gate swept stays on disk as the baseline and the manifest names the new file.

The export goes through ``ppo.export_onnx.export`` from the checkpoint, so the
widening is ``ppo.model._widen_padded_embedding`` (new rows seeded from the old
OOV row). This script then CHECKS the result against the shipped graph instead
of trusting it, initializer by initializer:

* the graph inputs and outputs have the same names and shapes;
* every initializer that is not a widened table is bit-identical to the
  shipped one (same weights, and none of the "wide checkpoint exported into
  the default-width net" class: a width change would move every shape);
* a widened table is exactly the re-lay: id rows copied, the new rows and the
  OOV row equal to the old OOV row, the PAD row equal to the old PAD row;
* every embedding table is as tall as this build's vocab says it must be.

Behavioural equivalence on real observations is a separate check:
``scripts/vocab_reexport_equivalence.py``.

USAGE (from sts2-pycli, in the worktree's wheel venv)::

    python scripts/reexport_champions_vocab.py --suffix _v0111vocab
"""

from __future__ import annotations

import argparse
import hashlib
import shutil
import sys
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "python"))

#: The categorical columns, in ``empty_cat`` order, and their table names.
CAT_TABLES = [
    "emb_card",
    "emb_upg",
    "emb_relic",
    "emb_potion",
    "emb_room",
    "emb_event",
    "emb_fixed",
    "emb_optidx",
    "emb_surface",
    "emb_boss",
    "emb_deckop",
    "emb_ench",
]


def sha256_of(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def initializers(path: Path) -> dict[str, np.ndarray]:
    import onnx
    from onnx import numpy_helper

    model = onnx.load(str(path))
    return {i.name: numpy_helper.to_array(i) for i in model.graph.initializer}


def io_shapes(path: Path) -> dict[str, tuple]:
    import onnx

    model = onnx.load(str(path))
    out = {}
    for v in list(model.graph.input) + list(model.graph.output):
        dims = tuple(d.dim_value or d.dim_param for d in v.type.tensor_type.shape.dim)
        out[v.name] = (v.type.tensor_type.elem_type, dims)
    return out


def table_name(name: str) -> str | None:
    """``net.emb_card.weight`` -> ``emb_card``; ``None`` for a non-table."""
    for t in CAT_TABLES:
        if name.endswith(f"{t}.weight"):
            return t
    return None


def check_relay(old: np.ndarray, new: np.ndarray, what: str) -> int:
    """Assert ``new`` is ``_widen_padded_embedding(old)``; return rows added."""
    assert old.ndim == 2 and new.ndim == 2, what
    assert old.shape[1] == new.shape[1], f"{what}: embedding dim moved {old.shape} -> {new.shape}"
    old_n, new_n = old.shape[0] - 2, new.shape[0] - 2
    assert new_n > old_n, f"{what}: did not grow"
    assert np.array_equal(new[:old_n], old[:old_n]), f"{what}: a learned id row moved"
    for r in range(old_n, new_n):
        assert np.array_equal(new[r], old[old_n]), f"{what}: new row {r} is not the old OOV row"
    assert np.array_equal(new[new_n], old[old_n]), f"{what}: OOV row changed"
    assert np.array_equal(new[new_n + 1], old[old_n + 1]), f"{what}: PAD row changed"
    return new_n - old_n


def verify(old_path: Path, new_path: Path) -> dict[str, int]:
    """Structural equivalence of a re-export; returns {table: rows added}."""
    from ppo.schema import empty_cat

    assert io_shapes(old_path) == io_shapes(new_path), (
        f"graph I/O changed: {io_shapes(old_path)} -> {io_shapes(new_path)}"
    )
    old, new = initializers(old_path), initializers(new_path)
    assert old.keys() == new.keys(), (
        f"initializer set changed: -{sorted(old.keys() - new.keys())} "
        f"+{sorted(new.keys() - old.keys())}"
    )
    grown: dict[str, int] = {}
    for name in sorted(old):
        a, b = old[name], new[name]
        if a.shape == b.shape:
            assert a.dtype == b.dtype and np.array_equal(a, b), f"{name}: weights differ"
            continue
        t = table_name(name)
        assert t is not None, f"{name}: non-embedding shape moved {a.shape} -> {b.shape}"
        grown[t] = check_relay(a, b, name)
    # Every table the graph consumes must be exactly as tall as this build
    # indexes it (pad id + 1), the same guard the Rust loader applies.
    n_cat = io_shapes(new_path)["cat"][1][2]
    pads = empty_cat()
    for col, t in enumerate(CAT_TABLES[:n_cat]):
        hits = [v for k, v in new.items() if table_name(k) == t]
        assert len(hits) == 1, f"{t}: {len(hits)} initializers"
        assert hits[0].shape[0] == pads[col] + 1, (
            f"{t}: {hits[0].shape[0]} rows, this build's pad id is {pads[col]}"
        )
    # Recorded intermediate shapes too (tract reads them): unchanged, except a
    # grown table's own entry, which must carry its new height.
    old_vi, new_vi = value_infos(old_path), value_infos(new_path)
    assert old_vi.keys() == new_vi.keys(), "value_info set changed"
    for name, dims in old_vi.items():
        if table_name(name) in grown:
            assert new_vi[name] == (new[name].shape[0], *dims[1:]), name
        else:
            assert new_vi[name] == dims, f"value_info {name}: {dims} -> {new_vi[name]}"
    return grown


def value_infos(path: Path) -> dict[str, tuple]:
    import onnx

    model = onnx.load(str(path))
    return {
        v.name: tuple(d.dim_value or d.dim_param for d in v.type.tensor_type.shape.dim)
        for v in model.graph.value_info
    }


def shipped_manifest(rev: str) -> dict:
    """The manifest at ``rev``: which nets were shipped before the re-export."""
    import subprocess
    import tomllib

    text = subprocess.run(
        ["git", "show", f"{rev}:sts2-pycli/data/ppo/champions.toml"],
        cwd=ROOT.parent,
        check=True,
        capture_output=True,
        text=True,
    ).stdout
    return tomllib.loads(text)


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--suffix", required=True, help="appended to each stem, e.g. _v0111vocab")
    ap.add_argument(
        "--base-rev",
        default="HEAD",
        help="git rev whose champions.toml names the shipped nets to re-export",
    )
    args = ap.parse_args()

    import champions
    from ppo.export_onnx import export

    for character, entry in shipped_manifest(args.base_rev).items():
        old_onnx = champions.ppo_dir() / entry["onnx"]
        old_ckpt = champions.ppo_dir() / entry["ckpt"]
        assert old_ckpt.exists(), f"{character}: no checkpoint"
        stem = old_onnx.stem
        assert not stem.endswith(args.suffix), f"{character}: {stem} already re-exported"
        new_onnx = old_onnx.with_name(f"{stem}{args.suffix}.onnx")
        new_ckpt = old_ckpt.with_name(f"{stem}{args.suffix}.pt")
        print(f"\n[{character}] {old_onnx.name} -> {new_onnx.name}")
        assert sha256_of(old_onnx) == entry["sha256"], f"{character}: shipped net != manifest hash"

        shutil.copyfile(old_ckpt, new_ckpt)
        assert sha256_of(new_ckpt) == sha256_of(old_ckpt)
        export(str(old_ckpt), str(new_onnx))
        assert not new_onnx.with_name(new_onnx.name + ".data").exists(), "external data sidecar"
        if io_shapes(new_onnx) != io_shapes(old_onnx):
            # A champion gated before the boss / deck-op / enchant columns or
            # the later cont slots: today's exporter emits the CURRENT input
            # contract (12 cat, 25 cont), which feeds the net columns it never
            # had. Their weights would be zeros (load_compat), but that is a
            # different graph from the one the gate swept, so keep the shipped
            # graph and swap only its grown tables (`widen_in_place`).
            print(
                f"    exporter emits {io_shapes(new_onnx)['cat'][1]} cat / "
                f"{io_shapes(new_onnx)['cont'][1]} cont, shipped graph takes "
                f"{io_shapes(old_onnx)['cat'][1]} / {io_shapes(old_onnx)['cont'][1]}: "
                "widening the shipped graph's tables in place"
            )
            widen_in_place(old_onnx, old_ckpt, new_onnx)
        grown = verify(old_onnx, new_onnx)
        print(f"    tables grown: {grown}; every other initializer bit-identical")
        print(f"    sha256 {sha256_of(new_onnx)}")
    return 0


def widen_in_place(old_onnx: Path, ckpt: Path, out: Path) -> None:
    """Write ``old_onnx`` with its grown tables replaced, to ``out``.

    The replacement tables come from the CHECKPOINT, widened the way every
    re-export widens them (``load_compat`` -> ``_widen_padded_embedding``), and
    each is asserted equal to the shipped table re-laid, so the checkpoint and
    the shipped graph are proven to be the same weights before either is used.
    Nothing else in the graph is touched.
    """
    import onnx
    import torch
    from onnx import numpy_helper
    from ppo.model import RunPolicyNet, load_compat

    net = RunPolicyNet()
    ck = torch.load(str(ckpt), map_location="cpu", weights_only=True)
    load_compat(net, ck.get("state_dict", ck))
    widened = {k: v.detach().numpy() for k, v in net.state_dict().items()}

    model = onnx.load(str(old_onnx))
    replaced = []
    for i, init in enumerate(model.graph.initializer):
        t = table_name(init.name)
        if t is None:
            continue
        want = widened[f"{t}.weight"]
        have = numpy_helper.to_array(init)
        if have.shape == want.shape:
            continue
        check_relay(have, want, f"{init.name} (checkpoint vs shipped graph)")
        model.graph.initializer[i].CopyFrom(numpy_helper.from_array(want, init.name))
        replaced.append(t)
        # The exporter also records the table's shape in `value_info`; tract
        # checks it against the initializer and refuses the graph on a
        # mismatch ("Failed analyse for node ... Const"), onnxruntime does not.
        infos = [v for v in model.graph.value_info if v.name == init.name]
        assert len(infos) <= 1, init.name
        for v in infos:
            dims = v.type.tensor_type.shape.dim
            assert [d.dim_value for d in dims] == list(have.shape), init.name
            dims[0].dim_value = want.shape[0]
    assert replaced, "nothing grew"
    onnx.checker.check_model(model)
    onnx.save(model, str(out))


if __name__ == "__main__":
    raise SystemExit(main())
