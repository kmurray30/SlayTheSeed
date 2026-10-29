"""Unit tests for the #301 lethal-fight autopsy (pure logic only — no DLL,
no MCTS; those paths are exercised by the sweep itself)."""

from __future__ import annotations

import json
from pathlib import Path

import pytest
from diag_lethal_autopsy import (
    BUCKET_AMBIGUOUS,
    BUCKET_ATTRITION,
    BUCKET_BLUNDER,
    BUCKET_BLUNDER_SUSPECT,
    BUCKET_UNSCORED,
    EXCLUDE_EVENT_FLOOR,
    EXCLUDE_NO_COMBAT,
    EXCLUDE_NO_TERMINAL,
    EXCLUDE_RECON_FAILED,
    EXCLUDE_SURVIVED,
    FIGHT_BOSS,
    FIGHT_ELITE,
    FIGHT_MONSTER,
    OPTIMISM_HP_PER_TURN_MAX,
    SIMS_BOSS,
    SIMS_REGULAR,
    KillerFight,
    autopsy_run,
    classify,
    fight_class,
    find_killer_fight,
    flag_divergence,
    gate_verdict,
    p_survive,
    render_report,
    replay_fight,
    seed_of_log,
    sims_for,
)
from realgame_random import JsonlLogger
from shadow_replay import _iter_decision_rows

FIXTURE_DIR = Path(__file__).parent / "fixtures" / "hint_adapter"


def test_log_meta_writes_a_meta_record(tmp_path):
    lg = JsonlLogger(tmp_path)
    lg.log_meta(seed=1042, character="Ironclad", ascension=0)
    lg.close()

    recs = [json.loads(x) for x in lg.path.read_text(encoding="utf-8").splitlines() if x.strip()]
    assert len(recs) == 1
    assert recs[0]["meta"] == {"seed": 1042, "character": "Ironclad", "ascension": 0}
    # A meta record is not a decision: readers keyed on state/action must skip it.
    assert recs[0]["step"] is None
    assert "state" not in recs[0]


def test_headless_log_with_leading_meta_record_is_consumable(tmp_path):
    """Regression: shadow_replay must skip leading meta records.

    A headless log file's FIRST record is a meta record (seed/character/ascension).
    This test imports and exercises _iter_decision_rows directly (the same code
    path the reader uses), so removing/breaking the skip guard fails the test.
    """
    # Create a fixture with meta record + multiple decision rows (including
    # lookback scenario: two decisions in a row)
    rows = [
        {
            "step": None,
            "ts": "2026-07-26T00:00:00+00:00",
            "meta": {"seed": 999, "character": "Ironclad", "ascension": 0},
        },
        {
            "step": 0,
            "ts": "2026-07-26T00:00:00+00:00",
            "state": {"decision": "combat_play"},
            "action": {"action": "play_card"},
        },
        {
            "step": 1,
            "ts": "2026-07-26T00:00:00+00:00",
            "state": {"decision": "combat_play"},
            "action": {"action": "end_turn"},
        },
    ]

    # Exercise the helper directly — same code path shadow_replay.replay_run uses
    decision_rows = list(_iter_decision_rows(rows))

    # Must skip meta record (index 0) and yield decision rows with original indices
    assert len(decision_rows) == 2
    assert decision_rows[0] == (1, rows[1])  # First decision at index 1
    assert decision_rows[1] == (2, rows[2])  # Second decision at index 2

    # Verify the yielded rows carry "state" (what the reader needs)
    for idx, row in decision_rows:
        assert "state" in row, f"Decision row at index {idx} missing 'state' key"


def _combat(hp, *, floor=30, act=2, room="Monster", is_elite=False, enemies=("Cultist",)):
    return {
        "step": 1,
        "state": {
            "decision": "combat_play",
            "player": {"hp": hp, "max_hp": 80, "deck_size": 20},
            "enemies": [{"name": n, "hp": 30} for n in enemies],
            "context": {"total_floor": floor, "act": act, "room_type": room, "is_elite": is_elite},
            "run_state": {"character": "Ironclad"},
        },
        "action": {"action": "end_turn"},
    }


def _room(kind, hp, *, floor=30, act=2):
    return {
        "step": 1,
        "state": {
            "decision": kind,
            "player": {"hp": hp, "max_hp": 80},
            "context": {"total_floor": floor, "act": act, "room_type": kind},
            "run_state": {"character": "Ironclad"},
        },
        "action": {"action": "proceed"},
    }


def _card_select(hp, *, floor=30, act=2, room="Monster"):
    """A ``card_select`` row in the REAL wire shape the headless logs emit.

    Note what is MISSING versus ``_combat``: no ``enemies``, no ``run_state``.
    That is why a ``card_select`` can never serve as an entry state — it does
    not carry a roster and ``reconstruct`` expects a ``combat_play`` shape.
    """
    return {
        "step": 2,
        "state": {
            "decision": "card_select",
            "player": {"hp": hp, "max_hp": 80},
            "cards": [],
            "min_select": 0,
            "max_select": 2,
            "context": {"total_floor": floor, "act": act, "room_type": room},
        },
        "action": {"action": "select_cards", "args": {"indices": ""}},
    }


def _game_over(hp=0, victory=False):
    """The REAL ``game_over`` shape: ``victory`` is the outcome flag and the
    player stub reports ``hp: 0`` whether the run was won or lost (verified
    across all 58 game_over records of the 2026-07-26 sweep)."""
    return {
        "step": 9,
        "state": {
            "decision": "game_over",
            "victory": victory,
            "player": {"hp": hp, "max_hp": 80},
            "context": {"total_floor": 30, "act": 2, "room_type": "Monster"},
            "run_state": {"character": "Ironclad"},
        },
        "action": None,
    }


def test_fight_class_reads_room_type_case_insensitively():
    assert fight_class(_combat(50, room="Boss")["state"]) == FIGHT_BOSS
    assert fight_class(_combat(50, room="boss")["state"]) == FIGHT_BOSS
    assert fight_class(_combat(50, room="Elite")["state"]) == FIGHT_ELITE
    assert fight_class(_combat(50, room="Monster")["state"]) == FIGHT_MONSTER


def test_fight_class_honours_the_is_elite_flag():
    assert fight_class(_combat(50, room="Monster", is_elite=True)["state"]) == FIGHT_ELITE


def test_seed_comes_from_the_meta_record():
    recs = [{"step": None, "ts": "x", "meta": {"seed": 1042}}, _combat(50)]
    assert seed_of_log(recs) == 1042
    assert seed_of_log([_combat(50)]) is None


def test_killer_fight_is_the_last_combat_before_death():
    recs = [
        {"step": None, "ts": "x", "meta": {"seed": 1042, "character": "Ironclad"}},
        _combat(70, floor=10, act=1, room="Monster"),  # an earlier, survived fight
        _room("rest", 60, floor=11, act=1),
        _combat(48, floor=30, act=2, room="Elite", enemies=("Sentry", "Sentry")),
        _combat(20, floor=30, act=2, room="Elite", enemies=("Sentry", "Sentry")),
        _game_over(),
    ]
    fight, reason = find_killer_fight(recs)
    assert reason is None
    assert fight is not None
    assert fight.seed == 1042
    assert fight.fight_class == FIGHT_ELITE
    assert fight.floor == 30
    assert fight.act == 2
    # ENTRY state = the FIRST combat_play of the killer fight, not the last.
    assert fight.entry_hp == 48
    assert fight.entry_state["player"]["hp"] == 48


def test_mid_fight_card_select_does_not_truncate_the_entry_state():
    """Fights are interleaved with ``card_select`` rows (Dual Wield, Headbutt,
    an exhaust prompt...). Walking back over ``combat_play`` ALONE stops at the
    first such row and reports a mid-fight state — many rounds and tens of HP
    into the fight — as the "entry". The entry must be the first
    ``combat_play`` of the whole fight.
    """
    recs = [
        {"step": None, "ts": "x", "meta": {"seed": 1038}},
        _combat(80, floor=30, act=2, room="Elite", enemies=("Sentry", "Sentry")),
        _card_select(80, floor=30, room="Elite"),
        _combat(52, floor=30, act=2, room="Elite", enemies=("Sentry", "Sentry")),
        _card_select(31, floor=30, room="Elite"),
        _combat(4, floor=30, act=2, room="Elite", enemies=("Sentry", "Sentry")),
        _game_over(),
    ]
    fight, reason = find_killer_fight(recs)
    assert reason is None
    assert fight is not None
    assert fight.entry_hp == 80, "walked back only to the last card_select"
    assert fight.entry_state["player"]["hp"] == 80


def test_walk_back_does_not_cross_a_non_combat_card_select():
    """``card_select`` also fires OUT of combat (RestSite 218 / Event 89 /
    Shop 30 rows in the sweep). Stepping over one of those would splice the
    previous floor's fight onto this one and hand back an entry state from a
    fight the run already survived.
    """
    recs = [
        {"step": None, "ts": "x", "meta": {"seed": 7}},
        _combat(70, floor=28, act=2, room="Monster"),  # an earlier, SURVIVED fight
        _card_select(70, floor=29, room="RestSite"),  # smithing at the campfire
        _combat(48, floor=30, act=2, room="Elite", enemies=("Sentry",)),
        _combat(20, floor=30, act=2, room="Elite", enemies=("Sentry",)),
        _game_over(),
    ]
    fight, reason = find_killer_fight(recs)
    assert reason is None
    assert fight is not None
    assert fight.floor == 30
    assert fight.entry_hp == 48


def test_a_card_select_is_never_itself_the_entry_state():
    """The entry state is fed to ``reconstruct`` -> ``adapter.translate``,
    which needs the ``combat_play`` wire shape (a ``card_select`` row carries
    no ``enemies`` and no ``run_state``). Even when the fight's block STARTS
    with a card_select in the log, the entry must be its first combat_play.
    """
    recs = [
        {"step": None, "ts": "x", "meta": {"seed": 8}},
        _card_select(66, floor=30, room="Elite"),
        _combat(66, floor=30, act=2, room="Elite", enemies=("Sentry",)),
        _combat(12, floor=30, act=2, room="Elite", enemies=("Sentry",)),
        _game_over(),
    ]
    fight, reason = find_killer_fight(recs)
    assert reason is None
    assert fight is not None
    assert fight.entry_state["decision"] == "combat_play"
    assert fight.entry_hp == 66
    assert fight.roster == "Sentry"


def test_victorious_run_is_excluded_even_though_the_hp_stub_reads_zero():
    """The killer-fight autopsy must never score a WON run.

    ``game_over`` carries the outcome in ``victory``; its player stub reports
    ``hp: 0`` regardless (all 58 records of the sweep, including the 11 wins).
    A guard reading that stub's hp can therefore never fire. A victory whose
    last decision is a ``combat_play`` — beating the act-3 boss — would
    otherwise be autopsied as a killer fight and would vote in the gate.
    """
    recs = [
        {"step": None, "ts": "x", "meta": {"seed": 1003}},
        _combat(60, floor=48, act=3, room="Boss", enemies=("Heart",)),
        _game_over(hp=0, victory=True),
    ]
    fight, reason = find_killer_fight(recs)
    assert fight is None
    assert reason == EXCLUDE_SURVIVED


def test_a_lost_run_with_the_same_hp_stub_is_still_autopsied():
    # The mirror of the test above: hp==0 is NOT the discriminator, victory is.
    recs = [
        {"step": None, "ts": "x", "meta": {"seed": 1004}},
        _combat(60, floor=48, act=3, room="Boss", enemies=("Heart",)),
        _game_over(hp=0, victory=False),
    ]
    fight, reason = find_killer_fight(recs)
    assert reason is None
    assert fight is not None
    assert fight.fight_class == FIGHT_BOSS


def test_a_log_with_no_terminal_record_gets_its_own_exclusion_reason():
    # Distinct from a WIN: the run never reached game_over at all (truncated
    # or still in flight). Naming it separately keeps the exclusion histogram
    # honest instead of filing an unfinished run under "not lost".
    recs = [_combat(70), _room("rest", 60)]
    fight, reason = find_killer_fight(recs)
    assert fight is None
    assert reason == EXCLUDE_NO_TERMINAL


def test_issue_300_event_floor_death_is_excluded_not_classified():
    # #300: run ends on an EVENT floor at healthy HP with no lethal combat.
    recs = [
        {"step": None, "ts": "x", "meta": {"seed": 1051}},
        _combat(90, floor=20, act=2),
        _room("event", 126, floor=41, act=3),
        _game_over(hp=0),
    ]
    fight, reason = find_killer_fight(recs)
    assert fight is None
    assert reason == EXCLUDE_EVENT_FLOOR


def test_death_after_a_non_event_non_combat_room_is_excluded():
    # Distinct from the #300 case above: the last room is not an event, so the
    # reason must be the generic one, deterministically.
    recs = [{"step": None, "ts": "x", "meta": {"seed": 7}}, _room("rest", 100), _game_over(hp=0)]
    fight, reason = find_killer_fight(recs)
    assert fight is None
    assert reason == EXCLUDE_NO_COMBAT


def test_p_survive_excludes_timed_out_replays_from_the_denominator():
    replays = [
        {"player_won": True, "timed_out": False},
        {"player_won": False, "timed_out": False},
        {"player_won": False, "timed_out": True},  # not a loss — no verdict
    ]
    assert p_survive(replays) == 0.5  # noqa: RUF069


def test_p_survive_is_none_when_every_replay_timed_out():
    assert p_survive([{"player_won": False, "timed_out": True}]) is None


def test_classification_thresholds_are_the_pre_registered_ones():
    assert classify(0.0) == BUCKET_ATTRITION
    assert classify(0.35) == BUCKET_ATTRITION  # boundary is inclusive
    assert classify(0.36) == BUCKET_AMBIGUOUS
    assert classify(0.74) == BUCKET_AMBIGUOUS
    assert classify(0.75) == BUCKET_BLUNDER  # boundary is inclusive
    assert classify(1.0) == BUCKET_BLUNDER
    assert classify(None) == BUCKET_UNSCORED


def test_boss_fights_get_the_boss_sim_budget():
    assert sims_for(FIGHT_BOSS) == SIMS_BOSS
    assert sims_for(FIGHT_ELITE) == SIMS_REGULAR
    assert sims_for(FIGHT_MONSTER) == SIMS_REGULAR


def _fake_killer_fight() -> KillerFight:
    return KillerFight(
        seed=1,
        character="Ironclad",
        floor=10,
        act=1,
        fight_class=FIGHT_MONSTER,
        entry_hp=40,
        entry_max_hp=80,
        roster="Nibbit",
        entry_state={},
    )


def test_optimistic_parity_downgrades_a_blunder_to_suspect():
    # signed = pred_hp - actual_hp; >0 means the engine thinks the player kept
    # MORE hp than reality, i.e. it is optimistic about this fight.
    assert flag_divergence(BUCKET_BLUNDER, 3.0) == BUCKET_BLUNDER_SUSPECT
    assert (
        flag_divergence(BUCKET_BLUNDER, OPTIMISM_HP_PER_TURN_MAX + 0.01) == BUCKET_BLUNDER_SUSPECT
    )


def test_clean_parity_leaves_a_blunder_alone():
    assert flag_divergence(BUCKET_BLUNDER, 0.2) == BUCKET_BLUNDER
    assert (
        flag_divergence(BUCKET_BLUNDER, -3.0) == BUCKET_BLUNDER
    )  # pessimistic: not a false blunder
    assert flag_divergence(BUCKET_BLUNDER, None) == BUCKET_BLUNDER


def test_only_blunders_are_downgraded():
    assert flag_divergence(BUCKET_ATTRITION, 9.0) == BUCKET_ATTRITION
    assert flag_divergence(BUCKET_AMBIGUOUS, 9.0) == BUCKET_AMBIGUOUS


def _rec(cls, bucket):
    return {
        "seed": 1,
        "class": cls,
        "bucket": bucket,
        "p_survive": 0.5,
        "entry_hp": 40,
        "entry_max_hp": 80,
        "act": 2,
        "floor": 30,
        "roster": "Cultist",
    }


def test_gate_unparks_only_on_a_non_boss_attrition_majority():
    recs = [
        _rec(FIGHT_ELITE, BUCKET_ATTRITION),
        _rec(FIGHT_MONSTER, BUCKET_ATTRITION),
        _rec(FIGHT_MONSTER, BUCKET_BLUNDER),
    ]
    unpark, why = gate_verdict(recs)
    assert unpark is True
    assert "2" in why


def test_boss_deaths_do_not_count_toward_the_gate():
    # Bosses are reported but scored separately: the park is conditioned on
    # NON-boss deaths.
    recs = [
        _rec(FIGHT_BOSS, BUCKET_ATTRITION),
        _rec(FIGHT_BOSS, BUCKET_ATTRITION),
        _rec(FIGHT_MONSTER, BUCKET_BLUNDER),
    ]
    unpark, _ = gate_verdict(recs)
    assert unpark is False


def test_gate_is_false_without_a_strict_majority():
    recs = [_rec(FIGHT_MONSTER, BUCKET_ATTRITION), _rec(FIGHT_ELITE, BUCKET_BLUNDER)]
    unpark, _ = gate_verdict(recs)
    assert unpark is False


def test_excluded_runs_never_reach_the_gate():
    # The excluded record here mirrors the REAL production shape emitted by
    # autopsy_run's EXCLUDE_RECON_FAILED path: recon failure happens AFTER
    # classification, so `class` is already set to the fight's real non-boss
    # class while `bucket` is absent. A record with BOTH "excluded" set and a
    # non-boss "class" is exactly the shape that would slip past the CLASS
    # half of gate_verdict's filter alone -- only the `not r.get("excluded")`
    # guard keeps it out. If that guard were dropped, this record would
    # silently vote (missing "bucket" != BUCKET_ATTRITION) and could flip the
    # verdict.
    recs = [
        _rec(FIGHT_MONSTER, BUCKET_ATTRITION),
        {"seed": 9, "class": FIGHT_MONSTER, "excluded": EXCLUDE_RECON_FAILED},
    ]
    unpark, why = gate_verdict(recs)
    assert unpark is True
    assert "1" in why


def test_gate_is_false_and_undecided_when_no_fights_are_scored():
    # Zero voting fights (all boss, all excluded, or both) must NOT vacuously
    # unpark -- the reason must say the gate is undecided, not silently pick
    # a default.
    recs = [_rec(FIGHT_BOSS, BUCKET_ATTRITION), {"seed": 9, "excluded": EXCLUDE_EVENT_FLOOR}]
    unpark, why = gate_verdict(recs)
    assert unpark is False
    assert "cannot be decided" in why


def test_report_names_the_verdict_and_the_exclusions():
    recs = [_rec(FIGHT_MONSTER, BUCKET_ATTRITION), {"seed": 9, "excluded": EXCLUDE_EVENT_FLOOR}]
    md = render_report(recs)
    assert "#301" in md
    assert EXCLUDE_EVENT_FLOOR in md
    assert BUCKET_ATTRITION in md


def test_report_labels_the_unscored_column_by_what_it_counts():
    # The bucket is only ever reached when EVERY replay of a fight timed out
    # (p_survive -> None). The published writeup calls that column "timed
    # out"; the table header must say the same thing.
    recs = [_rec(FIGHT_MONSTER, BUCKET_UNSCORED)]
    header = next(line for line in render_report(recs).splitlines() if line.startswith("| scope |"))
    assert "timed out" in header
    assert BUCKET_UNSCORED not in header


def test_report_does_not_advertise_a_prior():
    # The bundled policy prior was removed on 2026-08-15 and search is always
    # uniform now, so the published markdown must not claim a prior setting --
    # a stale `use_prior=...` line would tell a reader the replays were
    # configured against something that no longer exists.
    recs = [_rec(FIGHT_MONSTER, BUCKET_ATTRITION)]
    md = render_report(recs)
    assert "use_prior" not in md
    assert "hidden_info=" in md, "the rest of the deployed-search tuple must survive"


# --- engine-wiring integration test (Task 8a) --------------------------------
# Everything above is pure logic and cannot see signature/schema drift between
# this module and the compiled `sts2_rs` engine / `sts2_hint.adapter` -- none
# of it ever calls `adapter.translate` or `sts2_rs.state_from_dict`. This test
# does, against a REAL `combat_play` wire state captured from a headless
# smoke sweep (`fixtures/hint_adapter/combat_play_monster_ironclad.json`,
# provenance in the file), so it is the one test in this module that would
# catch that class of bug. `slow` because it drives the compiled MCTS engine.
def _load_combat_play_fixture() -> dict:
    data = json.loads(
        (FIXTURE_DIR / "combat_play_monster_ironclad.json").read_text(encoding="utf-8")
    )
    return data["state"]


def _fixture_killer_fight(*, seed: int = 1001) -> KillerFight:
    """Build a KillerFight straight from the fixture, bypassing find_killer_fight.

    This test is about engine wiring (reconstruct -> replay -> classify), not
    fight-location logic -- that is already covered by
    ``test_killer_fight_is_the_last_combat_before_death`` et al. above.
    """
    state = _load_combat_play_fixture()
    player = state["player"]
    ctx = state["context"]
    return KillerFight(
        seed=seed,
        character="Ironclad",
        floor=ctx.get("total_floor"),
        act=int(ctx.get("act") or 0),
        fight_class=fight_class(state),
        entry_hp=int(player["hp"]),
        entry_max_hp=int(player["max_hp"]),
        roster="+".join(sorted(e.get("name", "?") for e in state.get("enemies") or [])),
        entry_state=state,
    )


@pytest.mark.slow
def test_replay_fight_wires_a_real_combat_play_state_into_the_engine():
    fight = _fixture_killer_fight()

    # Trivial budget (sims=2) so a real engine call still runs in seconds --
    # the point is proving the wire-up, not a meaningful survival estimate.
    results = replay_fight(fight, replays=1, sims=2)

    assert len(results) == 1
    record = results[0]
    for key in ("player_won", "final_hp", "timed_out"):
        assert key in record, f"replay record missing {key!r}: {record}"


@pytest.mark.slow
def test_autopsy_run_end_to_end_on_a_synthetic_log_built_from_the_fixture(tmp_path):
    """autopsy_run over a synthetic log whose one killer-fight entry state is
    the real captured fixture. Exercises load_records -> find_killer_fight ->
    reconstruct -> replay_fight -> classify -> flag_divergence in one call,
    the same sequence the real sweep runs per log file.
    """
    state = _load_combat_play_fixture()
    game_over_state = {
        "decision": "game_over",
        "player": {"hp": 0, "max_hp": state["player"]["max_hp"]},
        "context": state["context"],
        "run_state": {"character": "Ironclad"},
    }
    log_path = tmp_path / "synthetic_killer_fight.jsonl"
    log_path.write_text(
        "\n".join(
            json.dumps(rec)
            for rec in (
                {
                    "step": None,
                    "ts": "2026-07-26T00:00:00+00:00",
                    "meta": {"seed": 1001, "character": "Ironclad", "ascension": 0},
                },
                {
                    "step": 0,
                    "ts": "2026-07-26T00:00:01+00:00",
                    "state": state,
                    "action": {"action": "end_turn"},
                },
                {
                    "step": 1,
                    "ts": "2026-07-26T00:00:02+00:00",
                    "state": game_over_state,
                    "action": None,
                },
            )
        )
        + "\n",
        encoding="utf-8",
    )

    rec = autopsy_run(log_path, replays=1)

    assert not rec.get("excluded"), rec
    assert rec["bucket"] in (
        BUCKET_ATTRITION,
        BUCKET_BLUNDER,
        BUCKET_AMBIGUOUS,
        BUCKET_BLUNDER_SUSPECT,
        BUCKET_UNSCORED,
    )
