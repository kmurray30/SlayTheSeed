"""GH#286: every id the engine can emit must resolve to a non-OOV vocab index.

The run-level policy encoder maps card/relic/potion/event names to embedding
ids through ``ppo.vocab.Registry``; a name that fails to canonicalize to a
registry key silently falls into the single reserved OOV slot, making the net
blind to that id. This bit Necrobinder: 67 registry entries carry a trailing
``_CARD`` disambiguation suffix (``DEBILITATE_CARD``, ``BARRICADE_CARD``, …)
that the engine-emitted forms lack, so ~10% of the card universe was
permanently OOV until vocab.py commit a082efeef.

These tests enumerate the name universe from ``sts2_rs.list_registry_id_forms``
— one ``(Debug, python_name)`` pair per enum variant, straight from the engine
enums rather than re-derived from the registry under test — and probe every
form the engine emits:

- the Debug/PascalCase form (``master_deck_detailed``, potion-inventory ids),
- the raw python name (``list_registry_names``),
- the prefixed reward-option form (``CARD.<NAME>`` from
  ``card_reward_option_ids``),
- for cards, the scraped C# form: real-game ``.run`` files namespace by
  ``CARD.`` and never carry the Rust-only ``_CARD`` suffix (the form that was
  OOV pre-fix — this file must FAIL against the pre-a082efeef vocab).

``CardId::all()`` (the ``"card"`` universe here) is by construction every card
the engine can instantiate — all five characters plus colorless, curse,
status, and special cards; the witness tests below spot-check that span.
"""

from __future__ import annotations

from collections.abc import Iterator

import pytest
import sts2_rs
from ppo import vocab

# kind -> (registry factory, engine id-namespace prefix)
KINDS: dict[str, tuple] = {
    "card": (vocab.cards, "CARD"),
    "relic_id": (vocab.relics, "RELIC"),
    "potion": (vocab.potions, "POTION"),
    "event": (vocab.events, "EVENT"),
}


def _emitted_forms(kind: str, prefix: str) -> Iterator[str]:
    """Every string form the engine emits for every id of ``kind``."""
    for debug, py in sts2_rs.list_registry_id_forms(kind):
        yield debug
        yield py
        yield f"{prefix}.{py}"
        if kind == "card":
            # Scraped C# ids never carry the Rust-only _CARD suffix
            # (mirrors parity_fuzz._csharp_card_id).
            yield f"CARD.{py.removesuffix('_CARD') or py}"


@pytest.mark.parametrize("kind", KINDS)
def test_every_emitted_form_resolves_non_oov(kind: str) -> None:
    reg_fn, prefix = KINDS[kind]
    reg = reg_fn()
    misses = sorted({f for f in _emitted_forms(kind, prefix) if reg.index(f) == reg.oov})
    assert not misses, (
        f"{kind}: {len(misses)} engine-emitted forms land in the OOV slot "
        f"(net is blind to them): {misses[:20]}"
    )


@pytest.mark.parametrize("kind", KINDS)
def test_no_canonical_collisions_within_registry(kind: str) -> None:
    """First-writer-wins keying silently merges canonical collisions into one
    embedding row — assert none exist so a colliding future name fails loud."""
    reg = KINDS[kind][0]()
    canon = {vocab.canonical(n) for n in reg.names}
    assert len(canon) == len(reg.names), (
        f"{kind}: {len(reg.names) - len(canon)} canonical() collisions in registry"
    )


def test_card_universe_spans_all_characters_and_card_classes() -> None:
    """Witnesses that the card universe covers all five characters (starter
    Strikes) plus colorless / curse / status pools."""
    reg = vocab.cards()
    witnesses = [
        "STRIKE_IRONCLAD",
        "STRIKE_SILENT",
        "STRIKE_DEFECT",
        "STRIKE_REGENT",
        "STRIKE_NECROBINDER",
        "APOTHEOSIS",  # colorless
        "ASCENDERS_BANE",  # curse
        "BURN",  # status
        "DAZED",  # status
        "WOUND",  # status
        "SLIMED",  # status
    ]
    missing = [w for w in witnesses if reg.index(w) == reg.oov]
    assert not missing, f"card universe is missing whole pools: {missing}"
