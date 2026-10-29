"""Engine-backed parsed-vs-structured event payload parity (GH #237).

Forces every event on the Rust engine and, for each presented option, compares
the encoder's regex-scraped payload (``event_payload`` over the description)
against the engine's STRUCTURED deltas now surfaced on the option dict
(``gold_delta`` / ``hp_delta`` / ``max_hp_delta``). This:

  * proves the structured plumbing reaches every event's options end-to-end,
  * catches regex numbers that CONTRADICT the engine (a regex that lies is a
    bug — asserted hard), and
  * surfaces the silent-miss class the fix targets: options where the regex
    goes dark (0) but the engine actually moves gold/HP (reported; the
    structured path now covers them).

Gated on the ``sts2_rs`` wheel — skipped where it isn't built.
"""

from __future__ import annotations

import sys
from pathlib import Path

import pytest

PY = str(Path(__file__).resolve().parents[1] / "python")
if PY not in sys.path:
    sys.path.insert(0, PY)

sts2_rs = pytest.importorskip("sts2_rs")

from ppo.encoder import event_payload, structured_event_payload  # noqa: E402

CHARACTERS = ["Ironclad", "Silent", "Defect", "Regent", "Necrobinder"]
SEED = "EVTPAY0"


def _event_names(character: str) -> list[str]:
    rm = sts2_rs.RunManager(
        string_seed=SEED, character=character, ascension_level=0, neow_start=True
    )
    names: list[str] = []
    for act in rm.generated_rooms():
        names.extend(act.get("events_shuffled", []))
    # Dedupe, keep order.
    return list(dict.fromkeys(names))


def _forced_options(character: str, name: str) -> list[dict] | None:
    rm = sts2_rs.RunManager(
        string_seed=SEED, character=character, ascension_level=0, neow_start=True
    )
    if rm.force_event(name) is None:
        return None
    return rm.event_option_infos() or []


def test_structured_keys_present_on_every_option():
    """The #237 plumbing must reach every presented option: each carries the
    three structured delta keys (so the encoder never silently regexes)."""
    checked = 0
    for character in CHARACTERS:
        for name in _event_names(character):
            opts = _forced_options(character, name)
            if not opts:
                continue
            for opt in opts:
                assert "gold_delta" in opt, f"{character}/{name} opt missing gold_delta"
                assert "hp_delta" in opt, f"{character}/{name} opt missing hp_delta"
                assert "max_hp_delta" in opt, f"{character}/{name} missing max_hp_delta"
                checked += 1
    assert checked > 0, "no event options were checked — force_event wiring broken?"


def test_regex_never_contradicts_structured_gold():
    """Where the description regex extracts a nonzero GOLD, it must agree with
    the engine's structured gold_delta. A regex number that disagrees with the
    engine is a scraping bug — the exact hazard #237 removes."""
    max_hp = 80
    mismatches: list[str] = []
    silent_misses = 0
    for character in CHARACTERS:
        for name in _event_names(character):
            opts = _forced_options(character, name)
            if not opts:
                continue
            for opt in opts:
                struct = structured_event_payload(opt, max_hp)
                if struct is None:
                    continue
                s_gold, _ = struct
                text = f"{opt.get('label') or ''}. {opt.get('description') or ''}"
                r_gold, _ = event_payload(text, max_hp)
                # Compare the integer gold amounts (structured gold is a whole
                # number of gold; the regex sums whole numbers), sidestepping
                # float-equality fuzz.
                r_g, s_g = round(r_gold), round(s_gold)
                if r_g != 0 and r_g != s_g:
                    mismatches.append(
                        f"{character}/{name}/{opt.get('option_id')}: "
                        f"regex={r_g} structured={s_g} "
                        f"(desc={opt.get('description')!r})"
                    )
                elif r_g == 0 and s_g != 0:
                    silent_misses += 1
    # A lying regex is a hard failure.
    assert not mismatches, "regex gold contradicts engine:\n" + "\n".join(mismatches)
    # Not an assertion on the count (it drifts with text), just visibility:
    # these are the silent-zero cases the structured path now covers.
    print(f"structured path covers {silent_misses} regex-silent gold option(s)")


def test_some_costly_option_carries_a_real_structured_payload():
    """Sanity: across the sweep, at least one option actually moves gold or HP
    per the engine — otherwise the structured path is featurizing all zeros and
    the sweep proves nothing."""
    nonzero = 0
    for character in CHARACTERS:
        for name in _event_names(character):
            opts = _forced_options(character, name)
            if not opts:
                continue
            for opt in opts:
                # Read the raw integer deltas the engine surfaced; a nonzero
                # in any of them is a real payload (no float fuzz).
                if any(opt.get(k) for k in ("gold_delta", "hp_delta", "max_hp_delta")):
                    nonzero += 1
    assert nonzero > 0, "no event option carried a nonzero structured payload"
