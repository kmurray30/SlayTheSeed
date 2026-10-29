"""Autobattle mode (run-level drill): the pure core and the autoplay loop.

The pure core (`workbench.autobattle`) needs neither engine nor net. The loop tests
script `run_mode_step` so both advance paths' stopping rule, change-diff window
and caps are exercised without the rust backend: `autobattle_advance` (skip the
leg in one call) and `autobattle_step` (walk it one paintable decision at a time,
which is how the fights are watched). One rust-backed test pins the contract that
an autobattle only ever stops on a real run-level decision.
"""

import os
from pathlib import Path

import pytest
from fake_game import FakeGame  # shared stub from tests/workbench/fake_game.py
from fastapi.testclient import TestClient
from workbench import autobattle, branching
from workbench.server import create_app

_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
_ONNX = Path(_ROOT) / "data" / "ppo" / "ppo_fullrun_it350.onnx"

# ── pure core ────────────────────────────────────────────────────────────


def _cards(*specs):
    """``("Strike", True)`` / ``"Strike"`` → deck card dicts."""
    out = []
    for spec in specs:
        name, upgraded = spec if isinstance(spec, tuple) else (spec, False)
        out.append({"name": name, "upgraded": upgraded})
    return out


def _state(decision="card_reward", *, floor=1, hp=50, gold=99, deck=(), relics=(), potions=()):
    return {
        "decision": decision,
        "floor": floor,
        "act": 1,
        "player": {
            "hp": hp,
            "max_hp": 75,
            "gold": gold,
            "deck": _cards(*deck),
            "relics": [{"name": r} for r in relics],
            "potions": [{"name": p} for p in potions],
        },
    }


@pytest.mark.parametrize(
    "decision",
    ["card_reward", "card_select", "map_select", "shop", "rest_site", "event_choice", "treasure"],
)
def test_run_level_decisions_are_stop_points(decision):
    assert autobattle.is_stop_point(_state(decision))


@pytest.mark.parametrize("decision", ["combat_play", "game_over"])
def test_combat_and_game_over_never_stop(decision):
    """Skipping combat is the whole premise; game over ends the autobattle."""
    assert not autobattle.is_stop_point(_state(decision))
    # Even if a caller passes a config that names them, they must not stop.
    assert not autobattle.is_stop_point(_state(decision), frozenset({decision}))


def test_unrecognised_decision_is_autoplayed_not_stopped():
    """A surface the UI may not render must never strand the autobattle on it."""
    assert not autobattle.is_stop_point(_state("unknown"))
    assert not autobattle.is_stop_point({})


def test_first_stop_reports_absolutes_and_no_deltas():
    diff = autobattle.context_diff(None, _state(deck=["Strike", "Defend"]))
    assert diff.hp_delta is None and diff.gold_delta is None
    assert diff.from_floor is None and diff.deck_size == 2
    assert diff.deck.is_empty
    assert "HP 50/75" in diff.lines


def test_deck_diff_reports_adds_and_removes():
    prev = _state(deck=["Strike", "Strike", "Defend"])
    cur = _state(deck=["Strike", "Defend", "Inflame"])
    diff = autobattle.context_diff(prev, cur)
    assert diff.deck.added == ["Inflame"]
    assert diff.deck.removed == ["Strike"]
    assert diff.deck.upgraded == []
    assert "Added Inflame" in diff.lines and "Removed Strike" in diff.lines


def test_upgrade_folds_into_its_own_bucket():
    """Upgrading Strike is one upgrade, not a removal plus an addition."""
    prev = _state(deck=["Strike", "Strike"])
    cur = _state(deck=["Strike", ("Strike", True)])
    diff = autobattle.context_diff(prev, cur)
    assert diff.deck.upgraded == ["Strike"]
    assert diff.deck.added == [] and diff.deck.removed == []
    assert "Upgraded Strike" in diff.lines


def test_upgraded_copy_beyond_the_pairing_counts_as_added():
    """A second upgraded copy with no matching departure is genuinely new."""
    prev = _state(deck=["Strike", "Strike"])
    cur = _state(deck=["Strike", ("Strike", True), ("Strike", True)])
    diff = autobattle.context_diff(prev, cur)
    assert diff.deck.upgraded == ["Strike"]
    assert diff.deck.added == ["Strike+"]


def test_resource_and_tray_changes():
    prev = _state(hp=60, gold=120, relics=["Burning Blood"], potions=["Fire Potion"])
    cur = _state(hp=48, gold=45, relics=["Burning Blood", "Bag of Prep"], potions=[])
    diff = autobattle.context_diff(prev, cur, autoplayed=7)
    assert diff.hp_delta == -12 and diff.gold_delta == -75
    assert diff.relics_gained == ["Bag of Prep"] and diff.relics_lost == []
    assert diff.potions_lost == ["Fire Potion"]
    assert "Relic Bag of Prep" in diff.lines
    assert "Used potion Fire Potion" in diff.lines
    assert "HP 48/75 (−12)" in diff.lines
    assert "Gold 45 (−75)" in diff.lines
    assert "7 decisions auto-played" in diff.lines


def test_unchanged_quantities_are_omitted():
    """Reading only what moved is the point — no zero-delta noise."""
    same = _state(deck=["Strike"], relics=["Burning Blood"])
    lines = autobattle.context_diff(same, same).lines
    assert not any(line.startswith(("Added", "Removed", "Upgraded", "Relic")) for line in lines)
    assert "HP 50/75" in lines  # absolutes still shown, without a delta suffix


def test_missing_fields_degrade_rather_than_raise():
    diff = autobattle.context_diff({"decision": "shop"}, {"decision": "shop"})
    assert diff.hp is None and diff.gold is None and diff.deck_size == 0


def test_matching_the_champion_is_always_matched():
    assert autobattle.verdict_band(True, None) == autobattle.BAND_MATCHED
    assert autobattle.verdict_band(True, 0.9) == autobattle.BAND_MATCHED


def test_a_close_disagreement_is_a_near_tie_not_a_miss():
    """Reporting a coin flip as a miss trains the player to match noise."""
    assert autobattle.verdict_band(False, 0.0) == autobattle.BAND_NEAR_TIE
    assert (
        autobattle.verdict_band(False, autobattle.NEAR_TIE_PROB_MARGIN) == autobattle.BAND_NEAR_TIE
    )


def test_a_clear_disagreement_is_a_miss():
    assert (
        autobattle.verdict_band(False, autobattle.NEAR_TIE_PROB_MARGIN + 1e-9)
        == autobattle.BAND_DIFFERED
    )
    assert autobattle.verdict_band(False, 0.6) == autobattle.BAND_DIFFERED


def test_no_margin_collapses_to_two_bands_rather_than_guessing():
    """The heuristic brain exposes no probabilities; inventing a near-tie there
    would be a claim the data can't support."""
    assert autobattle.verdict_band(False, None) == autobattle.BAND_DIFFERED


def test_near_tie_threshold_is_overridable():
    assert autobattle.verdict_band(False, 0.2, near_tie=0.3) == autobattle.BAND_NEAR_TIE


def test_singular_autoplay_line():
    assert "1 decision auto-played" in autobattle.context_diff(None, _state(), autoplayed=1).lines


# ── autoplay loop ────────────────────────────────────────────────────────


def _scripted(tmp_path, monkeypatch, states):
    """A session whose ``run_mode_step`` walks *states* (root is ``states[0]``).

    Returns ``(session, step)``; call ``step()`` directly to stand in for the
    human committing an action at a stop, which is what moves the run off that
    stop in real use.
    """
    monkeypatch.setattr(branching, "Game", FakeGame)
    sess = branching.BranchingLiveSession(str(tmp_path))
    sess.start("Ironclad", "seed", 0)
    assert sess._root_id is not None
    sess._nodes[sess._root_id].state = states[0]
    remaining = iter(states[1:])

    def _step():
        state = next(remaining)
        node = sess._add_node(sess._active_id, state, action="auto", args={}, label="auto")
        sess._active_id = node.id
        over = state.get("decision") == "game_over"
        return {
            "state": state,
            "over": over,
            "done": over,
            "victory": False,
            "error": None,
            "steps": 0,
            "debug": {},
        }

    monkeypatch.setattr(sess, "run_mode_step", _step)
    monkeypatch.setattr(sess, "_brain_recommend", lambda: None)
    return sess, _step


def test_advance_autoplays_combat_and_stops_at_the_next_run_decision(tmp_path, monkeypatch):
    sess, _ = _scripted(
        tmp_path,
        monkeypatch,
        [
            _state("combat_play", floor=1),
            _state("combat_play", floor=1),
            _state("card_reward", floor=1, deck=["Strike", "Inflame"]),
        ],
    )
    out = sess.autobattle_advance()
    assert out["stopped"] is True and out["over"] is False
    assert out["autoplayed"] == 2
    assert out["state"]["decision"] == "card_reward"


def test_advance_returns_immediately_when_already_at_a_stop(tmp_path, monkeypatch):
    sess, _ = _scripted(tmp_path, monkeypatch, [_state("map_select")])
    out = sess.autobattle_advance()
    assert out["stopped"] is True and out["autoplayed"] == 0


def test_diff_spans_the_autoplayed_window_not_one_step(tmp_path, monkeypatch):
    """The change-diff must compare stop→stop; summarizing the skipped combats
    is exactly what makes a stop cheap to understand."""
    sess, commit = _scripted(
        tmp_path,
        monkeypatch,
        [
            _state("map_select", hp=70, deck=["Strike", "Defend"]),
            _state("combat_play", hp=61, deck=["Strike", "Defend"]),
            _state("card_reward", hp=61, deck=["Strike", "Defend"]),
            _state("combat_play", hp=52, deck=["Strike", "Defend", "Inflame"]),
            _state("shop", hp=52, deck=["Strike", "Defend", "Inflame"], relics=["Bag of Prep"]),
        ],
    )
    first = sess.autobattle_advance()
    assert first["autoplayed"] == 0 and first["diff"]["hp_delta"] is None
    assert first["diff"]["deck"]["added"] == []  # opening card shows absolutes only

    commit()  # the human picks a map node
    second = sess.autobattle_advance()
    assert second["autoplayed"] == 1 and second["diff"]["hp_delta"] == -9

    commit()  # the human takes a card
    third = sess.autobattle_advance()
    # Spans the whole window since the card_reward stop: deck gain AND relic.
    assert third["diff"]["deck"]["added"] == ["Inflame"]
    assert third["diff"]["relics_gained"] == ["Bag of Prep"]
    assert third["diff"]["hp_delta"] == -9


def test_advance_reports_game_over(tmp_path, monkeypatch):
    sess, _ = _scripted(
        tmp_path, monkeypatch, [_state("combat_play"), _state("game_over", floor=17)]
    )
    out = sess.autobattle_advance()
    assert out["over"] is True and out["stopped"] is False


def test_advance_is_bounded(tmp_path, monkeypatch):
    """A policy that never reaches a stop must not spin the request forever."""
    sess, _ = _scripted(tmp_path, monkeypatch, [_state("combat_play")] * 20)
    out = sess.autobattle_advance(max_steps=5)
    assert out["autoplayed"] == 5 and out["stopped"] is False and out["over"] is False


def test_advance_without_a_live_run_raises(tmp_path, monkeypatch):
    monkeypatch.setattr(branching, "Game", FakeGame)
    with pytest.raises(RuntimeError):
        branching.BranchingLiveSession(str(tmp_path)).autobattle_advance()


# ── watch path: one paintable decision per call ──────────────────────────


def test_step_plays_one_decision_at_a_time(tmp_path, monkeypatch):
    """The point of the watch path: each combat decision is handed back so the
    caller can paint it, instead of the whole fight resolving out of sight."""
    sess, _ = _scripted(
        tmp_path,
        monkeypatch,
        [
            _state("combat_play", hp=70),
            _state("combat_play", hp=64),
            _state("card_reward", hp=64, deck=["Strike", "Inflame"]),
        ],
    )
    first = sess.autobattle_step()
    assert first["played"] is True and first["stopped"] is False
    assert first["state"]["decision"] == "combat_play" and first["state"]["player"]["hp"] == 64
    # Mid-fight there is no stop→stop window yet, so no card is rendered.
    assert first["diff"] is None

    second = sess.autobattle_step()
    assert second["stopped"] is True and second["state"]["decision"] == "card_reward"
    assert second["diff"] is not None


def test_step_diff_spans_the_whole_watched_leg_not_the_last_move(tmp_path, monkeypatch):
    """Watching must not shrink the change-diff to the final step: the window is
    stop→stop however many calls it took to walk."""
    sess, commit = _scripted(
        tmp_path,
        monkeypatch,
        [
            _state("map_select", hp=70, deck=["Strike"]),
            _state("combat_play", hp=64, deck=["Strike"]),
            _state("combat_play", hp=58, deck=["Strike"]),
            _state("card_reward", hp=58, deck=["Strike", "Inflame"], relics=["Bag of Prep"]),
        ],
    )
    assert sess.autobattle_step()["stopped"] is True  # opening stop, nothing played
    commit()  # the human picks a map node
    while not (out := sess.autobattle_step())["stopped"]:
        pass
    assert out["diff"]["hp_delta"] == -12  # both combat steps, not just the last
    assert out["diff"]["deck"]["added"] == ["Inflame"]
    assert out["diff"]["relics_gained"] == ["Bag of Prep"]
    # The human's own committed pick is not an auto-played decision; the two
    # combat steps the agent took are.
    assert out["diff"]["autoplayed"] == 2


def test_step_at_a_stop_plays_nothing(tmp_path, monkeypatch):
    """A caller can loop the stepper unconditionally: sitting on a stop reports
    the stop rather than playing the human's decision for them."""
    sess, _ = _scripted(tmp_path, monkeypatch, [_state("map_select")])
    out = sess.autobattle_step()
    assert out["played"] is False and out["stopped"] is True


def test_step_reports_game_over(tmp_path, monkeypatch):
    sess, _ = _scripted(
        tmp_path, monkeypatch, [_state("combat_play"), _state("game_over", floor=17)]
    )
    out = sess.autobattle_step()
    assert out["over"] is True and out["stopped"] is False
    assert out["diff"] is not None  # the run's last card still summarizes the leg


def test_a_half_watched_leg_still_diffs_from_the_last_stop(tmp_path, monkeypatch):
    """Watching a fight then skipping the rest is one leg, not two: the diff must
    still span back to the stop, and count every decision auto-played."""
    sess, commit = _scripted(
        tmp_path,
        monkeypatch,
        [
            _state("map_select", hp=70),
            _state("combat_play", hp=64),
            _state("combat_play", hp=55),
            _state("shop", hp=55),
        ],
    )
    sess.autobattle_step()
    commit()
    sess.autobattle_step()  # watched one move…
    out = sess.autobattle_advance()  # …then skipped the rest
    assert out["stopped"] is True and out["state"]["decision"] == "shop"
    assert out["diff"]["hp_delta"] == -15 and out["diff"]["autoplayed"] == 2
    assert out["autoplayed"] == 1  # what THIS call played, not the window


def test_step_payload_withholds_the_champion_pick(tmp_path, monkeypatch):
    """Same rule as the skip path: seeing the recommendation while choosing
    destroys the training signal."""
    sess = _stopped_sess(tmp_path, monkeypatch)
    assert "Inflame" not in str(sess.autobattle_step())


def test_step_without_a_live_run_raises(tmp_path, monkeypatch):
    monkeypatch.setattr(branching, "Game", FakeGame)
    with pytest.raises(RuntimeError):
        branching.BranchingLiveSession(str(tmp_path)).autobattle_step()


# ── verdict (revealed only after the human commits) ──────────────────────


#: The champion's pick used across the verdict tests.
_PICK = {"action": "select_card_reward", "args": {"index": 1}}


def _panel(*probs):
    """A PPO policy panel over ``select_card_reward`` indices 0..n-1."""
    return {
        "ppo_available": True,
        "options": [
            {
                "action": "select_card_reward",
                "args": {"index": i},
                "label": f"Take card {i}",
                "prob": p,
                "is_best": False,
            }
            for i, p in enumerate(probs)
        ],
    }


def _stopped_sess(tmp_path, monkeypatch, champion=_PICK, panel=None):
    """A session paused at a card-reward stop with *champion* as the brain's pick.

    *panel* supplies the per-option policy probabilities the near-tie band
    needs; None stands in for a brain that exposes none (the heuristic brain).
    """
    sess, _ = _scripted(tmp_path, monkeypatch, [_state("card_reward")])
    monkeypatch.setattr(sess, "_brain_recommend", lambda: champion)
    monkeypatch.setattr(sess, "ppo_policy_panel", lambda: panel)
    monkeypatch.setattr(branching.render, "describe_action", lambda state, action: "Take Inflame")
    sess.autobattle_advance()
    return sess


def test_verdict_unavailable_before_any_action(tmp_path, monkeypatch):
    verdict = _stopped_sess(tmp_path, monkeypatch).autobattle_verdict()
    assert verdict["available"] is False
    assert "no action taken" in verdict["reason"]


def test_verdict_unavailable_when_the_brain_declined(tmp_path, monkeypatch):
    sess = _stopped_sess(tmp_path, monkeypatch, champion=None)
    sess.apply("select_card_reward", {"index": 1})
    assert sess.autobattle_verdict()["available"] is False


def test_verdict_reports_agreement(tmp_path, monkeypatch):
    sess = _stopped_sess(tmp_path, monkeypatch)
    sess.apply("select_card_reward", {"index": 1})
    verdict = sess.autobattle_verdict()
    assert verdict["available"] is True and verdict["agreed"] is True
    assert verdict["champion_label"] == "Take Inflame"


def test_verdict_reports_disagreement(tmp_path, monkeypatch):
    sess = _stopped_sess(tmp_path, monkeypatch)
    sess.apply("select_card_reward", {"index": 0})
    verdict = sess.autobattle_verdict()
    assert verdict["available"] is True and verdict["agreed"] is False


def test_verdict_reports_disagreement_on_a_different_verb(tmp_path, monkeypatch):
    sess = _stopped_sess(tmp_path, monkeypatch)
    sess.apply("skip_card_reward", {})
    assert sess.autobattle_verdict()["agreed"] is False


def test_verdict_bands_a_close_disagreement_as_a_near_tie(tmp_path, monkeypatch):
    sess = _stopped_sess(tmp_path, monkeypatch, panel=_panel(0.38, 0.40))
    sess.apply("select_card_reward", {"index": 0})
    verdict = sess.autobattle_verdict()
    assert verdict["agreed"] is False
    assert verdict["band"] == autobattle.BAND_NEAR_TIE
    assert verdict["margin"] == pytest.approx(0.02)
    assert verdict["your_prob"] == pytest.approx(0.38)
    assert verdict["champion_prob"] == pytest.approx(0.40)


def test_verdict_bands_a_clear_disagreement_as_a_miss(tmp_path, monkeypatch):
    sess = _stopped_sess(tmp_path, monkeypatch, panel=_panel(0.05, 0.90))
    sess.apply("select_card_reward", {"index": 0})
    verdict = sess.autobattle_verdict()
    assert verdict["band"] == autobattle.BAND_DIFFERED
    assert verdict["margin"] == pytest.approx(0.85)


def test_verdict_without_probabilities_reports_no_margin(tmp_path, monkeypatch):
    """The heuristic brain has no policy distribution — the verdict must say so
    (margin None) rather than assume the picks were far apart or close."""
    sess = _stopped_sess(tmp_path, monkeypatch, panel=None)
    sess.apply("select_card_reward", {"index": 0})
    verdict = sess.autobattle_verdict()
    assert verdict["margin"] is None and verdict["band"] == autobattle.BAND_DIFFERED


def test_heuristic_brain_gets_no_margin_from_the_ppo_panel(tmp_path, monkeypatch):
    """`ppo_policy_panel` evaluates the net whatever brain is driving, so under
    the heuristic brain its probabilities describe a DIFFERENT policy than the
    one being graded. Using them would measure the wrong thing."""
    sess, _ = _scripted(tmp_path, monkeypatch, [_state("card_reward")])
    sess.set_run_mode(off_combat_brain="heuristic")
    monkeypatch.setattr(sess, "_brain_recommend", lambda: _PICK)
    monkeypatch.setattr(sess, "ppo_policy_panel", lambda: _panel(0.38, 0.40))
    monkeypatch.setattr(branching.render, "describe_action", lambda state, action: "Take Inflame")
    sess.autobattle_advance()
    sess.apply("select_card_reward", {"index": 0})
    verdict = sess.autobattle_verdict()
    assert verdict["margin"] is None and verdict["band"] == autobattle.BAND_DIFFERED


def test_matched_pick_is_never_banded_as_a_near_tie(tmp_path, monkeypatch):
    sess = _stopped_sess(tmp_path, monkeypatch, panel=_panel(0.38, 0.40))
    sess.apply("select_card_reward", {"index": 1})
    verdict = sess.autobattle_verdict()
    assert verdict["band"] == autobattle.BAND_MATCHED and verdict["margin"] == pytest.approx(0.0)


def test_margin_is_measured_against_your_pick_not_the_runner_up(tmp_path, monkeypatch):
    """Your pick may be third; what matters is the gap to the option you passed
    up, not the spread between the top two."""
    sess = _stopped_sess(
        tmp_path,
        monkeypatch,
        champion={"action": "select_card_reward", "args": {"index": 2}},
        panel=_panel(0.02, 0.40, 0.44),
    )
    sess.apply("select_card_reward", {"index": 0})
    verdict = sess.autobattle_verdict()
    assert verdict["margin"] == pytest.approx(0.42)
    assert verdict["band"] == autobattle.BAND_DIFFERED


def test_verdict_never_exposes_a_win_probability(tmp_path, monkeypatch):
    """The critic's value is shaped-return Φ, not a win probability. Autobattle mode
    grades agreement only — same honesty rule as the WPA review."""
    sess = _stopped_sess(tmp_path, monkeypatch)
    sess.apply("select_card_reward", {"index": 1})
    verdict = sess.autobattle_verdict()
    assert not {"value", "win_prob", "win_pct", "delta"} & set(verdict)


def test_verdict_is_consumed_once(tmp_path, monkeypatch):
    sess = _stopped_sess(tmp_path, monkeypatch)
    sess.apply("select_card_reward", {"index": 1})
    assert sess.autobattle_verdict()["available"] is True
    assert sess.autobattle_verdict()["available"] is False


def test_advance_payload_withholds_the_champion_pick(tmp_path, monkeypatch):
    """Revealing the recommendation alongside the options would destroy the
    training signal, so it must not ride along in the advance payload."""
    out = _stopped_sess(tmp_path, monkeypatch).autobattle_advance()
    assert "Inflame" not in str(out)


# ── endpoints ────────────────────────────────────────────────────────────


def _client(tmp_path, monkeypatch):
    monkeypatch.setattr(branching, "Game", FakeGame)
    images, logs = tmp_path / "images", tmp_path / "logs"
    images.mkdir()
    logs.mkdir()
    client = TestClient(create_app(images_root=images, logs_dir=logs))
    resp = client.post(
        "/api/live/start", json={"character": "Ironclad", "seed": "s", "backend": "csharp"}
    )
    assert resp.status_code == 200
    return client


def test_advance_endpoint_returns_a_rendered_step_and_diff(tmp_path, monkeypatch):
    client = _client(tmp_path, monkeypatch)
    body = client.post("/api/live/autobattle/advance").json()
    assert body["stopped"] is True and body["autoplayed"] == 0
    assert body["step"]["state"]["decision"] == "map_select"
    assert body["diff"]["deck"] == {"added": [], "removed": [], "upgraded": []}


def test_verdict_endpoint_grades_the_committed_action(tmp_path, monkeypatch):
    monkeypatch.setattr(
        branching.BranchingLiveSession,
        "_brain_recommend",
        lambda self: {"action": "select_map_node", "args": {"col": 0, "row": 0}},
    )
    client = _client(tmp_path, monkeypatch)
    client.post("/api/live/autobattle/advance")
    client.post(
        "/api/live/action", json={"action": "select_map_node", "args": {"col": 0, "row": 0}}
    )
    verdict = client.get("/api/live/autobattle/verdict").json()
    assert verdict["available"] is True and verdict["agreed"] is True


def test_step_endpoint_returns_a_rendered_step(tmp_path, monkeypatch):
    client = _client(tmp_path, monkeypatch)
    body = client.post("/api/live/autobattle/step").json()
    assert body["stopped"] is True and body["played"] is False
    assert body["step"]["state"]["decision"] == "map_select"
    assert body["diff"] is not None


def test_autobattle_endpoints_require_a_live_run(tmp_path, monkeypatch):
    monkeypatch.setattr(branching, "Game", FakeGame)
    images, logs = tmp_path / "images", tmp_path / "logs"
    images.mkdir()
    logs.mkdir()
    client = TestClient(create_app(images_root=images, logs_dir=logs))
    assert client.post("/api/live/autobattle/advance").status_code == 400
    assert client.post("/api/live/autobattle/step").status_code == 400
    assert client.get("/api/live/autobattle/verdict").status_code == 400


# ── rust backend contract ────────────────────────────────────────────────


@pytest.fixture
def _ppo_env(monkeypatch):
    """The PPO brain needs the ONNX checkpoint; combat needs no net."""
    monkeypatch.setenv("STS2_RUNAI_PPO", str(_ONNX))
    monkeypatch.delenv("STS2_RUNAI_VALUE", raising=False)


def test_rust_autobattle_only_ever_stops_on_run_level_decisions(tmp_path, _ppo_env):
    """The contract the whole mode rests on: the human is never handed a combat
    turn, and combats really are auto-played in between."""
    sess = branching.BranchingLiveSession(str(tmp_path), backend="rust")
    sess.start_run_mode(
        "Ironclad", 3, off_combat_brain="ppo", combat_sims=50, combat_boss_sims=100, threads=1
    )
    stops = 0
    for _ in range(12):
        out = sess.autobattle_advance(max_steps=2_000)
        if not out["stopped"]:
            assert out["over"] or out["error"] is not None
            break
        stops += 1
        assert autobattle.is_stop_point(out["state"])
        assert out["state"]["decision"] not in ("combat_play", "game_over")
        # Commit the champion's own pick so the run advances past this stop.
        pick = sess._autobattle_pending
        if pick is None:
            break
        sess.apply(pick["action"], pick["args"])
    assert stops >= 3
