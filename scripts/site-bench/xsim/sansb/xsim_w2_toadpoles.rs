//! Cross-engine workload W2 ("toadpoles") on sansb/sts2-sim's v0.111.0 engine.
//!
//! NOT part of sansb/sts2-sim. Written by the spire-mentor benchmark
//! (`scripts/site-bench/xsim/sansb/` in spire-mentor) and dropped into
//! `v0.111.0/engine/examples/` of a scratch clone.
//!
//! W2 is sansb's own `sts-sim bench` workload `ironclad_toadpoles_uniform_v1`
//! (src/bench.rs), re-implemented line for line on the public API so it can
//! ALSO report what the built-in bench does not: the random policy's win rate,
//! mean actions per playout, and fights/s. It embeds the same byte-pinned
//! entry (`fixtures/bench_ironclad_toadpoles_uniform_v1.json`, sha256
//! 6a5f557d…ee2: Ironclad, 5 Strike / 4 Defend / 1 Bash, 68/80 HP, no relic,
//! hand already dealt, two Toadpoles at 26 and 25 HP), the same SplitMix64
//! policy, the same per-trajectory seed derivation, the same warm-up (2,000
//! playouts), the same 64-action safety cap and the same FNV-1a action
//! checksum. So `work.checksum` and `work.transitions` here MUST equal the
//! built-in bench's for the same `--trajectories/--seed/--actions`; if they
//! differ, the two did different work.
//!
//! Per transition: enumerate legal actions, pick one uniformly, then
//! `engine::apply_action_into(&state, ..)` returns the successor (the
//! engine's clone-and-apply semantics).
//!
//! Output: ONE JSON object on the last stdout line.

use std::time::Instant;

use serde_json::json;
use sts_sim::boundary::HotBoundary;
use sts_sim::canonical::CanonicalStateV2;
use sts_sim::catalog::Catalog;
use sts_sim::engine::{self, Action, Event, LegalActionBuffer, SelectionAnswer};
use sts_sim::hot::{HotState, PileId};

const ENTRY: &str = include_str!("../fixtures/bench_ironclad_toadpoles_uniform_v1.json");
const USAGE: &str = "usage: xsim_w2_toadpoles [--trajectories N] [--actions M] [--seed S] [--warmup T] \
     [--policy uniform|instance] [--quick]";

struct Config {
    trajectories: u32,
    actions: u32,
    seed: u64,
    warmup: u32,
    /// `false` = uniform over the engine's deduplicated legal list (the
    /// built-in bench's policy, the default); `true` = weighted to one action
    /// per (hand card instance, alive enemy) + EndTurn, the list spire-mentor's
    /// engine enumerates (see xsim_w1_site.rs `Policy`).
    instance: bool,
}

fn parse_args() -> Config {
    let argv: Vec<String> = std::env::args().skip(1).collect();
    // The built-in bench's defaults (src/bench.rs WORKLOADS[0]).
    let mut c = Config {
        trajectories: 240_000,
        actions: 64,
        seed: 0x5354_5332_5045_5246,
        warmup: 2_000,
        instance: false,
    };
    let mut i = 0;
    let int = |i: usize| -> u64 {
        argv.get(i + 1)
            .and_then(|v| v.parse().ok())
            .unwrap_or_else(|| {
                eprintln!("{} expects an integer; {USAGE}", argv[i]);
                std::process::exit(2)
            })
    };
    while i < argv.len() {
        match argv[i].as_str() {
            "--trajectories" => c.trajectories = int(i) as u32,
            "--actions" => c.actions = int(i) as u32,
            "--seed" => c.seed = int(i),
            "--warmup" => c.warmup = int(i) as u32,
            "--policy" => {
                c.instance = match argv.get(i + 1).map(String::as_str) {
                    Some("uniform") => false,
                    Some("instance") => true,
                    _ => {
                        eprintln!("--policy must be uniform or instance; {USAGE}");
                        std::process::exit(2)
                    }
                }
            }
            "--quick" => {
                c.trajectories = 10_000;
                i += 1;
                continue;
            }
            other => {
                eprintln!("unknown flag {other:?}; {USAGE}");
                std::process::exit(2)
            }
        }
        i += 2;
    }
    if c.trajectories == 0 || c.actions == 0 {
        eprintln!("--trajectories and --actions must be > 0");
        std::process::exit(2)
    }
    c
}

fn admit(entry: &str) -> (HotState, Catalog) {
    let document: CanonicalStateV2 = serde_json::from_str(entry).expect("canonical v2 entry");
    document.validate_schema().expect("schema");
    let catalog = HotBoundary::catalog_from_canonical(&document).expect("catalog");
    let state = HotBoundary::from_canonical(&document, &catalog).expect("boundary");
    engine::admit(&document, &state, &catalog).expect("admission");
    (state, catalog)
}

struct PolicyRng(u64);
impl PolicyRng {
    fn next_u64(&mut self) -> u64 {
        self.0 = self.0.wrapping_add(0x9E37_79B9_7F4A_7C15);
        let mut word = self.0;
        word = (word ^ (word >> 30)).wrapping_mul(0xBF58_476D_1CE4_E5B9);
        word = (word ^ (word >> 27)).wrapping_mul(0x94D0_49BB_1331_11EB);
        word ^ (word >> 31)
    }
    fn below(&mut self, bound: usize) -> usize {
        (self.next_u64() % bound as u64) as usize
    }
    fn unit(&mut self) -> f64 {
        ((self.next_u64() >> 11) as f64) / ((1u64 << 53) as f64)
    }
}

/// One action per (hand card instance, alive enemy) + EndTurn weights.
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

fn trajectory_seed(seed: u64, trajectory: u32) -> u64 {
    seed ^ u64::from(trajectory).wrapping_mul(0x9E37_79B9_7F4A_7C15)
}

const PRIME: u64 = 0x0000_0100_0000_01B3;
fn fold(h: &mut u64, word: u64) {
    for byte in word.to_le_bytes() {
        *h ^= u64::from(byte);
        *h = h.wrapping_mul(PRIME);
    }
}
fn fold_action(h: &mut u64, action: &Action) {
    match action {
        Action::Play {
            uid,
            target,
            selection,
        } => {
            fold(
                h,
                (1u64 << 56) | (u64::from(*uid) << 16) | u64::from(target.map_or(255, u16::from)),
            );
            fold(h, selection.get().map_or(u64::MAX, u64::from));
        }
        Action::UsePotion { slot, target } => fold(
            h,
            (2u64 << 56) | (u64::from(*slot) << 16) | u64::from(target.map_or(255, u16::from)),
        ),
        Action::EndTurn => fold(h, 3u64 << 56),
        Action::Select { answer } => match answer {
            SelectionAnswer::CardUid(uid) => fold(h, (4u64 << 56) | u64::from(*uid)),
            SelectionAnswer::OptionIndex(index) => fold(h, (5u64 << 56) | u64::from(*index)),
        },
    }
}

#[derive(Default)]
struct M {
    transitions: u64,
    enumerations: u64,
    legal_seen: u64,
    terminal: u64,
    budget: u64,
    no_legal: u64,
    refused: u64,
    wins: u64,
    losses: u64,
    checksum: u64,
}

fn won(s: &HotState) -> bool {
    s.history.over && s.hp > 0 && s.monsters.iter().all(|m| m.hp <= 0)
}

#[allow(clippy::too_many_arguments)]
fn play_one(
    entry: &HotState,
    catalog: &Catalog,
    seed: u64,
    max_actions: u32,
    instance: bool,
    weights: &mut Vec<f64>,
    legal: &mut LegalActionBuffer,
    events: &mut Vec<Event>,
    m: &mut M,
) {
    let mut rng = PolicyRng(seed);
    let mut state = entry.clone();
    for _ in 0..max_actions {
        if state.history.over {
            m.terminal += 1;
            if won(&state) {
                m.wins += 1
            } else {
                m.losses += 1
            }
            return;
        }
        let actions = engine::legal_actions_into(&state, catalog, legal);
        m.enumerations += 1;
        m.legal_seen += actions.len() as u64;
        if actions.is_empty() {
            m.no_legal += 1;
            return;
        }
        let pick = if instance {
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
        } else {
            rng.below(actions.len())
        };
        let action = actions[pick];
        fold_action(&mut m.checksum, &action);
        match engine::apply_action_into(&state, catalog, &action, events) {
            Ok(next) => {
                state = next;
                m.transitions += 1;
            }
            Err(_) => {
                m.refused += 1;
                return;
            }
        }
    }
    m.budget += 1;
}

fn main() {
    let c = parse_args();
    let (entry, catalog) = admit(ENTRY);
    let mut legal = LegalActionBuffer::new();
    let mut events: Vec<Event> = Vec::new();
    let mut weights: Vec<f64> = Vec::with_capacity(32);
    let mut warm = M {
        checksum: 0xCBF2_9CE4_8422_2325,
        ..M::default()
    };
    for t in 0..c.warmup {
        play_one(
            &entry,
            &catalog,
            trajectory_seed(c.seed, t),
            c.actions,
            c.instance,
            &mut weights,
            &mut legal,
            &mut events,
            &mut warm,
        );
    }
    let mut m = M {
        checksum: 0xCBF2_9CE4_8422_2325,
        ..M::default()
    };
    let t0 = Instant::now();
    for t in 0..c.trajectories {
        play_one(
            &entry,
            &catalog,
            trajectory_seed(c.seed, t),
            c.actions,
            c.instance,
            &mut weights,
            &mut legal,
            &mut events,
            &mut m,
        );
    }
    let secs = t0.elapsed().as_secs_f64();
    let out = json!({
        "bench": "xsim_w2_toadpoles",
        "engine": "sansb/sts2-sim v0.111.0 (sts-sim crate)",
        "workload": "ironclad_toadpoles_uniform_v1 (sansb's own bench fixture): Ironclad starter, 68/80 HP, no relic, vs 2 Toadpoles (26/25 HP); uniform random playouts from one entry state; clone-and-apply per transition; one thread",
        "config": {"trajectories": c.trajectories, "actions": c.actions, "seed": c.seed, "warmup": c.warmup,
                   "policy": if c.instance { "instance" } else { "uniform" }},
        "work": {
            "transitions": m.transitions,
            "legal_action_enumerations": m.enumerations,
            "mean_branching_factor": m.legal_seen as f64 / m.enumerations.max(1) as f64,
            "checksum": format!("{:016x}", m.checksum),
            "stops": {"terminal": m.terminal, "budget": m.budget, "no_legal_actions": m.no_legal, "refused": m.refused},
        },
        "playouts": c.trajectories,
        "wins": m.wins,
        "losses": m.losses,
        "win_rate": m.wins as f64 / f64::from(c.trajectories),
        "mean_actions_per_playout": m.transitions as f64 / f64::from(c.trajectories),
        "elapsed_s": secs,
        "actions_per_s": m.transitions as f64 / secs,
        "playouts_per_s": f64::from(c.trajectories) / secs,
        "nanos_per_action": secs * 1e9 / m.transitions.max(1) as f64,
    });
    println!("{out}");
}
