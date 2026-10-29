//! Cross-engine workload W1 ("site") on sansb/sts2-sim's v0.111.0 engine.
//!
//! NOT part of sansb/sts2-sim. Written by the spire-mentor benchmark
//! (`scripts/site-bench/xsim/sansb/` in spire-mentor) and dropped into
//! `v0.111.0/engine/examples/` of a scratch clone; Cargo discovers it as an
//! example target. It uses only the crate's public library surface, the same
//! calls the crate's own `bench` and `composition_sweep` use.
//!
//! # Workload (spire-mentor's site benchmark "M1", as faithfully as this engine allows)
//!
//! * Ironclad, ascension 0, the 10-card starter deck (5 Strike, 4 Defend,
//!   1 Bash) at 80/80 HP, plus Burning Blood (`--relics none` drops it);
//!   no potions, 3 empty belt slots. NOTE: this engine holds Burning Blood as
//!   a census-inert relic (inventory only; its post-victory +6 heal is outside
//!   its combat-only scope), so it adds no per-action relic dispatch here.
//! * One Act-1 Overgrowth encounter per seed, round-robin: seed `s` fights
//!   `ENCOUNTERS[s % len]`, in spire-mentor's registry order (the 22 ids
//!   below, identical to `scripts/site-bench/bench/w1_encounters.txt`).
//!   `--encounters a,b,c` replaces the list (e.g. to run the intersection
//!   with another engine).
//! * Real monster AI: the start state is the engine's own combat opening
//!   (`entry::build_root`: monster creation with rolled HP, roster draws on
//!   the per-fight Encounter stream, opening shuffle, room-entry and
//!   before-combat relic hooks, first player turn), built from a synthetic
//!   schema-20 save exactly the way the crate's `composition_sweep` example
//!   builds its roots (the checked-in `fixtures/entry_builder_v1.json` save,
//!   re-pointed). Every run stream is derived from the run-seed string
//!   `XSIMW1<s>` at counter 0 with the crate's own `rng::run_stream_at_zero`.
//! * Start state built once per seed, UNTIMED (opening + admission).
//! * Each fight: clone the start state (TIMED) and play uniformly random
//!   legal actions until the fight ends. This engine's only transition API is
//!   `engine::apply_action_into(&state, ..) -> Result<HotState>` (a successor
//!   per action, i.e. clone-and-apply per transition); spire-mentor's engine
//!   mutates in place. That is the engine's native cost, not a harness choice.
//! * `--reps` fights per seed (default 50), `--seeds` seeds (default 2200).
//! * One thread. Policy RNG: SplitMix64 seeded `i * 1_000_003 + r` (i = seed
//!   index within the run, r = rep), `next % n` (as the crate's own bench).
//!
//! Output: progress/diagnostics on stderr, ONE JSON object on the last stdout
//! line. `fights`, `actions`, `wins`, `digest`, `action_checksum` and
//! `roots_digest` depend only on engine behaviour, so two runs of the same
//! binary with the same flags must print identical values.
//!
//! Build: `cargo build --release --example xsim_w1_site` (crate release
//! profile: thin LTO, codegen-units 1, panic=abort, no target-cpu flags,
//! system allocator).

use std::time::Instant;

use serde_json::{Value, json};
use sts_sim::boundary::HotBoundary;
use sts_sim::canonical::CanonicalStateV2;
use sts_sim::catalog::{Catalog, GameBuild};
use sts_sim::content_tables::UNLOCK_EPOCH_UNIVERSE_V1101;
use sts_sim::engine::{self, Action, Event, LegalActionBuffer, SelectionAnswer};
use sts_sim::entry::{self, EntryInput, EntryRequest, RootOutcome};
use sts_sim::hot::{HotState, PileId};

/// spire-mentor's `ACT1_ENCOUNTERS` (sts2-content/src/encounters/act1_overgrowth.rs),
/// registry order. These are the game's own `ModelId.Entry` strings, which is
/// also the wire id this engine takes (`ENCOUNTER.<id>`); the three elites
/// resolve to sansb's registered keys `BYGONE_EFFIGY` / `BYRDONIS` /
/// `PHROG_PARASITE` by its substring rule (`entry/opening/roster.rs`), while
/// the per-fight Encounter stream hashes the full id, as the game does.
const W1_ENCOUNTERS: [&str; 22] = [
    "SHRINKER_BEETLE_WEAK",
    "FUZZY_WURM_CRAWLER_WEAK",
    "NIBBITS_WEAK",
    "SLIMES_WEAK",
    "CUBEX_CONSTRUCT_NORMAL",
    "FLYCONID_NORMAL",
    "FOGMOG_NORMAL",
    "INKLETS_NORMAL",
    "MAWLER_NORMAL",
    "NIBBITS_NORMAL",
    "OVERGROWTH_CRAWLERS",
    "RUBY_RAIDERS_NORMAL",
    "SLIMES_NORMAL",
    "SLITHERING_STRANGLER_NORMAL",
    "SNAPPING_JAXFRUIT_NORMAL",
    "VINE_SHAMBLER_NORMAL",
    "BYGONE_EFFIGY_ELITE",
    "BYRDONIS_ELITE",
    "PHROG_PARASITE_ELITE",
    "VANTOM_BOSS",
    "CEREMONIAL_BEAST_BOSS",
    "THE_KIN_BOSS",
];

const USAGE: &str = "usage: xsim_w1_site [--seeds N] [--seed-start K] [--reps R] \
                     [--encounters ID,ID,...] [--relics burning-blood|none] \
                     [--max-steps M] [--policy uniform|instance] [--dump-root SEED:PATH] [--quick]";

struct Args {
    seeds: u64,
    seed_start: u64,
    reps: u64,
    encounters: Vec<String>,
    burning_blood: bool,
    max_steps: u64,
    dump_root: Option<(u64, String)>,
    policy: Policy,
}

fn parse_args() -> Args {
    let argv: Vec<String> = std::env::args().skip(1).collect();
    let mut args = Args {
        seeds: 2200,
        seed_start: 0,
        reps: 50,
        encounters: W1_ENCOUNTERS.iter().map(|s| s.to_string()).collect(),
        burning_blood: true,
        max_steps: 5000,
        dump_root: None,
        policy: Policy::Uniform,
    };
    let mut i = 0;
    let value = |i: usize| -> &str {
        argv.get(i + 1).map(String::as_str).unwrap_or_else(|| {
            eprintln!("{} requires a value; {USAGE}", argv[i]);
            std::process::exit(2)
        })
    };
    let int = |i: usize| -> u64 {
        value(i).parse().unwrap_or_else(|_| {
            eprintln!("{} expects an integer; {USAGE}", argv[i]);
            std::process::exit(2)
        })
    };
    while i < argv.len() {
        match argv[i].as_str() {
            "--seeds" => args.seeds = int(i),
            "--seed-start" => args.seed_start = int(i),
            "--reps" => args.reps = int(i),
            "--max-steps" => args.max_steps = int(i),
            "--dump-root" => {
                // Write seed SEED's start state (sts-sim-canonical-v2) to PATH.
                let (seed, path) = value(i).split_once(':').unwrap_or_else(|| {
                    eprintln!("--dump-root expects SEED:PATH; {USAGE}");
                    std::process::exit(2)
                });
                let seed = seed.parse().unwrap_or_else(|_| {
                    eprintln!("--dump-root SEED must be an integer; {USAGE}");
                    std::process::exit(2)
                });
                args.dump_root = Some((seed, path.to_string()));
            }
            "--encounters" => {
                args.encounters = value(i)
                    .split(',')
                    .map(|s| s.trim().trim_start_matches("ENCOUNTER.").to_string())
                    .filter(|s| !s.is_empty())
                    .collect()
            }
            "--policy" => {
                args.policy = match value(i) {
                    "uniform" => Policy::Uniform,
                    "instance" => Policy::Instance,
                    other => {
                        eprintln!("--policy must be uniform or instance, got {other:?}");
                        std::process::exit(2)
                    }
                }
            }
            "--relics" => {
                args.burning_blood = match value(i) {
                    "burning-blood" => true,
                    "none" => false,
                    other => {
                        eprintln!("--relics must be burning-blood or none, got {other:?}");
                        std::process::exit(2)
                    }
                }
            }
            "--quick" => {
                // 2 seeds per encounter of the default list, 5 fights each.
                args.seeds = 44;
                args.reps = 5;
                i += 1;
                continue;
            }
            "-h" | "--help" => {
                println!("{USAGE}");
                std::process::exit(0)
            }
            other => {
                eprintln!("unknown flag {other:?}; {USAGE}");
                std::process::exit(2)
            }
        }
        i += 2;
    }
    if args.seeds == 0 || args.reps == 0 || args.encounters.is_empty() {
        eprintln!("--seeds, --reps and the encounter list must be non-empty; {USAGE}");
        std::process::exit(2)
    }
    args
}

/// The checked-in synthetic schema-20 save (Ironclad starter deck + Burning
/// Blood vs Toadpoles), the same template `examples/composition_sweep` uses.
fn template() -> Value {
    let fixture: Value = serde_json::from_str(include_str!("../fixtures/entry_builder_v1.json"))
        .expect("the entry-builder fixture is JSON");
    fixture["cases"][0]["save"].clone()
}

/// The node kind an encounter is entered as (same rule as composition_sweep).
fn node_type(encounter: &str) -> &'static str {
    if encounter.ends_with("_BOSS") {
        "boss"
    } else if encounter.ends_with("_ELITE") {
        "elite"
    } else {
        "monster"
    }
}

fn run_seed_string(seed: u64) -> String {
    format!("XSIMW1{seed:06}")
}

/// One synthetic save: Ironclad A0, 5 Strike / 4 Defend / 1 Bash, 80/80 HP,
/// Burning Blood (or no relic), no potions, every stream at counter 0 derived
/// from the run-seed string.
fn save_json(template: &Value, seed: u64, burning_blood: bool) -> String {
    let mut save = template.clone();
    let run_seed = run_seed_string(seed);
    save["ascension"] = json!(0);
    save["rng"]["seed"] = json!(run_seed);
    let mut names: Vec<String> = save["rng"]["rngs"]
        .as_object()
        .expect("the template records its run streams")
        .keys()
        .cloned()
        .collect();
    // The template predates the orb stream; composition_sweep adds it too.
    if !names.iter().any(|n| n == "combat_orbs") {
        names.push("combat_orbs".to_string());
    }
    for name in names {
        let stream = sts_sim::rng::run_stream_at_zero(&run_seed, &name);
        save["rng"]["rngs"][&name] = json!({
            "counter": stream.counter,
            "s0": stream.words[0], "s1": stream.words[1],
            "s2": stream.words[2], "s3": stream.words[3],
        });
    }
    let mut deck: Vec<Value> = Vec::new();
    for _ in 0..5 {
        deck.push(json!({"floor_added_to_deck": 1, "id": "CARD.STRIKE_IRONCLAD"}));
    }
    for _ in 0..4 {
        deck.push(json!({"floor_added_to_deck": 1, "id": "CARD.DEFEND_IRONCLAD"}));
    }
    deck.push(json!({"floor_added_to_deck": 1, "id": "CARD.BASH"}));
    let epochs: Vec<String> = UNLOCK_EPOCH_UNIVERSE_V1101
        .iter()
        .map(|epoch| format!("EPOCH.{epoch}"))
        .collect();
    let player = &mut save["players"][0];
    player["character_id"] = json!("CHARACTER.IRONCLAD");
    player["max_hp"] = json!(80);
    player["current_hp"] = json!(80);
    player["base_orb_slot_count"] = json!(0);
    player["deck"] = Value::Array(deck);
    player["relics"] = if burning_blood {
        json!([{"floor_added_to_deck": 1, "id": "RELIC.BURNING_BLOOD"}])
    } else {
        json!([])
    };
    player["potions"] = json!([]);
    // A0 belt: 3 slots (A4 TightBelt removes one; the template save is A10).
    player["max_potion_slot_count"] = json!(3);
    player["unlock_state"]["unlocked_epochs"] = json!(epochs);
    serde_json::to_string(&save).expect("a save serializes")
}

/// The same four gates the crate's `bench` and `diff-serve` run.
fn admit(document: &CanonicalStateV2) -> Result<(HotState, Catalog), String> {
    document
        .validate_schema()
        .map_err(|r| format!("schema: {r}"))?;
    let catalog =
        HotBoundary::catalog_from_canonical(document).map_err(|r| format!("catalog: {r}"))?;
    let state =
        HotBoundary::from_canonical(document, &catalog).map_err(|r| format!("boundary: {r}"))?;
    engine::admit(document, &state, &catalog).map_err(|r| format!("admission: {r}"))?;
    Ok((state, catalog))
}

struct Root {
    encounter: usize,
    state: HotState,
    catalog: Catalog,
}

fn build_root(
    template: &Value,
    seed: u64,
    encounter: &str,
    burning_blood: bool,
) -> Result<(Root, Value, String, CanonicalStateV2), String> {
    // A relic-less save does not open: the opening projects an empty
    // `relics_entering` and the boundary refuses it as a non-canonical
    // default (`boundary_unrepresentable: NonCanonicalDefault { entity:
    // Player, field: "relics_entering" }`). Burning Blood is census-inert in
    // this engine (content_tables.rs RelicLedgerRow: `CensusInert`,
    // `implemented: false` — its only effect, the post-victory heal, lies
    // outside the combat-only scope), so it has no body in the opening or the
    // fight: `--relics none` opens WITH it and then drops the two relic keys,
    // which is the state a relic-less opening would have produced.
    let save = save_json(template, seed, true);
    let wire = format!("ENCOUNTER.{encounter}");
    let request = EntryRequest {
        input: EntryInput::Save(&save),
        encounter_id: Some(&wire),
        node_type: Some(node_type(encounter)),
        game_build: GameBuild::V0_111_0,
        mcr_splice: false,
    };
    let opening = match entry::build_root(&request, None) {
        RootOutcome::Opened(opening) => opening,
        RootOutcome::EntryOnly(_, refusal) => {
            return Err(format!("opening refused: {}: {refusal}", refusal.class()));
        }
        RootOutcome::Refused(refusal) => {
            return Err(format!("entry refused: {}: {refusal}", refusal.class()));
        }
    };
    let mut document = opening.document.clone();
    if !burning_blood {
        let relics = document.player.remove("relics_entering");
        if relics != Some(json!(["RELIC.BURNING_BLOOD"])) {
            return Err(format!("unexpected relics_entering {relics:?}"));
        }
        document.player.remove("relics_entering_dispatch_ordered");
    }
    let (state, catalog) = admit(&document)?;
    let digest = document.differential_digest();
    let doc = serde_json::to_value(&document).expect("a canonical state serializes");
    let roster: Vec<Value> = doc["monsters"]
        .as_array()
        .map(|ms| {
            ms.iter()
                .map(|m| json!({"kind": m["kind"], "hp": m["hp"], "max_hp": m["max_hp"]}))
                .collect()
        })
        .unwrap_or_default();
    let summary = json!({
        "monsters": roster,
        "player_hp": doc["player"]["hp"],
        "player_max_hp": doc["player"]["max_hp"],
        "relics": doc["player"]["relics_entering"],
    });
    Ok((
        Root {
            encounter: 0,
            state,
            catalog,
        },
        summary,
        digest,
        document,
    ))
}

/// SplitMix64 (the policy RNG the crate's own bench uses). Not a game RNG.
struct PolicyRng(u64);
impl PolicyRng {
    fn next_u64(&mut self) -> u64 {
        self.0 = self.0.wrapping_add(0x9E37_79B9_7F4A_7C15);
        let mut z = self.0;
        z = (z ^ (z >> 30)).wrapping_mul(0xBF58_476D_1CE4_E5B9);
        z = (z ^ (z >> 27)).wrapping_mul(0x94D0_49BB_1331_11EB);
        z ^ (z >> 31)
    }
    fn below(&mut self, n: usize) -> usize {
        (self.next_u64() % n as u64) as usize
    }
    fn unit(&mut self) -> f64 {
        ((self.next_u64() >> 11) as f64) / ((1u64 << 53) as f64)
    }
}

const FNV_OFFSET: u64 = 0xCBF2_9CE4_8422_2325;
const FNV_PRIME: u64 = 0x0000_0100_0000_01B3;

fn fnv_word(h: &mut u64, word: u64) {
    for byte in word.to_le_bytes() {
        *h ^= u64::from(byte);
        *h = h.wrapping_mul(FNV_PRIME);
    }
}

/// The crate bench's `(tag, uid, target, selection)` action encoding.
fn fold_action(h: &mut u64, action: &Action) {
    match action {
        Action::Play {
            uid,
            target,
            selection,
        } => {
            fnv_word(
                h,
                (1u64 << 56) | (u64::from(*uid) << 16) | u64::from(target.map_or(255, u16::from)),
            );
            fnv_word(h, selection.get().map_or(u64::MAX, u64::from));
        }
        Action::UsePotion { slot, target } => fnv_word(
            h,
            (2u64 << 56) | (u64::from(*slot) << 16) | u64::from(target.map_or(255, u16::from)),
        ),
        Action::EndTurn => fnv_word(h, 3u64 << 56),
        Action::Select { answer } => match answer {
            SelectionAnswer::CardUid(uid) => fnv_word(h, (4u64 << 56) | u64::from(*uid)),
            SelectionAnswer::OptionIndex(index) => fnv_word(h, (5u64 << 56) | u64::from(*index)),
        },
    }
}

/// How a fight picks among the legal actions.
///
/// * `Uniform` (default): uniform over THIS engine's legal-action list, as the
///   crate's own `bench` does. That list is deduplicated
///   (`engine::legal_actions_into` doc, src/engine/mod.rs): equal hand cards
///   collapse into one play, and identical monsters into one target. So with
///   `[Strike, Strike, Strike, Defend, Bash]` vs one monster it offers 4
///   actions where spire-mentor's engine (and the game's own per-card UI)
///   offers 6, and EndTurn is drawn 1/4 instead of 1/6 of the time: a
///   different, weaker random player (shorter fights, fewer wins).
/// * `Instance`: weights each offered action by how many (hand card instance,
///   alive target monster) pairs it stands for, which reproduces "uniform over
///   one action per card instance per alive enemy, plus EndTurn" — the list
///   spire-mentor's engine enumerates. Its weights cost a few tens of ns per
///   step inside the timed loop.
#[derive(Clone, Copy, PartialEq, Eq, Debug)]
enum Policy {
    Uniform,
    Instance,
}

/// `Instance` weights for `actions` (see [`Policy`]).
fn instance_weights(state: &HotState, actions: &[Action], weights: &mut Vec<f64>) -> f64 {
    let hand = state.piles.get(PileId::Hand).as_slice();
    let atom_of = |uid: u32| hand.iter().find(|c| c.uid == uid).map(|c| c.atom);
    weights.clear();
    let mut total = 0.0;
    for action in actions {
        let w = match action {
            Action::Play { uid, target, .. } => {
                let card = match atom_of(*uid) {
                    Some(atom) => {
                        let instances = hand.iter().filter(|c| c.atom == atom).count();
                        let offered = actions
                            .iter()
                            .filter(|b| {
                                matches!(b, Action::Play { uid: u2, target: t2, .. }
                                if t2 == target && atom_of(*u2) == Some(atom))
                            })
                            .count();
                        instances as f64 / offered.max(1) as f64
                    }
                    None => 1.0,
                };
                let tgt = match target {
                    Some(t) => match state.monsters.get(usize::from(*t)) {
                        Some(m) => state
                            .monsters
                            .iter()
                            .filter(|o| o.hp > 0 && *o == m)
                            .count()
                            .max(1) as f64,
                        None => 1.0,
                    },
                    None => 1.0,
                };
                card * tgt
            }
            _ => 1.0,
        };
        weights.push(w);
        total += w;
    }
    total
}

#[derive(Clone, Copy, PartialEq, Eq)]
enum Stop {
    Terminal,
    NoLegal,
    Refused,
    StepCap,
}

fn won(s: &HotState) -> bool {
    s.history.over && s.hp > 0 && s.monsters.iter().all(|m| m.hp <= 0)
}

/// One random fight on a fresh copy of `root`: (actions applied, won, stop).
#[allow(clippy::too_many_arguments)]
fn play(
    root: &Root,
    policy: Policy,
    weights: &mut Vec<f64>,
    policy_seed: u64,
    max_steps: u64,
    legal: &mut LegalActionBuffer,
    events: &mut Vec<Event>,
    checksum: &mut u64,
    legal_seen: &mut u64,
) -> (u64, bool, Stop) {
    let mut rng = PolicyRng(policy_seed);
    let mut state = root.state.clone();
    let mut steps = 0u64;
    loop {
        if state.history.over {
            return (steps, won(&state), Stop::Terminal);
        }
        if steps >= max_steps {
            return (steps, false, Stop::StepCap);
        }
        let actions = match engine::legal_actions_checked(&state, &root.catalog, legal) {
            Ok(actions) if !actions.is_empty() => actions,
            _ => return (steps, false, Stop::NoLegal),
        };
        *legal_seen += actions.len() as u64;
        let pick = match policy {
            Policy::Uniform => rng.below(actions.len()),
            Policy::Instance => {
                let total = instance_weights(&state, actions, weights);
                let mut x = rng.unit() * total;
                let mut k = actions.len() - 1;
                for (j, w) in weights.iter().enumerate() {
                    if x < *w {
                        k = j;
                        break;
                    }
                    x -= *w;
                }
                k
            }
        };
        let action = actions[pick];
        fold_action(checksum, &action);
        match engine::apply_action_into(&state, &root.catalog, &action, events) {
            Ok(next) => {
                state = next;
                steps += 1;
            }
            Err(_) => return (steps, false, Stop::Refused),
        }
    }
}

fn main() {
    let args = parse_args();
    let template = template();
    let n_enc = args.encounters.len();

    // ---- start states: built once, UNTIMED --------------------------------
    let t_setup = Instant::now();
    let mut roots: Vec<Root> = Vec::with_capacity(args.seeds as usize);
    let mut rosters: Vec<Value> = vec![Value::Null; n_enc];
    let mut roots_digest = FNV_OFFSET;
    let mut setup_failures: Vec<Value> = Vec::new();
    for s in args.seed_start..args.seed_start + args.seeds {
        let e = (s % n_enc as u64) as usize;
        let encounter = &args.encounters[e];
        match build_root(&template, s, encounter, args.burning_blood) {
            Ok((mut root, summary, digest, document)) => {
                if let Some((dump_seed, path)) = &args.dump_root
                    && *dump_seed == s
                {
                    std::fs::write(path, document.canonical_json())
                        .unwrap_or_else(|e| panic!("--dump-root {path}: {e}"));
                    eprintln!("wrote seed {s} ({encounter}) start state to {path}");
                }
                root.encounter = e;
                for b in digest.bytes() {
                    roots_digest ^= u64::from(b);
                    roots_digest = roots_digest.wrapping_mul(FNV_PRIME);
                }
                if rosters[e].is_null() {
                    rosters[e] = json!({"seed": s, "start": summary});
                }
                roots.push(root);
            }
            Err(detail) => {
                eprintln!("seed {s} encounter {encounter}: NOT BUILT: {detail}");
                if setup_failures.len() < 64 {
                    setup_failures
                        .push(json!({"seed": s, "encounter": encounter, "detail": detail}));
                }
            }
        }
    }
    let setup_s = t_setup.elapsed().as_secs_f64();
    if roots.is_empty() {
        println!(
            "{}",
            json!({"bench": "xsim_w1_site", "engine": "sansb/sts2-sim v0.111.0", "error": "no start state built", "setup_failures": setup_failures})
        );
        std::process::exit(1);
    }
    eprintln!(
        "built {} start states ({} failed) in {setup_s:.2}s; playing {} fights each",
        roots.len(),
        args.seeds as usize - roots.len(),
        args.reps
    );

    let mut legal = LegalActionBuffer::new();
    let mut events: Vec<Event> = Vec::new();
    let mut weights: Vec<f64> = Vec::with_capacity(32);
    // Warm-up: one fight, as spire-mentor's site_random_bench does.
    {
        let (mut c, mut l) = (FNV_OFFSET, 0u64);
        let _ = play(
            &roots[0],
            args.policy,
            &mut weights,
            1,
            args.max_steps,
            &mut legal,
            &mut events,
            &mut c,
            &mut l,
        );
    }

    let mut fights = 0u64;
    let mut wins = 0u64;
    let mut actions = 0u64;
    let mut legal_seen = 0u64;
    let mut stops = [0u64; 4];
    let mut digest = FNV_OFFSET;
    let mut checksum = FNV_OFFSET;
    let mut per_enc = vec![[0u64; 3]; n_enc]; // fights, wins, actions
    let t0 = Instant::now();
    for (i, root) in roots.iter().enumerate() {
        for r in 0..args.reps {
            let (steps, w, stop) = play(
                root,
                args.policy,
                &mut weights,
                (i as u64) * 1_000_003 + r,
                args.max_steps,
                &mut legal,
                &mut events,
                &mut checksum,
                &mut legal_seen,
            );
            fights += 1;
            wins += u64::from(w);
            actions += steps;
            stops[stop as usize] += 1;
            digest = (digest ^ ((steps << 1) | u64::from(w))).wrapping_mul(FNV_PRIME);
            let pe = &mut per_enc[root.encounter];
            pe[0] += 1;
            pe[1] += u64::from(w);
            pe[2] += steps;
        }
    }
    let secs = t0.elapsed().as_secs_f64();

    let per_encounter: Vec<Value> = args
        .encounters
        .iter()
        .enumerate()
        .map(|(e, id)| {
            let [f, w, a] = per_enc[e];
            json!({
                "encounter": id,
                "fights": f,
                "wins": w,
                "win_rate": if f > 0 { w as f64 / f as f64 } else { 0.0 },
                "actions_per_fight": if f > 0 { a as f64 / f as f64 } else { 0.0 },
                "first_root": rosters[e],
            })
        })
        .collect();
    let anomalies = stops[Stop::NoLegal as usize]
        + stops[Stop::Refused as usize]
        + stops[Stop::StepCap as usize];
    let out = json!({
        "bench": "xsim_w1_site",
        "engine": "sansb/sts2-sim v0.111.0 (sts-sim crate)",
        "game_build": "v0.111.0",
        "workload": "W1 site: Ironclad A0 starter deck 80/80 HP + relics, Act-1 Overgrowth encounters round-robin, start state built once per seed (untimed), each fight = clone + uniform random legal actions to the end (timed), one thread",
        "relics": if args.burning_blood { "burning-blood" } else { "none" },
        "policy": match args.policy { Policy::Uniform => "uniform (engine's deduplicated legal list)", Policy::Instance => "instance (weighted to one action per card instance per alive enemy + EndTurn)" },
        "encounters": args.encounters,
        "n_encounters": n_enc,
        "seeds": args.seeds,
        "seed_start": args.seed_start,
        "reps": args.reps,
        "start_states_built": roots.len(),
        "start_states_failed": args.seeds as usize - roots.len(),
        "setup_failures": setup_failures,
        "setup_s": setup_s,
        "fights": fights,
        "wins": wins,
        "win_rate": wins as f64 / fights as f64,
        "actions": actions,
        "actions_per_fight": actions as f64 / fights as f64,
        "mean_branching_factor": legal_seen as f64 / (actions.max(1)) as f64,
        "stops": {
            "terminal": stops[Stop::Terminal as usize],
            "no_legal_actions": stops[Stop::NoLegal as usize],
            "refused": stops[Stop::Refused as usize],
            "step_cap": stops[Stop::StepCap as usize],
        },
        "anomalies": anomalies,
        "digest": format!("{digest:016x}"),
        "action_checksum": format!("{checksum:016x}"),
        "roots_digest": format!("{roots_digest:016x}"),
        "elapsed_s": secs,
        "fights_per_s": fights as f64 / secs,
        "actions_per_s": actions as f64 / secs,
        "per_encounter": per_encounter,
    });
    println!("{out}");
}
