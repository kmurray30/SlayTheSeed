import { describe, it, expect, vi } from "vitest";
import { render, act } from "@testing-library/react";
import Board from "../Board";
import { SoundFixture } from "../../sound/SoundContext";
import { cuesForCombat } from "../../sound/triggers";
import { combatDiff } from "../../effects/combatDiff";
import { attackingEnemies } from "../../effects/enemyTurn";
import type { CombatState, Step } from "../../api";
import type { SfxMap } from "../../sound/sfxMap";

const MAP: SfxMap = {
  version: "test",
  generic: { blockHit: "event:/sfx/block_hit", blockGain: "event:/sfx/block_gain", blockBreak: "event:/sfx/block_break", heal: "event:/sfx/heal" },
  powers: {},
  cards: {},
  relics: {},
  monsters: {
    Cultist: {
      moves: [{ name: "DARK_STRIKE", intent: "Attack", hits: 1, event: "event:/sfx/e/cultist" }],
      defaultAttack: "event:/sfx/e/cultist",
      death: "event:/sfx/e/cultist_die",
    },
    // Two distinct hit counts, so a wrong-snapshot intent lookup resolves a
    // DIFFERENT move's sound than the right one — see the "resolves the
    // attack sound from the intent that already fired" test below.
    TwoMover: {
      moves: [
        { name: "JAB", intent: "Attack", hits: 1, event: "event:/sfx/e/jab" },
        { name: "HEAVY_SLASH", intent: "Attack", hits: 3, event: "event:/sfx/e/heavy" },
      ],
      defaultAttack: "event:/sfx/e/jab",
    },
  },
};

function state(over: Partial<CombatState> = {}): CombatState {
  return {
    decision: "combat_play", round: 1, energy: 3, max_energy: 3,
    draw_pile_count: 0, discard_pile_count: 0, exhaust_pile_count: 0,
    player: { name: "Ironclad", hp: 80, max_hp: 80, block: 0 },
    player_powers: [], enemies: [], hand: [], ...over,
  } as CombatState;
}

function step(idx: number, over: Partial<CombatState>): Step {
  return {
    idx, decision: "combat_play", scores: [], pv: null, chosen_action: null,
    source: "live", meta: { turn: over.round ?? 1, step_no: idx },
    state: state(over),
  };
}

describe("board combat cues (cheap check — Task 6 functions directly)", () => {
  // Kept as a cheap sanity check on cuesForCombat/combatDiff/attackingEnemies
  // composition, but this alone would pass whether or not Board.tsx ever
  // wires them up — see the "wired into Board" describe block below for the
  // test that actually observes the integration.
  it("turns an enemy turn into that enemy's named attack sound", () => {
    const prev = state({
      enemies: [{ index: 0, name: "Cultist", hp: 50, max_hp: 50, block: 0, intents: [{ type: "Attack", damage: 6, hits: 1 }] }],
    } as Partial<CombatState>);
    const next = state({
      round: 2,
      player: { name: "Ironclad", hp: 74, max_hp: 80, block: 0 },
      enemies: [{ index: 0, name: "Cultist", hp: 50, max_hp: 50, block: 0, intents: [{ type: "Attack", damage: 6, hits: 1 }] }],
    } as Partial<CombatState>);

    const cues = cuesForCombat(combatDiff(prev, next), {
      map: MAP,
      attackers: attackingEnemies(prev, next),
      enemyName: (i) => (prev.enemies ?? []).find((e) => e.index === i)?.name,
      enemyIntent: (i) => (prev.enemies ?? []).find((e) => e.index === i)?.intents?.[0],
    });

    expect(cues.map((c) => c.event)).toContain("event:/sfx/e/cultist");
  });
});

describe("board combat cues — wired into Board", () => {
  it("renders Board and observes emit fire the named enemy attack cue on an enemy turn", () => {
    // Defect (i) from Amendment 2: the reference test above calls Task 6's
    // functions directly and never renders Board, so it would pass whether
    // or not the wiring under test exists. This one renders Board inside a
    // SoundFixture whose `emit` is a real vi.fn(), so it can only pass if
    // Board.tsx actually calls sound.emit(cuesForCombat(...)).
    // Mutation caught: deleting the `sound.emit(...)` block from Board.tsx.
    const emit = vi.fn();
    const prev = step(0, {
      round: 1,
      player: { name: "Ironclad", hp: 80, max_hp: 80, block: 0 },
      enemies: [{ index: 0, name: "Cultist", hp: 50, max_hp: 50, block: 0, intents: [{ type: "Attack", damage: 6, hits: 1 }] }],
    } as Partial<CombatState>);
    const next = step(1, {
      round: 2,
      player: { name: "Ironclad", hp: 74, max_hp: 80, block: 0 },
      enemies: [{ index: 0, name: "Cultist", hp: 50, max_hp: 50, block: 0, intents: [{ type: "Attack", damage: 6, hits: 1 }] }],
    } as Partial<CombatState>);

    const { rerender } = render(
      <SoundFixture on map={MAP} emit={emit}>
        <Board step={prev} onPlayCard={() => {}} onEndTurn={() => {}} />
      </SoundFixture>,
    );
    act(() => {
      rerender(
        <SoundFixture on map={MAP} emit={emit}>
          <Board step={next} onPlayCard={() => {}} onEndTurn={() => {}} />
        </SoundFixture>,
      );
    });

    expect(emit).toHaveBeenCalledTimes(1);
    const events = (emit.mock.calls[0][0] as { event: string }[]).map((c) => c.event);
    expect(events).toContain("event:/sfx/e/cultist");
  });

  it("resolves a dying enemy's name from the PREVIOUS snapshot, not the current one", () => {
    // Defect (ii): a killed enemy is, by definition, absent from the CURRENT
    // snapshot. An `enemyName` that only reads the current snapshot returns
    // undefined for it, and cuesForCombat silently drops the death cue —
    // defeating Task 8b's per-enemy death sounds.
    // Mutation caught: reverting Board.tsx's enemyName lookup to read only
    // `s.enemies` instead of trying `prev.enemies` first.
    const emit = vi.fn();
    const prev = step(0, {
      round: 1,
      enemies: [{ index: 0, name: "Cultist", hp: 5, max_hp: 50, block: 0, intents: [{ type: "Attack", damage: 6, hits: 1 }] }],
    } as Partial<CombatState>);
    // Same round (the player's own card killed it this turn) — Cultist is
    // simply gone from the next snapshot's enemy list.
    const next = step(1, { round: 1, enemies: [] } as Partial<CombatState>);

    const { rerender } = render(
      <SoundFixture on map={MAP} emit={emit}>
        <Board step={prev} onPlayCard={() => {}} onEndTurn={() => {}} />
      </SoundFixture>,
    );
    act(() => {
      rerender(
        <SoundFixture on map={MAP} emit={emit}>
          <Board step={next} onPlayCard={() => {}} onEndTurn={() => {}} />
        </SoundFixture>,
      );
    });

    expect(emit).toHaveBeenCalledTimes(1);
    const events = (emit.mock.calls[0][0] as { event: string }[]).map((c) => c.event);
    expect(events).toContain("event:/sfx/e/cultist_die");
  });

  it("resolves the attack sound from the intent that already fired, not the one just rolled for next turn", () => {
    // Final review finding 10 (minor, 2026-08-23): Board.tsx:221-224's own
    // comment says this is load-bearing ("by `s` the enemy has already
    // rolled its NEXT move"), but nothing tested it — changing
    // `enemyIntent` from `prev` to `s` left all 549 tests green. TwoMover's
    // intent is hits:1 (JAB) in `prev` -- the move that fired THIS turn --
    // and hits:3 (HEAVY_SLASH) in `next` -- the move it already rolled for
    // NEXT turn. Reading from `s` instead of `prev` would resolve the wrong
    // move's sound.
    // Mutation caught: changing Board.tsx's `enemyIntent` lookup from
    // `prev.enemies` to `s.enemies` (or `next.enemies` in this test's terms).
    const emit = vi.fn();
    const prev = step(0, {
      round: 1,
      player: { name: "Ironclad", hp: 80, max_hp: 80, block: 0 },
      enemies: [{ index: 0, name: "TwoMover", hp: 50, max_hp: 50, block: 0, intents: [{ type: "Attack", damage: 6, hits: 1 }] }],
    } as Partial<CombatState>);
    const next = step(1, {
      round: 2,
      player: { name: "Ironclad", hp: 74, max_hp: 80, block: 0 },
      enemies: [{ index: 0, name: "TwoMover", hp: 50, max_hp: 50, block: 0, intents: [{ type: "Attack", damage: 12, hits: 3 }] }],
    } as Partial<CombatState>);

    const { rerender } = render(
      <SoundFixture on map={MAP} emit={emit}>
        <Board step={prev} onPlayCard={() => {}} onEndTurn={() => {}} />
      </SoundFixture>,
    );
    act(() => {
      rerender(
        <SoundFixture on map={MAP} emit={emit}>
          <Board step={next} onPlayCard={() => {}} onEndTurn={() => {}} />
        </SoundFixture>,
      );
    });

    expect(emit).toHaveBeenCalledTimes(1);
    const events = (emit.mock.calls[0][0] as { event: string }[]).map((c) => c.event);
    expect(events).toContain("event:/sfx/e/jab");
    expect(events).not.toContain("event:/sfx/e/heavy");
  });

  it("does not re-run the diff effect when only the sound context value changes (e.g. a volume tick)", () => {
    // Defect (iii): `sound` must NOT be in the diff effect's dependency
    // array. That effect's body also sets damage numbers, flashes, shakes
    // and 1-second timeouts, so if `sound` were a dependency, every volume
    // slider tick (which changes the SoundContext value) would re-run all of
    // that. Proven here by swapping in a brand-new `emit` reference (which is
    // exactly what a volume change does to the memoized context value — see
    // SoundContext's `useMemo` deps) on a re-render that changes NOTHING
    // else (same step object, same mode, same onPlayCard) and asserting the
    // new emit is never called — the effect that would call it did not fire.
    // Mutation caught: adding `sound` to the diff effect's dependency array.
    const calls: unknown[] = [];
    const onPlayCard = () => {};
    const emit1 = vi.fn((cues: unknown) => calls.push(cues));
    const prev = step(0, {
      round: 1,
      player: { name: "Ironclad", hp: 80, max_hp: 80, block: 0 },
      enemies: [{ index: 0, name: "Cultist", hp: 50, max_hp: 50, block: 0, intents: [{ type: "Attack", damage: 6, hits: 1 }] }],
    } as Partial<CombatState>);
    const next = step(1, {
      round: 2,
      player: { name: "Ironclad", hp: 74, max_hp: 80, block: 0 },
      enemies: [{ index: 0, name: "Cultist", hp: 50, max_hp: 50, block: 0, intents: [{ type: "Attack", damage: 6, hits: 1 }] }],
    } as Partial<CombatState>);

    const { rerender } = render(
      <SoundFixture on map={MAP} emit={emit1}>
        <Board step={prev} onPlayCard={onPlayCard} onEndTurn={() => {}} />
      </SoundFixture>,
    );
    act(() => {
      rerender(
        <SoundFixture on map={MAP} emit={emit1}>
          <Board step={next} onPlayCard={onPlayCard} onEndTurn={() => {}} />
        </SoundFixture>,
      );
    });
    expect(calls.length).toBe(1); // the real transition

    // "Volume tick": SAME step object (same `step.idx` and `s` reference),
    // same mode, same onPlayCard — only the SoundContext's `emit` reference
    // changes, exactly what SoundProvider's useMemo does when `volume` ticks.
    const emit2 = vi.fn((cues: unknown) => calls.push(cues));
    act(() => {
      rerender(
        <SoundFixture on map={MAP} emit={emit2}>
          <Board step={next} onPlayCard={onPlayCard} onEndTurn={() => {}} />
        </SoundFixture>,
      );
    });

    expect(emit2).not.toHaveBeenCalled();
    expect(calls.length).toBe(1); // unchanged — the diff effect did not re-run
  });
});
