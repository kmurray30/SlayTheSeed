import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, it, expect } from "vitest";
import { startComposite, isDone } from "../composite";
import { makeRng } from "../rng";
import type { VfxManifest, ParticlesEffect } from "../../manifest";

// Read at RUNTIME, not `import`ed — same reason as `simulateManifestCoverage
// .test.ts` / `bindingsRealTable.test.ts`: `sts2-wiki/` is `.dockerignore`d
// and the web build stage copies only `sts2-pycli/web/`, so a static import
// of the extracted manifest would fail `npm run build` inside the container.
// Absent tree => the suite skips; present tree => it checks against the REAL
// committed composites, not a synthetic fixture shaped like one.
const WEB_ROOT = resolve(__dirname, "../../../..");
const MANIFEST_PATH = resolve(WEB_ROOT, "../../sts2-wiki/site/public/images/vfx/manifest.json");

function readManifest(): VfxManifest | null {
  try {
    return JSON.parse(readFileSync(MANIFEST_PATH, "utf8")) as VfxManifest;
  } catch {
    return null;
  }
}

const loaded = readManifest();
const suite = loaded ? describe : describe.skip;
const manifest = loaded as VfxManifest;

function particles(name: string): ParticlesEffect {
  const e = manifest.effects[name];
  if (!e || e.kind !== "particles") {
    throw new Error(`${name}: not a "particles" effect in the committed manifest`);
  }
  return e;
}

suite("startComposite / isDone against the real committed manifest", () => {
  it("starts every emitter of a composite at once", () => {
    // NVfxParticleSystem._Ready sets Emitting on ALL children in one frame —
    // true for vfx_attack_blunt specifically because every one of its three
    // emitters authors startDelay: 0. See the staggered test below for the
    // one composite that does NOT hold this property.
    const live = startComposite(particles("vfx_attack_blunt"), makeRng(1));
    expect(live.emitters).toHaveLength(3);
    expect(live.emitters.every((e) => e.startedAt === 0)).toBe(true);
  });

  it("frees the composite after its authored lifetime", () => {
    const live = startComposite(particles("vfx_attack_blunt"), makeRng(1));
    expect(isDone(live, 1.24)).toBe(false);
    expect(isDone(live, 1.26)).toBe(true); // _lifetime = 1.25
  });

  it("stages vfx_scratch_impact's emitters instead of starting them all at once", () => {
    // NScratchVfx.PlaySequence: restarts _anticipationParticles, waits 0.1s,
    // then restarts _impactParticles — captured in the manifest as
    // per-emitter startDelay: 0.0 on vfx_scratch_flipbook (the anticipation
    // swing), 0.1 on all four _impactParticles members, INCLUDING both
    // same-named vfx_big_slash_impact_side_smoke emitters (emitter names are
    // not unique — see manifest.ts's module doc). Hardcoding a simultaneous
    // start would render this one staggered effect wrong.
    const live = startComposite(particles("vfx_scratch_impact"), makeRng(1));
    expect(live.emitters).toHaveLength(5);
    const anticipation = live.emitters.filter((e) => e.emitter.name === "vfx_scratch_flipbook");
    expect(anticipation).toHaveLength(1);
    expect(anticipation[0]?.startedAt).toBe(0);
    const impact = live.emitters.filter((e) => e.emitter.name !== "vfx_scratch_flipbook");
    expect(impact).toHaveLength(4);
    expect(impact.every((e) => e.startedAt === 0.1)).toBe(true);
    // Both same-named vfx_big_slash_impact_side_smoke members are in there.
    const sideSmoke = live.emitters.filter((e) => e.emitter.name === "vfx_big_slash_impact_side_smoke");
    expect(sideSmoke).toHaveLength(2);
  });

  it("applies the binding tint over the emitter's self_modulate", () => {
    // Task 6 normalizes every tint in the committed binding table to
    // `#RRGGBB` — the brief's own fixture predates that normalization.
    const live = startComposite(particles("vfx_spore_impact"), makeRng(1), "#8aad7d");
    expect(live.tint).toEqual([0x8a / 255, 0xad / 255, 0x7d / 255, 1]);
  });

  it("applies the per-effect randomisers the bespoke scripts add", () => {
    // NPoisonImpactVfx flips its smoke container on X per spawn (Vector2(
    // Randf() > 0.5 ? 1 : -1, 1) on the node `mirrorX` names); two spawns
    // with the same seed must be able to agree, and different seeds must be
    // able to differ. These 8 fixed seeds are verified (not assumed) to land
    // on both outcomes against this module's actual RNG below.
    const a = startComposite(particles("vfx_poison_impact"), makeRng(1));
    const b = startComposite(particles("vfx_poison_impact"), makeRng(1));
    expect(a.mirrored).toBe(b.mirrored);
    const seeds = [1, 2, 3, 4, 5, 6, 7, 8].map(
      (s) => startComposite(particles("vfx_poison_impact"), makeRng(s)).mirrored,
    );
    expect(new Set(seeds).size).toBe(2);
  });

  it("never draws the mirror coin flip for a composite with no mirrorX node", () => {
    // vfx_attack_blunt's mirrorX is null — NVfxParticleSystem (its script)
    // never calls Randf() for it, so this must be unconditionally false
    // rather than a 50/50 draw that happens to often land false.
    for (const seed of [1, 2, 3, 4, 5, 6, 7, 8]) {
      expect(startComposite(particles("vfx_attack_blunt"), makeRng(seed)).mirrored).toBe(false);
    }
  });
});
