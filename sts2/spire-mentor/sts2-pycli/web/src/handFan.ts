import { CARD_H, CARD_W } from "./cardGeometry";

/** The fan the held hand is laid out as: where each card sits at rest, and how
 *  the fan reacts when one of them is hovered.
 *
 *  This lives outside `Hand.tsx` because the reaction to hover carries
 *  invariants that are worth testing on their own — get them wrong and the
 *  hover OSCILLATES, which is a bug you can only see by driving a real
 *  pointer. See `Hand.hover.test.tsx`. */

/** card.tscn's card box is 300x422, so a card's height is 1.407x its width. */
export const CARD_ASPECT = CARD_H / CARD_W;

/** How far the cards either side of the hovered one slide out of its way, and
 *  how many slots along the fan that reaches.
 *
 *  Straight out of the game: `NPlayerHand.RefreshLayout` moves every holder
 *  that is not the focused one AWAY from it by
 *      Lerp(100, 0, min(1, |focus - i| / 4))
 *  units, against a 300-unit card. So the immediate neighbour steps a third of
 *  a card width aside at most, and the fourth one out does not move at all.
 *  The focused card itself never moves sideways — which is also what keeps the
 *  hover stable (see `.gs-handslot-hit`). */
const SPREAD_F = 100 / CARD_W;
const SPREAD_REACH = 4;

/** Static fan parameters for an n-card hand: per-slot rotation step, arc drop
 *  and overlap. Bigger hands overlap/curve more so the fan stays compact —
 *  mirroring the game's hand, but as pure layout (no motion dependence).
 *
 *  Overlap and arc drop are FRACTIONS of the card's width, not pixels, so the
 *  fan keeps its shape at every card size. The ref's 5-card hand steps ~180px
 *  between 215px cards (≈0.19 overlap), tilts the outermost card ~9°, and
 *  drops it ~60px below the middle one (≈0.28 of a card width). */
function fanParams(n: number) {
  if (n <= 4) return { overlapF: 0.15, rotStep: 5.0, liftF: 0.058 };
  if (n <= 6) return { overlapF: 0.21, rotStep: 4.5, liftF: 0.050 };
  if (n <= 8) return { overlapF: 0.33, rotStep: 3.6, liftF: 0.034 };
  return { overlapF: 0.44, rotStep: 2.8, liftF: 0.024 };
}

/** Where one slot sits in the fan. `overlap` is the negative left margin that
 *  packs the slot against the one before it (0 for the first); `dx` is the
 *  hover spread, `rot`/`drop` the resting arc pose. */
export type FanSlot = { overlap: number; dx: number; rot: number; drop: number };

/** Lay the whole fan out. `hover` is the POSITION (not the card index) of the
 *  hovered card, or -1 when nothing is hovered.
 *
 *  Only `dx` depends on `hover`, and only for the OTHER slots: the hovered
 *  card holds its ground while its neighbours step aside. That asymmetry is
 *  load-bearing, not cosmetic — a fan that can move a card TOWARD the pointer
 *  can flicker. */
export function fanLayout(n: number, cw: number, availW: number, hover: number): FanSlot[] {
  const { overlapF, rotStep, liftF } = fanParams(n);
  const mid = (n - 1) / 2;
  // The fan is `cw + (n - 1) * step` wide and `.gs-hand` is `nowrap`, so a big
  // hand at a narrow viewport spills past the stage. It is painted ABOVE the
  // End Turn plate and the pile plaques, which sit in the same bottom strip, so
  // the spill did not just look wrong — the outermost card box swallowed their
  // clicks. Tighten the step (never loosen it) until the fan fits the room the
  // chrome leaves; the floor keeps a huge hand from collapsing into one pile,
  // and the plates now out-rank the hand so a click still lands if it is hit.
  const step = Math.min(
    cw * (1 - overlapF),
    Math.max(cw * 0.18, (availW - cw) / Math.max(1, n - 1)),
  );
  const overlap = Math.round(cw - step);
  // Clamp the spread so it can never open a GAP between two slots: the widest
  // single step is the hovered card's neighbour, at 1 - 1/REACH of the
  // magnitude, and it has to stay inside the room those two slots overlap by.
  // A gap is a strip of fan with no card under the pointer — and a card that
  // un-spreads back INTO the pointer the moment the pointer falls in the gap
  // is exactly the flicker the hitbox split exists to kill.
  //
  // The room is less than the overlap, because the two slots are tilted
  // against each other: a slot pivots about its bottom, so `rotStep` apart they
  // shear sideways by up to a card height's worth of tangent by the time you
  // reach the top of the card. Measured: an n=5 fan overlaps 44px but only
  // 30px of that survives at the height of the card's art, and spreading the
  // full 44 opened a hole there.
  const shear = cw * CARD_ASPECT * Math.tan((rotStep * Math.PI) / 180);
  const spread = Math.min(cw * SPREAD_F, Math.max(0, overlap - shear) / (1 - 1 / SPREAD_REACH));
  return Array.from({ length: n }, (_, i) => {
    const off = i - mid;
    const away = hover < 0 ? 0 : Math.sign(i - hover);
    return {
      overlap: i === 0 ? 0 : overlap,
      dx: away * spread * (1 - Math.min(1, Math.abs(i - hover) / SPREAD_REACH)),
      rot: Math.max(-14, Math.min(14, off * rotStep)),
      drop: Math.min(cw * 0.3, off * off * liftF * cw),
    };
  });
}

/** How much of a RESTING card the game leaves above the screen's bottom edge,
 *  in SCENE px (see src/scene.ts for the coordinate system).
 *
 *  The held hand is UI anchored to the viewport's bottom edge, not scene
 *  content, and `NPlayerHand.RefreshLayout` lays every holder out against a
 *  container whose origin sits on that edge: at `HandPosHelper._baseScale`
 *  (0.8) of the 300x422 `card.tscn` box, with the middle card's centre
 *  `_offsetY = -50` above the origin. So a resting card shows
 *
 *      422 * 0.8 / 2 + 50 = 218.8
 *
 *  scene px of itself and hangs the other ~119 below the edge — the same crop
 *  `RefreshLayout` undoes for the FOCUSED holder, which it parks at
 *  `-Hitbox.Size.Y * 0.5 + 2`, flush with that edge and scaled back to 1.0.
 *  (The `+2` is why the two readings of Godot's pivot differ by 2px here;
 *  either way the rest line is 217-221 scene px above the edge.)
 *
 *  This is the number the workbench was missing, and the answer to "why does
 *  the real game not have this problem": the strip used to crop a flat 36px
 *  off cards of any size, so the resting fan reached ~270 scene px up the
 *  screen — over the row where every creature's power icons sit (`.gs-powers`,
 *  which hangs under the health bar and the nameplate). The game's own crop is
 *  ~50 scene px deeper than that, and that is the whole clearance. */
export const HAND_REST_VISIBLE = (CARD_H * 0.8) / 2 + 50;

/** Floor on the visible band, as a fraction of the card box.
 *
 *  `HAND_REST_VISIBLE` is an absolute scene-px band, so a stage whose cards are
 *  larger (relative to the scene) than the game's would crop them further than
 *  the game ever does — a narrow, tall window fits the scene by WIDTH, which
 *  shrinks the scene without shrinking the design-px card. The game shows 65%
 *  of its own card box at rest; never show less than this. */
export const HAND_REST_MIN_F = 0.55;
