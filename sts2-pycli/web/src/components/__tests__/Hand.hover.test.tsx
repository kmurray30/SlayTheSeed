import { fireEvent, render } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import Hand from "../Hand";
import { fanLayout } from "../../handFan";
import type { Card } from "../../api";

const hand = (n: number): Card[] =>
  Array.from({ length: n }, (_, i) => ({
    index: i, id: `CARD.STRIKE_${i}`, name: "Strike", cost: 1,
    type: "Attack", description: "Deal 6 damage.", image: null, can_play: true,
  }));

// jsdom has no `.gs-arena-stage` to measure, so the metrics hook keeps its
// fallbacks — cw 180, availW 900 — which makes these numbers exact.
const CW = 180, AVAIL = 900;
const slot = (container: HTMLElement, i: number) =>
  container.querySelectorAll<HTMLElement>(".gs-handslot")[i];

/**
 * A hovered card in the game's hand rises, straightens and grows, and the rest
 * of the fan steps aside — none of which may change WHICH card the pointer is
 * on. When it does, the hover oscillates: the card moves out from under the
 * cursor, the cursor unhovers it, it drops back under the cursor. A slow
 * sideways mouse move used to flip a card 20+ times over 30px of travel.
 *
 * The fix splits the slot in two — a hit frame that hover never touches, and
 * the card, which is the only thing that moves. These tests pin the invariants
 * that keep it that way; `verify` in the PR notes drives the real browser.
 */
describe("hovering a card in the fan", () => {
  it("never moves the hovered card itself", () => {
    // The one card that must hold its ground: everything else is measured
    // against it, and a hovered card that chased the pointer would be the
    // oscillation all over again.
    for (let n = 1; n <= 10; n++) {
      for (let h = 0; h < n; h++) {
        expect(fanLayout(n, CW, AVAIL, h)[h].dx).toBe(0);
      }
    }
  });

  it("moves every other card AWAY from it, never toward it", () => {
    const slots = fanLayout(7, CW, AVAIL, 3);
    for (let i = 0; i < 7; i++) {
      if (i === 3) continue;
      expect(Math.sign(slots[i].dx)).toBe(Math.sign(i - 3));
    }
  });

  it("falls off with distance and dies four slots out, like the game", () => {
    // NPlayerHand.RefreshLayout: Lerp(100, 0, min(1, |focus - i| / 4)).
    const d = fanLayout(10, CW, AVAIL, 0).map((s) => Math.abs(s.dx));
    expect(d[0]).toBe(0);
    expect(d[1]).toBeGreaterThan(d[2]);
    expect(d[2]).toBeGreaterThan(d[3]);
    expect(d[4]).toBe(0);
    expect(d[9]).toBe(0);
  });

  it("never opens a gap between two slots, at any height on the card", () => {
    // A strip of fan with no card under the pointer is the other way to make
    // the hover flicker: the pointer falls in the hole, the fan un-spreads,
    // and a card slides back under the pointer — which re-opens the hole.
    //
    // Two neighbours overlap by `overlap` at their bottom edge only. They are
    // tilted against each other and pivot about that edge, so they shear apart
    // as you go up the card: at height `y` the one on the right has slid
    // y*sin(rot) across, the one on the left less. The spread has to fit in
    // what is left at the WORST height, which is the top of the card.
    const cardH = CW * (422 / 300);
    for (let n = 2; n <= 10; n++) {
      for (let hov = 0; hov < n; hov++) {
        const slots = fanLayout(n, CW, AVAIL, hov);
        for (let i = 1; i < n; i++) {
          const opened = slots[i].dx - slots[i - 1].dx;   // how much they drift apart
          const rad = (d: number) => (d * Math.PI) / 180;
          const shear = cardH * (Math.sin(rad(slots[i].rot)) - Math.sin(rad(slots[i - 1].rot)));
          expect(opened).toBeLessThanOrEqual(slots[i].overlap - shear + 1e-9);
        }
      }
    }
  });

  it("leaves the resting fan alone when nothing is hovered", () => {
    const rest = fanLayout(6, CW, AVAIL, -1);
    expect(rest.map((s) => s.dx)).toEqual([0, 0, 0, 0, 0, 0]);
    const hovered = fanLayout(6, CW, AVAIL, 2);
    // Only `dx` may react to hover: the arc pose IS the hit frame.
    expect(hovered.map((s) => [s.overlap, s.rot, s.drop]))
      .toEqual(rest.map((s) => [s.overlap, s.rot, s.drop]));
  });
});

describe("the hand's hit frame", () => {
  it("marks exactly the pointed-at slot, and holds still while it does", () => {
    const { container } = render(<Hand cards={hand(5)} onCardClick={() => {}} />);
    const before = [...container.querySelectorAll<HTMLElement>(".gs-handslot-hit")]
      .map((el) => el.getAttribute("style"));

    fireEvent.pointerOver(slot(container, 2));
    const marked = [...container.querySelectorAll(".gs-handslot")]
      .map((el) => el.classList.contains("gs-hovered"));
    expect(marked).toEqual([false, false, true, false, false]);

    // The hitbox carries no styling of its own — its pose comes from the
    // slot's `--fan-*` vars, and hover must not touch those either.
    const after = [...container.querySelectorAll<HTMLElement>(".gs-handslot-hit")]
      .map((el) => el.getAttribute("style"));
    expect(after).toEqual(before);
    // The hovered slot's own transform is the identity: it is the one card in
    // the fan that does not budge sideways.
    expect(slot(container, 2).style.getPropertyValue("--fan-x")).toBe("0.0px");
    expect(slot(container, 1).style.getPropertyValue("--fan-x")).not.toBe("0.0px");

    fireEvent.pointerOut(slot(container, 2));
    expect([...container.querySelectorAll(".gs-handslot")]
      .some((el) => el.classList.contains("gs-hovered"))).toBe(false);
  });

  it("plays the card it covers", () => {
    // The click handler sits on the slot, not on the card: at rest the card is
    // `pointer-events: none` and the hitbox is what the pointer actually hits.
    const onCardClick = vi.fn();
    const { container } = render(<Hand cards={hand(3)} onCardClick={onCardClick} />);
    fireEvent.click(container.querySelectorAll(".gs-handslot-hit")[1]);
    expect(onCardClick).toHaveBeenCalledWith(expect.objectContaining({ index: 1 }));
  });

  it("does not play an unaffordable card", () => {
    const onCardClick = vi.fn();
    const cards = hand(2);
    cards[1] = { ...cards[1], can_play: false };
    const { container } = render(<Hand cards={cards} onCardClick={onCardClick} />);
    fireEvent.click(container.querySelectorAll(".gs-handslot-hit")[1]);
    expect(onCardClick).not.toHaveBeenCalled();
  });

  it("follows the card, not the slot, when a card is played out of the hand", () => {
    // Hover is held by CARD index: a stale fan POSITION would spread the fan
    // around whichever card slid into that slot.
    const { container, rerender } = render(<Hand cards={hand(3)} onCardClick={() => {}} />);
    fireEvent.pointerOver(slot(container, 2));
    rerender(<Hand cards={[hand(3)[0], hand(3)[2]]} onCardClick={() => {}} />);
    const marked = [...container.querySelectorAll(".gs-handslot")]
      .map((el) => el.classList.contains("gs-hovered"));
    expect(marked).toEqual([false, true]);
  });
});
