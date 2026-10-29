"""ONNX export: fixed-shape wrapper ≡ variable-shape torch forward."""

from pathlib import Path

import numpy as np
import pytest

torch = pytest.importorskip("torch")

from ppo.encoder import featurize_row  # noqa: E402
from ppo.env import PpoRunEnv, Terminal  # noqa: E402
from ppo.model import collate  # noqa: E402
from ppo.net_policy import NetPolicy  # noqa: E402

CKPT = "data/ppo/ppo_fullrun_it350.pt"
# A training checkpoint (retired net); .pt files are not part of the public
# export, so the test skips without it rather than failing.
pytestmark = pytest.mark.skipif(
    not (Path(__file__).resolve().parents[1] / CKPT).is_file(),
    reason=f"{CKPT} not present (checkpoints are private)",
)
T_MAX, A_MAX = 320, 160  # the shipped budget since GH #315 (was 96)


@pytest.fixture(autouse=True)
def _env(monkeypatch):
    # Policy-mode PpoRunEnv drives combat with the combat-only
    # MctsCombatAgent (GH #220) — no net files or env vars needed.
    monkeypatch.delenv("STS2_RUNAI_VALUE", raising=False)


def _decisions(n=8, seed=0):
    """First n multi-option decisions of a greedy run (obs, actions, metrics)."""
    env = PpoRunEnv(seed=seed)
    pol = NetPolicy(CKPT)
    out = env.reset()
    rows = []
    while not isinstance(out, Terminal) and len(rows) < n:
        rows.append((out.obs, out.actions, out.metrics))
        probs, _ = pol.distribution(out.obs, out.actions, out.metrics)
        out = env.step(int(np.argmax(probs)))
    assert len(rows) == n
    return rows, pol


def test_fixed_shape_forward_matches_variable():
    from ppo.export_onnx import ExportWrapper, pad_to_fixed

    rows, pol = _decisions()
    wrapper = ExportWrapper(pol.net).eval()
    for obs, actions, metrics in rows:
        feat = featurize_row(obs, actions, metrics)
        b = collate([feat])
        with torch.no_grad():
            ref_logits, ref_value, _ = pol.net(b)
            inp = pad_to_fixed(feat, T_MAX, A_MAX)
            logits, value = wrapper(
                *[
                    torch.from_numpy(inp[k])
                    for k in ("cat", "cont", "sec", "pad_mask", "obs_len", "act_mask")
                ]
            )
        n = feat.n_actions
        assert torch.allclose(logits[0, :n], ref_logits[0, :n], atol=1e-5)
        assert torch.allclose(value, ref_value, atol=1e-5)


def test_export_produces_valid_onnx(tmp_path):
    import onnx
    from ppo.export_onnx import export

    out = tmp_path / "net.onnx"
    export(CKPT, str(out), t_max=T_MAX, a_max=A_MAX)
    onnx.checker.check_model(str(out))
