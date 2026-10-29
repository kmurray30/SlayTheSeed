import json

from fastapi.testclient import TestClient
from workbench.server import create_app


def _client(tmp_path, fixture_log):
    images = tmp_path / "images"
    (images / "monsters").mkdir(parents=True)
    (images / "monsters" / "scroll_of_biting.png").write_bytes(b"x")
    logs = fixture_log.parent
    return TestClient(create_app(images_root=images, logs_dir=logs))


def _write_log(path, state):
    """Write a single-step ``state`` log line readable by TimelineStore."""
    path.write_text(
        json.dumps({"step": 1, "ts": "2026-05-24T00:00:00", "type": "state", "data": state}) + "\n"
    )


def test_card_reward_cost_backfilled_from_manifest(tmp_path):
    # The Rust backend ships reward cards with no ``cost``; the manifest backfill
    # must fill it so the card-frame cost corner isn't blank. Lay out a fake wiki
    # so ``wiki_data`` (= images_root.parents[2]/data) resolves to our manifest.
    images = tmp_path / "wiki" / "site" / "public" / "images"
    images.mkdir(parents=True)
    manifest = tmp_path / "wiki" / "data" / "v1" / "cards.json"
    manifest.parent.mkdir(parents=True)
    manifest.write_text(
        json.dumps(
            {"cards": [{"loc_key": "BASH", "type": "Attack", "rarity": "Basic", "energy_cost": 2}]}
        )
    )
    log = tmp_path / "run.jsonl"
    _write_log(
        log,
        {
            "decision": "card_reward",
            # reward card as the Rust backend emits it: no ``cost`` key.
            "cards": [{"index": 0, "id": "BASH", "name": "BASH.title", "type": "card"}],
            "player": {"name": "Ironclad"},
        },
    )
    c = TestClient(create_app(images_root=images, logs_dir=tmp_path))
    c.post("/api/load", json={"path": str(log)})
    card = c.get("/api/step/0").json()["state"]["cards"][0]
    assert card["cost"] == 2
    assert card["type"] == "Attack"  # also backfilled, proving the manifest loaded


def test_health(tmp_path, fixture_log):
    c = _client(tmp_path, fixture_log)
    assert c.get("/api/health").json() == {"ok": True}


def test_load_then_step_enriches_images(tmp_path, fixture_log):
    c = _client(tmp_path, fixture_log)
    r = c.post("/api/load", json={"path": str(fixture_log)})
    assert r.json()["count"] == 2

    step = c.get("/api/step/0").json()
    assert step["decision"] == "combat_play"
    # enemy image resolved to a served URL
    assert step["state"]["enemies"][0]["image"] == "/assets/monsters/scroll_of_biting.png"
    # missing card art -> null, not an error
    assert step["state"]["hand"][0]["image"] is None


def _combat_state_with_enemies(enemies):
    """Minimal combat_play state carrying the given ``enemies`` list."""
    return {
        "decision": "combat_play",
        "round": 1,
        "energy": 3,
        "max_energy": 3,
        "player": {"name": "Ironclad", "hp": 80, "max_hp": 80, "block": 0},
        "player_powers": [],
        "enemies": enemies,
        "hand": [],
    }


def test_state_dependent_idle_anim(tmp_path):
    """Spiny Toad / Tunneler get a ``rig_anim`` idle override derived from their
    powers, matching the game's animator branches (spikes up ↔ Thorns; hidden
    underground ↔ Burrowed)."""
    images = tmp_path / "images"
    (images / "monsters").mkdir(parents=True)
    log = tmp_path / "anim.jsonl"
    _write_log(
        log,
        _combat_state_with_enemies(
            [
                {
                    "index": 0,
                    "kind": "SpinyToad",
                    "name": "Spiny Toad",
                    "hp": 30,
                    "max_hp": 30,
                    "block": 0,
                    "powers": [{"id": "THORNS", "name": "Thorns", "amount": 5}],
                },
                {
                    "index": 1,
                    "kind": "SpinyToad",
                    "name": "Spiny Toad",
                    "hp": 30,
                    "max_hp": 30,
                    "block": 0,
                    "powers": [],
                },
                {
                    "index": 2,
                    "kind": "Tunneler",
                    "name": "Tunneler",
                    "hp": 40,
                    "max_hp": 40,
                    "block": 0,
                    "powers": [{"id": "BURROWED", "name": "Burrowed", "amount": 1}],
                },
                {
                    "index": 3,
                    "kind": "Tunneler",
                    "name": "Tunneler",
                    "hp": 40,
                    "max_hp": 40,
                    "block": 0,
                    "powers": [],
                },
            ]
        ),
    )
    c = TestClient(create_app(images_root=images, logs_dir=tmp_path))
    c.post("/api/load", json={"path": str(log)})
    enemies = c.get("/api/step/0").json()["state"]["enemies"]

    assert enemies[0]["rig_anim"] == "idle_loop"  # Spiny Toad w/ Thorns → spiked
    assert enemies[1]["rig_anim"] == "idle_naked_loop"  # Spiny Toad w/o Thorns → naked
    assert enemies[2]["rig_anim"] == "hidden_loop"  # Tunneler burrowed → hidden
    assert enemies[3]["rig_anim"] == "idle_loop"  # Tunneler above ground → idle


def test_step_out_of_range_404(tmp_path, fixture_log):
    c = _client(tmp_path, fixture_log)
    c.post("/api/load", json={"path": str(fixture_log)})
    assert c.get("/api/step/99").status_code == 404


def test_recommend_returns_scores(tmp_path, fixture_log, monkeypatch):
    from workbench import reasoning

    monkeypatch.setattr(
        reasoning,
        "score",
        lambda state, cfg: {
            "scores": [{"action": "Strike", "is_best": True}],
            "pv": None,
            "config": cfg,
        },
    )
    c = _client(tmp_path, fixture_log)
    c.post("/api/load", json={"path": str(fixture_log)})
    r = c.post("/api/recommend", json={"idx": 0, "config": {"sims": 10}}).json()
    assert r["scores"][0]["action"] == "Strike"


def test_non_combat_step_passthrough(tmp_path, fixture_log):
    c = _client(tmp_path, fixture_log)
    c.post("/api/load", json={"path": str(fixture_log)})
    r = c.get("/api/step/1")
    assert r.status_code == 200
    data = r.json()
    assert data["decision"] == "map_select"
    # _enrich only sets energy_image for combat_play steps
    assert "energy_image" not in data.get("state", {})


def test_recommend_caches_back_onto_step(tmp_path, fixture_log, monkeypatch):
    from workbench import reasoning

    monkeypatch.setattr(
        reasoning,
        "score",
        lambda state, cfg: {
            "scores": [{"action": "Strike", "is_best": True}],
            "pv": ["Strike"],
            "config": cfg,
        },
    )
    c = _client(tmp_path, fixture_log)
    c.post("/api/load", json={"path": str(fixture_log)})
    c.post("/api/recommend", json={"idx": 0, "config": {}})
    step = c.get("/api/step/0").json()
    assert step["scores"][0]["action"] == "Strike"
    assert step["pv"] == ["Strike"]


def test_load_nonexistent_path_404(tmp_path, fixture_log):
    c = _client(tmp_path, fixture_log)
    r = c.post("/api/load", json={"path": str(tmp_path / "nope.jsonl")})
    assert r.status_code == 404


def test_load_malformed_jsonl_422(tmp_path, fixture_log):
    bad = tmp_path / "bad.jsonl"
    bad.write_text("not json\n")
    c = _client(tmp_path, fixture_log)
    r = c.post("/api/load", json={"path": str(bad)})
    assert r.status_code == 422


def _rich_combat_state():
    """A combat_play state with relics, potions, and non-empty piles."""

    def card(name, cost=1, ctype="Attack"):
        return {
            "index": 0,
            "id": f"CARD.{name.upper()}",
            "name": name,
            "cost": cost,
            "type": ctype,
            "stats": {},
            "description": "",
        }

    return {
        "decision": "combat_play",
        "round": 1,
        "energy": 3,
        "max_energy": 3,
        "draw_pile_count": 3,
        "discard_pile_count": 2,
        "exhaust_pile_count": 1,
        "player": {
            "name": "Ironclad",
            "hp": 70,
            "max_hp": 80,
            "block": 0,
            "relics": [
                {
                    "name": "Ring of the Snake",
                    "description": "At the start of each combat, draw {Cards} additional cards.",
                    "vars": {"Cards": 2},
                }
            ],
            "potions": [
                {
                    "index": 0,
                    "name": "Fire Potion",
                    "description": "Deal {Damage} damage.",
                    "vars": {"Damage": 20},
                }
            ],
            # Full master deck (persists across the run; browsable anytime).
            "deck": [card("Strike"), card("Strike"), card("Bash")],
            "deck_size": 3,
        },
        "player_powers": [],
        "enemies": [{"index": 0, "name": "Scroll of Biting", "hp": 10, "max_hp": 10, "block": 0}],
        "hand": [card("Strike")],
        # Draw pile deliberately out of alphabetical order to prove scrambling.
        "draw_pile": [card("Strike"), card("Defend", ctype="Skill"), card("Bash")],
        "discard_pile": [card("Strike"), card("Defend", ctype="Skill")],
        "exhaust_pile": [card("Bash")],
    }


def test_combat_step_enriches_relics_and_potions(tmp_path):
    images = tmp_path / "images"
    (images / "monsters").mkdir(parents=True)
    log = tmp_path / "rich.jsonl"
    _write_log(log, _rich_combat_state())
    c = TestClient(create_app(images_root=images, logs_dir=tmp_path))
    c.post("/api/load", json={"path": str(log)})
    state = c.get("/api/step/0").json()["state"]

    relic = state["player"]["relics"][0]
    assert "image" in relic
    # Description template vars resolved from the relic's own ``vars`` dict.
    assert relic["description"] == "At the start of each combat, draw 2 additional cards."

    potion = state["player"]["potions"][0]
    assert "image" in potion
    assert potion["description"] == "Deal 20 damage."


def test_step_enriches_player_deck(tmp_path):
    """The full master deck is exposed and image-enriched on every decision so
    the deck viewer is reachable from any screen."""
    images = tmp_path / "images"
    (images / "monsters").mkdir(parents=True)
    log = tmp_path / "rich.jsonl"
    _write_log(log, _rich_combat_state())
    c = TestClient(create_app(images_root=images, logs_dir=tmp_path))
    c.post("/api/load", json={"path": str(log)})
    state = c.get("/api/step/0").json()["state"]

    deck = state["player"]["deck"]
    assert [card["name"] for card in deck] == ["Strike", "Strike", "Bash"]
    assert all("image" in card for card in deck)


def test_combat_step_exposes_piles_with_draw_scrambled(tmp_path):
    images = tmp_path / "images"
    (images / "monsters").mkdir(parents=True)
    log = tmp_path / "rich.jsonl"
    _write_log(log, _rich_combat_state())
    c = TestClient(create_app(images_root=images, logs_dir=tmp_path))
    c.post("/api/load", json={"path": str(log)})
    state = c.get("/api/step/0").json()["state"]

    # All three piles are exposed as full card lists, image-enriched.
    assert [card["name"] for card in state["discard_pile"]] == ["Strike", "Defend"]
    assert [card["name"] for card in state["exhaust_pile"]] == ["Bash"]
    assert all("image" in card for card in state["draw_pile"])

    # Draw pile must NOT reveal draw order: served sorted (here, alphabetical),
    # not in the original [Strike, Defend, Bash] order.
    drawn = [card["name"] for card in state["draw_pile"]]
    assert drawn == ["Bash", "Defend", "Strike"]


def _relic_log(tmp_path, relic):
    """Write an event_choice log whose player owns a single ``relic``."""
    log = tmp_path / "relic.jsonl"
    _write_log(
        log,
        {
            "decision": "event_choice",
            "round": 0,
            "event_name": "Neow",
            "options": [],
            "player": {"name": "Ironclad", "hp": 80, "max_hp": 80, "block": 0, "relics": [relic]},
        },
    )
    return log


def test_unknown_relic_loc_key_name_is_prettified(tmp_path):
    """A relic whose name leaks an unresolved loc key with no entry in our loc
    data is shown as a readable name (not the raw key), and its unresolved
    description is blanked rather than shown raw."""
    images = tmp_path / "images"
    images.mkdir(parents=True)
    log = _relic_log(
        tmp_path, {"name": "MADE_UP_RELIC.title", "description": "MADE_UP_RELIC.description"}
    )
    c = TestClient(create_app(images_root=images, logs_dir=tmp_path))
    c.post("/api/load", json={"path": str(log)})
    relic = c.get("/api/step/0").json()["state"]["player"]["relics"][0]
    assert relic["name"] == "Made Up Relic"
    assert relic["description"] == ""


def test_winged_boots_loc_key_resolves_from_relics_json(tmp_path):
    """The Neow 'Winged Boots' relic leaks WINGED_BOOTS.title/.description;
    relics.json now carries the entry, so both resolve (with vars applied)."""
    images = tmp_path / "images"
    images.mkdir(parents=True)
    log = _relic_log(
        tmp_path,
        {
            "name": "WINGED_BOOTS.title",
            "description": "WINGED_BOOTS.description",
            "vars": {"Rooms": 3},
        },
    )
    c = TestClient(create_app(images_root=images, logs_dir=tmp_path))
    c.post("/api/load", json={"path": str(log)})
    relic = c.get("/api/step/0").json()["state"]["player"]["relics"][0]
    assert relic["name"] == "Winged Boots"
    assert relic["description"] == (
        "You may ignore paths when choosing the next rooms to travel to 3 times."
    )


def test_non_combat_step_enriches_relics(tmp_path):
    """Relics persist, so they are enriched even outside combat."""
    images = tmp_path / "images"
    images.mkdir(parents=True)
    log = tmp_path / "evt.jsonl"
    _write_log(
        log,
        {
            "decision": "event_choice",
            "round": 0,
            "event_name": "Neow",
            "options": [],
            "player": {
                "name": "Ironclad",
                "hp": 80,
                "max_hp": 80,
                "block": 0,
                "relics": [
                    {"name": "Burning Blood", "description": "Heal {Hp} HP.", "vars": {"Hp": 6}}
                ],
            },
        },
    )
    c = TestClient(create_app(images_root=images, logs_dir=tmp_path))
    c.post("/api/load", json={"path": str(log)})
    state = c.get("/api/step/0").json()["state"]
    relic = state["player"]["relics"][0]
    assert "image" in relic
    assert relic["description"] == "Heal 6 HP."


def test_mcts_runs_aggregate_endpoint(tmp_path):
    import shutil
    from pathlib import Path

    src = Path("tests/workbench/fixtures/sample_mcts_run.jsonl")
    shutil.copy(src, tmp_path / "run0.jsonl")
    app = create_app(images_root="/tmp", logs_dir=str(tmp_path))
    client = TestClient(app)
    r = client.get("/api/mcts-runs")
    assert r.status_code == 200 and len(r.json()["runs"]) == 1
    a = client.get("/api/mcts-runs/aggregate")
    assert a.status_code == 200 and a.json()["n_losses"] == 1


def test_names_resolved_resolves_loc_keys():
    """Card/relic loc keys (as the Rust backend emits) resolve for log labels."""
    from workbench.server import _names_resolved

    loc = {"STRIKE_IRONCLAD.title": "Strike", "BLOOD_WALL.title": "Blood Wall"}
    state = {
        "hand": [{"name": "STRIKE_IRONCLAD.title", "id": "STRIKE_IRONCLAD"}],
        "player": {"deck": [{"name": "BLOOD_WALL.title", "id": "BLOOD_WALL"}]},
        "options": [{"title": "STRIKE_IRONCLAD.title"}],
    }
    out = _names_resolved(state, loc)
    assert out["hand"][0]["name"] == "Strike"
    assert out["player"]["deck"][0]["name"] == "Blood Wall"
    assert out["options"][0]["title"] == "Strike"
    # Original is untouched (deepcopy).
    assert state["hand"][0]["name"] == "STRIKE_IRONCLAD.title"


def test_simlab_api_available_when_serving_spa(tmp_path, fixture_log):
    """Single-container (serve_spa=True) deployment must expose the SimLab API.

    Regression: with serve_spa=True the SPA StaticFiles catch-all mounted at '/'
    used to shadow the (never-mounted) SimLab API, so a browser POST to
    /api/sweeps hit StaticFiles and returned 405 Method Not Allowed. The SimLab
    routes must be registered as explicit routes *before* the catch-all.
    """
    images = tmp_path / "images"
    images.mkdir()
    app = create_app(images_root=images, logs_dir=fixture_log.parent, serve_spa=True)
    client = TestClient(app)

    # GET /api/meta is a SimLab route — a 404/405 means it was never registered.
    r = client.get("/api/meta")
    assert r.status_code == 200, r.status_code
    assert "strategies" in r.json()

    # POST /api/sweeps is the exact request that 405'd before the fix. A bad
    # spec now reaches the handler and 422s (proving the route resolves to the
    # SimLab handler, not the GET/HEAD-only StaticFiles mount).
    r = client.post("/api/sweeps", json={"encounters": ["NOPE"], "n_seeds": 1})
    assert r.status_code != 405, "POST /api/sweeps hit the StaticFiles catch-all"
    assert r.status_code == 422, r.status_code


def test_api_map_attaches_room_icons(tmp_path):
    # A map_select step with a full graph must surface the real per-room-type
    # icon URLs on both the reconstructed rows and the connected graph.
    images = tmp_path / "images"
    rh = images / "run_history"
    rh.mkdir(parents=True)
    for slug in ("monster", "event", "elite"):
        (rh / f"{slug}.png").write_bytes(b"x")
    log = tmp_path / "run.jsonl"
    _write_log(
        log,
        {
            "decision": "map_select",
            "player": {"name": "Ironclad"},
            "choices": [
                {"col": 0, "row": 1, "type": "Monster"},
                {"col": 2, "row": 1, "type": "Event"},
            ],
            "full_map": {
                "rows": [[{"col": 2, "row": 2, "type": "Elite"}]],
                "boss": None,
            },
        },
    )
    c = TestClient(create_app(images_root=images, logs_dir=tmp_path))
    c.post("/api/load", json={"path": str(log)})
    body = c.get("/api/map").json()
    icons = {n["type"]: n["icon"] for n in body["rows"][0]["nodes"]}
    assert icons["Monster"] == "/assets/run_history/monster.png"
    assert icons["Event"] == "/assets/run_history/event.png"
    graph_node = body["graphs"][0]["full_map"]["rows"][0][0]
    assert graph_node["icon"] == "/assets/run_history/elite.png"
