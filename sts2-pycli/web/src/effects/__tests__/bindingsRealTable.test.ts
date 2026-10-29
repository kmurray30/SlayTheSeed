import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, it, expect } from "vitest";
import {
  effectsForEvent, ownerKeyCandidates, deathFadeFor, shouldFadeAfterDeath, type Bindings,
} from "../bindings";
import type { TargetRef } from "../types";

// The EXTRACTED (and committed) binding table, read for real — the same
// pattern `manifestShape.test.ts` uses for `manifest.json`, and for the
// same reason: `bindings.ts`'s `ownerKeyCandidates` (the `_MOVE`-suffixed
// key tried first, then the bare id) is a hypothesis about what shape the
// PYTHON extractor's owner keys actually take. A synthetic fixture proves
// this TypeScript code handles a bare-id row *shaped the way we assume it
// is*; it does not prove the real table still contains one. If a future
// change to `extract_vfx_bindings.py` ever renamed or restructured the
// owner-key convention, an all-synthetic suite would stay green while
// production silently mis-resolved every affected move.
//
// Read at RUNTIME, not `import`ed — same reason as `manifestShape.test.ts`:
// `sts2-wiki/` is `.dockerignore`d and the web build stage copies only
// `sts2-pycli/web/`, so a static import of this JSON would fail `npm run
// build` inside the container. Absent tree => the suite skips; present
// tree => it checks.
const WEB_ROOT = resolve(__dirname, "../../..");
const BINDINGS_PATH = resolve(
  WEB_ROOT,
  "../../sts2-wiki/site/public/images/vfx/vfx_bindings.json",
);

function readBindings(): Bindings | null {
  try {
    return JSON.parse(readFileSync(BINDINGS_PATH, "utf8")) as Bindings;
  } catch {
    return null;
  }
}

const loaded = readBindings();
const suite = loaded ? describe : describe.skip;
const bindings = loaded as Bindings;

const E: TargetRef = { side: "enemy", index: 0 };

suite("effectsForEvent against the real committed binding table", () => {
  /** The single pick for an owner the real table binds once. */
  function onlyPick(source: Parameters<typeof ownerKeyCandidates>[0]) {
    const picks = effectsForEvent({ kind: "hurt", target: E, amount: 6, source }, bindings);
    expect(picks).toHaveLength(1);
    return picks[0]!;
  }

  it("resolves a card row by its bare id", () => {
    // BASH: {owner: "BASH", kind: "card", effect: "vfx_attack_blunt"}.
    const r = onlyPick({ kind: "card", id: "BASH" });
    expect(r.effect).toBe("vfx_attack_blunt");
    expect(r.fellBackFrom).toBeUndefined();
  });

  it("resolves a move row via the `_MOVE`-suffixed candidate", () => {
    // Aeonglass:EBB_MOVE — the common case (196 of 226 move rows).
    const r = onlyPick({ kind: "move", monster: "Aeonglass", id: "EBB" });
    expect(r.effect).toBe("vfx_attack_blunt");
    expect(r.fellBackFrom).toBeUndefined();
  });

  it("resolves a move row via the bare-id candidate", () => {
    // AxeRubyRaider:SWING_1 — one of the 30 move rows (of 226) with no
    // `_MOVE` suffix at all. This is the row a `_MOVE`-only join would
    // silently miss; if the extractor ever stopped emitting bare-id rows,
    // or renamed this one, this assertion (not just the synthetic fixture
    // in bindings.test.ts) would catch it.
    const r = onlyPick({ kind: "move", monster: "AxeRubyRaider", id: "SWING_1" });
    expect(r.effect).toBe("vfx_attack_slash");
    expect(r.fellBackFrom).toBeUndefined();
  });

  it("still has at least one bare-id move row, so the bare-id candidate stays exercised", () => {
    const moveOwners = bindings.bindings.filter((b) => b.kind === "move").map((b) => b.owner);
    expect(moveOwners.some((o) => !o.endsWith("_MOVE"))).toBe(true);
  });

  it("publishes the alias tables the Rust coverage gate applies", () => {
    // `vfx_binding_coverage.rs` proves every engine move id resolves only
    // because it applies these two tables. They are published WITH the table
    // so this lookup applies the same ones; a table without them means the
    // renderer is silently back to a bare `_MOVE` join, which missed ~30
    // (monster, move) pairs while the gate stayed green.
    expect(bindings.aliases).toBeDefined();
    expect(Object.keys(bindings.aliases!.monsters).length).toBeGreaterThan(0);
    expect(Object.keys(bindings.aliases!.states).length).toBeGreaterThan(0);
  });

  it("resolves the aliased monsters and states against real rows", () => {
    const owners = new Set(bindings.bindings.map((b) => b.owner));
    for (const [engine, alias] of Object.entries(bindings.aliases!.monsters)) {
      // The aliased class must actually own rows; the engine name must not.
      expect([...owners].some((o) => o.startsWith(`${alias.monster}:`))).toBe(true);
      expect([...owners].some((o) => o.startsWith(`${engine}:`))).toBe(false);
    }
    for (const [key, alias] of Object.entries(bindings.aliases!.states)) {
      const monster = key.slice(0, key.indexOf(":"));
      expect(owners.has(`${monster}:${alias.state}`)).toBe(true);
    }
  });

  it("plays every row of an owner the table binds more than once", () => {
    // Wriggler:NASTY_BITE_MOVE carries both `vfx_bite` (a Spine rig) and
    // `vfx_wormy_impact`; a last-wins index kept only the second.
    const rows = bindings.bindings.filter((b) => b.owner === "Wriggler:NASTY_BITE_MOVE");
    expect(rows.length).toBeGreaterThan(1);
    const picks = effectsForEvent(
      { kind: "hurt", target: E, amount: 6, source: { kind: "move", monster: "Wriggler", id: "NASTY_BITE" } },
      bindings,
    );
    expect(picks).toHaveLength(rows.length);
  });
});


describe("the real table's deathFade section", () => {
  const b = readBindings();

  it.skipIf(!b)("carries the ten classes the v0.107.1 decomp overrides", () => {
    // Named rather than counted: a count passes just as happily on ten WRONG
    // rows, and this list is the whole reason `dissolveEffectFor` can gate at
    // all. The extractor's own completeness gate ties it to the decomp
    // (`scripts/test_extract_vfx_bindings.py::DeathFadeCoverageTest`); this
    // ties the PUBLISHED copy — the file the frontend fetches — to the same
    // list, which is the half a generator-only gate never covers.
    expect(Object.keys(b!.deathFade ?? {}).sort()).toEqual([
      "CeremonialBeast",
      "Crusher",
      "DecimillipedeSegment",
      "Fabricator",
      "GasBomb",
      "LivingFog",
      "PhantasmalGardener",
      "Rocket",
      "SoulNexus",
      "WaterfallGiant",
    ]);
  });

  it.skipIf(!b)("resolves all three Decimillipede segment kinds onto the one class", () => {
    // The real `aliases.monsters` hop, against the real table — the synthetic
    // version of this in `bindings.test.ts` proves the lookup handles an alias,
    // not that the published table still ships the three the engine emits.
    for (const kind of [
      "DecimillipedeSegmentFront",
      "DecimillipedeSegmentMiddle",
      "DecimillipedeSegmentBack",
    ]) {
      expect(shouldFadeAfterDeath(kind, b), kind).toBe(false);
    }
  });

  it.skipIf(!b)("gives the Kaiser Crab's arms no dissolve and a 2.5s death wait", () => {
    // GH #633's actual answer. The issue asked for a group-level dissolve for
    // the shared body; `Crusher.cs:57` / `Rocket.cs:37` say neither arm reaches
    // `NMonsterDeathVfx` at all, so there was never one to give it.
    for (const arm of ["Crusher", "Rocket"]) {
      expect(shouldFadeAfterDeath(arm, b), arm).toBe(false);
      expect(deathFadeFor(arm, b).deathAnimSeconds, arm).toBe(2.5);
    }
  });

  it.skipIf(!b)("leaves an ordinary monster out, so it keeps dissolving", () => {
    // The gate's other half. If the table ever grew a blanket row, every
    // corpse would stop dissolving and only this would notice.
    expect(shouldFadeAfterDeath("Cultist", b)).toBe(true);
  });
});
