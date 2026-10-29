"""Starter statistical reports over a recorded ``.run`` corpus.

Answers the spire-codex / sts2replays-style questions the trajectory corpus
was built for:

- **card win-rate** — which cards show up more in wins (``card_winrate``);
- **pick quality** — did picking a card correlate with winning vs skipping it
  (``card_pick_quality``, using the offered-vs-taken decision stream);
- **combos** — which card *pairs* co-occur in winning decks (``combos``);
- **event choices** — which event choices correlate with better outcomes
  (``event_choice_winrate``);
- **synergy usage** — for a catalog of known synergies, do decks that *hold*
  the combo actually *play* it, and does it win (``synergy_usage``);
- **great vs safe** — deck aggression: fast kills vs long grinds
  (``deck_aggression``).

CLI: ``python -m runstats.analyze <corpus_dir> [--min-n N] [--out report.md]``.
"""

from __future__ import annotations

import argparse
import json
import math
from collections import Counter
from itertools import combinations
from pathlib import Path
from typing import Any

from runstats.load import Run, base_card, card_label, load_corpus


def _wilson_lo(wins: int, n: int, z: float = 1.96) -> float:
    """Lower bound of the Wilson score interval — a sample-size-aware
    win-rate that doesn't over-reward a 2/2 card."""
    if n == 0:
        return 0.0
    p = wins / n
    denom = 1 + z * z / n
    centre = p + z * z / (2 * n)
    margin = z * math.sqrt((p * (1 - p) + z * z / (4 * n)) / n)
    return (centre - margin) / denom


def base_winrate(runs: list[Run]) -> float:
    return sum(r.won for r in runs) / len(runs) if runs else 0.0


# ── 1. card win-rate (presence in final deck) ─────────────────────────
def card_winrate(runs: list[Run], min_n: int = 20) -> list[dict[str, Any]]:
    n = Counter()
    w = Counter()
    for r in runs:
        for card in r.deck_set():
            n[card] += 1
            w[card] += r.won
    base = base_winrate(runs)
    rows = []
    for card, cnt in n.items():
        if cnt < min_n:
            continue
        wins = w[card]
        rows.append(
            {
                "card": card_label(card),
                "token": card,
                "runs_with": cnt,
                "winrate": round(wins / cnt, 4),
                "lift_vs_base": round(wins / cnt - base, 4),
                "wilson_lo": round(_wilson_lo(wins, cnt), 4),
            }
        )
    rows.sort(key=lambda x: x["wilson_lo"], reverse=True)
    return rows


# ── 2. pick quality (offered → picked vs skipped) ─────────────────────
def card_pick_quality(runs: list[Run], min_n: int = 15) -> list[dict[str, Any]]:
    picked_n, picked_w = Counter(), Counter()
    skip_n, skip_w = Counter(), Counter()
    for r in runs:
        for ev in r.card_rewards():
            picked = ev.get("picked")
            picked_base = base_card(picked) if picked else None
            for opt in ev.get("options", []):
                ob = base_card(opt)
                if ob == picked_base:
                    picked_n[ob] += 1
                    picked_w[ob] += r.won
                else:
                    skip_n[ob] += 1
                    skip_w[ob] += r.won
    rows = []
    cards = set(picked_n) | set(skip_n)
    for c in cards:
        pn, sn = picked_n[c], skip_n[c]
        if pn + sn < min_n:
            continue
        pick_wr = picked_w[c] / pn if pn else None
        skip_wr = skip_w[c] / sn if sn else None
        delta = (pick_wr - skip_wr) if (pick_wr is not None and skip_wr is not None) else None
        rows.append(
            {
                "card": card_label(c),
                "token": c,
                "offered": pn + sn,
                "pick_rate": round(pn / (pn + sn), 3),
                "winrate_if_picked": round(pick_wr, 4) if pick_wr is not None else None,
                "winrate_if_skipped": round(skip_wr, 4) if skip_wr is not None else None,
                "pick_delta": round(delta, 4) if delta is not None else None,
            }
        )
    rows.sort(key=lambda x: (x["pick_delta"] is None, -(x["pick_delta"] or 0)))
    return rows


# ── 3. combos (pair co-occurrence) ────────────────────────────────────
def combos(runs: list[Run], min_co: int = 15, top: int = 40) -> list[dict[str, Any]]:
    pair_n, pair_w = Counter(), Counter()
    for r in runs:
        cards = sorted(r.deck_set())
        won = r.won
        for a, b in combinations(cards, 2):
            pair_n[a, b] += 1
            pair_w[a, b] += won
    base = base_winrate(runs)
    rows = []
    for (a, b), cnt in pair_n.items():
        if cnt < min_co:
            continue
        wins = pair_w[a, b]
        rows.append(
            {
                "pair": f"{card_label(a)} + {card_label(b)}",
                "co_count": cnt,
                "winrate": round(wins / cnt, 4),
                "lift_vs_base": round(wins / cnt - base, 4),
                "wilson_lo": round(_wilson_lo(wins, cnt), 4),
            }
        )
    rows.sort(key=lambda x: x["wilson_lo"], reverse=True)
    return rows[:top]


# ── 4. event-choice win-rate ──────────────────────────────────────────
def event_choice_winrate(runs: list[Run], min_n: int = 10) -> list[dict[str, Any]]:
    n, w = Counter(), Counter()
    for r in runs:
        for event_id, choice in r.event_choices():
            key = (event_id, choice)
            n[key] += 1
            w[key] += r.won
    rows = []
    for (event_id, choice), cnt in n.items():
        if cnt < min_n:
            continue
        rows.append(
            {
                "event": event_id,
                "choice_idx": choice,
                "n": cnt,
                "winrate": round(w[event_id, choice] / cnt, 4),
                "wilson_lo": round(_wilson_lo(w[event_id, choice], cnt), 4),
            }
        )
    rows.sort(key=lambda x: (x["event"], -x["winrate"]))
    return rows


# ── 5. synergy usage ──────────────────────────────────────────────────
# A starter catalog. Each synergy requires ALL `require` substrings present in
# the deck (matched against base tokens); `payoff` names the card whose in-play
# usage we measure. Refine tokens against `card_winrate` output on real data.
DEFAULT_SYNERGIES: list[dict[str, Any]] = [
    {
        "name": "Inflame strength → Body Slam",
        "require": ["INFLAME", "BODY_SLAM"],
        "payoff": "BODY_SLAM",
    },
    {"name": "Second Wind exhaust-block", "require": ["SECOND_WIND"], "payoff": "SECOND_WIND"},
    {"name": "Battle Trance draw engine", "require": ["BATTLE_TRANCE"], "payoff": "BATTLE_TRANCE"},
    {"name": "Burning Pact exhaust-draw", "require": ["BURNING_PACT"], "payoff": "BURNING_PACT"},
    {"name": "Havoc value", "require": ["HAVOC"], "payoff": "HAVOC"},
]


def _deck_has(run: Run, substr: str) -> bool:
    return any(substr in c for c in run.deck_set())


def synergy_usage(
    runs: list[Run], catalog: list[dict[str, Any]] | None = None
) -> list[dict[str, Any]]:
    catalog = catalog or DEFAULT_SYNERGIES
    rows = []
    for syn in catalog:
        holders = [r for r in runs if all(_deck_has(r, s) for s in syn["require"])]
        if not holders:
            rows.append({"synergy": syn["name"], "n_holders": 0})
            continue
        payoff = syn["payoff"]
        used = 0
        total_plays = 0
        for r in holders:
            plays = sum(1 for c in r.combat_plays() if payoff in c)
            total_plays += plays
            if plays > 0:
                used += 1
        rows.append(
            {
                "synergy": syn["name"],
                "n_holders": len(holders),
                "winrate_holders": round(base_winrate(holders), 4),
                "used_rate": round(used / len(holders), 3),
                "avg_payoff_plays": round(total_plays / len(holders), 2),
            }
        )
    return rows


# ── 6. deck aggression (great vs safe) ────────────────────────────────
def deck_aggression(runs: list[Run]) -> dict[str, Any]:
    def profile(subset: list[Run]) -> dict[str, Any]:
        if not subset:
            return {}
        deck_sizes, fight_turns, plays_per_turn = [], [], []
        for r in subset:
            deck_sizes.append(len(r.deck()))
            fights = r.combats()
            for f in fights:
                if f["turns"] > 0:
                    fight_turns.append(f["turns"])
                    plays_per_turn.append(f["plays"] / max(f["turns"], 1))
        mean = lambda xs: round(sum(xs) / len(xs), 2) if xs else None  # noqa: E731
        return {
            "n": len(subset),
            "avg_deck_size": mean(deck_sizes),
            "avg_fight_turns": mean(fight_turns),
            "avg_plays_per_turn": mean(plays_per_turn),
        }

    return {
        "wins": profile([r for r in runs if r.won]),
        "losses": profile([r for r in runs if not r.won]),
    }


# ── report assembly ───────────────────────────────────────────────────
def build_report(runs: list[Run], min_n: int) -> dict[str, Any]:
    return {
        "n_runs": len(runs),
        "win_rate": round(base_winrate(runs), 4),
        "policy": runs[0].meta.get("policy") if runs else None,
        "combat_budget": runs[0].meta.get("combat_budget") if runs else None,
        "card_winrate": card_winrate(runs, min_n),
        "card_pick_quality": card_pick_quality(runs, max(min_n // 2, 8)),
        "combos": combos(runs, max(min_n // 2, 8)),
        "event_choice_winrate": event_choice_winrate(runs, max(min_n // 2, 5)),
        "synergy_usage": synergy_usage(runs),
        "deck_aggression": deck_aggression(runs),
    }


def _fmt_table(rows: list[dict[str, Any]], cols: list[str], limit: int = 25) -> str:
    if not rows:
        return "_(no rows above threshold)_\n"
    head = "| " + " | ".join(cols) + " |\n"
    sep = "| " + " | ".join("---" for _ in cols) + " |\n"
    body = ""
    for row in rows[:limit]:
        body += "| " + " | ".join(str(row.get(c, "")) for c in cols) + " |\n"
    return head + sep + body


def render_markdown(report: dict[str, Any]) -> str:
    md = f"# Run-corpus stats — {report['n_runs']} runs, "
    md += f"win rate {report['win_rate']:.1%} (policy={report['policy']}, {report['combat_budget']})\n\n"
    md += "## Card win-rate (present in final deck, Wilson-lower-bound ranked)\n\n"
    md += _fmt_table(
        report["card_winrate"], ["card", "runs_with", "winrate", "lift_vs_base", "wilson_lo"]
    )
    md += "\n## Best & worst by lift\n\n_top cards above; worst below_\n\n"
    md += _fmt_table(
        list(reversed(report["card_winrate"]))[:10],
        ["card", "runs_with", "winrate", "lift_vs_base"],
    )
    md += "\n## Pick quality (winrate if picked − if skipped)\n\n"
    md += _fmt_table(
        report["card_pick_quality"],
        ["card", "offered", "pick_rate", "winrate_if_picked", "winrate_if_skipped", "pick_delta"],
    )
    md += "\n## Top card combos (pair co-occurrence in winning decks)\n\n"
    md += _fmt_table(report["combos"], ["pair", "co_count", "winrate", "lift_vs_base", "wilson_lo"])
    md += "\n## Event-choice win-rate\n\n"
    md += _fmt_table(
        report["event_choice_winrate"],
        ["event", "choice_idx", "n", "winrate", "wilson_lo"],
        limit=40,
    )
    md += "\n## Synergy usage (does the deck play what it holds?)\n\n"
    md += _fmt_table(
        report["synergy_usage"],
        ["synergy", "n_holders", "winrate_holders", "used_rate", "avg_payoff_plays"],
    )
    md += "\n## Deck aggression — great (fast) vs safe (grindy)\n\n"
    agg = report["deck_aggression"]
    md += "| outcome | n | avg_deck_size | avg_fight_turns | avg_plays_per_turn |\n| --- | --- | --- | --- | --- |\n"
    for k in ("wins", "losses"):
        a = agg.get(k, {})
        md += f"| {k} | {a.get('n', '')} | {a.get('avg_deck_size', '')} | {a.get('avg_fight_turns', '')} | {a.get('avg_plays_per_turn', '')} |\n"
    return md


def main(argv: list[str] | None = None) -> None:
    ap = argparse.ArgumentParser(description="Statistical reports over a .run trajectory corpus.")
    ap.add_argument("corpus", help="directory of <seed>.run.gz files")
    ap.add_argument("--min-n", type=int, default=20, help="min sample size for card/combo tables")
    ap.add_argument("--out", type=Path, default=None, help="write markdown report here")
    ap.add_argument("--json", type=Path, default=None, help="write the full report JSON here")
    args = ap.parse_args(argv)

    runs = load_corpus(args.corpus)
    if not runs:
        raise SystemExit(f"no .run files under {args.corpus}")
    report = build_report(runs, args.min_n)
    md = render_markdown(report)
    print(md)
    # `render_markdown` emits em-dashes, `−` and `→`, and the JSON report can
    # carry any card/event name. `write_text` without an encoding uses the
    # locale default — cp1252 on Windows — and raises UnicodeEncodeError on the
    # first such character, so the report is never written.
    if args.out:
        args.out.write_text(md, encoding="utf-8")
        print(f"\nmarkdown → {args.out}")
    if args.json:
        args.json.write_text(json.dumps(report, indent=2), encoding="utf-8")
        print(f"json → {args.json}")


if __name__ == "__main__":
    main()
