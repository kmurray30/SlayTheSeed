"""Gates for the trainer's reusable-corpus dump (`--dump-corpus`).

A PPO campaign throws its collected decisions away every iteration: the
transitions are built, trained on, and dropped. Recording the same data again
for an offline study (`ppo.feature_probe`, `ppo.bc`) costs a fresh set of
sims — which is 95% of the loop's wall clock. The dump writes the corpus
`bc.load_corpus` already reads, straight out of a campaign you are running
anyway.

It is strided and off by default ON PURPOSE: a recorded run costs ~260 KiB
(measured on `ppo-regent/expert_corpus.jsonl`, 519 MB / 2000 runs), so a
960-game iteration is ~250 MB and an unstrided 150-iteration campaign would
write tens of gigabytes.
"""

import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "python"))

from ppo import trainer
from ppo.trainer import PPOConfig, corpus_dump_kwargs


def test_corpus_dump_is_off_by_default():
    cfg = PPOConfig()
    for it in range(5):
        assert corpus_dump_kwargs(cfg, it) == {}


def test_corpus_dump_fires_on_the_stride_and_nowhere_else(tmp_path):
    cfg = PPOConfig(dump_corpus=str(tmp_path), dump_corpus_every=10)
    fired = [it for it in range(31) if corpus_dump_kwargs(cfg, it)]
    assert fired == [0, 10, 20, 30]


def test_corpus_dump_turns_on_decision_recording(tmp_path):
    cfg = PPOConfig(dump_corpus=str(tmp_path), dump_corpus_every=10)
    kw = corpus_dump_kwargs(cfg, 0)
    assert kw["record_decisions"] is True
    assert kw["out_path"].startswith(str(tmp_path))


def test_corpus_dump_names_each_iteration_distinctly(tmp_path):
    cfg = PPOConfig(dump_corpus=str(tmp_path), dump_corpus_every=10)
    paths = [corpus_dump_kwargs(cfg, it)["out_path"] for it in (0, 10, 20)]
    assert len(set(paths)) == 3
    # Zero-padded so a shell glob sorts the corpus chronologically.
    assert os.path.basename(paths[0]) == "corpus_iter_0000.jsonl"
    assert os.path.basename(paths[2]) == "corpus_iter_0020.jsonl"


def test_corpus_dump_every_one_records_every_iteration(tmp_path):
    cfg = PPOConfig(dump_corpus=str(tmp_path), dump_corpus_every=1)
    assert all(corpus_dump_kwargs(cfg, it) for it in range(5))


def test_a_non_positive_stride_records_every_iteration_rather_than_dividing_by_zero(tmp_path):
    cfg = PPOConfig(dump_corpus=str(tmp_path), dump_corpus_every=0)
    assert all(corpus_dump_kwargs(cfg, it) for it in range(3))


def test_corpus_dump_creates_the_directory(tmp_path):
    target = tmp_path / "nested" / "corpus"
    cfg = PPOConfig(dump_corpus=str(target), dump_corpus_every=10)
    corpus_dump_kwargs(cfg, 0)
    assert target.is_dir()


def _spy_play_batch(monkeypatch):
    """Capture the kwargs the collector hands `play_batch`, play nothing."""
    seen: dict = {}

    def fake(seeds, **kw):
        seen["seeds"] = seeds
        seen.update(kw)
        return []

    monkeypatch.setattr(trainer, "play_batch", fake)
    return seen


def test_collect_rows_records_decisions_on_a_stride_iteration(tmp_path, monkeypatch):
    seen = _spy_play_batch(monkeypatch)
    cfg = PPOConfig(dump_corpus=str(tmp_path), dump_corpus_every=10, games_per_iter=4)
    trainer.collect_rows(cfg, 0, "acting.pt")
    assert seen["record_decisions"] is True
    assert os.path.basename(seen["out_path"]) == "corpus_iter_0000.jsonl"


def test_collect_rows_leaves_recording_alone_off_the_stride(tmp_path, monkeypatch):
    seen = _spy_play_batch(monkeypatch)
    cfg = PPOConfig(dump_corpus=str(tmp_path), dump_corpus_every=10, games_per_iter=4)
    trainer.collect_rows(cfg, 3, "acting.pt")
    assert "record_decisions" not in seen
    assert "out_path" not in seen


def test_the_dump_is_complete_and_loads_as_a_bc_corpus(tmp_path):
    """End-to-end: a real collection round-trips into `bc.load_corpus`.

    The invariant is COMPLETENESS — every decision the trainer builds a
    transition from must appear in the corpus. A dump that silently sampled
    runs, or dropped the last decision of each run, would still parse; only
    this count catches it.
    """
    import torch
    from ppo.bc import load_corpus
    from ppo.model import RunPolicyNet
    from ppo.rewards import ShapingConfig

    ckpt = tmp_path / "acting.pt"
    torch.save({"state_dict": RunPolicyNet().state_dict()}, ckpt)
    cfg = PPOConfig(
        character="Ironclad",
        games_per_iter=2,
        sims=4,
        boss_sims=4,
        procs=2,
        dump_corpus=str(tmp_path / "corpus"),
        dump_corpus_every=1,
    )
    rows = trainer.collect_rows(cfg, 0, str(ckpt))
    n_transitions = sum(len(r.get("transitions") or []) for r in rows)
    assert n_transitions > 0, "collection produced nothing to compare against"

    train, val, _ = load_corpus(
        str(tmp_path / "corpus" / "corpus_iter_0000.jsonl"), ShapingConfig(), val_mod=2
    )
    assert len(train) + len(val) == n_transitions


def test_collect_rows_plays_the_same_seeds_whether_or_not_it_records(tmp_path, monkeypatch):
    """The dump must not perturb the run distribution it is sampling."""
    plain = _spy_play_batch(monkeypatch)
    trainer.collect_rows(PPOConfig(games_per_iter=8), 0, "acting.pt")
    plain_seeds = list(plain["seeds"])

    dumping = _spy_play_batch(monkeypatch)
    cfg = PPOConfig(dump_corpus=str(tmp_path), dump_corpus_every=1, games_per_iter=8)
    trainer.collect_rows(cfg, 0, "acting.pt")
    assert list(dumping["seeds"]) == plain_seeds
