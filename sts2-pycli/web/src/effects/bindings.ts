import { assetUrl } from "../api";
import type { EffectEvent, EffectSource } from "./types";

/** One entry in the table's `effects` map: whether the extracted asset
 *  renders at all, and — when it doesn't — the effect to substitute. See
 *  design spec §1.4/§5. `fallback` is present on every `renderable: false`
 *  row (gated by `extract_vfx_bindings.py`'s own tests); still optional here
 *  because parsed JSON makes no such promise to the type checker. */
export interface BindingEffectInfo {
  renderable: boolean;
  fallback?: string;
}

/** One card/move -> effect row. `owner` is either a card id (`"BASH"`) or a
 *  `<PascalCase monster>:<SCREAMING_SNAKE state>` move key. `tint` is
 *  `#RRGGBB` (task 6 normalizes every tint to that form) or `null`. */
export interface BindingRow {
  owner: string;
  kind: "card" | "move";
  effect: string;
  tint: string | null;
  shape: string;
}

/** One published alias row: the id to look up instead, plus the C# line it
 *  was confirmed against (carried for review, not read by this module). */
export interface BindingAlias {
  reason: string;
}

/**
 * The reconciliations that must happen BEFORE a move key is looked up,
 * published with the table (`extract_vfx_bindings.py`'s `_MONSTER_ALIASES` /
 * `_STATE_ALIASES`) so this renderer and the Rust coverage gate
 * (`vfx_binding_coverage.rs`) cannot disagree about them.
 *
 * They used to live only in the Rust gate, which is how ~30 engine
 * `(monster, move)` pairs came to draw the generic slash here while that
 * gate reported full coverage — a green gate for a lookup nothing shared
 * with it.
 *
 * - `monsters`: engine monster id (what the combat log writes as
 *   `source_monster`) -> the decomp file stem the table actually keys its
 *   rows on. `MysteriousKnight` inherits `FlailKnight`'s move machine
 *   wholesale, the three `DecimillipedeSegment*` variants inherit the base
 *   class's, and `DenseVegetationWriggler` is an engine-only slot variant of
 *   `Wriggler` with no C# file at all.
 * - `states`: `"<monster>:<engine state id>"` -> the real C# state id, for
 *   the 18 ids that are a genuine RENAME (`SpinyToad:LASH` ->
 *   `TONGUE_LASH_MOVE`) rather than the `_MOVE`-suffix difference
 *   `ownerKeyCandidates` already handles mechanically. The monster half is
 *   the key AFTER the monster alias is applied.
 */
export interface BindingAliases {
  monsters: Record<string, BindingAlias & { monster: string }>;
  states: Record<string, BindingAlias & { state: string }>;
}

/**
 * What one monster class overrides about its own death, from
 * `MonsterModel`'s two virtuals. Only the exceptions are published, so a
 * monster ABSENT from `deathFade` takes the defaults: it fades, and its death
 * wait comes from its own rig.
 *
 * - `fade` — `NCreature.cs:1106` gates the whole `NMonsterDeathVfx` (the
 *   threshold dissolve) on `ShouldFadeAfterDeath`. `"never"` means the game
 *   gives this monster no dissolve at all. `"conditional"` means the C# is not
 *   a constant and `condition` carries the expression verbatim; the workbench
 *   has no way to evaluate one today and keeps dissolving, which is a stated
 *   choice rather than an oversight.
 * - `deathAnimSeconds` — `DeathAnimLengthOverride`. `NCreature.cs:1093` waits
 *   this long instead of the rig's remaining animation time, for a creature
 *   whose body is drawn somewhere other than its own animator. Present only
 *   where the game overrides it.
 */
export interface DeathFadeRow {
  fade?: "never" | "always" | "conditional";
  condition?: string;
  deathAnimSeconds?: number;
}

/** The extracted card/move -> VFX binding table (design spec §1.4). */
export interface Bindings {
  version: string;
  effects: Record<string, BindingEffectInfo>;
  bindings: BindingRow[];
  /** Optional for the same reason `aliases` is: a synthetic fixture need not
   *  spell it out. The real published table always carries it. */
  deathFade?: Record<string, DeathFadeRow>;
  /** Optional only because a synthetic fixture need not spell it out; the
   *  real published table always carries it, and `bindingsRealTable.test.ts`
   *  asserts as much. */
  aliases?: BindingAliases;
}

/**
 * Fetch the VFX binding table.
 *
 * Same shape as `manifest.ts`'s `loadManifest`: cache-busted (the art bucket
 * serves `immutable` at non-content-addressed URLs) and REJECTS on a failed
 * fetch rather than resolving to an empty/default table. That distinction
 * matters here specifically — `effectsForEvent(e, null)` already treats a
 * `null` table as "not fetched yet, keep today's behavior" for the race
 * before the first fetch resolves. If a *failed* fetch (e.g. a 404 because
 * the table never got published to the art tree) also quietly became
 * `null`, the two states would be indistinguishable: production would
 * silently render `vfx_attack_slash` for every hit while looking healthy.
 * `console.error` here — not `console.warn` — is what keeps a real failure
 * loud even if a caller's `.catch` only logs and moves on.
 */
export async function loadBindings(fetchImpl: typeof fetch = fetch): Promise<Bindings> {
  const bust = import.meta.env?.VITE_BUILD_ID ?? String(Date.now());
  const url = `${assetUrl("vfx/vfx_bindings.json")}?v=${encodeURIComponent(bust)}`;
  const r = await fetchImpl(url);
  if (!r.ok) {
    console.error(`vfx bindings fetch failed: ${r.status} ${url}`);
    throw new Error(`vfx bindings ${r.status}`);
  }
  return (await r.json()) as Bindings;
}

/** Per-`Bindings`-object owner index, built lazily and cached by object
 *  identity — `effectsForEvent` runs once per combat event, so re-scanning
 *  ~430 rows on every hit would be wasteful; the `Bindings` a caller holds
 *  (e.g. Board.tsx's React state) is a stable reference across renders.
 *
 *  One owner maps to a LIST, not a row: 15 owners carry more than one row
 *  because the C# builder chains more than one spawn call (a `WithHitFx`
 *  *and* a `WithHitVfxNode`, or a free-standing `VfxCmd`), and the game
 *  plays all of them. `new Map(rows.map(...))` — what this was — is
 *  last-wins, so it silently dropped 16 rows: `Wriggler:NASTY_BITE_MOVE`
 *  lost `vfx_bite`, `MechaKnight:CHARGE_MOVE` lost the tinted
 *  `vfx_big_slash_impact`, and `SOVEREIGN_BLADE`'s three rows became one,
 *  with "last in C# file order" as the only reason. */
const ownerIndexCache = new WeakMap<Bindings, Map<string, BindingRow[]>>();

function ownerIndex(b: Bindings): Map<string, BindingRow[]> {
  let idx = ownerIndexCache.get(b);
  if (!idx) {
    idx = new Map();
    for (const row of b.bindings) {
      const rows = idx.get(row.owner);
      if (rows) rows.push(row);
      else idx.set(row.owner, [row]);
    }
    ownerIndexCache.set(b, idx);
  }
  return idx;
}

/** Candidate owner keys for a source, in lookup order.
 *
 * Cards join on their bare id. Moves need three reconciliations, and the
 * Rust coverage gate (`vfx_binding_coverage.rs`) only proves the table
 * covers the engine when all three are applied — it applies all three, so
 * this must too, or the gate is green about a lookup nothing performs:
 *
 * 1. The MONSTER half can name a class that owns no rows: the table is keyed
 *    on decomp file stems, and `MysteriousKnight`/`DecimillipedeSegment*`/
 *    `DenseVegetationWriggler` inherit their move machines from another file
 *    (or have no file at all). `aliases.monsters` resolves that first.
 * 2. The `_MOVE` suffix is BIDIRECTIONAL, not a one-directional "always
 *    append": of the table's 226 move rows, 196 end in `_MOVE` (what C#'s
 *    `MoveState` constructor spells for most monsters) but 30 do not
 *    (`AxeRubyRaider:SWING_1`, `FrogKnight:BEETLE_CHARGE`,
 *    `OwlMagistrate:VERDICT`). Trying `id + "_MOVE"` then the bare `id`
 *    covers both with no per-monster list.
 * 3. 18 engine ids are a genuine RENAME of the C# id — a different word,
 *    abbreviation, typo or number scheme, with no rule to derive them from
 *    (`SpinyToad:LASH` -> `TONGUE_LASH_MOVE`, `MagiKnight:BOMB` ->
 *    `MAGIC_BOMB`). `aliases.states` is that hand list, published with the
 *    table; it is consulted LAST, so a real row always wins over an alias.
 */
export function ownerKeyCandidates(source: EffectSource, aliases?: BindingAliases): string[] {
  if (source.kind === "card") return [source.id];
  const monster = aliases?.monsters[source.monster]?.monster ?? source.monster;
  const keys = [`${monster}:${source.id}_MOVE`, `${monster}:${source.id}`];
  const renamed = aliases?.states[`${monster}:${source.id}`]?.state;
  if (renamed) keys.push(`${monster}:${renamed}`);
  return keys;
}

/** Every row bound to `source` — the first candidate key that matches at
 *  all, and then ALL of that key's rows. Empty when the source is unbound. */
function findRows(b: Bindings, source: EffectSource): BindingRow[] {
  const idx = ownerIndex(b);
  for (const key of ownerKeyCandidates(source, b.aliases)) {
    const rows = idx.get(key);
    if (rows) return rows;
  }
  return [];
}

/** The generic tail every fallback chain ends at — always renderable, and
 *  right for the plurality of real `WithHitFx` sites (design spec §1.5:
 *  180 of 357 literal sites). Also what an event with no source, or a
 *  source the table has no row for, plays — same as sub-project 1. */
const DEFAULT_EFFECT = "vfx_attack_slash";

/** Marks a substitution the table forced rather than one it named — an
 *  unbound owner (no row at all) is not the same failure as a bound row
 *  naming a non-renderable effect, but both need to be visible rather than
 *  silently indistinguishable from a correct, reviewed row. */
const UNBOUND = "<unbound>";

/** Resolve `effect` to something the renderer can actually draw, following
 *  the table's declared `fallback` chain (design spec §5) rather than
 *  assuming one hop is always enough. Cycle-guarded because the chain is
 *  hand-authored data, not something this lookup can prove acyclic. */
function resolveRenderable(effect: string, b: Bindings): { effect: string; fellBackFrom?: string } {
  let current = effect;
  const seen = new Set<string>();
  while (true) {
    const info = b.effects[current];
    // Absent from the map is NOT "not renderable" — the real table's own
    // gate (`test_every_row_names_a_known_effect`) guarantees every bound
    // row's effect appears here, so this only happens for a fixture that
    // doesn't bother declaring one; treat it as renderable rather than
    // forcing an unrequested fallback. Only an EXPLICIT `renderable: false`
    // triggers the chain.
    if (info?.renderable !== false) {
      return current === effect ? { effect: current } : { effect: current, fellBackFrom: effect };
    }
    seen.add(current);
    const next = info.fallback ?? DEFAULT_EFFECT;
    if (seen.has(next)) return { effect: DEFAULT_EFFECT, fellBackFrom: effect };
    current = next;
  }
}

/** One overlay effect selection: what to play, where to anchor it, and any
 *  tint/substitution the table carries. */
export interface EffectPick {
  effect: string;
  anchor: "origin" | "center" | "badge";
  tint: string | null;
  /** Set when the effect actually played differs from what a bound row (or
   *  the absence of one) named — either the table's declared fallback for a
   *  non-renderable effect, or `"<unbound>"` when no row matched at all. A
   *  silent substitution is indistinguishable from a correct row, which is
   *  how a bad table row would survive review — so it is not enough to
   *  RECORD it: `reportSubstitution` is what surfaces it, and Board.tsx
   *  calls that for every pick it plays. */
  fellBackFrom?: string;
}

/** Substitutions already logged, so a fight that replays the same unbound
 *  move 40 times logs it once. Module-scoped (not per-`Bindings`) because
 *  the point is one line per distinct gap per session. */
const reportedSubstitutions = new Set<string>();

function sourceLabel(source: EffectSource | undefined): string {
  if (!source) return "<no source>";
  return source.kind === "card" ? source.id : `${source.monster}:${source.id}`;
}

/**
 * Surfaces one substitution, once.
 *
 * `fellBackFrom` existed for a while with no consumer at all, which made it
 * exactly the silence it was added to break: all 65 non-renderable rows and
 * every unbound move substituted invisibly. This is the consumer.
 * `console.warn`, not `error` — a substitution is the table working as
 * designed (a declared fallback) or a real gap (`<unbound>`), and both are
 * review material rather than a broken render.
 */
export function reportSubstitution(source: EffectSource | undefined, pick: EffectPick): void {
  if (!pick.fellBackFrom) return;
  const key = `${sourceLabel(source)}|${pick.fellBackFrom}|${pick.effect}`;
  if (reportedSubstitutions.has(key)) return;
  reportedSubstitutions.add(key);
  const named = pick.fellBackFrom === UNBOUND ? "has no binding row" : `names ${pick.fellBackFrom}`;
  console.warn(`vfx: ${sourceLabel(source)} ${named}; playing ${pick.effect} instead`);
}

/**
 * Which overlay effects an event plays, selected from the extracted VFX
 * binding table (design spec §1.4) rather than a hardcoded guess.
 *
 * Returns a LIST because one owner can be bound to more than one effect and
 * the game plays all of them: 15 owners chain a `WithHitFx` with a
 * `WithHitVfxNode` or a `VfxCmd` in the same C# builder (`SOVEREIGN_BLADE`
 * chains three). Picking one of them — which is what taking the last row
 * for an owner amounted to — dropped 16 real rows, including
 * `Wriggler:NASTY_BITE_MOVE`'s `vfx_bite` rig and `MechaKnight:CHARGE_MOVE`'s
 * tinted `vfx_big_slash_impact`.
 *
 * `hurt` events resolve their `source` (Task 8) against the table: a card
 * source joins on its bare id, a move source joins through
 * `ownerKeyCandidates`' three reconciliations. No source, or no matching
 * row, plays the slash default and records `fellBackFrom: "<unbound>"`
 * rather than hiding the gap. A matched row whose effect is not yet
 * renderable resolves through the table's own declared fallback chain
 * instead of guessing one.
 *
 * `blockGain` is NOT table-driven — the table has no rows for it (defend
 * cards call no `WithHitFx`) — it keeps sub-project 1's fixed `vfx_block`.
 * `fullyBlocked`, `blockBreak` and `powerFlash` are the same shape: fixed,
 * single-effect events with no card/monster owner to look up, so each plays
 * one hardcoded manifest key rather than consulting the table —
 *
 * - `fullyBlocked` -> `vfx_block_spark_vfx`, anchored to the CREATURE
 *   (`"center"`, `NBlockSparkVfx.cs:86`'s `VfxSpawnPosition`). This is the
 *   same `CreatureCmd.cs` `WasFullyBlocked` call site that also plays the
 *   `block_hit` sound and Task 12's "Blocked" text — three separate visuals
 *   off one event, not a substitute for either.
 * - `blockBreak` -> `vfx_block_broken`, anchored to the block BADGE
 *   (`"badge"`, `NHealthBar.cs:575`), not the creature — the two block
 *   effects genuinely use different anchors (`sink.ts`'s `PlayedEffect.anchor`
 *   doc).
 * - `powerFlash` -> `vfx_power_flash_vfx`, anchored to the creature
 *   (`"center"`, `NCreature.OnPowerFlashed` plays it on the power's owner).
 *   The actual texture (the power's own icon) is not a binding-table
 *   concern — `Board.tsx` attaches it as `PlayedEffect.iconUrl` when it
 *   calls the sink, since this function has no access to the power icon
 *   cache.
 *
 * Kinds returning `[]` are handled by the existing CSS effects — the
 * damage number, the shake, the death fade — which sub-project 1 keeps.
 *
 * `bindings: null` (the table has not been fetched yet — see `loadBindings`)
 * behaves EXACTLY like sub-project 1: slash for every hit, `vfx_block` for
 * a block gain, no tint, no `fellBackFrom`. Dropping effects for the first
 * frames of a fight, while the table races the initial fetch, would be a
 * regression. The three fixed events above are unaffected either way — they
 * never consult `bindings`.
 */
export function effectsForEvent(e: EffectEvent, bindings: Bindings | null): EffectPick[] {
  if (e.kind === "hurt") {
    if (!bindings) return [{ effect: DEFAULT_EFFECT, anchor: "center", tint: null }];
    const rows = e.source ? findRows(bindings, e.source) : [];
    if (rows.length === 0) {
      return [{ effect: DEFAULT_EFFECT, anchor: "center", tint: null, fellBackFrom: UNBOUND }];
    }
    return rows.map((row) => {
      const resolved = resolveRenderable(row.effect, bindings);
      return {
        effect: resolved.effect,
        anchor: "center" as const,
        tint: row.tint,
        fellBackFrom: resolved.fellBackFrom,
      };
    });
  }
  if (e.kind === "blockGain") return [{ effect: "vfx_block", anchor: "center", tint: null }];
  if (e.kind === "fullyBlocked") return [{ effect: "vfx_block_spark_vfx", anchor: "center", tint: null }];
  if (e.kind === "blockBreak") return [{ effect: "vfx_block_broken", anchor: "badge", tint: null }];
  if (e.kind === "powerFlash") return [{ effect: "vfx_power_flash_vfx", anchor: "center", tint: null }];
  return [];
}


/**
 * What the game does when a monster of `kind` dies.
 *
 * `kind` is the wire's PascalCase `MonsterKind` (`server.py`'s
 * `normalize_kind`), which is also the decomp file stem the table is keyed on —
 * so the join is direct, except for the engine-only variants the published
 * `aliases.monsters` map already reconciles for move lookups. The three
 * `DecimillipedeSegment*` kinds are exactly that case: one C# class, three
 * engine kinds, and all three must find the class's `fade: "never"`.
 *
 * An unknown kind, a missing table, or a missing row all return `{}` — the
 * `MonsterModel` defaults — so every caller degrades to the behaviour that was
 * there before this table existed rather than to nothing at all.
 */
export function deathFadeFor(kind: string | null | undefined, b: Bindings | null): DeathFadeRow {
  if (!kind || !b?.deathFade) return {};
  const cls = b.aliases?.monsters[kind]?.monster ?? kind;
  return b.deathFade[cls] ?? {};
}

/**
 * Whether a dying monster of `kind` gets the threshold dissolve at all —
 * `NCreature.cs:1106`'s `monster.ShouldFadeAfterDeath` half of the guard.
 *
 * `"conditional"` reads as `true`: the workbench cannot evaluate the C#
 * expression (`WaterfallGiant`'s `PressureBuildupIdx == 0` is the only one at
 * v0.107.1), and dissolving is what it did before this gate existed. The
 * `condition` string rides along in the table for whoever implements it.
 */
export function shouldFadeAfterDeath(kind: string | null | undefined, b: Bindings | null): boolean {
  return deathFadeFor(kind, b).fade !== "never";
}
