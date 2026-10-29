/** How much to shrink the combat scene so it fits the stage we actually got.
 *
 *  The whole combat screen is authored at ONE size: the game's scene
 *  (`SCENE_W` x `SCENE_VIEW_H` — see scene.ts) for the room, the fighters and
 *  where they stand, and design px for the furniture that stands on top of it
 *  (the hand's 253px cards, the 102px energy orb, the 188px End Turn plate, the
 *  48px intent chips). `.gs-stage-scale` lays that composition out at
 *  `stage / zoom` and scales it back down by `zoom`, so the stage is always the
 *  same picture at a different size.
 *
 *  The zoom is therefore the scene's own fit, not a separate knob: it is the
 *  smaller of the two axis ratios against the design size, which is what
 *  Godot's `canvas_items` + `expand` stretch does with the real game's window.
 *  Deriving it from the HEIGHT alone (which this used to do) is what let the
 *  hand strip eat the fighters on anything narrower than 16:9 — at 1366x768 the
 *  scene had shrunk to 71% while the cards were still laid out at 100%, so the
 *  fan reached a third of the way up the room and cut the player's HP bar in
 *  half.
 *
 *  ---- Compact framing ----
 *
 *  A pure fit is right for a monitor and wrong for a tablet. At 1003x627 CSS px
 *  (an 8.8" tablet in landscape) the chrome leaves a ~1000x500 stage, and a
 *  fit zoom of 0.5 draws a card 106px tall and the Ironclad 150px — the game's
 *  composition, at a size you cannot read across a table. The real game has
 *  the same problem on that screen; a workbench does not have to.
 *
 *  So below a certain fit zoom the composition is FRAMED rather than fitted:
 *  laid out for a frame `framing`x smaller than the design one and cropped to
 *  it. Everything grows by that factor — the cards, the orb, the plate, and
 *  (through `fitScene`, which takes the same number) the fighters and the
 *  room, which loses its top and its sides, the part of the painting nothing
 *  stands in. The factor ramps continuously with the fit zoom so a window
 *  dragged across the threshold never jumps, and a desktop window, whose fit
 *  zoom is well above `FRAMING_NONE_AT`, is untouched.
 */
import { SCENE_W, SCENE_VIEW_H } from "./scene";

/** Stage size the scene composes at 1:1 — the game's own frame, less the HUD
 *  band the workbench draws above the stage instead of over it. */
export const DESIGN_STAGE_W = SCENE_W;
export const DESIGN_STAGE_H = SCENE_VIEW_H;

/** Floor on the zoom. Past this the card text stops being readable, and a
 *  stage this small means the chrome around the board is the real problem
 *  (see the compact-chrome rules in responsive.css). */
export const MIN_STAGE_ZOOM = 0.42;

/** Quantisation step. A resize drag would otherwise push a new zoom — and so a
 *  new layout of every card and rig — on every single pixel. */
const STEP = 0.02;

/** The most the composition is ever enlarged past its fit. 1.35 is where the
 *  Ironclad's 470px rig still clears the top of a 500px stage with the ground
 *  line where `fitScene` keeps it; past that the tallest ordinary rigs lose
 *  their heads. It is the cap for the CARDS and the furniture; the scene
 *  itself is pulled back per fight, as far as its tallest enemy's intent chip
 *  needs (`scene.sceneFramingFor`) — the Bygone Effigy's crown is a hundred
 *  scene px above the Ironclad's and lost its chip at this cap. */
export const MAX_FRAMING = 1.35;
/** The cap on a PORTRAIT stage, where the width binds the fit and the height
 *  has room to spare: nothing is cropped vertically at all, and sideways the
 *  row is laid out for the width the stage shows (`scene.rowHalfWidths`), so
 *  the fighters close up rather than fall off the edges. 2.2 is where a
 *  phone's cards (412 px wide) come out ~100 px and a tablet's the same
 *  ~140 px they are in landscape. Blended in over `PORTRAIT_BLEND` of aspect
 *  so a window dragged through square does not jump. */
export const MAX_FRAMING_PORTRAIT = 2.2;
const PORTRAIT_BLEND = 0.3;

/** The framing cap for a stage's aspect: `MAX_FRAMING` at 16:9 and anything
 *  wider, rising to `MAX_FRAMING_PORTRAIT` as the stage goes past square. */
function framingCap(stageW: number, stageH: number): number {
  const t = Math.min(1, Math.max(0, (1 - stageW / stageH) / PORTRAIT_BLEND));
  return MAX_FRAMING + (MAX_FRAMING_PORTRAIT - MAX_FRAMING) * t;
}
/** Fit zoom at or above which nothing is cropped — a 1366x768 laptop's stage
 *  fits at ~0.7, and a maximised 1080p window at 1. */
const FRAMING_NONE_AT = 0.75;
/** Fit zoom at or below which the full `MAX_FRAMING` applies — a tablet in
 *  landscape, with the chrome already compacted, fits at ~0.5. */
const FRAMING_FULL_AT = 0.45;

/** The raw fit: the smaller axis ratio against the design frame, capped at 1. */
function fitZoom(stageW: number, stageH: number): number {
  return Math.min(1, stageW / DESIGN_STAGE_W, stageH / DESIGN_STAGE_H);
}

/** How much larger than its fit the composition is drawn on a `stageW` x
 *  `stageH` stage: 1 on anything a monitor gives, up to `MAX_FRAMING` on a
 *  tablet. Rounded to 2 decimals so a resize drag does not push a fresh scene
 *  fit for every pixel. */
export function stageFraming(stageW: number, stageH: number): number {
  if (!(stageH > 0) || !(stageW > 0)) return 1;
  const raw = fitZoom(stageW, stageH);
  const t = Math.min(1, Math.max(0, (FRAMING_NONE_AT - raw) / (FRAMING_NONE_AT - FRAMING_FULL_AT)));
  return Math.round((1 + (framingCap(stageW, stageH) - 1) * t) * 100) / 100;
}

/** The scale factor for a `stageW` x `stageH` stage.
 *
 *  Never above 1: a stage with room to spare lays the scene out at the size it
 *  actually has and draws it there, rather than building it small and blowing
 *  it up past the art's resolution — a 4K window renders the rigs at 4K, it
 *  does not upscale a 1080p picture. (The scene still fills that stage; see
 *  `fitScene`, which is what maps scene px onto the layout box.)
 *
 *  `framing` defaults to `stageFraming` of the same box; pass 1 for the pure
 *  fit. */
export function stageZoom(stageW: number, stageH: number, framing: number = stageFraming(stageW, stageH)): number {
  if (!(stageH > 0) || !(stageW > 0)) return 1;
  const raw = Math.min(1, fitZoom(stageW, stageH) * framing);
  // FLOOR, not round: quantising upward would hand back a zoom the box cannot
  // actually hold, and the composition would oversail the stage it was just
  // fitted to. Divided THROUGH the step count rather than multiplied back by
  // it, so the result is an exact 2-decimal number and not 0.6000000000000001.
  const quantised = Math.floor(raw / STEP) / (1 / STEP);
  return Math.max(MIN_STAGE_ZOOM, quantised);
}
