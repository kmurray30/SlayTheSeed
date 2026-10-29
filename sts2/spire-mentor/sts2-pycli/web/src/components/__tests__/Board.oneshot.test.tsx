import { render, act } from "@testing-library/react";
import { useEffect, useRef } from "react";
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import Board from "../Board";
import type { Step } from "../../api";
import type { Clock } from "../../effects/scheduler";
import { TOTAL_SETTLE_MS } from "../../effects/timing";

/** Every one-shot clip a rig was actually asked to PLAY, in order.
 *
 *  Asserting on the `actionNonce` PROP is not enough for what is under test
 *  here: the bug is a prop that changes BACK to a value it already held, and a
 *  snapshot of the prop after the fact cannot tell that apart from "nothing
 *  happened". Only the sequence of plays can. */
const { shots } = vi.hoisted(() => ({ shots: [] as { who: string; clip?: string }[] }));

// Stand-in for the WebGL/Spine renderer (no GL context in jsdom), reproducing
// the one rule this file depends on: `SpineSprite` fires its one-shot whenever
// `actionNonce` CHANGES — see its `prevNonce` guard — with no notion of the
// value being older than the one before it.
vi.mock("../SpineSprite", () => {
  function SpineSpriteMock({ alt, actionAnim, actionNonce }: {
    alt?: string; actionAnim?: string; actionNonce?: number;
  }) {
    const prev = useRef<number | undefined>(undefined);
    useEffect(() => {
      const isNew = prev.current !== actionNonce;
      prev.current = actionNonce;
      if (!isNew || actionNonce == null) return;
      shots.push({ who: alt ?? "?", clip: actionAnim });
    }, [alt, actionAnim, actionNonce]);
    return <div data-testid="spine-mock" />;
  }
  return { default: SpineSpriteMock };
});

/** A hand-driven clock: the scheduler advances only when the test says so. */
function fakeClock() {
  let t = 0;
  let cbs: (() => void)[] = [];
  const clock: Clock = {
    now: () => t,
    frame: (cb) => {
      cbs.push(cb);
      return () => { cbs = cbs.filter((c) => c !== cb); };
    },
  };
  return {
    clock,
    advance(ms: number) {
      t += ms;
      const due = cbs;
      cbs = [];
      due.forEach((c) => c());
    },
  };
}

/** A live combat step with a rig on BOTH sides, so `Combatant` mounts the
 *  (mocked) rig renderer for the player as well as the enemy rather than
 *  falling back to a plain `<img>`. */
function combat(opts: { round: number; playerHp: number; enemyHp: number; intent: string }): Step {
  return {
    idx: 0, decision: "combat_play", scores: [], pv: null, chosen_action: null,
    source: "live", meta: { turn: opts.round, step_no: 0 },
    state: {
      decision: "combat_play", round: opts.round, energy: 3, max_energy: 3,
      draw_pile_count: 5, discard_pile_count: 0, exhaust_pile_count: 0,
      player: { name: "Ironclad", hp: opts.playerHp, max_hp: 80, block: 0, rig: "characters/ironclad" },
      enemies: [{
        index: 0, uid: 11, name: "Twig Slime", hp: opts.enemyHp, max_hp: 50, block: 0,
        image: null, rig: "monsters/twig_slime", intents: [{ type: opts.intent, damage: 7 }],
      }],
      hand: [],
    },
  } as unknown as Step;
}

const clipsFor = (who: string) => shots.filter((s) => s.who === who).map((s) => s.clip);

/** Comfortably past the last scheduled `setFx({})`, whose delay scales with the
 *  event count (`TOTAL_SETTLE_MS + HIT_SPACING_MS * events.length`). */
const HIT_SETTLE_SLACK_MS = 2000;

beforeEach(() => {
  shots.length = 0;
  vi.useFakeTimers({ shouldAdvanceTime: true });
});
afterEach(() => vi.useRealTimers());

describe("rig one-shot clips", () => {
  it("does not swing again when the enemy-turn flinch settles", () => {
    // The reported bug: play a Strike (the Ironclad lunges — correct), press
    // End Turn, and the Ironclad lunged a SECOND time as the enemy's hit
    // finished.
    //
    // `attack` is a pulse App stores as durable state — `liveAttack` is set on
    // every targeted play and never cleared — so when the hurt flag dropped off
    // `fx`, the player's `actionNonce` fell back to that still-present attack
    // nonce. A different value than the frame before is all `SpineSprite` needs
    // to replay the lunge.
    const { clock, advance } = fakeClock();
    const onPlayCard = () => {};
    const props = { onPlayCard, onEndTurn: () => {}, clock };

    // Combat opens: nothing has been played, so no lunge is armed.
    const { rerender } = render(
      <Board step={combat({ round: 1, playerHp: 80, enemyHp: 50, intent: "Attack" })} {...props} />,
    );
    expect(clipsFor("Ironclad")).toEqual([]);

    // Strike, at enemy 0. App bumps `attack` and the Ironclad lunges.
    const attack = { target: 0, nonce: 1000 };
    act(() => {
      rerender(
        <Board step={combat({ round: 1, playerHp: 80, enemyHp: 42, intent: "Attack" })}
          attack={attack} {...props} />,
      );
    });
    act(() => { advance(2000); });
    expect(clipsFor("Ironclad")).toEqual(["attack"]);

    // End Turn: the enemy strikes for 7. `attack` is UNCHANGED — the player
    // has played nothing since — and the player flinches.
    act(() => {
      rerender(
        <Board step={combat({ round: 2, playerHp: 73, enemyHp: 42, intent: "StatusCard" })}
          attack={attack} {...props} />,
      );
    });
    act(() => { advance(2000); });
    expect(clipsFor("Ironclad")).toEqual(["attack", "hurt"]);

    // ...and once the flinch settles, that is the end of it. The Ironclad
    // stands there; it does not throw a phantom punch at an enemy whose turn
    // it now is.
    act(() => { vi.advanceTimersByTime(TOTAL_SETTLE_MS + HIT_SETTLE_SLACK_MS); });
    expect(clipsFor("Ironclad")).toEqual(["attack", "hurt"]);

    // The enemy is the same bug seen from the other side: its slot falls back
    // to the same stale `attack` nonce, so it flinched a third time as the
    // board settled. (The two flinches it DOES get are the optimistic cue the
    // `attack` pulse fires at click time and the hit the state diff then
    // produces — that pairing predates this fix and is left alone.)
    expect(clipsFor("Twig Slime")).toEqual(["hurt", "hurt", "attack"]);
  });

  it("arms nothing when a fight opens on a pulse left over from the last one", () => {
    // `liveAttack` outlives the combat it was set in — `Board` unmounts at the
    // end of a fight and the next one mounts with the old nonce still in the
    // prop. A rig that has just appeared did not witness that swing.
    const { clock } = fakeClock();
    render(
      <Board step={combat({ round: 1, playerHp: 80, enemyHp: 50, intent: "Attack" })}
        attack={{ target: 0, nonce: 1000 }}
        onPlayCard={() => {}} onEndTurn={() => {}} clock={clock} />,
    );
    expect(shots).toEqual([]);
  });
});
