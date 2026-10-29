#!/usr/bin/env python3
"""Parse the C# decomp's VFX *binding* call sites: which card play or
monster move triggers which effect.

Design spec: `docs/superpowers/specs/2026-08-28-workbench-vfx-particles-design.md`
§1. Three call shapes carry a binding (§1.1), plus a fourth added during
review once it turned out one in-scope composite has no other route in:

* **`WithHitFx("vfx/<effect>", ...)`** — a hit-attached effect named by a
  string literal. ~357 sites; the ~8 with a non-literal first argument
  (`null`, a variable) are not extractable and produce no row.
* **`WithHitVfxNode(<factory>)`** — a hit-attached effect built by a C#
  factory (`N<Name>Vfx.Create(...)`) rather than named by a path. Often
  carries a hardcoded tint as a `new Color("...")` argument.
* **Direct `N<Name>Vfx.Create(...)` + `AddChildSafely(...)`** — a
  free-standing spawn, not attached to a hit (fired on card play or potion
  use).
* **`VfxCmd.PlayOnCreatureCenter(s)(...)` / `VfxCmd.PlayVfx(...)`** — a
  free-standing effect fired by explicit position rather than through the
  hit pipeline (`shape = "vfx_cmd"`). Not attached to a `WithHitFx`-style
  call at all, so it was out of scope for the original three-shape design
  until it turned out `vfx_coin_explosion_regular` has no other call
  shape anywhere in the decomp — `GremlinMerc.cs`,
  `HandOfGreed.cs`. `VfxCmd.PlayOnCreatureCenters` forwards to
  `PlayOnCreatureCenter` internally (`VfxCmd.cs:228-236`); the binding is
  attributed to the call site in the card/monster file, not that
  forwarding.

### The id-join hazard (design spec §1.6)

A monster's ``MoveState`` constructor id (e.g. ``"THRASH_MOVE"``) is **not**
the same string the Rust engine logs for that move, and the constructor's
handler *method name* (``ThrashMove``) does not reliably tell you which
state id it belongs to either — Crusher's ``BUG_STING_MOVE`` binds to
``BugStingMove``, and nothing in the two names alone says so. The only
sound path is constructor → method → literal: read the state id and its
method from the ``MoveState(...)`` call, locate that method's body, then
scan the body alone. Matching by name normalisation is unsound and is not
attempted here.

A monster row is keyed `<MonsterFileStem>:<STATE_ID>` — the monster half
verbatim PascalCase (`"Crusher"`, matching the decomp file stem and the
Rust engine's `MonsterKind::python_name()` — see
`sts2-core/src/monsters.rs`, and `combatlog.py::_source_ids`'s "left
uncased" note), the move half verbatim SCREAMING_SNAKE as the C# constructor
spells it (`"THRASH_MOVE"`, no `_MOVE`-suffix stripping — that
reconciliation belongs to whatever consumes this table, not to extraction).

A card row is keyed on the card id alone, upper-snake
(``"KNOCKOUT_BLOW"``) — this is `CardId::to_python_name()`
(`sts2-core/src/card_ids_generated.rs`), which is what
`sts2-py/src/run_manager.rs` puts on `LogSource::Card` and what
`combatlog.py` surfaces as `source_card`. A card's C# class name is
PascalCase (`KnockoutBlow`), so the join key needs a real
PascalCase-to-SCREAMING_SNAKE conversion, not `str.upper()` — `.upper()`
only happens to work for single-word ids like `Bash`; for anything else it
drops the underscores the join needs (`"KnockoutBlow".upper()` ==
`"KNOCKOUTBLOW"` != the real key `"KNOCKOUT_BLOW"`).

**The `Card`/`Status` suffix collision, resolved.** 68 decomp card classes
collide with an unrelated same-named `Power` (or, for `Sloth`, a `Status`)
and `card_ids_generated.rs` disambiguates the `CardId` variant with a
`Card`/`Status` suffix the decomp class name itself doesn't carry — e.g.
decomp class `Accuracy` but `CardId::AccuracyCard => "ACCURACY_CARD"`. This
was found systematically, by diffing every decomp `Cards/*.cs` stem
against every `CardId` variant in `card_ids_generated.rs` — not guessed,
and not "a small number" as an earlier, weaker survey (round 1) put it.
Rather than a second, hand-maintained 68-entry list to keep in sync
(exactly the pattern that has bitten this repo before — four hand-kept
champion lists, one missed edit, a container silently serving the wrong
net), `card_owner_id()` reads `card_ids_generated.rs` itself and tries the
`""`/`"Card"`/`"Status"` suffixes against it directly, so all 68 resolve
mechanically with nothing to maintain.
`CardOwnerIdResolutionTest.test_card_owner_ids_resolve_against_card_ids_generated_rs`
still guards this — repurposed in round 2 to assert every decomp
`Cards/*.cs` stem resolves to a real `CardId`, except a short, explicit
list of classes with no `CardId` at all (`DeprecatedCard` — an internal
base, not a live card). A monster state-id equivalent of this hazard, if
one exists, is still whatever design spec §1.4's id-resolution gate would
catch — nothing here claims monster ids are similarly guarded.
"""

from __future__ import annotations

import functools
import re
from dataclasses import dataclass
from pathlib import Path

# `new MoveState("STATE_ID", MethodName, ...)` inside a monster's
# `GenerateMoveStateMachine()`. Matched globally (not root-scoped): a file
# commonly declares several `MoveState`s back to back, in whatever order the
# state list is authored, and each is independent.
_MOVE_STATE = re.compile(r'new\s+MoveState\(\s*"(?P<id>[A-Z0-9_]+)"\s*,\s*(?P<method>[A-Za-z_]\w*)')

#: `(?P<path>...)` captures everything after `"vfx/`, which is USUALLY a
#: flat effect id (`"vfx/vfx_attack_slash"`) but not always: task-7's engine
#: coverage gate (`vfx_binding_coverage.rs`) found `VineShambler.cs:63`'s
#: `.WithHitFx("vfx/monsters/vine_shambler_vines/vine_shambler_vines_vfx")`
#: — a real literal site, verified against the design spec's own §"Non-goals"
#: list, which already names `vine_shambler_vines_vfx` as an expected table
#: entry — that the original `[a-zA-Z0-9_]+` class silently dropped because
#: it stops at the first `/`. `_hit_fx_bindings` below takes this path's
#: final `/`-segment as the effect id, which is a no-op for every other site
#: (none of the other 356 literal sites nest a directory under `vfx/`) and
#: recovers the one that does.
_WITH_HIT_FX = re.compile(r'\.WithHitFx\(\s*"vfx/(?P<path>[a-zA-Z0-9_/]+)"')
_WITH_HIT_VFX_NODE = re.compile(r"\.WithHitVfxNode\(")
_VFX_CREATE = re.compile(r"\bN(?P<cls>[A-Za-z0-9]*Vfx)\.Create\b")
_COLOR_LITERAL = re.compile(r'Color\(\s*"(?P<hex>[0-9a-fA-F#]+)"\s*\)')
_ADD_CHILD_SAFELY = re.compile(r"\.AddChildSafely\(")

# The 4th shape (module docstring): a free-standing effect fired by explicit
# position rather than through the hit pipeline.
_VFX_CMD_CALL = re.compile(r"\bVfxCmd\.(?:PlayOnCreatureCenters?|PlayVfx)\(")
# Not anchored to `WithHitFx`'s "right after the open paren" position: unlike
# `WithHitFx`, the path isn't always the first argument
# (`PlayVfx(position, "vfx/...", container)`), so this is searched over the
# whole balanced argument list instead, the same way `_tint` searches for a
# `Color` literal.
_VFX_PATH_LITERAL = re.compile(r'"vfx/(?P<effect>[a-zA-Z0-9_]+)"')

# `N<Name>Vfx` -> effect id. Deliberately small and explicit, not a string
# transform (design spec §1.1's closing note): class name and scene name do
# not follow one rule (`NSmokePuffVfx` -> `vfx_smoke_puff`, but `NScratchVfx`
# -> `vfx_scratch_impact`). Each entry is verified against that class's own
# `scenePath`/`ScenePath` constant in `MegaCrit.Sts2.Core.Nodes.Vfx/N<Name>Vfx.cs`
# (or the sibling `.Cards`/`.Utilities` namespace for a few of them) — not
# guessed.
#
# Covers every class actually reached by a `WithHitVfxNode` factory in
# Cards+Monsters (reconciled against all 30 real call sites in review round
# 2 — see task-5-report.md), not only the 8 that reach one of the 13
# in-scope particle composites. The other 8 entries below
# (`SpikeSplashVfx`.."StabVfx") resolve to real effect ids the renderer has
# no asset for yet — design spec §1.5: "the table will carry their rows;
# the renderer will fall back" — the same treatment the `hit_fx` shape
# already gives the long-tail `WithHitFx` literal sites (`vfx_dramatic_stab`
# etc.); leaving a resolvable factory unresolved here would have been the
# inconsistent choice, not the safe one. This is NOT the same as widening to
# every `N*Vfx.Create` class in the codebase (there are ~39) — a class
# reached only through the free-standing "spawn" shape and never through
# `WithHitVfxNode` is deliberately still absent; see
# `test_a_factory_for_an_out_of_scope_effect_produces_no_row`.
#
# `SpikeSplashVfx`, `KinPriestGrenadeVfx` and `StabVfx` are the odd ones
# out: every other class's own `scenePath` names a resource that already
# starts `vfx_...` (e.g. `NBigSlashVfx.scenePath` -> `"vfx/vfx_big_slash"`),
# but these three's real resources are `"vfx/spike_splash_vfx"`,
# `"vfx/monsters/kin_priest_grenade_vfx"` and `"res://scenes/vfx/stab_vfx.tscn"`
# — the game itself suffixes `_vfx` here instead of prefixing it. Task-6
# review round 1 asked for one map-key convention; renaming the *stem* would
# fabricate an id no `scenePath` constant actually contains, so the fix
# is a `vfx_` prefix added on top of the verbatim game name, not a rename
# of it — `vfx_spike_splash_vfx` still contains the real
# `spike_splash_vfx` unchanged.
_IMPACT_VFX_EFFECT = {
    "PoisonImpactVfx": "vfx_poison_impact",
    "SporeImpactVfx": "vfx_spore_impact",
    "GaseousImpactVfx": "vfx_gaseous_impact",
    "GoopyImpactVfx": "vfx_goopy_impact",
    "WormyImpactVfx": "vfx_wormy_impact",
    "ScratchVfx": "vfx_scratch_impact",
    "SmokePuffVfx": "vfx_smoke_puff",
    "FireBurstVfx": "vfx_fire_burst",
    "SpikeSplashVfx": "vfx_spike_splash_vfx",
    "BigSlashVfx": "vfx_big_slash",
    "BigSlashImpactVfx": "vfx_big_slash_impact",
    "KinPriestGrenadeVfx": "vfx_kin_priest_grenade_vfx",
    "GrandFinaleImpactVfx": "vfx_grand_finale_impact",
    "DaggerSprayImpactVfx": "vfx_dagger_spray_impact",
    "StabVfx": "vfx_stab_vfx",
    "ShivThrowVfx": "vfx_shiv_throw",
}

# Matches a capital letter that starts a new word (not position 0), so
# inserting `_` there and upper-casing the result turns PascalCase into
# SCREAMING_SNAKE: "KnockoutBlow" -> "Knockout_Blow" -> "KNOCKOUT_BLOW".
# Verified against every multi-word `to_python_name()` arm sampled from
# `card_ids_generated.rs` (`GoForTheEyes` -> `GO_FOR_THE_EYES`, `SicEm` ->
# `SIC_EM`, `BeamCell` -> `BEAM_CELL`, ...).
_WORD_START = re.compile(r"(?<!^)(?=[A-Z])")

# `card_ids_generated.rs`'s `to_python_name()` match arms:
# `Self::<Variant> => "<PYTHON_NAME>",`.
_CARD_ID_ARM = re.compile(r'Self::(?P<variant>\w+)\s*=>\s*"(?P<python_name>[A-Z0-9_]+)"')
_CARD_IDS_RS = (
    Path(__file__).resolve().parents[2]
    / "sts2-rs"
    / "crates"
    / "sts2-core"
    / "src"
    / "card_ids_generated.rs"
)
# Tried in this order against the generated table: a plain decomp stem
# first, then the two known disambiguation suffixes (see `card_owner_id`).
_CARD_ID_VARIANT_SUFFIXES = ("", "Card", "Status")


@functools.lru_cache(maxsize=1)
def _card_id_python_names() -> dict[str, str]:
    """`{CardId variant name: to_python_name() string}`, read live from the
    generated `sts2-rs` table — the single source of truth, not a second,
    driftable copy of it. `{}` if the sibling checkout isn't present (this
    module still works standalone; `card_owner_id` just falls back to the
    mechanical conversion for every name). Cached: the file is ~2400 lines
    and this is looked up once per parsed card file.
    """
    if not _CARD_IDS_RS.is_file():
        return {}
    return dict(_CARD_ID_ARM.findall(_CARD_IDS_RS.read_text()))


def card_owner_id(name: str) -> str:
    """A card's real join key: `CardId::to_python_name()` for `name`
    (a decomp file stem, e.g. `"KnockoutBlow"` -> `"KNOCKOUT_BLOW"`).

    Reads the generated table (`_card_id_python_names()`) rather than only
    guessing — `name`, `name + "Card"` and `name + "Status"` are tried in
    that order against it, so the ~68 decomp classes that collide with a
    same-named `Power`/`Status` and get a `CardId` variant disambiguated
    with that suffix (`Accuracy.cs` -> `CardId::AccuracyCard` ->
    `"ACCURACY_CARD"`) resolve correctly with no hand-maintained exception
    list — see `CardOwnerIdResolutionTest` in `test_vfx_bindings.py`. Falls
    back to the mechanical PascalCase-to-SCREAMING_SNAKE conversion only
    when the generated table can't be read at all (no sibling `sts2-rs`
    checkout) or `name` matches no `CardId` variant under any of the three
    suffixes (a fixture name in a unit test, or a genuinely variantless
    decomp class like `DeprecatedCard`).
    """
    table = _card_id_python_names()
    for suffix in _CARD_ID_VARIANT_SUFFIXES:
        python_name = table.get(name + suffix)
        if python_name is not None:
            return python_name
    return _WORD_START.sub("_", name).upper()


@dataclass(frozen=True)
class Binding:
    """One VFX call site, resolved to the effect it plays.

    `owner` is the join key described in the module docstring: the card id
    (upper-snake) for `kind == "card"`, or `<Monster>:<STATE_ID>` for
    `kind == "move"`.
    """

    owner: str
    kind: str  # "card" | "move"
    effect: str
    tint: str | None
    shape: str  # "hit_fx" | "hit_vfx_node" | "spawn" | "vfx_cmd"


def _balanced(text: str, open_idx: int, open_ch: str = "(", close_ch: str = ")") -> tuple[str, int]:
    """Text strictly between the delimiter at `open_idx` and its match.

    Returns `(inner, index_just_past_the_close)`. C# VFX call sites nest
    delimiters (`new Color("...")` inside a `Create(...)` arg list, `{ ... }`
    method bodies containing their own blocks), so a `[^)]*`-style regex
    truncates at the first inner close. `text[open_idx]` must be `open_ch`.
    """
    depth = 0
    for i in range(open_idx, len(text)):
        if text[i] == open_ch:
            depth += 1
        elif text[i] == close_ch:
            depth -= 1
            if depth == 0:
                return text[open_idx + 1 : i], i + 1
    return text[open_idx + 1 :], len(text)


def _method_body_span(text: str, method_name: str) -> tuple[int, int] | None:
    """The `(start, end)` span of the `{ ... }` body of a C# method declared
    as `method_name` — end-exclusive, `start` just past the opening `{`.

    Matches the declaration specifically (modifiers ... name ( params ) {),
    not a bare reference to the name — a `MoveState` constructor also
    mentions the method name, but only as a delegate (`ThrashMove,`, no
    trailing `(`), so it can't satisfy this pattern. `[^)]*` for the
    parameter list is safe: game move-method signatures are plain
    (`IReadOnlyList<Creature> targets`), never nested parens.
    """
    sig = re.compile(
        r"(?:private|protected|public|internal)[^\n{;]*?\b"
        + re.escape(method_name)
        + r"\s*\([^)]*\)\s*\{"
    )
    m = sig.search(text)
    if m is None:
        return None
    open_idx = m.end() - 1
    _, end = _balanced(text, open_idx, "{", "}")
    return open_idx + 1, end - 1


def _hit_fx_bindings(text: str) -> list[tuple[str, str | None, str]]:
    out: list[tuple[str, str | None, str]] = []
    for m in _WITH_HIT_FX.finditer(text):
        effect = m.group("path").rsplit("/", 1)[-1]
        if not effect.startswith("vfx_"):
            # `VineShambler.cs:63`'s nested-path site is the one literal
            # `WithHitFx` call in the whole decomp whose scene name doesn't
            # already start with `vfx_` — it SUFFIXES instead
            # (`vine_shambler_vines_vfx`), the same naming quirk
            # `_IMPACT_VFX_EFFECT`'s docstring documents for `SpikeSplashVfx`
            # / `KinPriestGrenadeVfx` / `StabVfx`. Same fix: a `vfx_` prefix
            # added on top of the verbatim game name (not a rename — the
            # real `vine_shambler_vines_vfx` stays intact inside it), so
            # `test_effect_keys_use_one_naming_convention` holds without
            # inventing a name the game doesn't have.
            effect = f"vfx_{effect}"
        out.append((effect, None, "hit_fx"))
    return out


def _hit_vfx_node_bindings(
    text: str,
) -> tuple[list[tuple[str, str | None, str]], list[tuple[int, int]]]:
    """`WithHitVfxNode(...)` sites, plus the spans of text they occupy.

    The spans let `_spawn_bindings` skip a `N*Vfx.Create(...)` call already
    claimed here — a factory handed to `WithHitVfxNode` is not a
    free-standing spawn, and scanning both would double the row.
    """
    out: list[tuple[str, str | None, str]] = []
    spans: list[tuple[int, int]] = []
    for m in _WITH_HIT_VFX_NODE.finditer(text):
        inner, end = _balanced(text, m.end() - 1)
        spans.append((m.start(), end))
        create_m = _VFX_CREATE.search(inner)
        if create_m is None:
            continue
        effect = _IMPACT_VFX_EFFECT.get(create_m.group("cls"))
        if effect is None:
            continue
        args = ""
        after = create_m.end()
        if after < len(inner) and inner[after] == "(":
            args, _ = _balanced(inner, after)
        out.append((effect, _tint(args), "hit_vfx_node"))
    return out, spans


def _tint(args: str) -> str | None:
    """The hardcoded `Color("...")` argument, normalised to `Binding`'s
    documented `#RRGGBB` form (task-6 review round 1). The C# source is
    inconsistent — `new Color("8aad7d")` and `new Color("#402f45")` both
    appear for real — so this is where the one true form gets picked,
    once, rather than leaving every consumer of `tint` to re-derive it.
    """
    m = _COLOR_LITERAL.search(args)
    if m is None:
        return None
    hex_value = m.group("hex")
    return hex_value if hex_value.startswith("#") else f"#{hex_value}"


def _spawn_bindings(text: str, exclude: list[tuple[int, int]]) -> list[tuple[str, str | None, str]]:
    """Direct `N*Vfx.Create(...)` sites not already claimed by a
    `WithHitVfxNode` factory, gated on `AddChildSafely` appearing anywhere
    in the same scope — the free-standing "spawn" shape (module docstring's
    third shape): fired on card play / potion use rather than attached to a
    hit. The gate is scope-wide rather than tied to the specific
    `CombatVfxContainer` accessor because real sites indirect through a
    local variable (`Node val = ...CombatVfxContainer; ... val.AddChildSafely(...)`)
    with no literal `CombatVfxContainer` token next to the `.Create(...)`
    call at all.
    """
    if _ADD_CHILD_SAFELY.search(text) is None:
        return []
    out: list[tuple[str, str | None, str]] = []
    for m in _VFX_CREATE.finditer(text):
        if any(start <= m.start() < end for start, end in exclude):
            continue
        effect = _IMPACT_VFX_EFFECT.get(m.group("cls"))
        if effect is None:
            continue
        args = ""
        if m.end() < len(text) and text[m.end()] == "(":
            args, _ = _balanced(text, m.end())
        out.append((effect, _tint(args), "spawn"))
    return out


def _vfx_cmd_bindings(text: str) -> list[tuple[str, str | None, str]]:
    """`VfxCmd.PlayOnCreatureCenter(s)` / `VfxCmd.PlayVfx(...)` sites naming
    an effect by a literal `"vfx/<effect>"` path argument — the 4th shape
    (module docstring). `PlayOnCreatureCenters` forwarding to
    `PlayOnCreatureCenter` inside `VfxCmd.cs` is irrelevant here: this only
    ever sees the calling card/monster file's own text.
    """
    out: list[tuple[str, str | None, str]] = []
    for m in _VFX_CMD_CALL.finditer(text):
        args, _ = _balanced(text, m.end() - 1)
        path_m = _VFX_PATH_LITERAL.search(args)
        if path_m is None:
            continue
        out.append((path_m.group("effect"), _tint(args), "vfx_cmd"))
    return out


def _bindings_in_text(text: str) -> list[tuple[str, str | None, str]]:
    hit_vfx_node, spans = _hit_vfx_node_bindings(text)
    return (
        _hit_fx_bindings(text)
        + hit_vfx_node
        + _spawn_bindings(text, spans)
        + _vfx_cmd_bindings(text)
    )


def parse_card_file(name: str, text: str) -> list[Binding]:
    """Bindings in one `MegaCrit.Sts2.Core.Models.Cards/<name>.cs` file.

    `name` is the file stem (PascalCase class name, e.g. `"KnockoutBlow"`);
    see the module docstring for why the owner key is not simply its
    upper-case.
    """
    owner = card_owner_id(name)
    return [
        Binding(owner=owner, kind="card", effect=effect, tint=tint, shape=shape)
        for effect, tint, shape in _bindings_in_text(text)
    ]


def parse_monster_file(name: str, text: str) -> list[Binding]:
    """Bindings in one `MegaCrit.Sts2.Core.Models.Monsters/<name>.cs` file.

    `name` is the file stem (PascalCase class name, e.g. `"Crusher"`), kept
    verbatim as the owner's monster half — see the module docstring.

    Walks each `MoveState("ID", Method, ...)` constructor to that method's
    body and scans the body alone, rather than the whole file, so a binding
    inside one move's handler is never attributed to a different move that
    happens to also appear in the file. Bindings outside every matched
    move's body — a helper method `GenerateMoveStateMachine()` doesn't
    reach, or (as in a minimal fixture) a file with no `MoveState` wrapper
    at all — still get a row, keyed at the monster level (`name` with no
    `:STATE_ID` suffix) rather than silently dropped.
    """
    bindings: list[Binding] = []
    consumed: list[tuple[int, int]] = []
    for m in _MOVE_STATE.finditer(text):
        span = _method_body_span(text, m.group("method"))
        if span is None:
            # NOT a silent `continue` (review round 3): a matched MoveState
            # whose method can't be resolved would otherwise drop that
            # state's bindings with no error at all -- the same shape of
            # bug that cost 8 VFX classes in round 2, just one step
            # upstream of it. A full-decomp sweep
            # (MonsterMoveBodyResolutionTest) found zero real cases, so a
            # genuine hit here means either a new C# authoring pattern
            # `_method_body_span`'s signature regex doesn't match, or the
            # caller passed the wrong file's text for `name`.
            raise ValueError(
                f"{name}: MoveState {m.group('id')!r} references method "
                f"{m.group('method')!r}, but no matching method declaration "
                "was found in this file's text"
            )
        start, end = span
        consumed.append((start, end))
        owner = f"{name}:{m.group('id')}"
        for effect, tint, shape in _bindings_in_text(text[start:end]):
            bindings.append(
                Binding(owner=owner, kind="move", effect=effect, tint=tint, shape=shape)
            )

    leftover = text
    for start, end in consumed:
        leftover = leftover[:start] + " " * (end - start) + leftover[end:]
    for effect, tint, shape in _bindings_in_text(leftover):
        bindings.append(Binding(owner=name, kind="move", effect=effect, tint=tint, shape=shape))
    return bindings


#: `public override bool ShouldFadeAfterDeath => <expr>;` — the guard on
#: `NCreature.cs:1106`, which is what decides whether a dying monster gets an
#: `NMonsterDeathVfx` (the threshold dissolve) at all. `MonsterModel.cs:311`
#: defaults it to `true`, so only the overrides are interesting; `virtual` is
#: excluded so the base declaration is not read as a monster's own answer.
_SHOULD_FADE = re.compile(
    r"public\s+override\s+bool\s+ShouldFadeAfterDeath\s*=>\s*(?P<expr>[^;]+);"
)

#: `public override float DeathAnimLengthOverride => <seconds>f;` —
#: `NCreature.cs:1093` waits this long instead of the rig's remaining animation
#: time, for a creature whose body is drawn somewhere other than its own
#: `_spineAnimator`. `MonsterModel.cs:321` defaults it to `0f`, and
#: `HasDeathAnimLengthOverride` reads `> 0f`, so a literal zero is the default
#: rather than an override worth recording.
_DEATH_ANIM_LENGTH = re.compile(
    r"public\s+override\s+float\s+DeathAnimLengthOverride\s*=>\s*(?P<seconds>[0-9]*\.?[0-9]+)f?\s*;"
)


def parse_death_fade(name: str, text: str) -> dict[str, object] | None:
    """What one monster class overrides about its own death, or `None`.

    `None` means "this monster takes `MonsterModel`'s defaults" — it fades
    after death and its death wait comes from its own rig — which is the
    overwhelming majority, so the published table carries only the exceptions.

    The returned row uses `fade`, not a bool, because the C# is not always a
    constant: `WaterfallGiant.cs:157` is `PressureBuildupIdx == 0`. Recording
    that as `false` would be a lie and recording it as `true` would hide it, so
    it gets its own value and carries the expression verbatim for whoever
    implements it.

    Args:
        name: the C# file stem (PascalCase class name, e.g. ``"Crusher"``).
        text: that file's source.

    Returns:
        A row with `fade` (`"never"` / `"always"` / `"conditional"`, absent when
        the class only overrides the death wait), `condition` (only when
        conditional) and `deathAnimSeconds` (only when
        `DeathAnimLengthOverride > 0`), or `None` when the class overrides
        neither.
    """
    row: dict[str, object] = {}

    m = _SHOULD_FADE.search(text)
    if m is not None:
        expr = m.group("expr").strip()
        if expr == "false":
            row["fade"] = "never"
        elif expr == "true":
            # Redundant with the base class, but a class that says it out loud
            # is not a parse failure — record it so the completeness gate still
            # accounts for the override it found.
            row["fade"] = "always"
        else:
            row["fade"] = "conditional"
            row["condition"] = expr

    m = _DEATH_ANIM_LENGTH.search(text)
    if m is not None:
        seconds = float(m.group("seconds"))
        if seconds > 0:
            row["deathAnimSeconds"] = seconds

    return row or None


def mentions_death_override(text: str) -> bool:
    """Whether a file mentions either death override at all.

    Deliberately looser than the two regexes above — a bare substring test — so
    that a future build writing `ShouldFadeAfterDeath` in a shape the parsers do
    not match is caught by the completeness gate as an UNPARSED override rather
    than silently reported as "this monster takes the defaults". That gate is
    the only thing standing between a decomp refresh and a monster quietly
    dissolving when the game does not dissolve it.
    """
    return "ShouldFadeAfterDeath" in text or "DeathAnimLengthOverride" in text
