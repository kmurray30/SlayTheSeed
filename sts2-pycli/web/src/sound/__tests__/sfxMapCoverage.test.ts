// The only file in src/ that needs Node globals/modules (node:fs, node:path,
// __dirname below) -- tsconfig.app.json sets `"types": []` so @types/node
// isn't ambient for the rest of the app (final review finding 5). This file
// is carved out of tsconfig.app.json's `include` and typechecked instead
// under tsconfig.node-tests.json, which opts back into `"types": ["node"]`
// for just this one file — a triple-slash `/// <reference types="node" />`
// here would NOT achieve the same scoping: @types/node's globals are a
// `declare global` augmentation, which TypeScript merges across the whole
// program once any file references the package, not just the file with the
// directive.
import { describe, it, expect } from "vitest";
import { readFileSync, existsSync, statSync, readdirSync } from "node:fs";
import { resolve, relative, extname, sep } from "node:path";
import { soundAssetRelPath, type SfxMap } from "../sfxMap";
import { ALL_UI_CUE_KINDS, type UiCueKind } from "../runCues";

const WEB_ROOT = resolve(__dirname, "../../..");
const AUDIO_ROOT = resolve(WEB_ROOT, "../../sts2-wiki/site/public/audio");
// eventAssetUrl prefixes `audio/`; AUDIO_ROOT already *is* that directory, so
// only the `event:/` scheme is stripped when checking the file on disk.

// The one file `readNonTestSources` excludes from its sweep — named here,
// once, so the exclusion below and the "exclusion path still exists" test
// further down can never drift apart. See that function's doc comment for
// why the exclusion exists at all.
const RUN_CUES_PATH = resolve(WEB_ROOT, "src/sound/runCues.ts");

/**
 * Every event-valued field the map can name. AMENDMENT (a): the original
 * sweep only walked `moves[].event`, so an unshipped `defaultAttack` (or the
 * newer `death`/`hurt` fields) passed the coverage checks below untouched.
 * `map.powers` holds `"Buff"`/`"Debuff"` valence strings, not events, and
 * must NOT be swept in — the `event:/` prefix filter keeps it out even if a
 * future field is added here by mistake.
 */
function allEvents(map: SfxMap): string[] {
  return [
    ...Object.values(map.generic),
    ...Object.values(map.cards),
    ...Object.values(map.relics),
    // AMENDMENT (d): the player's own attack/cast/death sounds. Without this
    // line the `characters` table would be the one part of the map whose
    // events nothing verifies against the shipped tree — and it is generated
    // from a NAMING CONVENTION (`CharacterModel.cs:200`), not parsed from a
    // literal, so a convention change is exactly the drift that would
    // otherwise reach the browser as a 404 per card played.
    ...Object.values(map.characters ?? {}).flatMap((c) => [c.attack, c.cast, c.die]),
    // AMENDMENT (e): the per-card HIT sounds. Same argument as (a) and (d) —
    // an unswept table is an unverified one — but this table is also the
    // reason the filter below had to grow a second scheme: 80 of its 81
    // entries are `res://debug_audio/` paths, not `event:/` ones.
    ...Object.values(map.cardHits ?? {}),
    // The Defect's orb queue and the per-act chest sound. Same argument as
    // every amendment above: an unswept table is an unverified one.
    ...Object.values(map.orbs ?? {}).flatMap((o) => [o.passive, o.evoke, o.channel]),
    ...Object.values(map.treasure ?? {}),
    ...Object.values(map.monsters).flatMap((m) => [
      ...m.moves.map((mv) => mv.event),
      m.defaultAttack,
      m.death,
      m.hurt,
    ]),
  ].filter(
    // BOTH of the game's sound schemes, deliberately. This used to accept
    // `event:/` alone, which did not merely skip the `res://debug_audio/`
    // entries — it made them INVISIBLE to every check in this file, so a
    // debug-audio path that named nothing on disk would have passed the
    // "audio file is present" test silently. `map.powers` holds valence
    // strings, not sound paths, and is still excluded by both prefixes.
    (e): e is string =>
      typeof e === "string" && (e.startsWith("event:/") || e.startsWith("res://debug_audio/")),
  );
}

/** Recursively lists every `.webm` under `dir`, as paths relative to `dir` with the extension stripped. */
function listWebmRelPaths(dir: string): string[] {
  const out: string[] = [];
  for (const ent of readdirSync(dir, { recursive: true, withFileTypes: true })) {
    if (!ent.isFile() || extname(ent.name) !== ".webm") continue;
    // Node's recursive-readdir Dirent exposes the containing dir as
    // `parentPath` (the pre-20.12 alias `path` is kept as a fallback in case
    // this repo's Node moves under us).
    // Cast through `unknown` (rather than reading `ent.path` directly) so
    // referencing the deprecated pre-20.12 `Dirent.path` alias here doesn't
    // trip the deprecation diagnostic on a field we only touch as a fallback.
    const direntPaths = ent as unknown as { parentPath?: string; path?: string };
    const parentPath = direntPaths.parentPath ?? direntPaths.path;
    if (!parentPath) throw new Error(`Dirent for ${ent.name} has neither parentPath nor path`);
    const rel = relative(dir, resolve(parentPath, ent.name));
    out.push(rel.split(sep).join("/").replace(/\.webm$/, ""));
  }
  return out;
}

/**
 * Strips `//`-to-end-of-line and `/* … *\/` spans, so a comment that merely
 * QUOTES call syntax (e.g. `// NOTE: gold pickups play emitUi("gold") for
 * consistency`) can't satisfy the reachability sweep the way review found it
 * could — see the residual-limits note on `readNonTestSources` and the
 * "every supported UI cue kind…" test below.
 *
 * Deliberately naive: it does not know about a line- or block-comment marker
 * appearing inside a string or template literal, so it will occasionally
 * strip real code too
 * (e.g. a URL literal containing `//`). That trade is intentional — it
 * produces a false FAILURE, which is fail-safe (a real call site briefly
 * reads as missing, someone investigates, finds the truth) and immediately
 * diagnosable from the flagged line. The alternative failure mode — a false
 * PASS from an unstripped comment — is invisible, which is what actually
 * happened here. Do not make this string-literal-aware; that trade points
 * the wrong direction for a test whose entire job is refusing to be fooled.
 */
function stripComments(text: string): string {
  return text.replace(/\/\*[\s\S]*?\*\//g, "").replace(/\/\/.*$/gm, "");
}

/**
 * Concatenates every non-test `.ts`/`.tsx` source under `dir` — with
 * comments stripped (see `stripComments`) — for the coarse call-site regex
 * search in the "UI cue reachability" suite below.
 *
 * `RUN_CUES_PATH` (`sound/runCues.ts`) is deliberately excluded even though
 * it isn't a test file: it's the file that DEFINES `UiCueKind` and
 * `UI_KEYS`, so it already contains every kind name as a quoted literal (the
 * string-literal union type, and `UI_KEYS`' own values — e.g.
 * `mapOpen: { key: "mapOpen", ... }`). Sweeping it in would make the
 * reachability check pass even with every real call site deleted — verified
 * by mutation (see task-11-report.md): deleting both `emitUi("mapOpen")`
 * call sites from App.tsx only turns this check red with runCues.ts excluded
 * from the sweep. Because this exclusion is what makes the reachability test
 * non-vacuous, the "the runCues.ts exclusion path still exists" test below
 * asserts `RUN_CUES_PATH` resolves to a real file — a rename or move of that
 * file would otherwise silently stop matching here and the reachability
 * test would go back to grading its own lookup table.
 */
function readNonTestSources(dir: string): string {
  const chunks: string[] = [];
  for (const ent of readdirSync(dir, { recursive: true, withFileTypes: true })) {
    if (!ent.isFile()) continue;
    if (!/\.tsx?$/.test(ent.name)) continue;
    if (ent.name.includes(".test.")) continue;
    // Cast through `unknown` (rather than reading `ent.path` directly) so
    // referencing the deprecated pre-20.12 `Dirent.path` alias here doesn't
    // trip the deprecation diagnostic on a field we only touch as a fallback.
    const direntPaths = ent as unknown as { parentPath?: string; path?: string };
    const parentPath = direntPaths.parentPath ?? direntPaths.path;
    if (!parentPath) throw new Error(`Dirent for ${ent.name} has neither parentPath nor path`);
    const full = resolve(parentPath, ent.name);
    if (full.split(sep).includes("__tests__")) continue;
    if (full === RUN_CUES_PATH) continue;
    chunks.push(stripComments(readFileSync(full, "utf8")));
  }
  return chunks.join("\n");
}

// Both inputs are extracted from a game install (`just import-game`) and are
// not published: without them the suite skips instead of failing.
const SFX_MAP_PATH = resolve(WEB_ROOT, "public/sfx_map.json");
const GAME_ASSETS_PRESENT = existsSync(SFX_MAP_PATH) && existsSync(AUDIO_ROOT);

describe.skipIf(!GAME_ASSETS_PRESENT)("sfx_map.json coverage", () => {
  const map = (GAME_ASSETS_PRESENT
    ? JSON.parse(readFileSync(SFX_MAP_PATH, "utf8"))
    : {}) as SfxMap;

  it("is generated from the version we ship", () => {
    expect(map.version).toBe("v0.107.1");
  });

  it("is not empty", () => {
    // A regex that stops matching after a game update fails here rather than
    // shipping a silently mute workbench.
    expect(Object.keys(map.generic).length).toBeGreaterThan(5);
    expect(Object.keys(map.monsters).length).toBeGreaterThan(20);
  });

  // AMENDMENT (c) supersedes the original existsSync-only check: a file that
  // exists can still be silent (see the known-silent-file note in
  // task-11-report.md), and a strict size floor catches the empty/near-empty
  // case for free. The smallest shipped file today is 1299 bytes, so a floor
  // of 1000 has real headroom without decoding audio in a unit test.
  it("names only events whose audio file is present and non-trivial", () => {
    const bad = [...new Set(allEvents(map))]
      .map((e) => resolve(AUDIO_ROOT, `${soundAssetRelPath(e)}.webm`))
      .filter((p) => !existsSync(p) || statSync(p).size < 1000);
    expect(bad).toEqual([]);
  });

  // AMENDMENT (b): the reverse direction. Shipped-but-unreachable is the
  // failure this feature keeps producing (`heal` shipped with no code path —
  // Ruling 28; 91 per-enemy death sounds sat unreachable behind
  // triggers.ts:81 until Ruling 29). `existsSync` cannot see either
  // direction, so this asserts a CEILING on orphans: the number can only
  // fall (recovering one narrows the ceiling), never silently regress.
  //
  // Measured on the shipped tree (2026-08-24, post-Task-8b): 566 files ship,
  // the map names 273 distinct events, 0 are missing, 293 are orphaned. Most
  // orphans are unreachable BY DESIGN: per-move buff/cast/stun/idle_loop/
  // spawn flavour, and `_die` files whose monster class declares no
  // `DeathSfx` the decomp names (see the known-silent lagavulin_matriarch_die
  // case in task-11-report.md — it's one of these 293, not a live bug).
  // Nothing in the decomp names them, so absent beats wrong. Raise the
  // ceiling only with a reason.
  //
  // Raised 293 -> 295, then back to 293 (2026-08-24, final-review finding 1
  // fix, corrected in the 2026-08-24 re-review's finding 4): `monsters`/
  // `cards`/`relics` are now keyed by display title, not class name (the wire
  // sends the title; see sts2-wiki/scripts/build_sfx_map.py's
  // `_class_titles`). The first-round fix dropped TestSubject entirely
  // because its STATIC title is "Test Subject #C{Count}" (an unresolved
  // runtime template), orphaning its 2 audio files (test_subject_bite,
  // test_subject_slash) and raising the ceiling to 295. The re-review found
  // that's the wrong call: the workbench's own bridge
  // (`sts2_hint.display_maps.monster_title`) already strips that exact
  // template tail before the name ever reaches the wire, so "Test Subject"
  // (the bare species name) IS a stable, real wire string —
  // `_strip_unresolved_template` now keys TestSubject under it, recovering
  // both files and taking the ceiling back down to 293.
  //
  // Lowered 293 -> 277 (2026-08-24, player-attack-sound fix): the map now
  // carries a `characters` table, so all 15 of the five characters'
  // `_attack`/`_cast`/`_die` files became reachable at once (274 named ->
  // 289). Every attack card now plays its character's own sound, which is
  // what the game does and what the workbench was silently missing — the
  // reason an Ironclad Strike sounded like nothing but a card woosh.
  //
  // Lowered 277 -> 275 (same day, gold-tier fix): gold_2/gold_3 became
  // reachable once `goldGainCue` started thresholding on the amount gained
  // the way `PlayerCmd.cs:152` does. `card_movement_B_play_into_discard`
  // became an orphan in the same change and is EXPECTED to stay one — the
  // branch of `SfxCmd.PlayCardSwooshSfx` that would play it is unreachable
  // in v0.107.1 (see build_sfx_map.py's `cardPlay` note), so the game itself
  // never plays that file either.
  //
  // Raised 275 -> 320 (2026-08-24, debug-audio layer): the tree grew from 566
  // to 618 files because `extract_sfx` now also copies the game's SECOND
  // sound layer, `res://debug_audio/` — the plain Godot streams
  // `NDebugAudioManager` plays for moments FMOD has no event for.
  //
  // Lowered 320 -> 279 in the same session, by wiring the rest of that layer
  // plus the FMOD events it exposed as neighbours: the turn banners and
  // battle start, card deal/exhaust/select, the Defect's whole orb table
  // (channel/evoke/passive, FMOD and debug mixed), potions, the rest-site
  // jingles, the per-act chest sounds, the shop merchant, Osty's summon, the
  // deck-op trio (smith/transform/enchant), the universal monster death fade,
  // and the card-signature sounds that live in vfx nodes rather than in the
  // card (Hellraiser, Fan of Knives, Hyperbeam). Named events: 273 -> 339.
  //
  // What is LEFT is left on purpose, and in four groups — check which one a
  // new orphan belongs to before raising this number:
  //  1. the game never plays it. `map_hover`, `regent_intro`, `ui_click.wav`,
  //     `STS_SFX_BurnCard_v1` and `relic_*` (the nine material variants) are
  //     referenced NOWHERE in the decompilation; `doom_apply` has a
  //     `TmpSfx` constant and zero callers.
  //  2. the orb slot is superseded. All five `*_orb_channel.mp3` lose to an
  //     FMOD override, `GlassOrb` never calls `PlayEvokeSfx()`, and Lightning
  //     overrides all three. `load_orb_sfx` reads the call graph, so these
  //     drop out by construction rather than by a denylist.
  //  3. no workbench surface: main menu (`SOTE_Logo…`), character-select
  //     unlock, the run-history screen (`STS_DeathStinger…`), multiplayer
  //     (`map_ping`, `map_split_tick`), the timeline screens.
  //  4. it fires on a specific event OPTION's outcome (`hey` in Jungle Maze,
  //     `hiss` in Dense Vegetation, `doll_room_amb`), and the wire exposes
  //     only the resulting state, not which branch ran.
  it("does not ship a large body of audio nothing can reach", () => {
    const CEILING = 279;
    const named = new Set(allEvents(map).map(soundAssetRelPath));
    const shipped = listWebmRelPaths(AUDIO_ROOT);
    const orphans = shipped.filter((f) => !named.has(f)).sort();
    // Pinned at the exact measured value (not padded) so a single map/asset
    // change that regresses coverage — e.g. one `death` key removed from a
    // monster entry — trips this immediately rather than hiding inside slack.
    // Verified by mutation: deleting BowlbugEgg's `death` key alone (293 ->
    // 294 orphans) fails only this assertion. Lower this number, never raise
    // it without a stated reason.
    //
    // On failure, print the full orphan list (not just the count) so a
    // maintainer can tell "new audio shipped ahead of being wired" (usually
    // fine, raise the ceiling with that reason) from "a previously-named
    // event silently stopped being named" (a real regression, e.g. a dropped
    // `death` key) without writing a diff script first.
    const message = orphans.length > CEILING
      ? `orphan count ${orphans.length} exceeds ceiling ${CEILING} (+${orphans.length - CEILING}); full orphan list:\n${orphans.join("\n")}`
      : undefined;
    expect(orphans.length, message).toBeLessThanOrEqual(CEILING);
    expect(shipped.length).toBeGreaterThan(500);
  });
});

/**
 * A CALL-SHAPED pattern for `kind`: `emitUi("kind")` or `cueForUi(<anything
 * within ~80 chars>, "kind")` — not just any quoted occurrence of the name.
 *
 * RULING (task-11 fix round 2): the original word-shaped regex
 * (`/["'`]kind["'`]/` over all of `src`) was proven to pass on a lie. It
 * matched Shop.tsx's unrelated `"relic" | "potion"` ware-kind string even
 * though no call site ever passes `"relic"` to `emitUi`/`cueForUi` — caught
 * by review, confirmed by mutation (see task-11-report.md: renaming Shop's
 * ware-kind strings broke nothing, proving they were never load-bearing for
 * this check). The same class of false pass was sitting dormant for `click`
 * (`addEventListener("click", …)` in catalog/{graph,main,heatmap}.ts) and
 * `energy` (`alt="…energy"` in CardFrame.tsx) — both currently masked by
 * `KNOWN_UNREACHABLE`, so shrinking that list without this fix would have
 * revived the same failure mode. Anchoring the match to the actual call
 * syntax closes all of these at once, while staying a coarse text search —
 * no parser.
 */
function callSiteRe(kind: string): RegExp {
  const q = `["'\`]${kind}["'\`]`;
  return new RegExp(`emitUi\\(\\s*${q}\\s*\\)|cueForUi\\([\\s\\S]{0,80}?${q}`);
}

describe("UI cue reachability", () => {
  // Each gap is deliberate, for a stated reason:
  const KNOWN_UNREACHABLE: UiCueKind[] = [
    // A combat moment, so it belongs to `triggers.ts`'s `GENERIC` table, not
    // the run-level layer — that table has no entry either.
    "energy",
    // No canonical click site exists; wiring every button in the app is a
    // different task, not this one.
    "click",
    // The map has no `victory` event, so `cueForUi` resolves it to `null` by
    // design. A win is silent, deliberately.
    "victory",
  ];

  it("every supported UI cue kind is actually called somewhere", () => {
    // Static reachability, not behaviour: proves a call-shaped string exists
    // OUTSIDE comments, which is the one thing the unit tests cannot show.
    // App.tsx has no render harness, so deleting `emitUi("mapOpen")` is
    // otherwise invisible — the full suite was verified to stay green with
    // both map cue calls removed (Task 10's review, closed here — see
    // task-11-report.md for the out-of-tree mutation run).
    //
    // RESIDUAL LIMITS — read before "fixing" this further. This is a coarse
    // text sweep, not a proof of correct behaviour, and it has been fooled
    // three times in review (task-11-report.md carries all three, each with
    // its mutation): (1) sweeping `runCues.ts` itself, whose own lookup table
    // quotes every kind name — closed by excluding that one file; (2) an
    // unrelated identifier that happens to share a kind's name (Shop.tsx's
    // `"relic"` ware-kind string) — closed by anchoring the match to call
    // syntax; (3) a comment that merely QUOTES call syntax without any real
    // call existing — closed by stripping comments before matching. If a
    // fourth way to fool this text sweep turns up, the answer is a real
    // App-level render test, not a smarter regex — this file stops here.
    //
    // `callSiteRe`'s ~80-character window between `cueForUi(` and the kind
    // literal is a DELIBERATE trade, not a number to tune: a first argument
    // longer than that produces a false FAILURE (the real call site reads as
    // missing), which is fail-safe and trivially diagnosable by reading the
    // flagged line — unlike a false PASS, which is invisible. Do not widen it
    // to "fix" a false failure; widening trades a safe, loud failure mode for
    // an unsafe, silent one.
    const src = readNonTestSources(resolve(WEB_ROOT, "src"));
    for (const kind of ALL_UI_CUE_KINDS) {
      if (KNOWN_UNREACHABLE.includes(kind)) continue;
      expect(src, `no call site passes "${kind}" to emitUi/cueForUi`).toMatch(callSiteRe(kind));
    }
  });

  it("the known-unreachable list is honest", () => {
    // Pins the three deliberate gaps so recovering one forces this list to
    // shrink, and so nobody can quietly ADD to it to make a failure go away.
    expect([...KNOWN_UNREACHABLE].sort()).toEqual(["click", "energy", "victory"]);
  });

  it("the runCues.ts exclusion path still exists", () => {
    // `readNonTestSources` excludes RUN_CUES_PATH so the reachability check
    // above isn't graded against runCues.ts's own `UI_KEYS` lookup table
    // (see that function's doc comment). If runCues.ts is ever renamed or
    // moved, this string stops matching any real file, the exclusion
    // silently stops excluding anything, and "every supported UI cue kind
    // is actually called somewhere" goes back to being satisfied by the
    // kind's own definition instead of a real call site — passing even with
    // every call site deleted. This assertion fails loudly instead.
    expect(existsSync(RUN_CUES_PATH)).toBe(true);
  });
});

// Final review finding 9 (minor, 2026-08-23): `stripComments` closed the
// eleventh "test that cannot fail" (a comment merely QUOTING call syntax
// satisfying the reachability sweep), but only against the specific decoy
// that prompted the fix, which no longer exists in the tree — removing
// `stripComments(...)` from `readNonTestSources` above left all 549 tests
// green. This pins the function directly rather than relying on a decoy.
describe("stripComments", () => {
  it("strips a line comment to end of line", () => {
    expect(stripComments('const x = 1; // emitUi("gold")\nconst y = 2;')).toBe(
      "const x = 1; \nconst y = 2;",
    );
  });

  it("strips a block comment, including one spanning multiple lines", () => {
    expect(stripComments("a(); /* emitUi(\n  \"gold\"\n) */ b();")).toBe("a();  b();");
  });

  it("does not touch real code with no comments", () => {
    const code = 'emitUi("gold");';
    expect(stripComments(code)).toBe(code);
  });

  it("a comment merely quoting a call site no longer satisfies a raw substring search", () => {
    // The exact shape the reachability sweep was fooled by (task-11-report.md):
    // a comment that quotes emitUi("gold") with no real call anywhere in the
    // string. Stripped, the literal substring must not survive.
    const src = '// NOTE: gold pickups play emitUi("gold") for consistency\nconst z = 1;';
    expect(stripComments(src)).not.toContain('emitUi("gold")');
  });
});
