from workbench.playlog import format_play_log


def _e(id, *, upgraded=False, energy_spent=0, auto_played=False, target=None):
    return {
        "id": id,
        "upgraded": upgraded,
        "energy_spent": energy_spent,
        "auto_played": auto_played,
        "target": target,
    }


def test_empty_log_is_no_lines():
    assert format_play_log([]) == []


def test_manual_xcost_shows_x_value():
    assert format_play_log([_e("Cascade", energy_spent=3)]) == ["Play Cascade (X=3)"]


def test_non_xcost_manual_has_no_x_suffix():
    assert format_play_log([_e("Strike")]) == ["Play Strike"]


def test_upgraded_marked_with_plus():
    assert format_play_log([_e("Strike", upgraded=True)]) == ["Play Strike+"]


def test_auto_plays_indented_under_trigger():
    log = [
        _e("Cascade", energy_spent=3),
        _e("Strike", auto_played=True),
        _e("Strike", auto_played=True),
        _e("Defend", auto_played=True),
    ]
    assert format_play_log(log) == [
        "Play Cascade (X=3)",
        "  ↳ Strike (auto-played)",
        "  ↳ Strike (auto-played)",
        "  ↳ Defend (auto-played)",
    ]


def test_name_resolver_is_applied():
    out = format_play_log(
        [_e("CASCADE", energy_spent=2)], name_of=lambda i: {"CASCADE": "Cascade"}.get(i, i)
    )
    assert out == ["Play Cascade (X=2)"]
