import { afterEach, describe, expect, it, vi } from "vitest";
import { bareToken, detectCharacter, labelFor, synergyFor } from "./synergy";

describe("bareToken", () => {
  it("strips CARD. namespace", () => {
    expect(bareToken("CARD.STRIKE_IRONCLAD")).toBe("STRIKE_IRONCLAD");
  });
  it("strips upgrade marker", () => {
    expect(bareToken("WHIRLWIND+1")).toBe("WHIRLWIND");
    expect(bareToken("CARD.IRON_WAVE+2")).toBe("IRON_WAVE");
  });
  it("passes through a bare token", () => {
    expect(bareToken("BASH")).toBe("BASH");
  });
});

describe("labelFor", () => {
  it("drops the character suffix and title-cases", () => {
    expect(labelFor("STRIKE_IRONCLAD")).toBe("Strike");
    expect(labelFor("STRIKE_NECROBINDER")).toBe("Strike");
  });
  it("keeps multi-word non-suffix tokens", () => {
    expect(labelFor("BODY_SLAM")).toBe("Body Slam");
    expect(labelFor("HOWL_FROM_BEYOND")).toBe("Howl From Beyond");
  });
});

describe("detectCharacter", () => {
  it("detects from a STRIKE_<X> basic", () => {
    expect(detectCharacter(["BASH", "STRIKE_SILENT", "DEFEND_SILENT"])).toBe("silent");
    expect(detectCharacter(["STRIKE_REGENT"])).toBe("regent");
  });
  it("detects from a DEFEND_<X> basic", () => {
    expect(detectCharacter(["DEFEND_DEFECT"])).toBe("defect");
  });
  it("returns null when no basic is present", () => {
    expect(detectCharacter(["WHIRLWIND", "BODY_SLAM"])).toBeNull();
  });
});

describe("synergyFor", () => {
  afterEach(() => vi.unstubAllGlobals());

  const catalog = {
    character: "TestChar",
    edges: [
      { pair: ["CINDER", "WHIRLWIND"], pmi: 2.0, econ_S: null, combat_S: 4.5, quadrant: "confirmed" },
      { pair: ["CINDER", "HAVOC"], pmi: 0.4, econ_S: null, combat_S: 1.0, quadrant: "undiscovered" },
      { pair: ["CINDER", "DEBT"], pmi: 1.0, econ_S: null, combat_S: -3.0, quadrant: "myth" },
      { pair: ["CINDER", "STRIKE_IRONCLAD"], pmi: 0.1, econ_S: null, combat_S: 0.0, quadrant: "noise" },
    ],
  };

  function stubFetch(c: string, body: unknown): void {
    vi.stubGlobal("fetch", vi.fn(async (url: string) =>
      url.includes(`catalog_${c}.json`)
        ? ({ ok: true, json: async () => body } as Response)
        : ({ ok: false, status: 404 } as Response),
    ));
  }

  it("returns confirmed/undiscovered partners, ranked, with counts", async () => {
    stubFetch("alpha", catalog);
    const hints = await synergyFor("alpha", "CARD.CINDER", [
      "CARD.WHIRLWIND",
      "CARD.HAVOC",
      "CARD.HAVOC", // duplicate → count 2
      "CARD.DEBT", // myth → excluded
      "CARD.STRIKE_IRONCLAD", // noise → excluded
    ]);
    expect(hints.map((h) => h.partner)).toEqual(["WHIRLWIND", "HAVOC"]);
    expect(hints[0].quadrant).toBe("confirmed");
    expect(hints[1].quadrant).toBe("undiscovered");
    expect(hints[1].count).toBe(2);
    expect(hints[1].label).toBe("Havoc");
  });

  it("ignores the offered card itself", async () => {
    stubFetch("beta", catalog);
    const hints = await synergyFor("beta", "CARD.CINDER", ["CARD.CINDER", "CARD.WHIRLWIND"]);
    expect(hints.map((h) => h.partner)).toEqual(["WHIRLWIND"]);
  });

  it("returns [] when the catalog is missing", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => ({ ok: false, status: 404 }) as Response));
    expect(await synergyFor("missing-char-xyz", "CARD.CINDER", ["CARD.WHIRLWIND"])).toEqual([]);
  });

  it("ranks metric runlevel > combat > ppo > pmi", async () => {
    const c = {
      character: "rank",
      edges: [
        // runlevel present → wins over combat/econ
        { pair: ["CINDER", "WHIRLWIND"], pmi: 2.0, econ_S: 9, combat_S: 9, runlevel_S: 3.3, quadrant: "confirmed" },
        // no runlevel, no combat → ppo (econ_S) tier
        { pair: ["CINDER", "HAVOC"], pmi: 2.0, econ_S: 1.4, combat_S: null, quadrant: "confirmed" },
        // only pmi
        { pair: ["CINDER", "STRIKE_IRONCLAD"], pmi: 0.9, econ_S: null, combat_S: null, quadrant: "confirmed" },
      ],
    };
    stubFetch("rank", c);
    const hints = await synergyFor("rank", "CARD.CINDER", ["WHIRLWIND", "HAVOC", "STRIKE_IRONCLAD"]);
    const by = new Map(hints.map((h) => [h.partner, h]));
    expect(by.get("WHIRLWIND")!.metric).toBe("runlevel");
    expect(by.get("WHIRLWIND")!.score).toBe(3.3);
    expect(by.get("HAVOC")!.metric).toBe("ppo");
    expect(by.get("STRIKE_IRONCLAD")!.metric).toBe("pmi");
  });
});
