"""C# side of the site workload: the same random fights, played through the
shipped game's own assembly by the headless harness (sts2-pycli/src/Sts2Headless).

Workload = site_random_bench.rs, as close as the harness allows:
  * Ironclad A0, starter deck + Burning Blood: a FRESH run per fight, opened with
    played_save=True so Neow is skipped and the deck stays the starter deck;
  * one Act-1 Overgrowth encounter per fight, round-robin over the same 22 ids
    the Rust site_random_bench cycles through (K fights each);
  * uniformly random legal actions (`legal_actions`, which the C# side filters
    with the full CanPlay predicate) until the fight leaves combat_play.

Timed region per fight: the first legal_actions call .. the response that
leaves combat_play (PLAY ONLY). Process boot, start_run and enter_room are
untimed and reported as setup_s. One process per fight: the harness allows one
start_run per process, and a lost fight ends the run. A watchdog kills a wedged
fight (swallowed-NRE turns are a known harness failure) and records it.

Also recorded per fight, to tell computation from waiting (October 2026: the
process was busy for 56% of play time, so the gap is both, and the site no
longer says it is mostly the asynchronous action pipeline):
  * la_s  - time inside the legal_actions round trips (a pipe + JSON round trip
            that advances nothing: an upper bound on the per-call harness cost);
  * cpu_s - CPU time the headless process (all its threads) used during play,
            from /proc/<pid>/stat; cpu_s / play_s near 1 means it is computing,
            near 0 that it is waiting.

Engine-independent: run once per protocol, not per engine.

  python -I cs_random_fights.py --pycli <sts2-pycli root> --per-encounter 3 --out cs.jsonl
  (set STS2_HEADLESS_BIN to a Release build of sts2-pycli/src/Sts2Headless and
  STS2_GAME_DIR to the game's assemblies; method in
  sts2-rs/docs/perf/2026-10-14-site-performance.md)
"""

import argparse
import json
import os
import random
import statistics
import sys
import threading
import time

# sts2-rs crates/sts2-content/src/encounters/act1_overgrowth.rs (ACT1_ENCOUNTERS)
ENCOUNTERS = {
    "weak": ["SHRINKER_BEETLE_WEAK", "FUZZY_WURM_CRAWLER_WEAK", "NIBBITS_WEAK", "SLIMES_WEAK"],
    "normal": [
        "CUBEX_CONSTRUCT_NORMAL", "FLYCONID_NORMAL", "FOGMOG_NORMAL", "INKLETS_NORMAL",
        "MAWLER_NORMAL", "NIBBITS_NORMAL", "OVERGROWTH_CRAWLERS", "RUBY_RAIDERS_NORMAL",
        "SLIMES_NORMAL", "SLITHERING_STRANGLER_NORMAL", "SNAPPING_JAXFRUIT_NORMAL",
        "VINE_SHAMBLER_NORMAL",
    ],
    "elite": ["BYGONE_EFFIGY_ELITE", "BYRDONIS_ELITE", "PHROG_PARASITE_ELITE"],
    "boss": ["VANTOM_BOSS", "CEREMONIAL_BEAST_BOSS", "THE_KIN_BOSS"],
}
TIER = {e: t for t, es in ENCOUNTERS.items() for e in es}
CLK_TCK = os.sysconf("SC_CLK_TCK") if hasattr(os, "sysconf") else 100


def proc_cpu_s(pid):
    """utime + stime of `pid` (all threads), seconds; None when unreadable."""
    try:
        with open(f"/proc/{pid}/stat") as fh:
            rest = fh.read().rsplit(")", 1)[1].split()
        return (int(rest[11]) + int(rest[12])) / CLK_TCK  # fields 14, 15 of stat(5)
    except (OSError, IndexError, ValueError):
        return None


def compact(state):
    """Untimed provenance snapshot: list lengths and scalar fields only."""
    if not isinstance(state, dict):
        return repr(state)[:200]
    out = {}
    for k, v in state.items():
        if isinstance(v, list):
            if k in ("relics",):
                out[k] = [r.get("id", r) if isinstance(r, dict) else r for r in v][:12]
            else:
                out[k] = f"len={len(v)}"
        elif isinstance(v, (int, float, str, bool)) or v is None:
            out[k] = v
    return out


def one_fight(Game, i, enc, timeout_s, snapshot):
    row = {"i": i, "encounter": enc, "tier": TIER[enc]}
    t_boot = time.perf_counter()
    g = Game()
    timer = threading.Timer(timeout_s, g.proc.kill)
    timer.start()
    try:
        s = g.start("Ironclad", seed=f"SITE{i}", ascension=0, played_save=True, act1="overgrowth")
        if s.get("decision") != "map_select":
            row["opened_on"] = s.get("decision")
            s = g.skip_neow(s)
        resp = g.enter_room("combat", encounter=enc)
        row["setup_s"] = round(time.perf_counter() - t_boot, 4)
        if resp.get("decision") != "combat_play":
            row["error"] = f"enter_room -> {json.dumps(resp)[:300]}"
            return row
        if snapshot:
            row["start_state"] = compact(g.get_state())
        rng = random.Random(1_000_003 * i + 17)
        steps = 0
        la_s = 0.0
        cpu0 = proc_cpu_s(g.proc.pid)
        t0 = time.perf_counter()
        while resp.get("decision") == "combat_play" and steps < 3000:
            t_la = time.perf_counter()
            acts = (g.legal_actions() or {}).get("actions") or []
            la_s += time.perf_counter() - t_la
            if not acts:
                row["anomaly"] = "no legal actions"
                break
            a = rng.choice(acts)
            name = a.get("action")
            if name == "end_turn":
                resp = g.end_turn()
            elif name == "play_card":
                resp = g.play_card(a["card_index"], a.get("target_index"))
            else:
                resp = g.act(name, **{k: v for k, v in a.items() if k != "action"})
            steps += 1
            # A mid-fight card prompt (rare with the starter deck) counts as a decision.
            while resp.get("decision") == "card_select" and steps < 3000:
                n = max(1, int(resp.get("min_select", 1) or 1))
                resp = g.select_cards(",".join(str(j) for j in range(n)))
                steps += 1
        play_s = time.perf_counter() - t0
        cpu1 = proc_cpu_s(g.proc.pid)
        final = resp.get("decision")
        row.update(steps=steps, play_s=round(play_s, 5), la_s=round(la_s, 5), final=final,
                   won=final not in ("game_over", "combat_play", None))
        if cpu0 is not None and cpu1 is not None:
            row["cpu_s"] = round(cpu1 - cpu0, 3)
        if final is None:
            row["anomaly"] = f"no decision in response: {json.dumps(resp)[:200]}"
        elif final == "combat_play":
            row["anomaly"] = f"step cap {steps} reached mid-fight"
    except Exception as e:  # wedge (watchdog kill -> EOF) or harness error
        row["error"] = repr(e)[:300]
    finally:
        timer.cancel()
        try:
            g.close()
        except Exception:
            pass
    return row


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--pycli", required=True, help="sts2-pycli root holding python/sts2_client.py and lib/")
    ap.add_argument("--per-encounter", type=int, default=3)
    ap.add_argument("--limit", type=int, default=0, help="stop after N fights (smoke test)")
    ap.add_argument("--timeout", type=float, default=120.0, help="per-fight watchdog, seconds")
    ap.add_argument("--out", required=True)
    a = ap.parse_args()
    sys.path.insert(0, os.path.join(a.pycli, "python"))
    from sts2_client import Game  # noqa: E402

    encs = [e for es in ENCOUNTERS.values() for e in es]
    plan = [(k * len(encs) + j, e) for k in range(a.per_encounter) for j, e in enumerate(encs)]
    if a.limit:
        plan = plan[: a.limit]
    rows = []
    with open(a.out, "w") as f:
        f.write(json.dumps({"meta": {"headless_bin": os.environ.get("STS2_HEADLESS_BIN"),
                                     "pycli": a.pycli, "fights_planned": len(plan),
                                     "python": sys.version.split()[0]}}) + "\n")
        for n, (i, enc) in enumerate(plan):
            row = one_fight(Game, i, enc, a.timeout, snapshot=(n < len(encs)))
            rows.append(row)
            f.write(json.dumps(row) + "\n")
            f.flush()
        ok = [r for r in rows if "play_s" in r and "error" not in r and "anomaly" not in r]
        play = sum(r["play_s"] for r in ok)
        steps = sum(r["steps"] for r in ok)
        la = sum(r.get("la_s", 0.0) for r in ok)
        cpu_rows = [r for r in ok if "cpu_s" in r]
        summ = {
            "SUMMARY": True,
            "fights_ok": len(ok),
            "fights_failed": len(rows) - len(ok),
            "decisions": steps,
            "play_s": round(play, 4),
            "fights_per_s_playonly": round(len(ok) / play, 3) if play else None,
            "decisions_per_s_playonly": round(steps / play, 2) if play else None,
            "ms_per_decision": round(1000 * play / steps, 3) if steps else None,
            "decisions_per_fight": round(steps / len(ok), 2) if ok else None,
            "win_rate": round(sum(r["won"] for r in ok) / len(ok), 3) if ok else None,
            "legal_actions_share_of_play": round(la / play, 3) if play else None,
            "cpu_util_during_play": round(sum(r["cpu_s"] for r in cpu_rows)
                                          / sum(r["play_s"] for r in cpu_rows), 3)
            if cpu_rows and sum(r["play_s"] for r in cpu_rows) else None,
            "setup_s_median": round(statistics.median(r["setup_s"] for r in rows if "setup_s" in r), 3)
            if any("setup_s" in r for r in rows) else None,
        }
        f.write(json.dumps(summ) + "\n")
    print(json.dumps(summ))


if __name__ == "__main__":
    main()
