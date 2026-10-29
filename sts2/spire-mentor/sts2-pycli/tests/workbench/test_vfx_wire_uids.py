"""The VFX layer joins per-hit log rows to board slots by creature uid.

`LogCreature::Monster.uid` and the encounter entry's `uid` are both
`c.id.get()`, so the join is exact — but only if BOTH ends actually put the
uid on the wire. Without it the client can only match on display name, and
two Twig Slimes in one encounter share a name: every hit would land on
whichever slime was found first.
"""

from __future__ import annotations

import pytest
from workbench.combatlog import LogNames, format_combat_log

_NIBBIT = {"kind": "monster", "id": "Nibbit", "uid": 7}
_PLAYER = {"kind": "player", "id": "IRONCLAD", "uid": None}


def _damage_row() -> dict:
    return {
        "kind": "damage",
        "round": 1,
        "side": "player",
        "source": None,
        "dealer": _PLAYER,
        "receiver": _NIBBIT,
        "unblocked": 6,
        "blocked": 2,
        "hp_before": 20,
        "hp_after": 14,
    }


def test_damage_row_carries_the_target_uid() -> None:
    (row,) = format_combat_log([_damage_row()], LogNames())
    assert row["target_uid"] == 7
    assert row["actor_uid"] is None


def test_damage_row_carries_the_block_split_and_resulting_hp() -> None:
    (row,) = format_combat_log([_damage_row()], LogNames())
    assert row["blocked"] == 2
    assert row["hp_after"] == 14
    # `amount` stays the UNBLOCKED number the log panel prints.
    assert row["amount"] == 6


def test_a_fully_blocked_hit_is_distinguishable_from_a_landed_one() -> None:
    fully_blocked = _damage_row() | {"unblocked": 0, "blocked": 6, "hp_after": 20}
    (row,) = format_combat_log([fully_blocked], LogNames())
    assert row["amount"] == 0
    assert row["blocked"] == 6


def test_block_and_power_rows_carry_uids_too() -> None:
    rows = format_combat_log(
        [
            {
                "kind": "block",
                "round": 1,
                "side": "enemy",
                "source": None,
                "receiver": _NIBBIT,
                "amount": 5,
            },
            {
                "kind": "power",
                "round": 1,
                "side": "player",
                "source": None,
                "applier": _PLAYER,
                "receiver": _NIBBIT,
                "power": "WEAK",
                "amount": 2,
            },
        ],
        LogNames(),
    )
    assert rows[0]["actor_uid"] == 7
    assert rows[1]["target_uid"] == 7
    assert rows[1]["actor_uid"] is None


def test_a_self_applied_power_agrees_on_who_it_is_about() -> None:
    """A powerless-applier power falls back to the target — uid included.

    ``actor``/``actor_side`` already fall back to the target when
    ``applier`` is ``None`` (a self-applied power with no explicit source),
    so the row reads "Nibbit gained 2 Strength". ``actor_uid`` must make the
    same fallback: a display field naming a specific creature while its
    join key says "nobody" is exactly the silently-wrong join this task
    exists to eliminate.
    """
    (row,) = format_combat_log(
        [
            {
                "kind": "power",
                "round": 1,
                "side": "enemy",
                "source": None,
                "applier": None,
                "receiver": _NIBBIT,
                "power": "STRENGTH",
                "amount": 2,
            }
        ],
        LogNames(),
    )
    assert row["actor"] == row["target"]
    assert row["actor_side"] == row["target_side"]
    assert row["actor_uid"] == row["target_uid"] == 7


pytest.importorskip("sts2_rs", reason="rust backend requires the sts2_rs extension")


def test_board_enemies_carry_the_uid_the_log_rows_are_keyed_by() -> None:
    """The board's `uid` and the encounter's `uid` are the same CreatureUid.

    Structured exactly like `test_enemy_kind_on_wire.py`, including the
    close-in-`finally`: an open session log blocks `rmtree`.
    """
    import tempfile

    import sts2_rs
    from workbench.backends.rust_game import build_combat_view
    from workbench.branching import BranchingLiveSession
    from workbench.options import normalize_options

    def drive_to_combat(sess, state, max_steps: int = 40):
        for _ in range(max_steps):
            if state["decision"] == "combat_play":
                return state
            opts = normalize_options(state)
            assert opts, f"no navigable options at decision {state['decision']!r}"
            o = opts[0]
            state = sess.apply(o["action"], o.get("args") or {})["state"]
        raise AssertionError("did not reach combat within step budget")

    with tempfile.TemporaryDirectory() as d:
        sess = BranchingLiveSession(logs_dir=d, backend="rust", loc={})
        try:
            state = sess.start(character="Ironclad", seed="PREVIEWTEST", ascension=0)
            drive_to_combat(sess, state)

            rm = sess.game.rm  # type: ignore[attr-defined]
            cs = rm.combat_state()
            wire = sts2_rs.state_to_dict(cs)
            living = [c for c in wire["encounter"] if not c.get("is_dead")]
            assert living, "combat should have at least one living enemy"

            view = build_combat_view(cs, "Ironclad", legal=rm.available_actions())
            assert [e["uid"] for e in view["enemies"]] == [c["uid"] for c in living]
            # uids are per-creature; `index` is an encounter slot.
            assert len({e["uid"] for e in view["enemies"]}) == len(view["enemies"])
        finally:
            sess.quit()
