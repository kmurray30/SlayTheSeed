"""Gates for the PPO env's surrogate combat backend (`combat="surrogate"`).

The backend swaps the per-decision combat MCTS for one surrogate forward pass
per fight — the lever that makes a screening run affordable, since collection
is ~87% of a campaign's wall clock and combat search is nearly all of it.

What is gated here is the *plumbing contract*, which is what a wrong wiring
would break silently:

- fights resolve without being played, and the run still advances;
- the deployed reward bridge still runs, so a won fight still produces its
  card reward — the surrogate replaces the playing of a fight, not its rewards;
- the survival draw varies per fight (a per-run seed would give every fight in
  a run the same uniform, which is not a Bernoulli process);
- the same seed reproduces the same run.

The surrogate's *accuracy* is a different question, gated by
`eval_combat_surrogate_net.py` and by the deck-sensitivity check in
`docs/ppo-screen-playbook.md`. These tests use a stub net with hand-set
outputs and say nothing about it.
"""

from __future__ import annotations

import json
import os
import sys

import pytest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "python"))

sts2_rs = pytest.importorskip("sts2_rs")

# Width of the ranker-feature dense context this build expects. Read from the
# loader's own error rather than hardcoded, so a feature-schema change makes
# these tests adapt instead of failing with an unrelated message.
_CONTEXT_DIM_HINT = 1482


def _schema_and_dim(tmp_path) -> tuple[str, int]:
    """Recover `(schema_id, context_dim)` by asking the loader to reject stubs."""
    p = tmp_path / "probe.json"

    def _try(schema_id: str, cdim: int) -> str | None:
        p.write_text(json.dumps(_net_json(schema_id, cdim, win_logit=0.0, hp=0.0)))
        try:
            sts2_rs.CombatSurrogate(str(p))
        except Exception as e:
            return str(e)
        return None

    err = _try("?", _CONTEXT_DIM_HINT)
    assert err and "this build is" in err, err
    schema_id = err.split("this build is '")[1].split("'")[0]
    err = _try(schema_id, _CONTEXT_DIM_HINT)
    if err is None:
        return schema_id, _CONTEXT_DIM_HINT
    assert "expected" in err, err
    return schema_id, int(err.rsplit("expected", 1)[1].strip().rstrip("."))


def _net_json(schema_id: str, cdim: int, *, win_logit: float, hp: float) -> dict:
    """A stub surrogate whose output is constant and hand-chosen.

    `w1` is all zeros and `b1 = [1.0]`, so the single hidden unit is 1.0 after
    the ReLU regardless of the context; the heads then read straight off
    `w_win` / `w_hp`. Constant on purpose: these tests are about wiring, and a
    constant makes the expected run outcome exactly predictable.
    """
    return {
        "schema_id": schema_id,
        "engine": "stub",
        "context_dim": cdim,
        "n_encounters": 1,
        "enc_dim": 1,
        "hidden": 1,
        "feat_mu": [0.0] * cdim,
        "feat_sd": [1.0] * cdim,
        "enc_emb": [[0.0], [0.0]],
        "w1": [[0.0]] * (cdim + 1),
        "b1": [1.0],
        "w_win": [win_logit],
        "b_win": 0.0,
        "w_hp": [hp],
        "b_hp": 0.0,
    }


@pytest.fixture(scope="module")
def schema(tmp_path_factory) -> tuple[str, int]:
    return _schema_and_dim(tmp_path_factory.mktemp("probe"))


def _write_net(tmp_path, schema, *, win_logit: float, hp: float) -> str:
    schema_id, cdim = schema
    p = tmp_path / f"surrogate_{win_logit}_{hp}.json"
    p.write_text(json.dumps(_net_json(schema_id, cdim, win_logit=win_logit, hp=hp)))
    return str(p)


def _env(path: str, seed: int = 1234, **kw):
    from ppo.env import PpoRunEnv

    return PpoRunEnv(
        seed=seed,
        ascension=0,
        character="Ironclad",
        combat="surrogate",
        surrogate_path=path,
        **kw,
    )


def test_rejects_a_misconfigured_backend(tmp_path, schema):
    from ppo.env import PpoRunEnv

    with pytest.raises(ValueError, match="surrogate_path"):
        PpoRunEnv(seed=1, combat="surrogate")
    with pytest.raises(ValueError, match="mcts"):
        PpoRunEnv(seed=1, combat="heuristic")
    # Expert mode drives combat itself, so there is no seam to swap. Accepting
    # this silently would make the G1 parity gate compare two worlds.
    with pytest.raises(ValueError, match="expert"):
        _env(_write_net(tmp_path, schema, win_logit=8.0, hp=0.0), expert=True)


def test_fights_resolve_without_being_played_and_rewards_still_arrive(tmp_path, schema):
    """`win_logit=8` ⇒ win_prob ≈ 0.9997, `hp=0` ⇒ no HP lost: the run should
    walk deep into the act, and every combat should be booked to the
    'combat-surrogate' resolution class rather than 'combat-agent'.

    That it *progresses* is the reward-bridge assertion: `resolve_combat_fast`
    exits through the ordinary `exit_combat_and_transition`, so a won fight
    still generates its card reward and the run still has decisions to yield.
    """
    from ppo.env import Decision, Terminal

    env = _env(_write_net(tmp_path, schema, win_logit=8.0, hp=0.0))
    out = env.reset()
    surfaces = []
    steps = 0
    while isinstance(out, Decision) and steps < 400:
        surfaces.append(out.surface)
        out = env.step(0)
        steps += 1

    classes = {k for _, _, _, k in env.resolution_log}
    assert "combat-surrogate" in classes
    assert "combat-agent" not in classes
    # The reward bridge ran: card rewards only exist downstream of a won fight.
    assert "card-pick" in surfaces, surfaces[:20]
    if isinstance(out, Terminal):
        assert out.metrics["total_floor"] > 5


def test_a_certain_loss_ends_the_run_at_the_first_fight(tmp_path, schema):
    """`win_logit=-8` ⇒ win_prob ≈ 0.0003. The first fight should end the run,
    which is the other half of `resolve_combat_fast`'s contract."""
    from ppo.env import Decision, Terminal

    env = _env(_write_net(tmp_path, schema, win_logit=-8.0, hp=0.5))
    out = env.reset()
    steps = 0
    while isinstance(out, Decision) and steps < 400:
        out = env.step(0)
        steps += 1
    assert isinstance(out, Terminal)
    assert not out.won
    assert out.metrics["total_floor"] < 10


def test_survival_draw_varies_across_fights(tmp_path, schema):
    """A per-RUN seed would hand every fight in the run the same uniform, so a
    marginal run would either survive all of them or die at the first — the
    exact bug this keys `_outcome_base` on the floor to avoid.

    At win_prob = 0.5 a run that reaches several fights must therefore not be
    deterministic in the "all survive / first kills" sense across seeds.
    """
    from ppo.env import Decision, Terminal

    path = _write_net(tmp_path, schema, win_logit=0.0, hp=0.02)
    floors = []
    for seed in range(2000, 2016):
        env = _env(path, seed=seed)
        out = env.reset()
        steps = 0
        while isinstance(out, Decision) and steps < 400:
            out = env.step(0)
            steps += 1
        assert isinstance(out, Terminal)
        floors.append(out.metrics["total_floor"])
    # A shared-uniform bug collapses this to two values (die at fight 1, or
    # clear the act); a real Bernoulli process spreads the death floor out.
    assert len(set(floors)) >= 4, floors


def test_same_seed_reproduces_the_same_run(tmp_path, schema):
    from ppo.env import Decision, Terminal

    path = _write_net(tmp_path, schema, win_logit=0.4, hp=0.05)

    def _play(seed: int):
        env = _env(path, seed=seed)
        out = env.reset()
        trace, steps = [], 0
        while isinstance(out, Decision) and steps < 400:
            trace.append((out.surface, len(out.actions)))
            out = env.step(0)
            steps += 1
        assert isinstance(out, Terminal)
        return trace, out.metrics["total_floor"], out.won

    assert _play(4242) == _play(4242)


def test_raises_when_not_in_combat(tmp_path, schema):
    """The PyO3 seam is precondition-checked rather than silently no-op, so a
    caller that loses track of the phase finds out immediately."""
    net = sts2_rs.CombatSurrogate(_write_net(tmp_path, schema, win_logit=0.0, hp=0.0))
    rm = sts2_rs.RunManager(seed=7, character="Ironclad", ascension_level=0, neow_start=True)
    with pytest.raises(RuntimeError, match="phase=combat"):
        rm.surrogate_resolve_combat(net, 12345)
