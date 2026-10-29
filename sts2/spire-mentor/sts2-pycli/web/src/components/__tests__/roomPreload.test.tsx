/**
 * The room goes up whole, or not at all.
 *
 * GH: a fight opened on a room that DREW ITSELF — the far wall, then the
 * canopy, then the floor, then the props — because each of a scene's layers
 * (5 to 68 of them, up to 37 distinct files) is its own element with its own
 * `background-image` and the browser paints each the instant it arrives. These
 * tests pin the gate that holds the stack back until every one of its files has
 * landed (`roomPreload.ts`), and the two things that must not make it hang: a
 * file that 404s, and a room whose art is already in the cache (which must not
 * gate at all).
 */
import { render, screen, act, cleanup } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { Enemy, Step } from "../../api";

/** A stand-in for the browser's image loader: jsdom fetches nothing, so the
 *  test decides when a file "arrives" — which is the whole thing under test. */
class FakeImage {
  static made: FakeImage[] = [];
  onload: (() => void) | null = null;
  onerror: (() => void) | null = null;
  complete = false;
  private url = "";
  get src(): string { return this.url; }
  set src(v: string) { this.url = v; FakeImage.made.push(this); }
}

/** Every image the room asked for since the last reset, oldest first. */
const requested = () => FakeImage.made.map((i) => i.src);

/** Deliver every outstanding request, as the network would — and keep going
 *  until nothing new is asked for: a rig's atlas is fetched first and only
 *  then are its page images requested, so one round is not enough. */
const delivered = new WeakSet<FakeImage>();
async function deliverAll(how: "onload" | "onerror" = "onload"): Promise<void> {
  let quiet = 0;
  for (let round = 0; round < 12 && quiet < 2; round++) {
    // Let a fetch that just resolved ask for its page images first.
    await act(async () => { await Promise.resolve(); await Promise.resolve(); });
    const pending = FakeImage.made.filter((i) => !delivered.has(i));
    if (pending.length === 0) { quiet++; continue; }
    quiet = 0;
    await act(async () => {
      for (const img of pending) { delivered.add(img); img[how]?.(); }
    });
  }
}

/** The rig files a fight fetches (`.skel`, `.atlas`); the atlas names no
 *  pages, so the gate falls back to the rig's own `.png` (an Image). */
const fetched: string[] = [];
const fakeFetch = (async (input: RequestInfo | URL) => {
  const url = typeof input === "string" ? input : input instanceof URL ? input.href : input.url;
  if (!/\.(skel|atlas)$/.test(url)) return new Response("", { status: 404 });
  fetched.push(url);
  return new Response(url.endsWith(".atlas") ? "" : "skel", { status: 200 });
}) as typeof fetch;

function enemy(name: string, slug: string): Enemy {
  return {
    index: 0, name, hp: 100, max_hp: 100, block: 0,
    image: `monsters/${slug}.png`, rig: `monster_rigs/${slug}/${slug}`,
  };
}

function step(actName: string, enemies: Enemy[] = [enemy("Toadpole", "toadpole")]): Step {
  return {
    idx: 0, decision: "combat_play", scores: [], pv: null, chosen_action: null,
    source: "live", meta: { turn: 1, step_no: 0, act_name: actName, act: 1, floor: 3 },
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

const gate = () => {
  const room = screen.getByTestId("arena-bg").closest(".gs-arena-stage")!.getAttribute("data-room-ready");
  const scene = screen.getByTestId("arena-bg").closest(".gs-board")!.getAttribute("data-scene-ready");
  // The room and the fighters are one gate: they may never disagree.
  expect(scene).toBe(room);
  return room;
};

// Every test starts from a cold cache: the module remembers what it has loaded
// for the life of the page, which is the point of it, so the module itself has
// to be new for each one.
let Board: typeof import("../Board").default;
let preload: typeof import("../roomPreload");

beforeEach(async () => {
  FakeImage.made = [];
  fetched.length = 0;
  vi.stubGlobal("Image", FakeImage);
  vi.stubGlobal("fetch", fakeFetch);
  vi.resetModules();
  Board = (await import("../Board")).default;
  preload = await import("../roomPreload");
});

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe("roomArtUrls", () => {
  it("lists a stack's files once each, under the served rooms/ tree", () => {
    const layer = (src: string) => ({ src, left: 0, top: 0, w: 10, h: 10 });
    const urls = preload.roomArtUrls(
      (rel) => `/assets/${rel}`,
      [layer("a.png"), layer("b.png")],
      // Variants of one slot share art freely — the same torch hangs in all
      // three of Glory's ruin variants — so a stack really does repeat files.
      [layer("b.png"), layer("c.png")],
    );
    expect(urls).toEqual(["/assets/rooms/a.png", "/assets/rooms/b.png", "/assets/rooms/c.png"]);
  });
});

describe("the room's art gate", () => {
  it("holds the room back until every layer of the stack has arrived", async () => {
    render(<Board step={step("Underdocks")} />);
    // The stack is real art, and more than one file of it.
    expect(requested().length).toBeGreaterThan(1);
    expect(requested().every((u) => u.includes("/rooms/"))).toBe(true);
    expect(gate()).toBe("0");

    // One file short is still not a room: the gate must not open on a partial
    // stack, which is precisely the state that was reaching the screen.
    const [first, ...rest] = FakeImage.made;
    await act(async () => { first.onload?.(); });
    expect(gate()).toBe("0");

    await act(async () => { for (const img of rest) { delivered.add(img); img.onload?.(); } });
    delivered.add(first);
    // The enemy's rig page (asked for once its atlas answered) is still out.
    expect(gate()).toBe("0");
    await deliverAll();
    expect(gate()).toBe("1");
  });

  it("does not gate a room whose art is already loaded", async () => {
    const { unmount } = render(<Board step={step("Underdocks")} />);
    await deliverAll();
    expect(gate()).toBe("1");
    unmount();

    // Second visit to the same room: it is already in the browser, so it paints
    // on the first frame — stepping back and forth through a replay must not
    // fade the backdrop in and out.
    const before = requested().length;
    render(<Board step={step("Underdocks")} />);
    expect(gate()).toBe("1");
    expect(requested().length).toBe(before);
  });

  it("is not held open by a file the deploy is missing", async () => {
    render(<Board step={step("Overgrowth")} />);
    expect(gate()).toBe("0");
    // A 404 is settled, not pending. A layer that will never arrive renders as
    // nothing either way; waiting on it would cost the whole room.
    await deliverAll("onerror");
    expect(gate()).toBe("1");
  });

  it("re-gates when the fight moves to a room that has not been loaded", async () => {
    const { rerender } = render(<Board step={step("Overgrowth")} />);
    await deliverAll();
    expect(gate()).toBe("1");

    FakeImage.made = [];
    rerender(<Board step={step("Hive", [enemy("The Insatiable", "the_insatiable")])} />);
    expect(gate()).toBe("0");
    expect(requested().length).toBeGreaterThan(0);
    await deliverAll();
    expect(gate()).toBe("1");
  });
});

describe("atlasPages", () => {
  it("reads the page image names out of a Spine atlas", () => {
    const text = "axebot.png\nsize:1365,507\nfilter:Linear,Linear\nscale:0.172\narm 2\nbounds:1254,282,61,109\naxebot_2.png\nsize:10,10\n";
    expect(preload.atlasPages(text)).toEqual(["axebot.png", "axebot_2.png"]);
    expect(preload.atlasPages("")).toEqual([]);
  });
});

describe("the fight's art gate", () => {
  function withHand(st: Step, images: string[]): Step {
    const hand = images.map((image, i) => ({
      index: i, name: `c${i}`, cost: 1, type: "attack", image, can_play: true,
    }));
    return { ...st, state: { ...st.state, hand } } as unknown as Step;
  }

  it("waits for the enemy's rig and the opening hand, not just the room", async () => {
    render(<Board step={withHand(step("Underdocks"), ["/assets/card_atlas/x/a.png"])} />);
    await act(async () => { await Promise.resolve(); });
    // Both rig files were fetched; the card portrait and the rig page are Images.
    expect(fetched.filter((u) => u.endsWith("toadpole.skel") || u.endsWith("toadpole.atlas")).length).toBe(2);
    const wanted = () => FakeImage.made.map((i) => i.src);
    expect(wanted()).toContain("/assets/card_atlas/x/a.png");
    expect(wanted().some((u) => u.endsWith("toadpole/toadpole.png"))).toBe(true);
    // Every room layer in — still held: the rig page and the card are out.
    await act(async () => {
      for (const img of FakeImage.made) if (img.src.includes("/rooms/")) { delivered.add(img); img.onload?.(); }
    });
    expect(gate()).toBe("0");
    await deliverAll();
    expect(gate()).toBe("1");
  });

  it("does not re-gate the fight when the hand changes", async () => {
    const st = step("Underdocks");
    const { rerender } = render(<Board step={withHand(st, ["/assets/card_atlas/x/a.png"])} />);
    await deliverAll();
    expect(gate()).toBe("1");
    const before = FakeImage.made.length;
    // Next turn: a card the browser has never seen. It pops in on its own; the
    // fight that is already on screen must not hide itself to wait for it.
    rerender(<Board step={withHand(st, ["/assets/card_atlas/x/a.png", "/assets/card_atlas/x/new.png"])} />);
    expect(gate()).toBe("1");
    expect(FakeImage.made.length).toBe(before);
  });

  it("re-gates for the next fight", async () => {
    const { rerender } = render(<Board step={step("Underdocks")} />);
    await deliverAll();
    expect(gate()).toBe("1");
    rerender(<Board step={step("Underdocks", [enemy("Byrdpip", "byrdpip")])} />);
    expect(gate()).toBe("0");
    await deliverAll();
    expect(gate()).toBe("1");
  });
});

