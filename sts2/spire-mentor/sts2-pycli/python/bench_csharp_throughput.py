#!/usr/bin/env python3
"""Measure C# headless-harness combat throughput, apples-to-apples with the
Rust `combat_throughput` bench (Ironclad starter deck vs a single Nibbit,
"always pick first legal action" agent, play to combat end).

The harness is NOT built for bulk rollouts: a single `start_run` per process
(a second errors "State is already set"), and individual turns can WEDGE in
headless (a swallowed Godot-node NRE leaves Python's readline() blocked
forever — see memory `feedback_csharp_headless_stuck_turn_is_swallowed_nre`).
So we:
  * do ONE start_run + skip_neow, then loop enter_room -> play -> reset,
  * print each combat's timing IMMEDIATELY (flushed) so a wedge still leaves
    clean partial data, and
  * expect the caller to wrap us in `timeout` as the watchdog.

Metrics (computed at the end from completed combats):
  boot           — spawn -> {"type":"ready"}.
  transitions/s  — in-combat actions / in-combat wall (cleanest, most
                   charitable to C#: excludes setup & reset).
  combats/s      — completed combats / total wall (realistic).
  ms_per_round_trip — raw Python<->C# IPC hop cost.

NIBBITS_WEAK = a single `IsAlone` Nibbit — the monster the Rust fixture uses
(its enemy is MonsterId("Nibbit"); there is no real Cultist in STS2).
"""

import json
import os
import sys
import time

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__))))
from sts2_client import Game

ENCOUNTER = sys.argv[1] if len(sys.argv) > 1 else "NIBBITS_WEAK"
N_COMBATS = int(sys.argv[2]) if len(sys.argv) > 2 else 40
N_BOOTS = int(sys.argv[3]) if len(sys.argv) > 3 else 5


def measure_boot(n):
    samples = []
    for _ in range(n):
        t0 = time.perf_counter()
        g = Game()
        samples.append(time.perf_counter() - t0)
        g.close()
    samples.sort()
    return samples[len(samples) // 2], min(samples)


def play_to_end(g, resp):
    tx = 0
    for _ in range(4000):
        if resp.get("decision") != "combat_play":
            break
        acts = g.legal_actions().get("actions") or []
        if not acts:
            break
        a = acts[0]
        resp = (
            g.end_turn()
            if a["action"] == "end_turn"
            else g.play_card(a["card_index"], a.get("target_index"))
        )
        tx += 1
    return resp, tx


def main():
    print(f"encounter={ENCOUNTER}  n_combats={N_COMBATS}  n_boots={N_BOOTS}", file=sys.stderr)

    boot_med, boot_min = measure_boot(N_BOOTS)
    print(
        json.dumps({"boot_median_s": round(boot_med, 4), "boot_min_s": round(boot_min, 4)}),
        flush=True,
    )

    g = Game()
    s = g.start("Ironclad", seed="0")
    g.skip_neow(s)

    # Warm-up combat (JIT / first-combat init), untimed.
    warm, _ = play_to_end(g, g.enter_room("combat", encounter=ENCOUNTER))
    if warm.get("decision") == "card_reward":
        g.skip_card_reward()

    rt0 = g.step
    play_wall = 0.0
    total_tx = 0
    done = 0
    t_all = time.perf_counter()

    for i in range(N_COMBATS):
        resp = g.enter_room("combat", encounter=ENCOUNTER)
        if resp.get("decision") != "combat_play":
            # post-combat screen leaked through; clear and retry next iter
            if resp.get("decision") == "card_reward":
                g.skip_card_reward()
            print(json.dumps({"combat": i, "skipped": resp.get("decision")}), flush=True)
            continue
        t1 = time.perf_counter()
        resp, tx = play_to_end(g, resp)
        dt = time.perf_counter() - t1
        play_wall += dt
        total_tx += tx
        done += 1
        print(
            json.dumps(
                {
                    "combat": i,
                    "tx": tx,
                    "play_ms": round(1000 * dt, 2),
                    "final": resp.get("decision"),
                }
            ),
            flush=True,
        )
        if resp.get("decision") == "card_reward":
            g.skip_card_reward()
        elif resp.get("decision") in ("game_over", "defeat"):
            # HP depleted across combats (no per-combat reset). Stop BEFORE the
            # next enter_room, which would wedge on the dead-run state.
            print(json.dumps({"note": "player died; stopping clean"}), flush=True)
            break

    wall = time.perf_counter() - t_all
    round_trips = g.step - rt0
    g.close()

    summary = {
        "SUMMARY": True,
        "encounter": ENCOUNTER,
        "boot_median_s": round(boot_med, 4),
        "combats_completed": done,
        "total_transitions": total_tx,
        "avg_tx_per_combat": round(total_tx / done, 1) if done else None,
        "wall_total_s": round(wall, 4),
        "wall_play_s": round(play_wall, 4),
        "transitions_per_sec_playonly": round(total_tx / play_wall, 1) if play_wall else None,
        "combats_per_sec_end_to_end": round(done / wall, 2) if wall else None,
        "round_trips": round_trips,
        "ms_per_round_trip": round(1000 * wall / round_trips, 4) if round_trips else None,
    }
    print(json.dumps(summary, indent=2), flush=True)


if __name__ == "__main__":
    main()
