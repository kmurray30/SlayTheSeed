# docs/ index

Two kinds of content live here. **Reference docs** are maintained and safe to
rely on; **lab-notebook history** is a point-in-time record of how work was
done — internal, never part of the public export, and read as history, not
as current truth (files are dated `YYYY-MM-DD-…` and are not updated when
reality moves on).

## Reference docs

| Doc | What it is |
|---|---|
| [bring-your-own-game.md](bring-your-own-game.md) | How the game-derived inputs (DLLs, localization, decompiled source, art, sound, the workbench bundle) are regenerated from a game install: `just import-game`, tiers, prerequisites. |
| [browser-workbench.md](browser-workbench.md) | The browser-only workbench (Rust port → WASM in a Web Worker, IndexedDB, PWA): architecture, build/gates, the golden-parity harness against the Python oracle, roadmap. |
| [open-source-plan.md](open-source-plan.md) | The public snapshot repo (`tfriedel/spire-mentor`): the settled decisions (D1–D8), the export boundary, the sync workflow. |
| [ppo-champion-training-playbook.md](ppo-champion-training-playbook.md) | **Required reading** before training or promoting a run-policy champion — pre-flight checks, recipe, gating rules. |
| [site/](site/) | The public documentation site (static HTML + screenshots, no build step), published to GitHub Pages from the public repo. Its numbers are copied from the docs and `champions.toml`; refresh them when a champion is promoted. |
| [run-ai-explainer.html](run-ai-explainer.html) | Self-contained explainer of the run-AI stack. |
| [sts2-synergy-explainer.html](sts2-synergy-explainer.html) | Self-contained explainer of the card-synergy system. |

Repo-level references live at the root: [../ARCHITECTURE.md](../ARCHITECTURE.md),
[../PROVENANCE.md](../PROVENANCE.md), [../CONTRIBUTING.md](../CONTRIBUTING.md),
[../BUILDING-WINDOWS.md](../BUILDING-WINDOWS.md).

## Internal lab-notebook history

| Dir | What it is |
|---|---|
| `plans/` | Dated implementation plans as they were written (e.g. the real-game bridge plan, the champion-manifest plan). A plan being here does not mean it was executed verbatim. |
| `handoffs/` | Session-to-session handoff notes for long-running efforts. |
| `experiments/` | Experiment write-ups with their result numbers frozen at the time of the run. |
| `superpowers/` | Older plan/spec pairs from the superpowers workflow era; predecessor to the current planning docs. |
| `synergy/` | The v1 card-synergy catalog + data. **Superseded** by synergy v2 (see the note at the top of `synergy/README.md`); kept for provenance. |

These trees stay verbatim: historical documents keep their stale paths and
retired-tooling references on purpose. Do not "fix" them, and do not cite
them as current behavior — grep the code (or `sts2-wiki/decompiled/`) instead.
