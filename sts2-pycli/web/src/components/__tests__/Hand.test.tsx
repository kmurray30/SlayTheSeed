import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import Hand from "../Hand";
import { cardBoxHeight } from "../../cardGeometry";
import { HAND_REST_VISIBLE } from "../../handFan";
import type { Card } from "../../api";

const hand = (n: number): Card[] =>
  Array.from({ length: n }, (_, i) => ({
    index: i, id: `CARD.STRIKE_${i}`, name: "Strike", cost: 1,
    type: "Attack", description: "Deal 6 damage.", image: null,
  }));

/** Overlap the fan applied, read back off the slots' inline margin. */
function overlaps(container: HTMLElement): number[] {
  return [...container.querySelectorAll<HTMLElement>(".gs-handslot")]
    .slice(1)
    .map((el) => -parseFloat(el.style.marginLeft));
}

// jsdom has no `.gs-arena-stage` ancestor to measure, so the metrics hook keeps
// its fallbacks — cw 180, availW 900 — which makes these numbers exact.
const CW = 180, AVAIL = 900;

describe("the hand fan", () => {
  it("keeps its natural spread when it fits", () => {
    // 4 cards: overlapF 0.15 → step 153px → fan 639px, well inside the room.
    const { container } = render(<Hand cards={hand(4)} />);
    expect(overlaps(container)).toEqual([27, 27, 27]);
  });

  it("tightens rather than running under the chrome", () => {
    // 10 cards at the natural 0.44 overlap span 180 + 9*100.8 = 1087px, which
    // overruns the room the End Turn plate and the pile plaques leave. The fan
    // is `nowrap` and painted in the same bottom strip as those controls, so
    // the spill used to put a card box on top of them.
    const { container } = render(<Hand cards={hand(10)} />);
    const step = CW - overlaps(container)[0];
    expect(CW + 9 * step).toBeLessThanOrEqual(AVAIL);
    expect(step).toBeLessThan(CW * (1 - 0.44));
  });

  it("never loosens the spread to fill the room", () => {
    // The fit is a ceiling, not a target: a 5-card hand has room to spread
    // further and must not take it.
    const { container } = render(<Hand cards={hand(5)} />);
    expect(overlaps(container)[0]).toBe(Math.round(0.21 * CW));
  });
});

describe("the hand strip's reserved height", () => {
  const strip = (c: HTMLElement) => c.querySelector<HTMLElement>(".gs-hand")!;

  it("holds a card's height open when the hand empties", () => {
    // The strip is the arena's flex sibling and the arena is `flex: 1`, so a
    // strip that collapses on the last card played hands its height to the
    // arena — which bottom-aligns the combatants, dropping the player and the
    // enemies down the screen mid-turn. `--gs-handmin` is what holds it open.
    const { container, rerender } = render(<Hand cards={hand(3)} />);
    const held = strip(container).style.getPropertyValue("--gs-handmin");
    expect(held).toBe(`${cardBoxHeight(CW)}px`);
    rerender(<Hand cards={[]} />);
    expect(strip(container).style.getPropertyValue("--gs-handmin")).toBe(held);
  });

  it("publishes the game's rest line, which is what the crop is measured from", () => {
    // `--gs-handshow` is how much of a resting card the stage's bottom edge
    // leaves visible; game-skin.css turns it into the negative margin that
    // crops the rest away (`--gs-handbleed`). The strip used to crop a flat
    // 36px instead, which put the resting fan over the enemies' power rows.
    // jsdom has no `.gs-stage-scale` to measure, so this is the fallback: the
    // game's own 218.8 scene px.
    const { container } = render(<Hand cards={hand(5)} />);
    expect(strip(container).style.getPropertyValue("--gs-handshow"))
      .toBe(`${Math.round(HAND_REST_VISIBLE)}px`);
  });

  it("reserves exactly the card box it draws, at every hand size", () => {
    for (const n of [1, 5, 10]) {
      const { container } = render(<Hand cards={hand(n)} />);
      // Same number CardFrame renders at, so the reserve can't drift from the
      // fan it is reserving for.
      expect(strip(container).style.getPropertyValue("--gs-handmin"))
        .toBe(`${cardBoxHeight(CW)}px`);
    }
  });
});
