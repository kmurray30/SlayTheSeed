"""Per-encounter random-play outcome check across engines (untimed, "same work" evidence).

  python3 -I perenc_compare.py <dir with w1_encounters.txt> <ours_perenc_dir> \
      <sansb_w1_instance.json> <sts2core_w1_instance.txt>

(measure_all.sh's `perenc` block passes scripts/site-bench/bench as the first argument.)
ours_perenc_dir holds one site_random_bench JSON per encounter, produced with
  site_random_bench --encounters <ID> --seeds 200 --seed-start 0 --reps 50
(independent seeds per start state; do NOT invoke once per seed: that reuses policy
seeds 0..49 for every start state and biases the baseline, 31.2% vs 32.4% overall).
Prints win rate and actions/fight per encounter with deltas and 2 SE; last line JSON.
"""
import json
import math
import sys


def last(p):
    return json.loads(open(p).read().strip().splitlines()[-1])


b2, odir, sbp, scp = sys.argv[1:5]
enc = [x.strip() for x in open(b2 + "/w1_encounters.txt") if x.strip()]
sbe = {x["encounter"]: x for x in last(sbp)["per_encounter"]}
sce = {x["id"]: x for x in last(scp)["per_encounter"]}
rows = []
print(f'{"encounter":28s} {"ours":>6s} {"sansb":>6s} {"d_pp":>6s} {"sts2c":>6s} {"d_pp":>6s} {"2se":>4s} | {"o apf":>6s} {"sb":>6s} {"co":>6s}')
for e in enc:
    r = last(f"{odir}/{e}.json")
    f, w, a = r["fights"], r["wins"], r["decisions"]
    pw = w / f
    s, c = sbe[e], sce[e]
    cw = c["wins"] / c["fights"]
    se = 2 * math.sqrt(pw * (1 - pw) / f + s["win_rate"] * (1 - s["win_rate"]) / s["fights"])
    row = {"encounter": e, "ours_win": round(pw, 4), "sansb_win": s["win_rate"], "sts2core_win": round(cw, 4),
           "two_se_pp": round(100 * se, 2), "ours_apf": round(a / f, 2), "sansb_apf": round(s["actions_per_fight"], 2),
           "sts2core_apf": round(c["actions"] / c["fights"], 2)}
    rows.append(row)
    print(f'{e:28s} {pw:6.3f} {s["win_rate"]:6.3f} {100*(s["win_rate"]-pw):+6.1f} {cw:6.3f} {100*(cw-pw):+6.1f} '
          f'{100*se:4.1f} | {a/f:6.2f} {s["actions_per_fight"]:6.2f} {c["actions"]/c["fights"]:6.2f}')
print(json.dumps({"bench": "perenc_compare", "rows": rows}))
