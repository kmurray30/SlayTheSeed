"""Tests for synergy.order — hierarchical clustering card seriation."""

from synergy.order import compute_card_order


def _block_pairs() -> list[dict]:
    """Synthetic two-block PMI: {A,B,C} vs {D,E,F}, cross-block ≈ 0."""
    high = 3.0
    low = 0.01
    block1 = ["A", "B", "C"]
    block2 = ["D", "E", "F"]
    pairs = []
    for i, a in enumerate(block1):
        for b in block1[i + 1 :]:
            pairs.append({"pair": [a, b], "pmi": high})
    for i, a in enumerate(block2):
        for b in block2[i + 1 :]:
            pairs.append({"pair": [a, b], "pmi": high})
    for a in block1:
        for b in block2:
            pairs.append({"pair": [a, b], "pmi": low})
    return pairs


def _contiguous(order: list[str], group: list[str]) -> bool:
    """Return True iff all members of group appear as a contiguous run in order."""
    positions = [order.index(t) for t in group if t in order]
    if not positions:
        return False
    positions.sort()
    return positions[-1] - positions[0] == len(positions) - 1


def test_block_order_keeps_blocks_contiguous():
    """Two tight PMI blocks should remain contiguous after optimal leaf ordering."""
    pairs = _block_pairs()
    order = compute_card_order(pairs)
    assert len(order) == 6
    block1 = ["A", "B", "C"]
    block2 = ["D", "E", "F"]
    assert _contiguous(order, block1), f"block1 not contiguous in {order}"
    assert _contiguous(order, block2), f"block2 not contiguous in {order}"


def test_signal_ids_filter_restricts_output():
    """signal_ids restricts to just those tokens."""
    pairs = _block_pairs()
    signal = {"A", "B", "D", "E"}
    order = compute_card_order(pairs, signal_ids=signal)
    assert set(order) == signal


def test_signal_ids_blocks_still_contiguous():
    """Even with restricted signal_ids, same-block cards stay adjacent."""
    pairs = _block_pairs()
    # Use only A,B from block1 and D,E,F from block2 — block1 subset still contiguous
    signal = {"A", "B", "D", "E", "F"}
    order = compute_card_order(pairs, signal_ids=signal)
    assert _contiguous(order, ["A", "B"]), f"A,B not contiguous in {order}"
    assert _contiguous(order, ["D", "E", "F"]), f"D,E,F not contiguous in {order}"


def test_empty_pairs_returns_empty():
    order = compute_card_order([])
    assert order == []


def test_single_card_returns_single():
    pairs = [{"pair": ["A", "B"], "pmi": 1.0}]
    order = compute_card_order(pairs, signal_ids={"A"})
    assert order == ["A"]


def test_order_is_deterministic():
    """Same input always produces same output (no random)."""
    pairs = _block_pairs()
    order1 = compute_card_order(pairs)
    order2 = compute_card_order(pairs)
    assert order1 == order2
