import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import Board from "../Board";
import {
  CANVAS_H,
  CANVAS_W,
  DEFAULT_SCENE,
  atmosphereVars,
  pickScene,
  resolveScene,
  roomLayerStyle,
  sceneAtmosphere,
  sceneScale,
  type RoomLayer,
} from "../roomScenes";
import { ROOM_MANIFEST } from "../roomScenes.data";
import { ROOM_CANVAS_H, ROOM_CANVAS_W } from "../../scene";
import type { Enemy, Step } from "../../api";

/** An enemy the way the backend sends one: the art slug rides on `rig`/`image`,
 *  alias table already applied (Crusher -> kaiser_crab, and so on). */
function enemy(name: string, slug: string): Enemy {
  return {
    index: 0, name, hp: 100, max_hp: 100, block: 0,
    image: `monsters/${slug}.png`, rig: `monster_rigs/${slug}/${slug}`,
  };
}

/** A minimal combat step: the act name and the roster are what pick the room. */
function step(actName: string, enemies: Enemy[] = [enemy("Toadpole", "toadpole")],
              floor = 3, act = 1): Step {
  return {
    idx: 0, decision: "combat_play", scores: [], pv: null, chosen_action: null,
    source: "live", meta: { turn: 1, step_no: 0, act_name: actName, act, floor },
    state: {
      decision: "combat_play", round: 1, energy: 3, max_energy: 3,
      draw_pile_count: 0, discard_pile_count: 0, exhaust_pile_count: 0,
      draw_pile: [], discard_pile: [], exhaust_pile: [],
      player: { name: "Ironclad", hp: 64, max_hp: 80, block: 0 },
      enemies,
      hand: [],
    },
  } as unknown as Step;
}

/** The far stack's art, FRONTMOST FIRST.
 *
 *  Each layer is its own element now, and the DOM holds them back-to-front (the
 *  manifest's own order, which is what a stack of positioned elements paints
 *  correctly). Reversing here keeps every "is drawn on top of" assertion below
 *  reading the same way it did against the old first-is-topmost CSS list. */
function bgImages(testId = "arena-bg"): string {
  const el = screen.getByTestId(testId);
  return [...el.querySelectorAll<HTMLElement>(".gs-room-canvas > div")]
    .map(layerImage)
    .reverse()
    .join(", ");
}

/** The art on one layer element.
 *
 *  An additively blended layer (the fog the rooms drift) paints on a CHILD:
 *  its tint filter and its blend mode cancel each other out when they sit on
 *  one element — see `roomLayerPaintStyle`. */
function layerImage(el: HTMLElement): string {
  return el.style.backgroundImage
    || (el.firstElementChild as HTMLElement | null)?.style.backgroundImage
    || "";
}

/** A layer's axis-aligned box on the canvas — its rect turned by `t`, which is
 *  NOT the same as (`left`, `top`, `w`, `h`): a half-turn puts the art on the
 *  far side of its own origin. */
function bbox(l: RoomLayer): { left: number; top: number; right: number; bottom: number } {
  const [a, b, c, d] = l.t ?? [1, 0, 0, 1];
  const xs = [0, l.w].flatMap((u) => [0, l.h].map((v) => l.left + a * u + c * v));
  const ys = [0, l.w].flatMap((u) => [0, l.h].map((v) => l.top + b * u + d * v));
  return { left: Math.min(...xs), top: Math.min(...ys),
           right: Math.max(...xs), bottom: Math.max(...ys) };
}

describe("the canvas", () => {
  it("is the same canvas the scene port places", () => {
    // Two owners, one canvas: `scene.ts` sizes the element from the combat-room
    // port, `roomScenes` places each layer on it from the derived manifest.
    // They only agree by construction if these agree.
    expect([CANVAS_W, CANVAS_H]).toEqual([ROOM_CANVAS_W, ROOM_CANVAS_H]);
  });
});

describe("roomLayerStyle", () => {
  it("leaves a full-canvas layer exactly the canvas", () => {
    const st = roomLayerStyle({ src: "x.png", left: 0, top: 0, w: CANVAS_W, h: CANVAS_H });
    expect([st.left, st.top, st.width, st.height])
      .toEqual(["0px", "0px", `${CANVAS_W}px`, `${CANVAS_H}px`]);
    expect(st.transform).toBeUndefined();
  });

  it("draws a short layer at its own height, not blown up to fill the box", () => {
    // The Underdocks ruin band is 518 of the canvas's 900px. `cover` scaled it
    // to the full stage (1.74x) and bottom-anchored it — a wall of stalactites.
    expect(roomLayerStyle({ src: "x.png", left: 0, top: 0, w: CANVAS_W, h: 518 }).height)
      .toBe("518px");
  });

  it("lands a narrow prop's LEFT edge on its canvas offset", () => {
    // The Kin's brazier flame is 163px wide on a 1920px canvas.
    const st = roomLayerStyle({ src: "x.png", left: 1422.5, top: 0, w: 163.13, h: CANVAS_H });
    expect(st.left).toBe("1422.5px");
  });

  it("turns a mirrored prop about the corner its scene turns it about", () => {
    // A Control has no flip, so these scenes mirror by rotating a half turn —
    // and a half turn about the rect's own origin puts the art on the OTHER
    // side of it. Anchoring the transform anywhere but 0 0 moves the art.
    const st = roomLayerStyle({
      src: "x.png", left: 500, top: 100, w: 200, h: 80, t: [-1, 0, 0, -1],
    });
    expect(st.transformOrigin).toBe("0 0");
    expect(st.transform).toBe("matrix(-1, 0, 0, -1, 0, 0)");
  });

  it("carries an inherited modulate through as opacity and a tint filter", () => {
    // Seven layers in the game carry one: Glory's `03_c` plate at alpha 0.83,
    // the Insatiable's floor at 0.47, the Queen's teal war-historian mural.
    const st = roomLayerStyle({
      src: "x.png", left: 0, top: 0, w: 10, h: 10, alpha: 0.5, tint: [0.2, 0.5, 0.46],
    });
    expect(st.opacity).toBe(0.5);
    expect(st.filter).toBe("url(#gs-tint-200-500-460)");
  });
});

describe("the room's own canvas scale", () => {
  it("reads the size each family authors, not one fitted constant", () => {
    // Two authored canvases ship: 2764.8x1296 (1.44) for the Overgrowth family
    // and 2881.5x1350.7 (1.50078) for the Hive/Glory one. Drawing both at 1.44
    // painted the latter 4.2% small.
    expect(sceneScale("overgrowth")).toBeCloseTo(1.44, 5);
    expect(sceneScale("underdocks")).toBeCloseTo(1.44, 5);
    expect(sceneScale("hive")).toBeCloseTo(1.500781, 5);
    expect(sceneScale("glory")).toBeCloseTo(1.500781, 5);
    expect(sceneScale("no_such_room")).toBeCloseTo(1.44, 5);
  });
});

describe("picking the room", () => {
  it("keys an ordinary fight on the act", () => {
    expect(pickScene("Underdocks")).toBe("underdocks");
    expect(pickScene("Hive", [enemy("Nibbit", "nibbit")])).toBe("hive");
  });

  it("takes either producer's spelling of the act name", () => {
    // THE TWO WIRES SPELL IT DIFFERENTLY and neither is wrong: the engine sends
    // the display name (`rust_game._act_name` -> "Underdocks") while SpireMentor
    // sends the act model's own `Id.Entry` ("UNDERDOCKS"). Every consumer of
    // `act_name` lowercases before keying, and this pins that — a case-sensitive
    // lookup here would silently paint the act-1 fallback for every live run,
    // which is the failure this whole field exists to fix.
    expect(pickScene("UNDERDOCKS")).toBe("underdocks");
    expect(pickScene("HIVE")).toBe("hive");
    expect(pickScene("GLORY")).toBe("glory");
    expect(pickScene("OVERGROWTH")).toBe("overgrowth");
  });

  it("falls back to the act-1 room for an unknown act", () => {
    expect(pickScene(null)).toBe(DEFAULT_SCENE);
    expect(pickScene("Somewhere Else")).toBe(DEFAULT_SCENE);
  });

  it("gives a boss with its own background that background", () => {
    expect(pickScene("Hive", [enemy("The Insatiable", "the_insatiable")]))
      .toBe("the_insatiable_boss");
    expect(pickScene("Overgrowth", [enemy("Vantom", "vantom")])).toBe("vantom_boss");
  });

  it("recognises a boss whose arms are separate combatants", () => {
    // Crusher and Rocket are the Kaiser Crab's two targetable arms; the backend
    // aliases both onto the shared `kaiser_crab` art slug.
    expect(pickScene("Hive", [enemy("Crusher", "kaiser_crab"), enemy("Rocket", "kaiser_crab")]))
      .toBe("kaiser_crab_boss");
  });

  it("recognises a boss from its NAME when no art has been staged", () => {
    // `rig` and `image` are both nullable. "The Insatiable" has to match
    // `the_insatiable` even though the workbench's slugify drops leading "the".
    const bare = { index: 0, name: "The Insatiable", hp: 1, max_hp: 1, block: 0, image: null };
    expect(pickScene("Hive", [bare as Enemy])).toBe("the_insatiable_boss");
  });

  it("leaves the Architect out — it is not a fight", () => {
    // The Architect is a no-op dummy behind a scripted ancient event, and its
    // background is a victory cutscene plate the scene blows up 2.2x. Faithful
    // to render, wrong to frame as a combat room.
    expect(pickScene("Glory", [enemy("The Architect", "architect")])).toBe("glory");
  });

  it("falls back to the act room when a boss scene paints nothing", () => {
    // The Merchant??? ambush composites over the shop room it springs in, so
    // its own background scene carries no room texture. Selecting it would put
    // a real fight on the bare fallback gradient.
    const merchant = enemy("The Merchant???", "fake_merchant_monster");
    expect(pickScene("Underdocks", [merchant])).toBe("underdocks");
  });

  it("leaves Aeonglass in the plain act room", () => {
    // AeonglassBoss is the one boss declaring HasCustomBackground => false.
    expect(pickScene("Glory", [enemy("Aeonglass", "hourglass_placeholder")])).toBe("glory");
  });
});

describe("rolling the layer variants", () => {
  it("picks the same variants for the same room and floor", () => {
    const a = resolveScene("overgrowth", 1, 5);
    const b = resolveScene("overgrowth", 1, 5);
    expect(a.far.map((l) => l.src)).toEqual(b.far.map((l) => l.src));
    expect(a.near.map((l) => l.src)).toEqual(b.near.map((l) => l.src));
  });

  it("varies across floors", () => {
    const srcs = (floor: number) =>
      resolveScene("overgrowth", 1, floor).far.map((l) => l.src).join("|");
    const seen = new Set(Array.from({ length: 12 }, (_, i) => srcs(i + 1)));
    expect(seen.size).toBeGreaterThan(1);
  });

  it("only ever names art the manifest actually lists", () => {
    const known = new Set<string>();
    for (const scene of Object.values(ROOM_MANIFEST.scenes)) {
      for (const variants of Object.values(scene.buckets)) {
        for (const v of variants) for (const l of v.layers) known.add(l.src);
      }
      for (const v of scene.fg) for (const l of v.layers) known.add(l.src);
      for (const slot of scene.slots) {
        if (slot.kind === "prop") for (const l of slot.layers) known.add(l.src);
      }
    }
    for (const key of Object.keys(ROOM_MANIFEST.scenes)) {
      for (let floor = 1; floor <= 20; floor++) {
        const { far, near } = resolveScene(key, 1, floor);
        for (const l of [...far, ...near]) expect(known, `${key}:${l.src}`).toContain(l.src);
      }
    }
  });

  it("draws exactly one variant per bucket, the way the game does", () => {
    // BackgroundAssets picks ONE scene per `_bg_NN` bucket and ONE `_fg_`.
    // Painting two is a pair the game would never show together.
    for (const [key, scene] of Object.entries(ROOM_MANIFEST.scenes)) {
      const { far, near, picks } = resolveScene(key, 1, 7);
      const drawn = [...far, ...near];
      for (const [index, variants] of Object.entries(scene.buckets)) {
        expect(picks[index], `${key} bucket ${index}`).toBeDefined();
        const chosen = variants.find((v) => v.id === picks[index])!;
        // Every layer the chosen variant paints is in the stack, and the stack
        // holds nothing from a bucket that drew nothing.
        for (const l of chosen.layers) {
          expect(drawn.some((d) => d.src === l.src), `${key} ${l.src}`).toBe(true);
        }
      }
      expect(Object.keys(picks).sort())
        .toEqual([...Object.keys(scene.buckets), ...(scene.fg.length ? ["fg"] : [])].sort());
    }
  });

  it("falls back to the default room for an unknown scene key", () => {
    expect(resolveScene("no_such_room", 1, 1).far.length).toBeGreaterThan(0);
  });
});

describe("arena backdrop", () => {
  it("gives the Underdocks the dock its fight happens on", () => {
    // GH: the room rendered as stalactites over open water — the game's layer
    // 04 (the planks) was missing from the manifest entirely.
    render(<Board step={step("Underdocks")} />);
    expect(bgImages()).toMatch(/underdocks_04_[ab]\.png/);
  });

  it("gives every layer its own element, in back-to-front DOM order", () => {
    render(<Board step={step("Underdocks")} />);
    const bg = screen.getByTestId("arena-bg");
    const els = [...bg.querySelectorAll<HTMLElement>(".gs-room-canvas > div")];
    expect(els.length).toBeGreaterThan(1);
    for (const el of els) {
      expect(layerImage(el)).toMatch(/^url\(/);
      expect(el.style.position).toBe("absolute");
    }
    // A positioned stack paints the LAST element on top, so the dock — the
    // frontmost of the far stack — must come last, after the water at layer 00.
    const raw = els.map(layerImage).join(", ");
    expect(raw.search(/underdocks_04_[ab]/)).toBeGreaterThan(raw.indexOf("underdocks_00"));
  });

  it("stands the Insatiable on its sand floor, not on bare Hive rock", () => {
    render(<Board step={step("Hive", [enemy("The Insatiable", "the_insatiable")])} />);
    const imgs = bgImages();
    expect(imgs).toContain("battlebg_hive_the_insatiable_floor.png");
    expect(imgs).toContain("stump1.png");
    // The floor is painted over the Hive backdrop, so it must be listed BEFORE
    // it — CSS draws the first-listed image on top.
    expect(imgs.indexOf("battlebg_hive_the_insatiable_floor"))
      .toBeLessThan(imgs.search(/hive_0[012]/));
  });

  it("gives the Waterfall Giant its own gorge, with no act room behind it", () => {
    render(<Board step={step("Underdocks", [enemy("Waterfall Giant", "waterfall_giant")])} />);
    const imgs = bgImages();
    expect(imgs).toContain("waterfallgiant_bg_4.png");
    expect(imgs).not.toContain("underdocks_00.png");
  });

  it("lights the Kin's brazier, which the plain Overgrowth room has not got", () => {
    render(<Board step={step("Overgrowth", [enemy("Kin Priest", "kin_priest")])} />);
    expect(bgImages()).toContain("overgrowth_bossflame_00.png");
  });

  it("leaves an ordinary Hive fight the plain Hive room", () => {
    render(<Board step={step("Hive", [enemy("Nibbit", "nibbit")])} />);
    const imgs = bgImages();
    expect(imgs).toContain("hive_00.png");
    expect(imgs).not.toContain("the_insatiable");
  });

  it("still fits each act layer's base plate the plain way", () => {
    // Every bucket's own backdrop plate covers the canvas; the small pieces
    // stacked on it (Overgrowth layer 04 hangs 14 individual fronds) do not,
    // and placing THEM is the point of carrying per-layer rects at all.
    const { far } = resolveScene("overgrowth", 1, 3);
    const plates = far.filter((l) => /^overgrowth\/overgrowth_\d\d(_[a-c])?\.png$/.test(l.src));
    expect(plates.length).toBeGreaterThan(0);
    for (const l of plates) {
      const st = roomLayerStyle(l);
      expect([st.left, st.top, st.width, st.height], l.src)
        .toEqual(["0px", "0px", `${CANVAS_W}px`, `${CANVAS_H}px`]);
    }
  });

  it("hangs the Overgrowth canopy from the top of the frame, not in mid-air", () => {
    // GH #607: layer 04's fourteen fronds are mirrored with `rotation = 3.14159`
    // (a Control has no flip) and the walk ignored it, so they floated
    // mid-frame as the reported "black arcs" and two landed off the canvas
    // entirely. Turned, they hang from the top edge the way the game's do.
    const bucket = ROOM_MANIFEST.scenes.overgrowth.buckets["04"];
    const fronds = bucket.flatMap((v) => v.layers)
      .filter((l) => /overgrowth_04_a_\d+\.png$/.test(l.src));
    expect(fronds.length).toBeGreaterThan(6);
    expect(fronds.filter((l) => l.t).length).toBeGreaterThan(6);
    // The canopy reaches the canvas's top edge, and no frond is left dangling
    // in the middle of the room with nothing above it.
    expect(Math.min(...fronds.map((l) => bbox(l).top))).toBeLessThan(20);
    for (const l of fronds) expect(bbox(l).top, l.src).toBeLessThan(CANVAS_H / 2);

    // And nothing the Overgrowth room can roll misses the canvas outright.
    const everything = [
      ...Object.values(ROOM_MANIFEST.scenes.overgrowth.buckets).flatMap((b) =>
        b.flatMap((v) => v.layers)),
      ...ROOM_MANIFEST.scenes.overgrowth.fg.flatMap((v) => v.layers),
    ];
    for (const l of everything) {
      const b = bbox(l);
      expect(b.right, l.src).toBeGreaterThan(0);
      expect(b.left, l.src).toBeLessThan(CANVAS_W);
      expect(b.bottom, l.src).toBeGreaterThan(0);
      expect(b.top, l.src).toBeLessThan(CANVAS_H);
    }
  });
});

describe("room weather", () => {
  it("gives weather only to the rooms whose scenes hang art in the air", () => {
    // Derived, not listed: a room the artists did not fill with particles
    // cannot acquire any. Keeping this exact stops a future re-derivation from
    // quietly fogging thirteen rooms that should stay clear.
    const hazy = Object.keys(ROOM_MANIFEST.scenes).filter((k) => sceneAtmosphere(k));
    expect(hazy.sort()).toEqual([
      "queen_boss", "the_insatiable_boss", "waterfall_giant_boss",
    ]);
  });

  it("gives the Insatiable warm air and the Waterfall Giant cold", () => {
    const sand = sceneAtmosphere("the_insatiable_boss")!;
    const spray = sceneAtmosphere("waterfall_giant_boss")!;
    expect(sand.rgb[0]).toBeGreaterThan(sand.rgb[2]);   // warm: red over blue
    expect(spray.rgb[2]).toBeGreaterThan(spray.rgb[0]); // cold: blue over red
  });

  it("scales density with how much the room actually hangs in the air", () => {
    const sand = sceneAtmosphere("the_insatiable_boss")!;
    const mist = sceneAtmosphere("queen_boss")!;
    expect(sand.particles).toBeGreaterThan(mist.particles);
    expect(sand.strength).toBeGreaterThan(mist.strength);
    expect(sand.strength).toBeLessThanOrEqual(1);
  });

  it("emits no custom properties at all for a clear room", () => {
    // The dust layer screen-blends, and a `mix-blend-mode` stacking context
    // shifts what composites beneath it even at zero alpha.
    expect(atmosphereVars("overgrowth")).toEqual({});
    expect(Object.keys(atmosphereVars("the_insatiable_boss"))).toContain("--gs-dust-rgb");
  });

  it("mounts the dust layer only where there is weather", () => {
    const { unmount } = render(
      <Board step={step("Hive", [enemy("The Insatiable", "the_insatiable")])} />);
    expect(screen.queryByTestId("arena-dust")).not.toBeNull();
    unmount();
    render(<Board step={step("Overgrowth")} />);
    expect(screen.queryByTestId("arena-dust")).toBeNull();
  });
});

describe("the room never covers a combatant", () => {
  it("gives every room exactly one foreground variant, the way the game does", () => {
    // BackgroundAssets picks ONE `_fg_` scene per combat ("there is only 1 fg
    // layer currently"), and everything from that slot on is the near half.
    for (const [key, scene] of Object.entries(ROOM_MANIFEST.scenes)) {
      const { near } = resolveScene(key, 1, 7);
      const hits = scene.fg.filter((v) => v.layers.some((l) => near.some((n) => n.src === l.src)));
      expect(hits.length, key).toBeLessThanOrEqual(1);
    }
  });
});
