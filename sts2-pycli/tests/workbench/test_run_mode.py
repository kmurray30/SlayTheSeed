"""Run-mode config on BranchingLiveSession (two orthogonal axes)."""

import os
from pathlib import Path

import pytest
from fake_game import (
    FakeGame,
)  # shared stub (tests/workbench/fake_game.py); import path works under bare `pytest` (how `just test` runs), unlike `tests.workbench.*`
from workbench import branching

_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
_ONNX = Path(_ROOT) / "data" / "ppo" / "ppo_fullrun_it350.onnx"


@pytest.fixture(autouse=True)
def _ppo_env(monkeypatch):
    """The PPO brain needs the ONNX checkpoint; the native HeuristicRunAgent
    (combat, every brain) loads no net and needs no env vars."""
    monkeypatch.setenv("STS2_RUNAI_PPO", str(_ONNX))
    monkeypatch.delenv("STS2_RUNAI_VALUE", raising=False)


def _sess(tmp_path, monkeypatch):
    monkeypatch.setattr(branching, "Game", FakeGame)
    return branching.BranchingLiveSession(str(tmp_path))


def test_defaults(tmp_path, monkeypatch):
    s = _sess(tmp_path, monkeypatch)
    assert s.off_combat_brain == "ppo"
    assert s.ppo_impl == "rust"
    # Benchmark (1000/4000) — the SPA's default preset and the offline
    # win-rate sweep's budget, so a session that never gets a posted budget
    # still plays at the budget the UI claims it is playing at. Keep this in
    # step with `PRESET_SIMS.benchmark` in web/src/components/CombatBudget.tsx.
    assert (s.combat_sims, s.combat_boss_sims, s.combat_threads) == (1000, 4000, 1)
    assert s.reward_sims is None


def test_set_run_mode_partial_and_validation(tmp_path, monkeypatch):
    s = _sess(tmp_path, monkeypatch)
    s.set_run_mode(off_combat_brain="heuristic", combat_sims=1000, combat_boss_sims=4000)
    assert s.off_combat_brain == "heuristic"
    assert (s.combat_sims, s.combat_boss_sims) == (1000, 4000)
    assert s.ppo_impl == "rust"  # untouched
    s.set_run_mode(ppo_impl="python")
    assert s.ppo_impl == "python" and s.off_combat_brain == "heuristic"
    with pytest.raises(ValueError):
        s.set_run_mode(off_combat_brain="bogus")
    with pytest.raises(ValueError):
        # The learned-ranker brain was removed with the ranker net.
        s.set_run_mode(off_combat_brain="ranker")
    with pytest.raises(ValueError):
        s.set_run_mode(ppo_impl="bogus")
    with pytest.raises(ValueError):
        s.set_run_mode(combat_sims=0)


CHARS = ["Ironclad"]


def _rust_sess(tmp_path, brain, *, sims=100, boss=200):
    """A real rust-backed run-mode session at a small budget (fast)."""
    s = branching.BranchingLiveSession(str(tmp_path), backend="rust")
    s.start_run_mode(
        "Ironclad",
        3,
        off_combat_brain=brain,
        combat_sims=sims,
        combat_boss_sims=boss,
        threads=1,
    )
    return s


@pytest.mark.parametrize("brain", ["heuristic", "ppo"])
def test_run_mode_step_finishes_a_run(tmp_path, brain):
    s = _rust_sess(tmp_path, brain)
    seen_combat = False
    for _ in range(20_000):
        out = s.run_mode_step()
        assert out["error"] is None
        if out.get("debug", {}) and (out["debug"] or {}).get("is_combat"):
            seen_combat = True
        if out["done"] or out["over"]:
            break
    assert out["over"] or out["done"]
    assert seen_combat  # combat was driven by the HeuristicRunAgent for every brain


def test_run_mode_step_reports_the_search_behind_a_combat_move(tmp_path):
    """A combat step must carry the MCTS root distribution that chose it.

    Before this, ``_live_step`` handed the frontend a hardcoded ``scores: []``
    for every autoplayed step, so the reasoning sidebar had nothing to draw and
    fell back to a flat "autoplaying…" — the search ran, and its result was
    thrown away at the wire.
    """
    s = _rust_sess(tmp_path, "heuristic")
    combat = None
    for _ in range(20_000):
        out = s.run_mode_step()
        assert out["error"] is None
        if (out.get("debug") or {}).get("is_combat"):
            combat = out
            break
        if out["done"] or out["over"]:
            break
    assert combat is not None, "never reached a combat decision"

    search = combat["search"]
    assert search is not None
    assert search["scores"], "a searched combat move must report its root distribution"
    assert search["action"], "and name the move it chose"
    # Rows carry what the sidebar's bars are drawn from.
    row = search["scores"][0]
    assert {"action", "visits", "q", "is_best", "stable_idx"} <= set(row)
    assert any(r["is_best"] for r in search["scores"])


def test_search_rows_are_labelled_against_the_pre_action_board(tmp_path):
    """The rows describe the position BEFORE the move, so they must be resolved
    against that board — not the one the same response returns.

    ``rows_from_mcts`` looks card names up out of ``state["hand"]`` by index.
    The move has already been applied by the time the step is built, so scoring
    against the returned state would rename every row (and, once something
    dies, retarget every ``target_index``). Pinning it: no row may claim a
    label the pre-action hand can't account for.
    """
    s = _rust_sess(tmp_path, "heuristic")
    for _ in range(20_000):
        # Snapshot the board the NEXT step will search from.
        before = s._active().state
        out = s.run_mode_step()
        assert out["error"] is None
        if out["search"] and out["search"]["scores"]:
            hand = {c.get("index") for c in before.get("hand") or []}
            played = [r for r in out["search"]["scores"] if r["stable_idx"] >= 0]
            assert played, "expected at least one card row"
            assert all(r["stable_idx"] in hand for r in played), (
                "a row indexes a card the pre-action hand did not hold — "
                "the scores were labelled against the wrong state"
            )
            return
        if out["done"] or out["over"]:
            break
    raise AssertionError("never reached a combat decision")


def test_off_combat_steps_report_no_search(tmp_path):
    """Only combat runs a tree. A map/reward pick must say so rather than
    leaving the previous fight's distribution on the wire, which would let the
    sidebar attribute a search to a decision that never had one."""
    s = _rust_sess(tmp_path, "heuristic")
    for _ in range(20_000):
        out = s.run_mode_step()
        assert out["error"] is None
        assert "search" in out, "every step must state whether it searched"
        if not (out.get("debug") or {}).get("is_combat"):
            assert out["search"] is None
            return
        if out["done"] or out["over"]:
            break
    raise AssertionError("never reached an off-combat decision")


def test_changing_the_budget_mid_run_reaches_the_live_agent(tmp_path):
    """ "Stop, raise the sims, resume" has to actually raise the sims.

    ``set_run_mode`` used to only store the new budget on the session, and the
    stepper only ever builds an agent when there is none — so a mid-run change
    was accepted, echoed back, and silently ignored by the search.
    """
    s = _rust_sess(tmp_path, "heuristic", sims=100, boss=200)
    s.run_mode_step()  # ensure the agent exists
    assert s._faithful_agent.budget() == {"sims": 100, "boss_sims": 200}

    s.set_run_mode(combat_sims=250, combat_boss_sims=900)
    assert s._faithful_agent.budget() == {"sims": 250, "boss_sims": 900}
    # …and the run keeps going on the new budget rather than erroring out.
    assert s.run_mode_step()["error"] is None


def test_changing_one_half_of_the_budget_leaves_the_other_alone(tmp_path):
    """A partial update must not reset the sibling knob to a default. The
    setter takes two independent Optionals, and the agent-side one applies them
    onto its CURRENT budget for exactly this reason."""
    s = _rust_sess(tmp_path, "heuristic", sims=100, boss=200)
    s.run_mode_step()
    s.set_run_mode(combat_sims=300)
    assert s._faithful_agent.budget() == {"sims": 300, "boss_sims": 200}


def test_start_run_mode_honors_ascension(tmp_path):
    """The faithful / run-mode start must construct the run at the configured
    ascension, not silently force A0. Regression: the deployed workbench dropped
    the user's ascension (A10 → A0) because `start_run_mode` hardcoded 0 and
    never threaded it to the Rust `RunManager`."""
    s = branching.BranchingLiveSession(str(tmp_path), backend="rust")
    s.start_run_mode(
        "Ironclad",
        3,
        ascension=10,
        off_combat_brain="heuristic",
        combat_sims=100,
        combat_boss_sims=200,
        threads=1,
    )
    assert s.ascension == 10
    assert s.game.rm.ascension_level == 10


def test_start_faithful_honors_ascension(tmp_path):
    """The back-compat `start_faithful` alias threads ascension too."""
    s = branching.BranchingLiveSession(str(tmp_path), backend="rust")
    s.start_faithful("Ironclad", 3, ascension=5, sims=100, boss_sims=200)
    assert s.ascension == 5
    assert s.game.rm.ascension_level == 5


def test_run_mode_step_lazy_agent_on_hand_played_run(tmp_path):
    """A run opened via plain start() (hand-play, no agent) is still drivable
    by run_mode_step — the combat agent builds lazily from the session budget."""
    s = branching.BranchingLiveSession(str(tmp_path), backend="rust")
    s.start("Ironclad", "7", 0)
    assert s._faithful_agent is None
    s.set_run_mode(off_combat_brain="heuristic", combat_sims=100, combat_boss_sims=200)
    out = s.run_mode_step()
    assert s._faithful_agent is not None
    assert out["error"] is None


# --- the map route follows whichever brain is driving ------------------------


def test_driving_first_col_is_none_under_the_heuristic_brain(tmp_path, monkeypatch):
    """The map's painted route comes from the Rust pathing surface, which IS the
    heuristic brain's plan — nothing to reconcile, so leave the planner alone."""
    s = _sess(tmp_path, monkeypatch)
    s.set_run_mode(off_combat_brain="heuristic")
    assert s._driving_first_col({"decision": "map_select"}) is None


def test_driving_first_col_is_none_off_the_map_screen(tmp_path, monkeypatch):
    """The overlay map is openable at any decision; only a real map choice has a
    next room for the policy to have an opinion about."""
    s = _sess(tmp_path, monkeypatch)
    called: list[int] = []
    monkeypatch.setattr(s, "_ppo_recommend", lambda: called.append(1))
    assert s._driving_first_col({"decision": "combat_play"}) is None
    assert not called, "must not pay a PPO forward pass outside a map choice"


def test_driving_first_col_takes_the_ppo_map_pick(tmp_path, monkeypatch):
    s = _sess(tmp_path, monkeypatch)
    monkeypatch.setattr(
        s, "_ppo_recommend", lambda: {"action": "select_map_node", "args": {"col": 4, "row": 7}}
    )
    assert s._driving_first_col({"decision": "map_select"}) == 4


def test_driving_first_col_ignores_a_non_map_recommendation(tmp_path, monkeypatch):
    """Guards the race where the decision moves on between the state snapshot
    and the recommendation: seed nothing rather than seed a bogus column."""
    s = _sess(tmp_path, monkeypatch)
    monkeypatch.setattr(s, "_ppo_recommend", lambda: {"action": "choose_option", "args": {}})
    assert s._driving_first_col({"decision": "map_select"}) is None


def test_driving_first_col_survives_a_failing_net(tmp_path, monkeypatch):
    """A seeded route is a nicety; the map must render without it."""

    def _boom():
        raise RuntimeError("net unavailable")

    s = _sess(tmp_path, monkeypatch)
    monkeypatch.setattr(s, "_ppo_recommend", _boom)
    assert s._driving_first_col({"decision": "map_select"}) is None


def _autoplay_to_combat_log(s, *, limit=20_000):
    """Step the run until a node lands with a non-empty combat-log slice."""
    for _ in range(limit):
        out = s.run_mode_step()
        assert out["error"] is None
        node = s._active()
        if node.combat_log:
            return node
        if out["done"] or out["over"]:
            break
    return None


def test_autoplayed_steps_carry_their_combat_log_slice(tmp_path):
    """An autoplayed ("play to end") step must record the same per-action tape
    a hand-played one does.

    Regression: ``_agent_step`` added its node with only a label, so every
    node the native run agent produced had an empty ``combat_log`` and empty
    ``details``. The history sidebar therefore had nothing to render under a
    play-to-end run's steps — no damage numbers, and (because the ``net/full``
    toggle only appears when some step has a tape) not even the toggle.
    """
    s = _rust_sess(tmp_path, "heuristic")
    node = _autoplay_to_combat_log(s)
    assert node is not None, "no autoplayed step recorded a combat-log slice"
    kinds = {r["kind"] for r in node.combat_log}
    assert kinds, "the slice must carry rendered rows"
    assert all(r.get("text") for r in node.combat_log)


def test_autoplayed_steps_carry_diff_details(tmp_path):
    """The turn chips' kill count and the no-tape fallback both read
    ``details``; an autoplayed step recorded none."""
    s = _rust_sess(tmp_path, "heuristic")
    for _ in range(20_000):
        out = s.run_mode_step()
        assert out["error"] is None
        if s._active().details:
            return
        if out["done"] or out["over"]:
            break
    raise AssertionError("no autoplayed step recorded any diff details")


def test_the_slices_of_one_fight_partition_its_combat_log(tmp_path):
    """Each step gets exactly the rows ITS action appended — no repeats, no gaps.

    The engine's combat log is combat-scoped and CUMULATIVE, so the slice has
    to be marked before the action. Handing every node the log as it stood
    would repeat the whole fight under every step; marking it in the wrong
    place would drop rows. Concatenating the fight's slices in order must
    reproduce the rendered log exactly.
    """
    from workbench.combatlog import format_combat_log

    s = _rust_sess(tmp_path, "heuristic")
    assert _autoplay_to_combat_log(s) is not None
    # Walk the rest of this fight, then compare the concatenated slices against
    # the engine's own log rendered in one go.
    for _ in range(200):
        out = s.run_mode_step()
        assert out["error"] is None
        if out["done"] or out["over"] or s._active().state.get("decision") != "combat_play":
            break
    # Same `keep` as `_slice_display_logs`: the node slices carry the view-only
    # rows the board's effect stream needs, so rendering the whole log any
    # other way would be comparing two different logs.
    whole = format_combat_log(
        s.game.combat_log(),
        s._log_names(),
        keep=branching._VFX_ONLY_LOG_KINDS,
        player_id=s._player_loc_id(),
    )
    assert whole, "the fight logged nothing to compare against"
    # The nodes of THIS fight: the tail of the active path whose slices are the
    # ones the still-live combat log holds.
    path = s._path_to(s._active_id)
    stitched: list[dict] = []
    for node in reversed(path):
        if not node.combat_log:
            break
        stitched = [*node.combat_log, *stitched]
    assert stitched == whole[-len(stitched) :]
    assert len(stitched) > max(len(n.combat_log) for n in path), (
        "one step held the whole fight — the slice was not marked per action"
    )


def test_loading_a_saved_run_mode_line_restores_its_tape(tmp_path):
    """Reloading a saved play-to-end run must rebuild the same history.

    A save stores only ``(action, args)`` per step, so the restore replays the
    line — native agent actions through ``_apply_native``. That path recorded
    no tape and no details either, so a run whose history read perfectly went
    blank the moment it was saved and loaded back.
    """
    s = _rust_sess(tmp_path, "heuristic")
    assert _autoplay_to_combat_log(s) is not None
    for _ in range(10):
        out = s.run_mode_step()
        if out["done"] or out["over"]:
            break
    line, _ = s.active_line()
    before = [(n.combat_log, n.details) for n in s._path_to(s._active_id)]

    s2 = branching.BranchingLiveSession(str(tmp_path), backend="rust")
    s2.load_line("Ironclad", str(s.seed), s.ascension, line, run_mode=True)
    after = [(n.combat_log, n.details) for n in s2._path_to(s2._active_id)]
    assert after == before
    assert any(clog for clog, _ in after), "the restored line carries no tape at all"
