"""A no-subprocess stand-in for the C# ``sts2_client.Game``, shared by the live tests.

It is a real :class:`GameBackend`: the five verbs plus the honest defaults for
everything else (no native run, no faithful start, empty card play-log), so the
session drives it exactly as it would the C# subprocess.
"""

from workbench.backends import GameBackend


class FakeGame(GameBackend):
    def __init__(self, *, forward_stderr: bool = False):
        self.closed = False
        self.calls: list[tuple[str, dict]] = []
        self._state = {"decision": "combat_play", "round": 1, "hand": [], "enemies": []}
        # Engine-shaped combat-log rows (``RunManager.combat_log()``). Only the
        # Rust backend has these for real; the fake grows one hit per ``act``
        # so the session's slice-the-tail logic is exercised.
        self.recording_combat_log = False
        self._combat_log: list[dict] = []

    def set_record_combat_log(self, on):
        self.recording_combat_log = bool(on)

    def combat_log(self):
        return list(self._combat_log)

    def start(self, character="Ironclad", seed="test", ascension=0, lang="en", act1="overgrowth"):
        self.calls.append(("start", {"character": character, "seed": seed}))
        self._state = {
            "decision": "map_select",
            "choices": [{"col": 0, "row": 0, "type": "Monster"}],
            "floor": 0,
            "act": 1,
            "act_name": "Act 1",
        }
        return self._state

    def act(self, action, **args):
        self.calls.append((action, args))
        if args.get("card_index") == 99:
            return {"type": "error", "message": "illegal"}
        if self.recording_combat_log:
            self._combat_log.append(
                {
                    "kind": "damage",
                    "round": 1,
                    "side": "player",
                    "text": "IRONCLAD dealt 6 damage to Nibbit",
                    "source": {"kind": "card", "id": "STRIKE_IRONCLAD", "upgraded": False},
                    "dealer": {"kind": "player", "id": "IRONCLAD", "uid": None},
                    "receiver": {"kind": "monster", "id": "Nibbit", "uid": 1},
                    "unblocked": 6,
                    "blocked": 0,
                    "hp_before": 20,
                    "hp_after": 14,
                }
            )
        self._state = {
            "decision": "combat_play",
            "round": 2,
            "hand": [],
            "enemies": [],
            "floor": 1,
            "act": 1,
            "act_name": "Act 1",
        }
        return self._state

    def get_state(self):
        return self._state

    def get_map(self, first_col=None):
        self.calls.append(("get_map", {}))
        return {
            "type": "map",
            "rows": [
                [
                    {
                        "col": 0,
                        "row": 0,
                        "type": "Monster",
                        "children": [{"col": 0, "row": 1}],
                        "visited": True,
                        "current": True,
                    }
                ],
                [
                    {
                        "col": 0,
                        "row": 1,
                        "type": "Elite",
                        "children": [],
                        "visited": False,
                        "current": False,
                    }
                ],
            ],
            "boss": {"col": 0, "row": 2, "type": "Boss"},
            "current_coord": {"col": 0, "row": 0},
        }

    def close(self):
        self.closed = True
