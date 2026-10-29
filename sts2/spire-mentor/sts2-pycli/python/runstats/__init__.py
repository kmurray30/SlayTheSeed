"""Statistical analysis over recorded ``.run`` trajectory corpora.

The Rust ``run_arena sweep --record-runs <dir>`` writes one gzipped
``<seed>.run.gz`` per run: the game's native ``RunHistory`` v9 schema at the
top level plus an ``sts2sim`` block (repro metadata + the per-decision stream
+ combat detail). This package loads such a corpus and produces spire-codex /
sts2replays-style aggregates: card win-rates, pick quality, combos, event-choice
outcomes, synergy usage, and deck-aggression profiles.

Entry points:

- :func:`runstats.load.load_corpus` — parse a directory of ``.run``/``.run.gz``.
- ``python -m runstats.analyze <dir>`` — print + write the starter reports.
"""

from runstats.load import load_corpus
