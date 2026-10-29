import { describe, it, expect } from "vitest";
import {
  GROUND_Y, HUD_BAND_H, ROOM_CANVAS_SCALE, ROOM_CANVAS_H, ROOM_CANVAS_W, SCENE_CX, SCENE_H,
  aspectFit, boundsWidth, cameraFor, fitScene, placeEnemies, placeOsty, placePlayer,
  renderedSize, roomCanvasScale,
} from "../../scene";
import { RIG_BOUNDS } from "../../rigBounds";

/**
 * The combat scene is the GAME's, ported from `NCombatRoom` — so these are
 * assertions about the decomp, not about a look we chose. Where a number can be
 * checked against a real screenshot it is
 * (docs/experiments/2026-08-22-workbench-visual-gauntlet/refs/game-combat-1.png,
 * a 1922x1083 capture of v0.107.1, i.e. the scene at 1:1).
 */
const RIG = (slug: string) => `/assets/monster_rigs/${slug}/${slug}`;

describe("combat scene geometry", () => {
  it("puts the ground line 200px under the scene centre", () => {
    // `Position = new Vector2(..., 200f - …)` in an enemy container parked at
    // `SceneContainer.Size * 0.5f`. In the reference capture the Ironclad's feet
    // land at y≈738 and its HP bar at y≈757.
    expect(GROUND_Y).toBe(SCENE_H / 2 + 200);
  });

  it("stands a lone fighter on each side of the centre, 480px out", () => {
    // With one creature and no clamp, `val = (960 - w) / 2` and the creature's
    // centre is `val + w/2` — 480 for ANY width under 660. The reference has the
    // Ironclad's body centred on x≈480 and the Fuzzy Wurm Crawler on x≈1440.
    for (const w of [200, 400, 600]) {
      expect(placeEnemies([w], 1)[0].x).toBeCloseTo(480, 6);
      expect(placePlayer(w, 1, false).x).toBeCloseTo(-480, 6);
    }
  });

  it("clamps the first creature to 150px off the centre line", () => {
    // `Math.Max(val, 150f)` — a very wide creature is pushed out, not centred.
    expect(placeEnemies([900], 1)[0].x).toBeCloseTo(150 + 450, 6);
  });

  it("spaces a row by 70px and lifts every second one when it gets tight", () => {
    const two = placeEnemies([300, 300], 1);
    expect(two[1].x - two[0].x).toBeCloseTo(370, 6); // 300 + the 70px gap
    expect(two[0].y).toBe(GROUND_Y);
    expect(two[1].y).toBe(GROUND_Y); // still roomy: no stagger

    // Four wide bodies do not fit: the gap collapses and the odd slots rise.
    const tight = placeEnemies([260, 260, 260, 260], 1);
    expect(tight[1].y).toBeLessThan(GROUND_Y);
    expect(tight[0].y).toBe(GROUND_Y);
  });

  it("gives the camera back the room it zooms out of", () => {
    // `num = 960f / scaling`: at 0.87 the enemy half is 1103 scene px wide, so
    // the same creature stands further out — and lands in the same place on
    // screen once the camera scales it back.
    const at1 = placeEnemies([400], 1)[0].x;
    const at087 = placeEnemies([400], 0.87)[0].x;
    expect(at087).toBeGreaterThan(at1);
    expect(at087 * 0.87).toBeCloseTo(at1, 6);
  });

  it("centres the player when the encounter says so", () => {
    // `KaiserCrabBoss.FullyCenterPlayers` — you are surrounded, not opposite.
    expect(placePlayer(400, 0.75, true).x).toBe(0);
    expect(placePlayer(400, 0.75, false).x).toBeLessThan(0);
  });

  it("hangs Osty off the player's right, 75px up", () => {
    const player = placePlayer(400, 1, false);
    const near = placeOsty(player, 400, 0);   // 150px reach at 0 max HP
    const far = placeOsty(player, 400, 150);  // 250px at 150+
    expect(near.y).toBe(GROUND_Y - 75);
    expect(far.x - near.x).toBeCloseTo(100, 6);
    expect(near.x).toBeGreaterThan(player.x);
  });
});

describe("creature sizing", () => {
  it("draws a rig at its authored scene scale, unmodified", () => {
    // The Ironclad's rig reaches 1209 units above its root at 0.28 px/unit —
    // 339 scene px, and in the reference capture its raised sword tip sits
    // ~345px above its feet.
    const box = RIG_BOUNDS.ironclad;
    const size = renderedSize({ rig: RIG("ironclad"), rigScale: 0.28 })!;
    expect(size.h).toBeCloseTo(box.h * 0.28, 6);
    expect(size.top).toBeCloseTo((box.y + box.h) * 0.28, 6);
    expect(size.top).toBeGreaterThan(330);
    expect(size.top).toBeLessThan(350);
  });

  it("makes the Kaiser Crab wider than the scene itself", () => {
    // 5479 rig units at 0.5 = 2740 scene px against a 1920px scene, and the
    // camera only pulls back to 0.75 — so a claw hangs off each edge. Anything
    // that "fits" it into the arena has lost the fight's whole framing.
    const size = renderedSize({ rig: RIG("kaiser_crab"), rigScale: 0.5 })!;
    expect(size.w).toBeGreaterThan(SCENE_CX * 2);
    expect(size.w * cameraFor([RIG("kaiser_crab")]).scale).toBeGreaterThan(SCENE_CX * 2);
  });

  it("reads a negative authored scale as a mirror, not a shrink", () => {
    // The Decimillipede segments are authored at -0.45 (the game renders them
    // with a negative X scale); the magnitude is still the size.
    const size = renderedSize({ rig: RIG("decimillipede_segment_back"), rigScale: -0.45 })!;
    expect(size.h).toBeCloseTo(RIG_BOUNDS.decimillipede_segment_back.h * 0.45, 6);
  });

  it("derives a footprint from the width the rig is DRAWN at, not its box height", () => {
    const box = RIG_BOUNDS.ironclad;
    // The margin rides on the drawn width. `bounds_h` is the other EDGE of the
    // same box (278 here against 351 px of drawn Ironclad) and says nothing
    // about x — deriving x from it made every footprint ~1.75x too wide, back
    // when the shipped `bounds_h` was itself ~2x the authored box (GH #611).
    expect(boundsWidth({ rig: RIG("ironclad"), rigScale: 0.28, boundsH: 278 }))
      .toBeCloseTo(box.w * 0.28 * 1.2, 6);
    // No authored scale to draw at: nothing but the box height is left.
    expect(boundsWidth({ rig: RIG("kaiser_crab"), boundsH: 400 }))
      .toBeCloseTo(400 * (RIG_BOUNDS.kaiser_crab.w / RIG_BOUNDS.kaiser_crab.h), 6);
  });

  it("spaces the row by the AUTHORED Bounds width when the game gives one", () => {
    // `NCombatRoom.PositionEnemies` spaces by `Visuals.Bounds.Size.X`, which is
    // now extracted (`bounds_w` in creature_scales.json) instead of estimated.
    // The authored number wins outright — the rig and its margin are only the
    // ladder for a combatant the manifest has no box for.
    expect(boundsWidth({
      rig: RIG("ironclad"), rigScale: 0.28, boundsH: 278, boundsW: 242,
    })).toBe(242);
    expect(boundsWidth({ boundsW: 356 })).toBe(356);
    // A zero or missing width is not an answer; fall back rather than collapse
    // the row onto one point.
    expect(boundsWidth({ rig: RIG("ironclad"), rigScale: 0.28, boundsW: 0 }))
      .toBeGreaterThan(0);
  });

  it("puts a two-enemy row where a game frame of that fight puts it", () => {
    // Glory, Living Shield + Turret Operator, camera 1. Measured off a capture
    // of that fight: the pair stands at x 0.675 and 0.89 of the 1920 scene,
    // i.e. +350 and +780 from its centre line. This is the case the old
    // footprint estimate blew up — it made the row 1267 px wide, over the
    // 960-150 the algorithm has, so the row took the collapse branch and walked
    // left into the player.
    // Widths are the boxes the PCK authors for those two creatures.
    const widths = [
      boundsWidth({ rig: RIG("living_shield"), rigScale: 0.15, boundsH: 400, boundsW: 356 }),
      boundsWidth({ rig: RIG("turret_operator"), rigScale: 0.15, boundsH: 220, boundsW: 280 }),
    ];
    const [a, b] = placeEnemies(widths, 1);
    expect(a.x).toBeGreaterThan(300); expect(a.x).toBeLessThan(370);
    expect(b.x).toBeGreaterThan(690); expect(b.x).toBeLessThan(800);
    // and both stand ON the ground line — no stagger, because the row fits.
    expect(a.y).toBe(GROUND_Y); expect(b.y).toBe(GROUND_Y);
  });

  it("keeps a crowded row clear of the player", () => {
    // Hive: two Bowlbugs and a Slumbering Beetle, camera 0.85 — the fight where
    // a Bowlbug was drawn standing ON the Ironclad, because the Beetle alone was
    // being spaced as 1560 scene px of monster.
    const widths = [
      boundsWidth({ rig: RIG("bowlbug"), rigScale: 0.275 }),
      boundsWidth({ rig: RIG("bowlbug"), rigScale: 0.275 }),
      boundsWidth({ rig: RIG("slumbering_beetle"), rigScale: 0.3, boundsH: 368 }),
    ];
    const first = placeEnemies(widths, 0.85)[0];
    const player = placePlayer(
      boundsWidth({ rig: RIG("ironclad"), rigScale: 0.28, boundsH: 278 }), 0.85, false);
    expect(first.x - player.x).toBeGreaterThan(400);
  });
});

describe("fitting the scene to a stage", () => {
  it("maps the scene below the HUD band onto the stage at the design size", () => {
    const fit = fitScene(1920, 1002);
    expect(fit.k).toBeCloseTo(1, 6);
    expect(fit.y(HUD_BAND_H)).toBeCloseTo(0, 6);
    expect(fit.y(SCENE_H)).toBeCloseTo(1002, 6);
    expect(fit.x(0)).toBeCloseTo(960, 6);
  });

  it("shows MORE room on a wide stage rather than shrinking the fight", () => {
    // The game's viewport does the same: a wider window reveals more scene at
    // the same vertical scale.
    const wide = fitScene(2400, 1002);
    expect(wide.k).toBeCloseTo(1, 6);
    expect(wide.visibleHalfW).toBeGreaterThan(SCENE_CX);
    expect(wide.viewH).toBeCloseTo(SCENE_H - HUD_BAND_H, 6);
  });

  it("NEVER crops the scene's width — a narrow stage reveals height instead", () => {
    // Godot's `canvas_items` + `expand` scales by the SMALLER axis ratio, so a
    // stage narrower than the game's frame keeps all 1920 px of elbow room and
    // opens up vertically. Fitting by height alone (which this used to do)
    // sliced the outer fighters — and both of the Kaiser Crab's claws — off the
    // sides of anything narrower than 16:9.
    const narrow = fitScene(960, 1002);
    expect(narrow.k).toBeCloseTo(0.5, 6);
    expect(narrow.visibleHalfW).toBeCloseTo(SCENE_CX, 6);
    expect(narrow.viewH).toBeGreaterThan(SCENE_H - HUD_BAND_H);
    // The extra rows open symmetrically about the band the stage stands in for.
    const centre = (HUD_BAND_H + SCENE_H) / 2;
    expect(narrow.viewTop + narrow.viewH / 2).toBeCloseTo(centre, 6);
  });

  it("keeps the room art covering the stage at any aspect", () => {
    // 1.44x centred is the game's own framing and covers its 1920x1080 frame
    // with room to spare, so an ordinary stage never moves off it...
    expect(roomCanvasScale(1920, 1002, 501, 1)).toBeCloseTo(ROOM_CANVAS_SCALE, 6);
    // ...but the canvas is a finite painting, and a stage far off that aspect
    // can reach past its edge. A room that stops before the stage does is a
    // black band, not a framing choice.
    const ultrawide = roomCanvasScale(3600, 1002, 501, 1);
    expect(ultrawide).toBeGreaterThan(ROOM_CANVAS_SCALE);
    expect(ROOM_CANVAS_W * ultrawide).toBeGreaterThanOrEqual(3600);
    const tall = roomCanvasScale(1920, 1600, 800, 1);
    expect(ROOM_CANVAS_H * tall).toBeGreaterThanOrEqual(1600);
  });

  it("shrinks a row that would stand off the right edge, and only then", () => {
    // `AdjustCreatureScaleForAspectRatio`.
    const widths = [400, 400];
    const slots = placeEnemies(widths, 1);
    expect(aspectFit(slots, widths, 1, 1200)).toBe(1);
    const squeezed = aspectFit(slots, widths, 1, 500);
    expect(squeezed).toBeGreaterThan(0);
    expect(squeezed).toBeLessThan(1);
  });
});

describe("the room canvas", () => {
  it("draws the 1920x900 painting at 1.44x, so the frame is its middle", () => {
    // Fitted against the reference capture: a scale sweep bottoms out at 1.44
    // with the canvas centred on the scene.
    expect(ROOM_CANVAS_SCALE).toBeCloseTo(1.44, 6);
    expect(1920 * ROOM_CANVAS_SCALE).toBeGreaterThan(1920);
    expect(900 * ROOM_CANVAS_SCALE).toBeGreaterThan(SCENE_H);
  });
});

describe("the encounter camera", () => {
  it("pulls back and centres the player for the Kaiser Crab", () => {
    expect(cameraFor([RIG("kaiser_crab")])).toEqual({ scale: 0.75, offsetY: 35, centerPlayer: true });
  });

  it("pulls back for the Decimillipede elite, whichever segment it sees first", () => {
    for (const part of ["front", "middle", "back"]) {
      expect(cameraFor([RIG(`decimillipede_segment_${part}`)]).scale).toBeCloseTo(0.87, 6);
    }
  });

  it("leaves an ordinary fight alone", () => {
    expect(cameraFor([RIG("nibbit"), RIG("shrinker_beetle")]))
      .toEqual({ scale: 1, offsetY: 0, centerPlayer: false });
    expect(cameraFor([null, undefined]).scale).toBe(1);
  });
});
