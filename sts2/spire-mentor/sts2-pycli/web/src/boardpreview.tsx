// Dev-only visual harness (not shipped). ?attack=<ms> fires the attack anim,
// ?enemies=<n> puts n of them on the board so targeting can be aimed.
import { StrictMode, useEffect, useState } from "react";
import { createRoot } from "react-dom/client";
import "./theme.css";
import "./game-skin.css";
import Board from "./components/Board";
import { HintsFixture } from "./hints/HintsContext";
import type { Step } from "./api";

const P = "/preview";
const card = (index: number, name: string, cost: number, type: string, description: string, art: string, rarity?: string) =>
  ({ index, id: `CARD.${name}`, name, cost, type, rarity, description, can_play: true, upgraded: false,
     // Attacks pick a target, which is what makes the targeting arrow reachable
     // here at all — without it every card plays instantly and never aims.
     target_type: type === "Attack" ? "AnyEnemy" : undefined,
     image: `${P}/${art}` });

// Switchable preview enemy (?enemy=<key>) so any rig's idle/attack/hurt framing
// can be eyeballed. Keep rigs copied under public/preview-rig/<key>/.
const ENEMIES: Record<string, { name: string; rig: string; rig_scale: number; rig_bounds_h: number }> = {
  nibbit: { name: "Nibbit", rig: "/preview-rig/nibbit/nibbit", rig_scale: 0.28, rig_bounds_h: 146 },
  shrinker_beetle: { name: "Shrinker Beetle", rig: "/preview-rig/shrinker_beetle/shrinker_beetle", rig_scale: 0.25, rig_bounds_h: 161 },
  cultists: { name: "Cultist", rig: "/preview-rig/cultists/cultists", rig_scale: 0.28, rig_bounds_h: 305 },
  // Lives in the wiki (not vendored) — symlink like the crab rigs below:
  //   ln -s ../../../../sts2-wiki/site/public/images/monster_rigs/test_subject \
  //         public/preview-rig/test_subject
  test_subject: { name: "Test Subject #C1", rig: "/preview-rig/test_subject/test_subject", rig_scale: 0.3, rig_bounds_h: 292 },
  // Symlink: ln -s ../../../../sts2-wiki/site/public/images/monster_rigs/fake_merchant_monster \
  //          public/preview-rig/fake_merchant_monster
  fake_merchant: { name: "The Merchant???", rig: "/preview-rig/fake_merchant_monster/fake_merchant_monster", rig_scale: 0.25, rig_bounds_h: 350 },
  // A tall, action-heavy boss whose motion scan yields a large bottom-overscan
  // margin — the case the feet-anchored framing (SpineSprite) exists to keep
  // grounded. Lives in the wiki (not vendored) — symlink like the crab rigs below:
  //   ln -s ../../../../sts2-wiki/site/public/images/monster_rigs/knowledge_demon \
  //         public/preview-rig/knowledge_demon
  knowledge_demon: { name: "Knowledge Demon", rig: "/preview-rig/knowledge_demon/knowledge_demon", rig_scale: 0.28, rig_bounds_h: 642 },
  // The tallest rig in the game (497px of authored Bounds), so the case that
  // shows a giant is drawn at its authored scene scale like everything else —
  // there is no height clamp to fit it into the arena any more. Symlink:
  //   ln -s ../../../../sts2-wiki/site/public/images/monster_rigs/queen \
  //         public/preview-rig/queen
  queen: { name: "Queen", rig: "/preview-rig/queen/queen", rig_scale: 0.14, rig_bounds_h: 497 },
  // A tall single enemy under a pulled-back encounter camera (0.88 / Down*50,
  // see scene.ts CAMERA_BY_RIG) — the case where a tablet's compact framing
  // (stageZoom.ts) cropped the intent chip off the top of the stage. Symlink:
  //   ln -s ../../../../sts2-wiki/site/public/images/monster_rigs/bygone_effigy \
  //         public/preview-rig/bygone_effigy
  bygone_effigy: { name: "Bygone Effigy", rig: "/preview-rig/bygone_effigy/bygone_effigy", rig_scale: 0.32, rig_bounds_h: 600 },
};
const enemyKey = new URLSearchParams(location.search).get("enemy") ?? "shrinker_beetle";
const E = ENEMIES[enemyKey] ?? ENEMIES.shrinker_beetle;

// ?powers=<n> stacks N fake buffs on BOTH sides so the multi-row-effects layout
// (does the character stay put as rows are added? does anything in the room
// reach down over them?) can be eyeballed / screenshotted.
const nPowers = Math.max(0, Number(new URLSearchParams(location.search).get("powers") ?? 0) | 0);
const previewPowers = Array.from({ length: nPowers }, (_, i) => ({
  name: `Buff ${i + 1}`, amount: i + 1, description: "preview", image: `${P}/pow_vuln.png`,
}));

// ?scene=<key> swaps the whole enemy roster so multi-part / sprite-only cases
// can be eyeballed against the single-rig default:
//   kaiser_crab        — faithful shared rig: Crusher (left) + Rocket (right) arms
//   kaiser_crab_before — the OLD behavior: two independent full-crab combatants
//   decimillipede      — three per-segment rigs (front/middle/back) tiling into
//                        one connected millipede
// The crab/segment rigs are large LFS / live in the wiki, so they are NOT
// vendored under public/. To use these scenes locally, symlink the rigs, e.g.:
//   ln -s ../../../../sts2-wiki/site/public/images/monster_rigs/kaiser_crab \
//         public/preview-rig/kaiser_crab
//   for s in front middle back; do ln -s \
//     ../../../../sts2-wiki/site/public/images/monster_rigs/decimillipede_segment_$s \
//     public/preview-rig/decimillipede_segment_$s; done
//   ln -s ../../../../sts2-wiki/site/public/images/monsters/kaiser_crab.png \
//         public/preview/kaiser_crab.png   # static sprite for the _before scene
const CRAB = "/preview-rig/kaiser_crab/kaiser_crab";
const scene = new URLSearchParams(location.search).get("scene");
function sceneEnemies(): Record<string, unknown>[] {
  if (scene === "kaiser_crab" || scene === "kaiser_crab_before") {
    const shared = scene === "kaiser_crab";
    // Before: the OLD behavior — each arm is its own combatant rendering the WHOLE
    // crab (here as the static sprite, which is what production actually showed
    // since the multi-page rig failed to load). After: one shared rig + rig_part.
    const common = shared
      ? { rig: CRAB, rig_scale: 0.5, rig_bounds_h: null }
      : { rig: null, rig_scale: null, rig_bounds_h: null };
    return [
      // `kind` matters, and this scene used to omit it: `deathFade` in the
      // binding table is keyed on MonsterKind, and both arms carry
      // `deathAnimSeconds: 2.5` there (`DeathAnimLengthOverride`). Without it
      // the corpse fell back to the 0.9 s default and was gone from the board
      // before its own death animation finished — which is what hid GH #642's
      // explosion, fired 1.53 s into `left/die`.
      { index: 0, name: "Crusher", kind: "Crusher", hp: 44, max_hp: 70, block: 0,
        intents: [{ type: "Attack", damage: 16, hits: 1, intent_image: `${P}/intent_attack_4.png` }],
        ...common, image: `${P}/kaiser_crab.png`,
        powers: [{ name: "Strength", amount: 2, image: `${P}/pow_vuln.png` }],
        ...(shared ? { rig_group: "kaiser_crab", rig_part: "left" } : {}) },
      { index: 1, name: "Rocket", kind: "Rocket", hp: 52, max_hp: 70, block: 9,
        intents: [{ type: "Buff", intent_image: null }],
        ...common, image: `${P}/kaiser_crab.png`,
        powers: [],
        ...(shared ? { rig_group: "kaiser_crab", rig_part: "right" } : {}) },
    ];
  }
  if (scene === "decimillipede") {
    // Each segment is its OWN Spine rig (front/middle/back) — the workbench
    // backend picks them by `kind` since all three share the name "Decimillipede".
    const seg = (index: number, part: string, hp: number) => ({
      index, name: "Decimillipede", hp, max_hp: 40, block: 0,
      intents: [{ type: "Attack", damage: 7, hits: 1, intent_image: `${P}/intent_attack_4.png` }],
      rig: `/preview-rig/decimillipede_segment_${part}/decimillipede_segment_${part}`,
      rig_scale: null, rig_bounds_h: null, image: `${P}/decimillipede.png`, powers: [],
      rig_group: "decimillipede", rig_part: part,
    });
    return [seg(0, "front", 40), seg(1, "middle", 33), seg(2, "back", 28)];
  }
  // ?enemies=<n> repeats the preview rig n times. A single enemy is auto-
  // targeted, so aiming — the targeting arrow, the hover snap, the enemy tint —
  // has no repro at all without at least two.
  const n = Math.min(4, Math.max(1, Number(new URLSearchParams(location.search).get("enemies") ?? 1) | 0));
  // ?stolen=<n> gives each enemy n cards held hostage (the Thieving Hopper's
  // SwipePower), so the card pinned on the thief can be eyeballed against the
  // creature it hangs on — does it clear the HP bar, the intent, the neighbour?
  const nStolen = Math.max(0, Number(new URLSearchParams(location.search).get("stolen") ?? 0) | 0);
  const stolen = Array.from({ length: nStolen }, (_, i) =>
    card(-1, i % 2 ? "Bash" : "Strike", 2, "Attack",
         "Deal 8 damage. Apply 2 Vulnerable.", "card_bash.png", "Basic"));
  // `?kind=<MonsterKind>` stamps the enemy's raw kind on the wire shape. It is
  // what `deathFade` joins on, so it is the difference between a preview death
  // that dissolves and one that (correctly) does not — see `?die=`.
  const kind = new URLSearchParams(location.search).get("kind");
  return Array.from({ length: n }, (_, index) => ({
    index, name: E.name, ...(kind ? { kind } : {}), hp: 30 - index * 6, max_hp: 72, block: 0,
    intents: [{ type: "Attack", damage: 21, hits: 1, intent_image: `${P}/intent_attack_4.png` }],
    rig: E.rig, rig_scale: E.rig_scale, rig_bounds_h: E.rig_bounds_h,
    powers: nPowers ? previewPowers
      : [{ name: "Vulnerable", amount: 2, image: `${P}/pow_vuln.png` }],
    // `?boss=` rides on `image`, which is where pickScene reads the art slug.
    image: bossSlug ? `/assets/monsters/${bossSlug}.png` : `${P}/enemy.png`,
    ...(stolen.length ? { stolen_cards: stolen } : {}) }));
}

/** `?act=<Overgrowth|Hive|Glory|Underdocks>` picks the arena backdrop, the same
 *  way a live step's `meta.act_name` does — the cheapest way to eyeball a room's
 *  parallax stack.
 *
 *  `?boss=<art-slug>` overrides it with an encounter that brings its own room
 *  (`the_insatiable`, `vantom`, `waterfall_giant`, `kin_priest`, `queen`, …; see
 *  BOSS_SCENES in roomScenes.ts). It stamps the slug onto the preview enemies
 *  rather than naming a scene, so the harness exercises the SAME selection path
 *  a live fight does instead of a shortcut around it.
 *
 *  `?floor=<n>` moves the deterministic variant roll, so a room's `_a`/`_b`/`_c`
 *  alternatives can be flipped through without a live run. */
const params = new URLSearchParams(location.search);
const actName = params.get("act") ?? undefined;
const bossSlug = params.get("boss");
const previewFloor = Number(params.get("floor") ?? 3) | 0;

const step = {
  idx: 0, decision: "combat_play", source: "log", pv: null, chosen_action: null,
  meta: { turn: 3, step_no: 0, act_name: actName, act: 1, floor: previewFloor },
  scores: [{ stable_idx: 2, win_prob: 0.74, is_best: true }, { stable_idx: -1, win_prob: 0.49, is_best: false }],
  state: {
    decision: "combat_play", round: 3, energy: 3, max_energy: 3,
    draw_pile_count: 9, discard_pile_count: 4, exhaust_pile_count: 1,
    energy_image: `${P}/energy_icon.png`,
    player: { name: "The Ironclad", hp: 58, max_hp: 72, block: 12, gold: 62,
      image: `${P}/player_cutout.png`, rig: "/preview-rig/ironclad", rig_scale: 0.28, rig_bounds_h: 278,
      relics: [
        { name: "Akabeko", description: "Your first Attack each combat deals 8 additional damage.", image: `${P}/relic_akabeko.png` },
        { name: "Anchor", description: "Start each combat with 10 Block.", image: `${P}/relic_anchor.png` },
        { name: "Bag of Marbles", description: "At the start of combat, apply 1 Vulnerable to ALL enemies.", image: `${P}/relic_bag_of_marbles.png` },
        { name: "Vajra", description: "At the start of combat, gain 1 Strength.", image: `${P}/relic_vajra.png` },
      ],
      potions: [], max_potion_slot_count: 3 },
    // `?orbs=<filled>[,<capacity>]` hangs a Defect orb belt on the player —
    // the belt is scaled to the CREATURE, so it can only be eyeballed against
    // a real rig.
    ...previewOrbs(),
    player_powers: previewPowers,
    enemies: sceneEnemies(),
    hand: previewHand(),
  },
} as unknown as Step;

/** `?orbs=2` → 2 channelled orbs in a 3-slot queue; `?orbs=2,5` sets the
 *  capacity too. Absent, the board has no orb belt at all (the Ironclad's). */
function previewOrbs() {
  const raw = new URLSearchParams(location.search).get("orbs");
  if (!raw) return {};
  const [n, cap] = raw.split(",").map((v) => Math.max(0, Number(v) | 0));
  const types = ["Lightning", "Frost", "Dark", "Plasma", "Glass"];
  return {
    orb_slots: Math.max(cap || 3, n),
    orbs: Array.from({ length: n }, (_, i) => ({
      index: i, type: types[i % types.length], name: types[i % types.length],
      passive: 3 + i, evoke: 8 + i,
      image: `/assets/orb_atlas/${types[i % types.length].toLowerCase()}.png`,
    })),
  };
}

/** The fan, `?cards=<n>` cards long (default 5, the ref's hand). Sizes past
 *  the five fixtures cycle them — the fan's shape is what a longer hand is for
 *  (tighter overlap, deeper arc), not the card faces. `?cards=0` empties it. */
function previewHand() {
  const faces = [
    card(0, "Iron Wave", 1, "Attack", "Gain 5 Block. Deal 5 damage.", "card_iron_wave.png", "Common"),
    card(1, "Shrug It Off", 1, "Skill", "Gain 8 Block.\nDraw 1 card.", "card_shrug_it_off.png", "Common"),
    card(2, "Bash", 2, "Attack", "Deal 8 damage. Apply 2 Vulnerable.", "card_bash.png", "Basic"),
    card(3, "Pommel Strike", 1, "Attack", "Deal 9 damage. Draw 1 card.", "card_pommel_strike.png", "Uncommon"),
    card(4, "Bloodletting", 0, "Skill", "Lose 3 HP. Gain 2 Energy.", "card_bloodletting.png", "Rare"),
  ];
  // `?cards=0` is the EMPTY hand (the moment the last card is played), not the
  // default: it is what reproduces the strip-collapse the reserve in
  // `.gs-hand`'s min-height exists to stop. Only an absent param means "5".
  const raw = new URLSearchParams(location.search).get("cards");
  if (raw == null) return faces;
  const n = Number(raw) | 0;
  if (n < 1) return [];
  return Array.from({ length: n }, (_, i) => ({ ...faces[i % faces.length], index: i }));
}

/** `?debuff=<ms>` / `?buff=<ms>` — the second step of a two-step preview, so the
 *  power surfaces that only exist ON A TRANSITION can be eyeballed headlessly.
 *  `Board` derives `powerAdd` by diffing consecutive steps (`combatDiff.ts`), so
 *  a single static step can never show the applied panel, its burst, or
 *  `AnimShake`. The debuff arm is the one that wobbles the creature. */
function stepWithPower(name: string, amount: number): Step {
  const st = step.state as unknown as { player_powers: unknown[] };
  return {
    ...step,
    idx: 1,
    state: {
      ...step.state,
      player_powers: [...st.player_powers, { name, amount, description: "preview", image: `${P}/pow_vuln.png` }],
    },
  } as unknown as Step;
}

/** The second step of a death preview: the board with no enemies left. */
function stepWithNoEnemies(): Step {
  return { ...step, idx: 1, state: { ...step.state, enemies: [] } } as unknown as Step;
}

function Demo() {
  const params = new URLSearchParams(location.search);
  const delay = Number(params.get("attack"));
  const debuffDelay = Number(params.get("debuff"));
  const buffDelay = Number(params.get("buff"));
  const dieDelay = Number(params.get("die"));
  const enemyAnim = params.get("anim") ?? undefined;  // loop e.g. "hurt" to check framing
  const [attack, setAttack] = useState<{ target: number; nonce: number } | undefined>(undefined);
  const [shown, setShown] = useState<Step>(step);
  useEffect(() => {
    if (!delay) return;
    const t = setTimeout(() => setAttack({ target: 0, nonce: 1 }), delay);
    return () => clearTimeout(t);
  }, [delay]);
  useEffect(() => {
    // `?die=<ms>` — the enemies leave the board, which is exactly what the
    // engine does with a dead creature (`rust_game.py` drops it), so `Board`
    // mounts the corpse from its own snapshot and runs the whole death path:
    // die clip, chrome fade, dissolve (or not) and the removal timer. `?kind=`
    // sets the enemy's MonsterKind, which is what `deathFade` joins on — so
    // `?die=1600&kind=SoulNexus` is the "should not dissolve" case and the same
    // URL without `kind` is the control.
    if (!dieDelay) return;
    const t = setTimeout(() => setShown(stepWithNoEnemies()), dieDelay);
    return () => clearTimeout(t);
  }, [dieDelay]);
  useEffect(() => {
    // "Vulnerable" is typed Debuff and "Strength" Buff in the real
    // `sfx_map.json`, so these take the two branches of `GetTypeForAmount`
    // without the preview needing a valence override.
    if (debuffDelay) {
      const t = setTimeout(() => setShown(stepWithPower("Vulnerable", 2)), debuffDelay);
      return () => clearTimeout(t);
    }
    if (buffDelay) {
      const t = setTimeout(() => setShown(stepWithPower("Strength", 2)), buffDelay);
      return () => clearTimeout(t);
    }
  }, [debuffDelay, buffDelay]);
  // `?panel=1` shows the old right inspector column; default is a full-width board
  // so the arena matches the real (full-width) workbench layout for screenshots.
  const showPanel = params.get("panel") === "1";
  // ?minh=<px> forces the arena taller than the player column so the
  // bottom-align slack (which used to shove the hero up as buffs stacked) is
  // reproducible headlessly without a tall enemy rig.
  const minh = Number(params.get("minh") ?? 0) | 0;
  return (
    <>
    {minh > 0 && <style>{`.gs-arena{min-height:${minh}px}`}</style>}
    <div style={{ display: "grid", gridTemplateColumns: showPanel ? "1fr 320px" : "1fr", height: "100vh" }}>
      <div style={{ overflow: "auto", ...(showPanel ? { borderRight: "1px solid var(--border)" } : {}) }}>
        {/* A rendering preview: show the full board incl. the MCTS score
            badges, which live play hides behind the hints switch. */}
        <HintsFixture on>
          <Board step={shown} onEndTurn={() => {}} onPlayCard={() => {}} scores={step.scores} attack={attack} enemyAnim={enemyAnim} />
        </HintsFixture>
      </div>
      {showPanel && <div style={{ background: "var(--panel)" }} />}
    </div>
    </>
  );
}
createRoot(document.getElementById("root")!).render(<StrictMode><Demo /></StrictMode>);
