import { describe, expect, it } from "vitest";
import { deathBurstRadiusFor, deathVfxExtent, dissolveSecondsFor, dissolveThreshold } from "../dissolve";

// The brief's own Step 1 draft got this test backwards in three independent
// ways (direction, easing curve, clamp target) — see the Task 14 controller
// addendum and `dissolveThreshold`'s own doc for the corrected derivation
// against `NMonsterDeathVfx.cs` and `shaders/dissolve.gdshader`. This file is
// the CORRECTED version.
describe("dissolveThreshold", () => {
  it("runs the threshold from 1 (visible) to 0 (dissolved) across the authored duration, eased EASE_OUT/TRANS_SINE", () => {
    // NMonsterDeathVfx tweens `shader_parameter/threshold` TO 0 over up to
    // 2.5s (NMonsterDeathVfx.cs:239) — the tween starts at 1 (thresholdFrom)
    // and its TARGET is 0 (thresholdTo), not the other way around.
    expect(dissolveThreshold(0, 2.5)).toBe(1);
    // Godot's EASE_OUT/TRANS_SINE is a Penner-style sine curve —
    // sin(t * PI/2) — not linear: at the halfway point that is sin(PI/4) ≈
    // 0.7071, so threshold ≈ 1 - 0.7071 ≈ 0.2929, not the brief's 0.5.
    expect(dissolveThreshold(1.25, 2.5)).toBeCloseTo(1 - Math.sin(Math.PI / 4), 10);
    expect(dissolveThreshold(2.5, 2.5)).toBe(0);
  });

  it("clamps past the end to fully DISSOLVED (0), not back to visible", () => {
    // A corpse whose removal is late must stay fully dissolved — the brief's
    // own rationale ("must not wrap back into showing the body again") was
    // right about the FAILURE MODE but backwards about which end is unsafe:
    // clamping toward 1 (visible) is what would re-materialize a dead
    // monster; clamping toward 0 (already fully eaten away) cannot.
    expect(dissolveThreshold(9, 2.5)).toBe(0);
  });

  it("treats a non-positive duration as already finished (fully dissolved)", () => {
    expect(dissolveThreshold(0, 0)).toBe(0);
    expect(dissolveThreshold(-1, 2.5)).toBe(1); // negative elapsed clamps to t=0 -> visible
  });
});

describe("dissolveSecondsFor", () => {
  const CAP = 2.5;
  const W = 1920;

  it("caps at 2.5s once the creature's larger padded extent reaches 5% of the screen", () => {
    // `min(2n / (0.1 * screenW) * 2.5, 2.5)` saturates at `2n >= 0.1 * 1920`,
    // i.e. `n >= 96`. With the 1.2 padding that is a 80-scene-px extent.
    expect(dissolveSecondsFor(80, 40, 1, CAP, W)).toBeCloseTo(CAP);
    expect(dissolveSecondsFor(4000, 3000, 1, CAP, W)).toBe(CAP);
  });

  it("gives a small creature a proportionally shorter dissolve", () => {
    // n = round(40 * 1.2) = 48, so 2n/192 = 0.5 of the cap.
    expect(dissolveSecondsFor(40, 20, 1, CAP, W)).toBeCloseTo(CAP / 2);
  });

  it("measures the LARGER extent, not the width", () => {
    // `Mathf.Max(size.X, size.Y)` — a tall thin creature is sized by its
    // height. Both of these must land on the same duration.
    expect(dissolveSecondsFor(20, 40, 1, CAP, W)).toBeCloseTo(dissolveSecondsFor(40, 20, 1, CAP, W));
  });

  it("shrinks with the encounter camera, which the C# folds into the bounds", () => {
    // `bounds * SceneContainer.Scale` — the Kaiser Crab's 0.75 camera makes
    // every creature in that fight smaller on screen, and so quicker to
    // dissolve.
    expect(dissolveSecondsFor(40, 20, 0.75, CAP, W)).toBeLessThan(dissolveSecondsFor(40, 20, 1, CAP, W));
  });

  it("separates the smallest shipped rig from the largest", () => {
    // Real numbers: `byrdpip` is 583x599 rig units and `fabricator` 4067x3185,
    // at authored scales small enough that the difference survives. A flat
    // 2.5s cap gave both the same dissolve, which is the gap this closes.
    const byrdpip = dissolveSecondsFor(583 * 0.11, 599 * 0.11, 1, CAP, W);
    const fabricator = dissolveSecondsFor(4067 * 0.11, 3185 * 0.11, 1, CAP, W);
    expect(byrdpip).toBeLessThan(fabricator);
    expect(fabricator).toBe(CAP);
  });

  it("falls back to the ceiling for a creature with no measurable box", () => {
    // Longer is the safe direction: the corpse's removal ends an over-long
    // dissolve, while an under-long one is cut off mid-fade.
    expect(dissolveSecondsFor(0, 0, 1, CAP, W)).toBe(CAP);
    expect(dissolveSecondsFor(40, 20, 1, CAP, 0)).toBe(CAP);
  });

  it("is inert for the two monsters that override the padding", () => {
    // `CalcifiedCultist`/`DampCultist` use (1.5, 1.2) rather than (1.2, 1.2),
    // and share the 870x1291 `cultists` rig — height dominates under either,
    // so the wider x padding never reaches `max`. This is why no per-monster
    // padding rides the wire; a monster whose WIDTH dominated would need one.
    const h = 1291 * 0.11;
    const w = 870 * 0.11;
    expect(Math.max(w * 1.5, h * 1.2)).toBeCloseTo(h * 1.2);
    expect(dissolveSecondsFor(w, h, 1, CAP, W)).toBeCloseTo(
      Math.min(((2 * Math.round(h * 1.2)) / (0.1 * W)) * CAP, CAP),
    );
  });
});

describe("deathBurstRadiusFor", () => {
  it("is half the creature's larger padded extent", () => {
    // `EmissionSphereRadius = node.Size.Y / 4f` where `node.Size = (n, n) * 2`
    // (`NMonsterDeathVfx.cs:202-204,231`), so `2n / 4 = n / 2` — and the
    // manifest's `emissionRadiusPerCreatureExtent` IS that 0.5.
    // n = round(80 * 1.2) = 96, so the radius is 48.
    expect(deathBurstRadiusFor(80, 40, 1, 0.5)).toBe(48);
  });

  it("measures the LARGER extent and rounds it, sharing that with the duration", () => {
    // Both numbers come from `Mathf.RoundToInt(Mathf.Max(size.X, size.Y))` —
    // one integer, two divisions of it. A tall thin creature is sized by its
    // height, and the rounding happens BEFORE the halving, so an odd `n`
    // lands on a half pixel rather than being rounded twice.
    expect(deathVfxExtent(20, 41, 1)).toBe(49); // round(41 * 1.2) = 49
    expect(deathBurstRadiusFor(20, 41, 1, 0.5)).toBe(24.5);
  });

  it("scales with the camera, like the dissolve duration does", () => {
    // `SceneContainer.Scale` multiplies the bounds before the viewport is
    // sized, so a zoomed-out encounter gets a proportionally smaller burst.
    expect(deathBurstRadiusFor(80, 40, 0.5, 0.5)).toBe(24); // round(48) / 2
  });

  it("returns null for a creature with no measurable box", () => {
    // Deliberately NOT a default radius. The manifest's own value is blanked
    // to 0 by `extract_vfx.py` (the game overwrites it per creature), so
    // there is no sane fallback — the caller skips the burst instead.
    expect(deathBurstRadiusFor(0, 0, 1, 0.5)).toBeNull();
    expect(deathVfxExtent(0, 0, 1)).toBeNull();
  });
});
