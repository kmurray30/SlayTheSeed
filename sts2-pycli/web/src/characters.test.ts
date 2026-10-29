import { describe, expect, it } from "vitest";
import { characterKey } from "./characters";

describe("characterKey", () => {
  it("accepts the engine lane's bare name", () => {
    expect(characterKey("Ironclad")).toBe("ironclad");
    expect(characterKey("Necrobinder")).toBe("necrobinder");
  });

  it("accepts the live-game lane's localized title", () => {
    // `sts2_realgame._character_title` builds "The " + <Title Case> from
    // `characters.json`'s `<ID>.title`, which all five carry. Three components
    // each derived this key separately without the strip, so every live-game
    // run silently lost the rest-site hero, the run-end sprite and the HUD
    // portrait.
    expect(characterKey("The Ironclad")).toBe("ironclad");
    expect(characterKey("The Silent")).toBe("silent");
    expect(characterKey("The Defect")).toBe("defect");
    expect(characterKey("The Regent")).toBe("regent");
    expect(characterKey("The Necrobinder")).toBe("necrobinder");
  });

  it("returns null rather than a key we have no art for", () => {
    for (const bad of [null, undefined, "", "The", "Watcher", "the "]) {
      expect(characterKey(bad)).toBeNull();
    }
  });

  it("strips only a leading article, not one inside the name", () => {
    // Guard against a blanket `replace(/the/g, "")`, which would turn
    // "The Silent" into "silen" via the "t" — and quietly break all five.
    expect(characterKey("  THE   silent  ")).toBe("silent");
  });
});
