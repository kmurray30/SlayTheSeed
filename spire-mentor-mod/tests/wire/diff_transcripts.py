#!/usr/bin/env python3
"""Readable diff of two BridgeServer harness transcripts.

usage: diff_transcripts.py A.jsonl B.jsonl [--context N] [--only SCN ...] [--skip SCN ...] [--semantic]

--semantic canonicalises every wire line / API result that parses as JSON
(sorted keys, compact) before comparing, i.e. it hides key-order and
escaping differences. The default (byte-exact) mode is the real test.

Compares scenario by scenario (scenarios are the "scenario" events the
harness writes; their names are stable), prints a summary table, then a
unified diff per differing scenario on a one-line-per-event rendering.
Changed server->client lines that are paired up in a diff hunk get an
annotation saying whether they are the same JSON value (only encoding /
key order / whitespace differ) or genuinely different content.

Exit: 0 identical, 1 differences, 2 usage / parse error.
"""
import argparse
import difflib
import json
import sys
from collections import OrderedDict


def load(path):
    scen = OrderedDict()
    cur = None
    with open(path, encoding="utf-8") as f:
        for n, raw in enumerate(f, 1):
            raw = raw.rstrip("\n")
            if not raw:
                continue
            try:
                e = json.loads(raw)
            except json.JSONDecodeError as ex:
                sys.exit(f"{path}:{n}: not JSON ({ex})")
            if e.get("ev") == "scenario":
                cur = e.get("name")
                scen[cur] = {"what": e.get("what", ""), "events": []}
                continue
            key = e.get("scn") or "(preamble)"
            if key not in scen:
                scen[key] = {"what": "", "events": []}
            scen[key]["events"].append(e)
    return scen


SEMANTIC = False


def j(v):
    return json.dumps(v, ensure_ascii=False, separators=(",", ":"))


def canon(v):
    """--semantic: canonicalise a wire string that is a JSON document."""
    if not SEMANTIC or not isinstance(v, str):
        return v
    try:
        return "<json>" + json.dumps(json.loads(v), ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    except Exception:
        return v


def render(e):
    e = dict(e)
    e.pop("scn", None)
    ev = e.pop("ev", "?")
    if ev in ("s2c", "s2c_none", "s2c_eof", "s2c_error", "s2c_timeout", "s2c_partial_line_at_eof"):
        c = e.pop("client", "?")
        if ev == "s2c":
            return f"S->C[{c}] {j(canon(e['line']))}"
        return f"{ev}[{c}] {j(e) if e else ''}".rstrip()
    if ev in ("c2s", "c2s_chunks", "client_connect", "client_close", "client_reset", "client_shutdown_send", "c2s_error"):
        c = e.pop("client", "?")
        if ev == "c2s" and "data" in e:
            note = f"   # {e['note']}" if e.get("note") else ""
            return f"C->S[{c}] {j(canon(e['data']))}{note}"
        return f"{ev}[{c}] {j(e) if e else ''}".rstrip()
    if ev == "api":
        call = e.pop("call")
        args = e.pop("args")
        return f"API {call}({j(args) if args is not None else ''}) -> {j(e)}"
    if ev == "api_start":
        return f"API_START {e.pop('id')} {e.pop('call')}({j(e.pop('args'))}) {j(e)}"
    if ev == "api_result":
        if "result" in e:
            e["result"] = canon(e["result"])
        return f"API_RESULT {e.pop('id')} {j(e)}"
    if ev == "probe":
        return f"PROBE {e.pop('id')} {j(e)}"
    return f"{ev.upper()} {j(e)}"


def s2c_payload(line):
    if not line.startswith("S->C["):
        return None
    try:
        body = json.loads(line.split("] ", 1)[1])
    except Exception:
        return None
    return body if isinstance(body, str) else None


def annotate(a_line, b_line):
    pa, pb = s2c_payload(a_line), s2c_payload(b_line)
    if SEMANTIC:
        return None
    if pa is None or pb is None:
        return None
    try:
        ja, jb = json.loads(pa), json.loads(pb)
    except Exception:
        return "   ~ wire text differs (not both valid JSON)"
    if ja == jb:
        if isinstance(ja, dict) and isinstance(jb, dict) and list(ja) != list(jb):
            return "   ~ SAME JSON value; key ORDER differs"
        return "   ~ SAME JSON value; only encoding/whitespace differs"
    if isinstance(ja, dict) and isinstance(jb, dict):
        keys = sorted(set(ja) | set(jb))
        diff = [k for k in keys if ja.get(k, "<absent>") != jb.get(k, "<absent>")]
        return "   ~ JSON differs in keys: " + ", ".join(diff)
    return "   ~ JSON value differs"


def scenario_diff(a, b, ctx):
    ra = [render(e) for e in a]
    rb = [render(e) for e in b]
    if ra == rb:
        return None
    out = []
    sm = difflib.SequenceMatcher(a=ra, b=rb, autojunk=False)
    for group in sm.get_grouped_opcodes(ctx):
        i1, i2, j1, j2 = group[0][1], group[-1][2], group[0][3], group[-1][4]
        out.append(f"@@ A events {i1 + 1}-{i2}  B events {j1 + 1}-{j2} @@")
        for tag, a1, a2, b1, b2 in group:
            if tag == "equal":
                out += ["   " + x for x in ra[a1:a2]]
                continue
            out += [" - " + x for x in ra[a1:a2]]
            out += [" + " + x for x in rb[b1:b2]]
            if tag == "replace":
                for x, y in zip(ra[a1:a2], rb[b1:b2]):
                    note = annotate(x, y)
                    if note:
                        out.append(note + f"  [A#{ra.index(x, a1) + 1}]")
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("a")
    ap.add_argument("b")
    ap.add_argument("--context", type=int, default=3)
    ap.add_argument("--only", nargs="*", default=None, help="scenario names (prefix match) to compare")
    ap.add_argument("--skip", nargs="*", default=[], help="scenario names (prefix match) to ignore")
    ap.add_argument("--semantic", action="store_true", help="compare JSON wire payloads by value (ignore key order / escaping)")
    args = ap.parse_args()
    global SEMANTIC
    SEMANTIC = args.semantic
    A, B = load(args.a), load(args.b)

    names = list(A)
    for n in B:
        if n not in A:
            names.append(n)

    def wanted(n):
        if any(n.startswith(s) for s in args.skip):
            return False
        return args.only is None or any(n.startswith(s) for s in args.only)

    rows, diffs = [], []
    for n in names:
        if not wanted(n):
            rows.append((n, "skipped", "", ""))
            continue
        if n not in A or n not in B:
            rows.append((n, "ONLY IN " + ("B" if n not in A else "A"), len(A.get(n, {"events": []})["events"]), len(B.get(n, {"events": []})["events"])))
            diffs.append(n)
            continue
        d = scenario_diff(A[n]["events"], B[n]["events"], args.context)
        rows.append((n, "identical" if d is None else "DIFFERS", len(A[n]["events"]), len(B[n]["events"])))
        if d is not None:
            diffs.append((n, d))

    print(f"A = {args.a}\nB = {args.b}\n")
    w = max(len(r[0]) for r in rows)
    print(f"{'scenario'.ljust(w)}  {'status':12} events(A/B)")
    for n, st, ca, cb in rows:
        print(f"{n.ljust(w)}  {st:12} {ca}/{cb}")
    print()
    for item in diffs:
        if isinstance(item, str):
            print(f"=== {item}: present in only one transcript ===\n")
            continue
        n, d = item
        print(f"=== {n}: {A[n]['what']} ===")
        print("\n".join(d))
        print()
    if diffs:
        print(f"RESULT: {len(diffs)} scenario(s) differ")
        return 1
    print("RESULT: transcripts identical")
    return 0


if __name__ == "__main__":
    sys.exit(main())
