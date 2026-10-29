"""Offline tests for :func:`combat_mirror.recognize_card_select`.

The recognition of mid-combat ``card_select`` screens the engine never opened
(generative offers, KnowledgeDemon's curse pick, Gambling Chip's turn-1
mulligan) is SHARED between the two world drivers —
``headless_eval.pick_card_select_mirrored`` (DLL world, rung 3.5) and
``realgame_mcts.pick_card_select_mirrored`` (live SpireMentor world) — so it
cannot fork and drift again. Tier one tests the helper on dialect-neutral
dicts; tier two drives the LIVE path end-to-end on a real bridge fixture with
a recording fake mirror, asserting the Gambling-Chip screen is recognized and
the turn-start resume is armed after ``inject_choice`` and before any search.
"""

from __future__ import annotations

import copy
import json
import sys
from pathlib import Path
from typing import Any

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "python"))

from combat_mirror import TurnStartBlock, recognize_card_select, skip_arity_from_world

FIXTURE_DIR = Path(__file__).resolve().parent / "fixtures" / "realgame_adapter"


def _load_fixture(name: str) -> dict[str, Any]:
    with (FIXTURE_DIR / name).open(encoding="utf-8") as fh:
        return json.load(fh)


def _board(
    *,
    hand: list[dict[str, Any]],
    relics: list[dict[str, Any]],
    round_: int = 1,
) -> dict[str, Any]:
    return {"round": round_, "hand": hand, "player": {"relics": relics}}


def _wire(
    *,
    hand: list[dict[str, Any]] | None = None,
    draw: list[dict[str, Any]] | None = None,
    encounter: list[dict[str, Any]] | None = None,
) -> dict[str, Any]:
    return {
        "deck": {
            "hand": hand or [],
            "draw_pile": draw or [],
            "discard_pile": [],
            "exhaust_pile": [],
        },
        "encounter": encounter or [],
    }


class TestRecognizeCardSelect:
    """Dialect-neutral recognition logic, no mirror involved."""

    def test_parked_kind_passes_through_untouched(self) -> None:
        options = [{"id": "STRIKE_IRONCLAD", "upgraded": False}]
        before = copy.deepcopy(options)
        kind, blocked_on = recognize_card_select(
            parked={"kind": "put_from_discard_on_draw", "cards": []},
            options=options,
            board=_board(hand=[], relics=[]),
            wire=_wire(),
            min_select=1,
        )
        assert kind == "put_from_discard_on_draw"
        assert blocked_on is None
        assert options == before, "a parked screen must not gain locators"

    def test_unparked_unrecognized_screen_keeps_card_select(self) -> None:
        # Options ARE present in a pile, so the generative shape fallback must
        # not fire; nothing else matches either.
        hand = [{"id": "STRIKE_IRONCLAD", "upgraded": False}]
        kind, blocked_on = recognize_card_select(
            parked=None,
            options=[{"id": "STRIKE_IRONCLAD", "upgraded": False}],
            board=_board(hand=hand, relics=[], round_=3),
            wire=_wire(hand=hand),
            min_select=1,
        )
        assert kind == "card_select"
        assert blocked_on is None

    def test_source_model_names_the_generative_kind(self) -> None:
        for src, want in [
            ("Toolbox", "add_generated_to_hand"),
            ("Quasar", "add_generated_to_hand"),
            ("Discovery", "add_generated_free_to_hand"),
            ("Splash", "add_generated_free_to_hand"),
            ("AttackPotion", "add_generated_free_to_hand"),
        ]:
            kind, blocked_on = recognize_card_select(
                parked=None,
                options=[{"id": "BASH", "upgraded": False}],
                board=_board(hand=[], relics=[]),
                wire=_wire(),
                min_select=1,
                source_model=src,
            )
            assert kind == want, src
            assert blocked_on is None

    def test_generative_shape_fallback_without_source_model(self) -> None:
        # Options exist in NO pile: round-1 + Toolbox reads as the one
        # non-free grant, anything else as a free generated offer.
        options = [{"id": "BASH", "upgraded": False}]
        wire = _wire(hand=[{"id": "STRIKE_IRONCLAD", "upgraded": False}])
        kind, _ = recognize_card_select(
            parked=None,
            options=copy.deepcopy(options),
            board=_board(hand=[], relics=[{"id": "TOOLBOX"}]),
            wire=wire,
            min_select=1,
        )
        assert kind == "add_generated_to_hand"
        kind, _ = recognize_card_select(
            parked=None,
            options=copy.deepcopy(options),
            board=_board(hand=[], relics=[]),
            wire=wire,
            min_select=1,
        )
        assert kind == "add_generated_free_to_hand"

    def test_every_choose_a_card_screen_caller_is_classified(self) -> None:
        """The generative table must stay complete against the decompile.

        ``FromChooseACardScreen`` is the only mid-combat entry point whose
        options exist in no pile, so it is the only one the shape fallback can
        be asked to classify. v0.107.1 has twelve callers; the three that are
        not generative grants-to-hand are excluded by name here, and every
        remaining one must be nameable — otherwise a new card lands in the
        fallback and is silently charged (or not charged) at random.

        This is the static half of the unparked audit: it fails when the game
        adds a caller, not 90 minutes into a sweep.
        """
        from combat_mirror import _GENERATIVE_SOURCES

        # AfterObtained relics -- run-level card pickers, never seen by the
        # combat mirror. KnowledgeDemon has its own `monster_curse_select` arm.
        non_combat_or_special = {
            "HeftyTablet",
            "LeadPaperweight",
            "MassiveScroll",
            "KnowledgeDemon",
        }
        v0_107_1_callers = {
            "AttackPotion",
            "ColorlessPotion",
            "Discovery",
            "HeftyTablet",
            "KnowledgeDemon",
            "LeadPaperweight",
            "MassiveScroll",
            "PowerPotion",
            "Quasar",
            "SkillPotion",
            "Splash",
            "Toolbox",
        }
        want = v0_107_1_callers - non_combat_or_special
        assert want <= set(_GENERATIVE_SOURCES), (
            f"unclassified FromChooseACardScreen callers: {sorted(want - set(_GENERATIVE_SOURCES))}"
        )
        # Free vs non-free is the whole point of the split -- getting it
        # backwards hands the search a card the game charges for.
        assert _GENERATIVE_SOURCES["Toolbox"] == "add_generated_to_hand"
        assert _GENERATIVE_SOURCES["Quasar"] == "add_generated_to_hand"
        assert _GENERATIVE_SOURCES["Splash"] == "add_generated_free_to_hand"

    def test_knowledge_demon_curse_pick(self) -> None:
        # The REAL wire dialect: `state_to_dict` encounter entries carry
        # `kind`, never `id`. The original guard read only `id`, so this
        # recognition could never fire in production — the screen fell
        # through to the generative-shape fallback and the mirror granted
        # the curse into hand (mirror_d1 seed-1004 records 100/102, mirror
        # a full round behind at hand [DISINTEGRATION]).
        for enemy in ({"kind": "KnowledgeDemon"}, {"id": "KnowledgeDemon"}):
            kind, blocked_on = recognize_card_select(
                parked=None,
                options=[
                    {"id": "DISINTEGRATION", "upgraded": False},
                    {"id": "SLOTH_STATUS", "upgraded": False},
                ],
                board=_board(hand=[], relics=[]),
                wire=_wire(encounter=[enemy]),
                min_select=1,
            )
            assert kind == "monster_curse_select", enemy
            assert blocked_on is None

    def test_gambling_chip_mulligan_recognized_and_located(self) -> None:
        # Two duplicate Strikes must resolve to two DISTINCT hand indices.
        hand = [
            {"id": "STRIKE_IRONCLAD", "upgraded": False},
            {"id": "STRIKE_IRONCLAD", "upgraded": False},
            {"id": "BASH", "upgraded": True},
        ]
        options = [
            {"id": "BASH", "upgraded": True},
            {"id": "STRIKE_IRONCLAD", "upgraded": False},
            {"id": "STRIKE_IRONCLAD", "upgraded": False},
        ]
        kind, blocked_on = recognize_card_select(
            parked=None,
            options=options,
            board=_board(hand=hand, relics=[{"id": "GAMBLING_CHIP"}]),
            wire=_wire(hand=hand),
            min_select=0,
            prompt_key="GAMBLING_CHIP.card_select_prompt",
        )
        assert kind == "discard_from_hand_and_draw"
        assert blocked_on == TurnStartBlock("relic", "GAMBLING_CHIP")
        assert [(o["pile"], o["index"]) for o in options] == [
            ("hand", 2),
            ("hand", 0),
            ("hand", 1),
        ]

    def test_a_turn_start_power_screen_is_recognized_on_any_turn(self) -> None:
        # GH #355. Both v0.107.1 relics with a turn-start card select are
        # hard-gated to turn 1, so "generalize the Gambling Chip fix to any
        # turn" is not another relic -- it is TyrannyPower /
        # ToolsOfTheTradePower, which carry no turn gate at all. Round 7 here
        # is the whole point: the Gambling-Chip recognition below requires
        # `round <= 1` and could never have covered this.
        hand = [
            {"id": "STRIKE_IRONCLAD", "upgraded": False},
            {"id": "STRIKE_IRONCLAD", "upgraded": False},
            {"id": "BASH", "upgraded": True},
        ]
        for source, expect_kind, expect_power in (
            ("TyrannyPower", "exhaust_from_hand", "TYRANNY"),
            ("ToolsOfTheTradePower", "discard_from_hand", "TOOLS_OF_THE_TRADE"),
        ):
            options = [
                {"id": "BASH", "upgraded": True},
                {"id": "STRIKE_IRONCLAD", "upgraded": False},
            ]
            kind, blocked_on = recognize_card_select(
                parked=None,
                options=options,
                board=_board(hand=hand, relics=[], round_=7),
                wire=_wire(hand=hand),
                min_select=2,
                source_model=source,
            )
            assert kind == expect_kind, source
            assert blocked_on == TurnStartBlock("power", expect_power), source
            # Located onto DISTINCT hand slots, in hand order (C# `FromHand`
            # passes the hand `Cards` with no OrderBy).
            assert [(o["pile"], o["index"]) for o in options] == [("hand", 2), ("hand", 0)]

    def test_a_turn_start_power_screen_the_hand_cannot_account_for_is_refused(self) -> None:
        # The locator pass is the safety net: an option that is not in the
        # mirror's hand means the board the mirror was seeded from is not the
        # board the screen was built from. Injecting it anyway would resolve
        # the pick against nothing AND arm a turn-start replay on a board that
        # may already have run it -- double Vigor / energy / AoE.
        hand = [{"id": "STRIKE_IRONCLAD", "upgraded": False}]
        kind, blocked_on = recognize_card_select(
            parked=None,
            options=[{"id": "IMMOLATE", "upgraded": False}],
            board=_board(hand=hand, relics=[], round_=4),
            wire=_wire(hand=hand),
            min_select=1,
            source_model="TyrannyPower",
        )
        assert kind == "card_select"
        assert blocked_on is None

    def test_entropy_is_deliberately_not_recognized(self) -> None:
        # EntropyPower is the third turn-start-select power, but the engine
        # has no in-hand transform ChoiceKind (its cluster arm transforms
        # `hand.take(amount)` outright with no screen), so there is nothing
        # correct to map the injection to. Pinned so the omission reads as a
        # decision rather than an oversight -- flip this test when the engine
        # grows the kind.
        hand = [{"id": "STRIKE_IRONCLAD", "upgraded": False}]
        kind, blocked_on = recognize_card_select(
            parked=None,
            options=[{"id": "STRIKE_IRONCLAD", "upgraded": False}],
            board=_board(hand=hand, relics=[], round_=4),
            wire=_wire(hand=hand),
            min_select=1,
            source_model="EntropyPower",
        )
        assert kind == "card_select"
        assert blocked_on is None

    def test_gamblers_brew_prompt_key_recognizes_but_does_not_arm(self) -> None:
        # GamblersBrew's potion screen has the identical shape; when the
        # world's prompt key names something other than the relic, the kind
        # still maps (same DiscardAndDraw) but the turn-start resume — the
        # part that must not misfire — stays unarmed.
        hand = [{"id": "STRIKE_IRONCLAD", "upgraded": False}]
        options = [{"id": "STRIKE_IRONCLAD", "upgraded": False}]
        kind, blocked_on = recognize_card_select(
            parked=None,
            options=options,
            board=_board(hand=hand, relics=[{"id": "GAMBLING_CHIP"}]),
            wire=_wire(hand=hand),
            min_select=0,
            prompt_key="GAMBLERS_BREW.card_select_prompt",
        )
        assert kind == "discard_from_hand_and_draw"
        assert blocked_on is None

    def test_gambling_chip_needs_the_relic(self) -> None:
        hand = [{"id": "STRIKE_IRONCLAD", "upgraded": False}]
        kind, blocked_on = recognize_card_select(
            parked=None,
            options=[{"id": "STRIKE_IRONCLAD", "upgraded": False}],
            board=_board(hand=hand, relics=[]),
            wire=_wire(hand=hand),
            min_select=0,
        )
        assert kind == "card_select"
        assert blocked_on is None

    def test_gambling_chip_shape_without_prompt_key_still_arms(self) -> None:
        # `prompt_key` is None on an unpatched entry point; the shape alone
        # must still arm (matching the DLL driver's behavior byte-for-byte).
        hand = [{"id": "STRIKE_IRONCLAD", "upgraded": False}]
        _, blocked_on = recognize_card_select(
            parked=None,
            options=[{"id": "STRIKE_IRONCLAD", "upgraded": False}],
            board=_board(hand=hand, relics=[{"name": "Gambling Chip"}]),
            wire=_wire(hand=hand),
            min_select=0,
        )
        assert blocked_on == TurnStartBlock("relic", "Gambling Chip")


class _RecordingMirror:
    """Minimal CombatMirror stand-in that records the call order.

    ``alive`` is False so ``pick_card_select_mirrored`` takes the
    minimum-legal early return after the injection — the test only cares
    that recognition fired and the resume was armed BEFORE any search.
    """

    alive = False

    def __init__(self) -> None:
        self.calls: list[tuple[str, Any]] = []

    def reconcile(self, wire: dict[str, Any]) -> list[Any]:
        self.calls.append(("reconcile", None))
        return []

    def parked_choice(self) -> dict[str, Any] | None:
        self.calls.append(("parked_choice", None))
        return None

    def inject_choice(self, kind: str, options: list[dict[str, Any]], *args: Any, **kwargs: Any):
        self.calls.append(("inject_choice", (kind, copy.deepcopy(options))))
        return []

    def arm_turn_start_resume(self, after_relic: str) -> bool:
        self.calls.append(("arm_turn_start_resume", after_relic))
        return True

    def arm_turn_start_resume_after_power(self, after_power: str) -> bool:
        self.calls.append(("arm_turn_start_resume_after_power", after_power))
        return True

    def arm_turn_start_resume_for(self, block: TurnStartBlock) -> bool:
        # The REAL dispatcher, not a stub: the two engine surfaces are not
        # interchangeable, so a test that recorded the call without routing it
        # would pass on a mirror that armed the wrong one.
        if block.source == "power":
            return self.arm_turn_start_resume_after_power(block.id)
        return self.arm_turn_start_resume(block.id)

    def search(self, cfg: Any) -> Any:  # pragma: no cover - must not be reached
        self.calls.append(("search", None))
        raise AssertionError("search must not run on a dead mirror")


class TestSkipArityFromWorld:
    """``min_select <= 0`` is not the skippability test for every screen.

    ``FromChooseACardScreen`` hard-codes ``GetSelectedCards(cards, 0, 1)``
    (CardSelectCmd.cs:230), so its reported minimum is a constant and carries
    no information; the truth is the separate ``canSkip`` argument, which
    defaults to false (CardSelectCmd.cs:216). Every OTHER entry point takes a
    ``CardSelectorPrefs`` whose ``MinSelect`` is the real arity — and whose
    ``Cancelable`` is an ``init`` field the public constructors never set, so
    it reads false on plainly-declinable screens and must NOT be trusted
    there.
    """

    def test_mandatory_choose_a_card_screen_loses_the_skip(self) -> None:
        """Toolbox / KnowledgeDemon: min 0 on the wire, but no skip button."""
        can_skip, min_select = skip_arity_from_world(
            cancelable=False,
            source="FromChooseACardScreen",
            min_select=0,
            max_select=1,
        )
        assert can_skip is False
        # Re-floored: both drivers gate "answer with nothing" on min <= 0, so
        # clearing can_skip alone would leave the screen declinable anyway.
        assert min_select == 1

    def test_skippable_choose_a_card_screen_is_untouched(self) -> None:
        """The ten callers that pass `canSkip: true` keep their skip."""
        assert skip_arity_from_world(
            cancelable=True, source="FromChooseACardScreen", min_select=0, max_select=1
        ) == (True, 0)

    def test_prefs_screens_ignore_cancelable(self) -> None:
        """Gambling Chip's mulligan must stay skippable.

        ``CardSelectorPrefs(prompt, 0, 999999999)`` leaves ``Cancelable``
        at its ``default(CardSelectorPrefs)`` false while the screen is
        answerable with nothing via confirmation. Trusting the flag here would
        force a discard AND break the mulligan recognition, which keys on
        ``min_select == 0``.
        """
        assert skip_arity_from_world(
            cancelable=False, source="FromHandForDiscard", min_select=0, max_select=5
        ) == (True, 0)

    def test_a_world_that_sends_no_flag_is_unchanged(self) -> None:
        """Older mod / unpatched entry point: keep the legacy inference."""
        assert skip_arity_from_world(
            cancelable=None, source="FromChooseACardScreen", min_select=0, max_select=1
        ) == (True, 0)
        assert skip_arity_from_world(cancelable=None, source=None, min_select=2, max_select=2) == (
            False,
            2,
        )


class TestLivePathGamblingChip:
    """The live SpireMentor driver recognizes the mulligan and arms the resume."""

    def _card_select_state(self) -> dict[str, Any]:
        combat = copy.deepcopy(_load_fixture("single_enemy_ironclad.json")["state"])
        combat["round"] = 1
        combat["run_state"]["relics"].append(
            {"id": "GAMBLING_CHIP", "name": "Gambling Chip", "stack_count": 1}
        )
        return {
            "type": "card_select",
            "prompt_key": "GAMBLING_CHIP.card_select_prompt",
            "min_count": 0,
            "max_count": 2147483647,  # C# int.MaxValue sentinel: choose any
            "cards": copy.deepcopy(combat["hand"]),
            "combat": combat,
            "run_state": combat["run_state"],
        }

    def test_recognizes_and_arms_before_any_search(self) -> None:
        from realgame_mcts import pick_card_select_mirrored
        from sts2_hint.recommender import RecommendConfig

        mirror = _RecordingMirror()
        state = self._card_select_state()
        action, picks = pick_card_select_mirrored(state, RecommendConfig(), mirror)

        names = [n for n, _ in mirror.calls]
        assert "inject_choice" in names and "arm_turn_start_resume" in names
        assert names.index("inject_choice") < names.index("arm_turn_start_resume"), (
            "same order as the DLL driver: inject, then arm, then search"
        )
        assert "search" not in names

        kind, options = next(a for n, a in mirror.calls if n == "inject_choice")
        assert kind == "discard_from_hand_and_draw"
        n_hand = len(state["combat"]["hand"])
        assert sorted((o["pile"], o["index"]) for o in options) == [
            ("hand", i) for i in range(n_hand)
        ], "every option must be located onto a distinct hand slot"

        armed = next(a for n, a in mirror.calls if n == "arm_turn_start_resume")
        assert "GAMBLING" in str(armed).upper().replace(" ", "_")

        # Dead-mirror early return: min_select 0 -> skip, picks None (the
        # caller must not replay anything onto a dropped mirror).
        assert action == {"action": "skip"}
        assert picks is None

    def test_parked_screen_does_not_arm(self) -> None:
        """A screen the engine derived itself keeps its kind and never arms."""
        from realgame_mcts import pick_card_select_mirrored
        from sts2_hint.recommender import RecommendConfig

        class _ParkedMirror(_RecordingMirror):
            def parked_choice(self) -> dict[str, Any] | None:
                self.calls.append(("parked_choice", None))
                return {"kind": "put_from_discard_on_draw", "cards": []}

        mirror = _ParkedMirror()
        state = self._card_select_state()
        pick_card_select_mirrored(state, RecommendConfig(), mirror)

        names = [n for n, _ in mirror.calls]
        assert "arm_turn_start_resume" not in names
        kind, _ = next(a for n, a in mirror.calls if n == "inject_choice")
        assert kind == "put_from_discard_on_draw"
