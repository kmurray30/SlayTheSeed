//! Cross-engine benchmark W2 "toadpoles" on lain-wood/sts2core (NOT part of upstream sts2core).
//!
//! Reproduces sansb/sts2-sim's own bench workload `ironclad_toadpoles_uniform_v1`
//! (`v0.111.0/engine/src/bench.rs`, entry `fixtures/bench_ironclad_toadpoles_uniform_v1.json`)
//! as faithfully as this engine allows:
//!
//! * entry = the fixture's turn-1 state: Ironclad 68/80 HP, no relic, 3 energy;
//!   hand [Strike, Strike, Defend, Strike, Strike]; draw pile top-first
//!   [Strike, Defend, Defend, Bash, Defend]; discard/exhaust empty;
//!   Toadpole 26/26 in slot 0 (front, next move SPIKEN = fixture `loop_pos` 2) and
//!   Toadpole 25/25 in slot 1 (back, next move WHIRL = fixture `loop_pos` 1);
//! * ascension 10 (`--ascension`, default 10): the fixture has no `player.ascension`
//!   field and sansb's admission reads that as A10 (`v0.111.0/engine/src/boundary.rs:636`).
//!   `State::ascension` feeds sts2core's `asc::adjust`, so the Toadpoles hit at their
//!   A9 `DeadlyEnemies` values (Spike Spit 4x3, Whirl 8; C# `Toadpole.cs:49,53`); the
//!   26/25 HP is pinned (26 is an A8 `ToughEnemies` roll, 22-26, `Toadpole.cs:32,34`).
//!   `--ascension 0` is this harness before 2026-10-14 (A0: Spike Spit 3x3, Whirl 7);
//! * 240,000 playouts from that one entry state, each: copy the entry (POD memcpy),
//!   then repeat {enumerate legal actions, pick one uniformly, apply} until the fight
//!   ends or 64 actions were applied (sansb's `actions` cap); 2,000 warm-up playouts;
//! * policy RNG = sansb's: SplitMix64 seeded `seed ^ (i * 0x9E3779B97F4A7C15)` for
//!   playout i, `seed = 0x5354533250455246`, `below(n) = next % n`;
//! * sts2core's `step(State, Action) -> State` takes and returns the state BY VALUE,
//!   so every transition already includes a full 3,880-byte state copy (sansb's bench
//!   is clone-and-apply per transition too).
//!
//! Deviations: sts2core cannot load sansb's game-RNG words (it has its own splitmix64
//! streams, seeded here by `--state-seed`, default 7), so the reshuffle on turn 3 and
//! later differ. sts2core collapses identical hand cards into one legal action (so
//! does sansb; spire-mentor's engine does not). `--policy instance` weights each
//! PlayCard by its identical copies in hand (= uniform over card instances).
//! `--entry shuffled --ascension 0` reproduces the 2026-10-12 harness instead
//! (State::new(68, 7) + begin_combat: a shuffled opening hand, not the fixture's).
//!
//! Output: human-readable lines, then ONE JSON object on the last stdout line.
//!
//!   CARGO_TARGET_DIR=<dir> cargo build --release --bin xsim_w2_toadpoles
//!   taskset -c 20 <dir>/release/xsim_w2_toadpoles               # 240k playouts
//!   <dir>/release/xsim_w2_toadpoles --quick                     # smoke (20k playouts)

use std::time::Instant;

use sts2core::content::{card, enemy, enemy_def};
use sts2core::state::State;
use sts2core::step::{begin_combat, current_move_ix, legal_actions, step, Action, MAX_ACTIONS};

#[derive(Clone, Copy, PartialEq, Eq)]
enum Policy {
    Engine,
    Instance,
}

struct Args {
    trajectories: u32,
    actions: u32,
    seed: u64,
    warmup: u32,
    state_seed: u64,
    policy: Policy,
    shuffled_entry: bool,
    ascension: u8,
}

fn usage() -> ! {
    eprintln!(
        "usage: xsim_w2_toadpoles [--trajectories N=240000] [--actions M=64] [--seed S=0x5354533250455246]\n\
         \x20                        [--warmup T=2000] [--state-seed X=7] [--policy engine|instance]\n\
         \x20                        [--entry fixture|shuffled] [--ascension A=10] [--quick]"
    );
    std::process::exit(2)
}

fn parse_u64(s: &str) -> u64 {
    if let Some(h) = s.strip_prefix("0x") {
        u64::from_str_radix(h, 16).unwrap_or_else(|_| usage())
    } else {
        s.parse().unwrap_or_else(|_| usage())
    }
}

fn parse_args() -> Args {
    let mut a = Args {
        trajectories: 240_000,
        actions: 64,
        seed: 0x5354_5332_5045_5246,
        warmup: 2_000,
        state_seed: 7,
        policy: Policy::Engine,
        shuffled_entry: false,
        ascension: 10,
    };
    let argv: Vec<String> = std::env::args().skip(1).collect();
    let mut i = 0;
    let val = |i: usize| -> String { argv.get(i + 1).cloned().unwrap_or_else(|| usage()) };
    while i < argv.len() {
        match argv[i].as_str() {
            "--trajectories" => { a.trajectories = parse_u64(&val(i)) as u32; i += 2; }
            "--actions" => { a.actions = parse_u64(&val(i)) as u32; i += 2; }
            "--seed" => { a.seed = parse_u64(&val(i)); i += 2; }
            "--warmup" => { a.warmup = parse_u64(&val(i)) as u32; i += 2; }
            "--state-seed" => { a.state_seed = parse_u64(&val(i)); i += 2; }
            "--quick" => { a.trajectories = 20_000; a.warmup = 500; i += 1; }
            "--ascension" => {
                a.ascension = u8::try_from(parse_u64(&val(i))).ok().filter(|&x| x <= 10).unwrap_or_else(|| usage());
                i += 2;
            }
            "--policy" => {
                a.policy = match val(i).as_str() {
                    "engine" => Policy::Engine,
                    "instance" => Policy::Instance,
                    _ => usage(),
                };
                i += 2;
            }
            "--entry" => {
                a.shuffled_entry = match val(i).as_str() {
                    "fixture" => false,
                    "shuffled" => true,
                    _ => usage(),
                };
                i += 2;
            }
            _ => usage(),
        }
    }
    if a.trajectories == 0 || a.actions == 0 {
        usage();
    }
    a
}

/// sansb's `PolicyRng` (SplitMix64, modulo reduction), verbatim semantics.
struct PolicyRng(u64);
impl PolicyRng {
    #[inline(always)]
    fn next_u64(&mut self) -> u64 {
        self.0 = self.0.wrapping_add(0x9E37_79B9_7F4A_7C15);
        let mut w = self.0;
        w = (w ^ (w >> 30)).wrapping_mul(0xBF58_476D_1CE4_E5B9);
        w = (w ^ (w >> 27)).wrapping_mul(0x94D0_49BB_1331_11EB);
        w ^ (w >> 31)
    }
    #[inline(always)]
    fn below(&mut self, bound: usize) -> usize {
        (self.next_u64() % bound as u64) as usize
    }
}

fn trajectory_seed(seed: u64, t: u32) -> u64 {
    seed ^ u64::from(t).wrapping_mul(0x9E37_79B9_7F4A_7C15)
}

#[inline(always)]
fn pick(s: &State, acts: &[Action; MAX_ACTIONS], n: usize, policy: Policy, rng: &mut PolicyRng) -> usize {
    match policy {
        Policy::Engine => rng.below(n),
        Policy::Instance => {
            let mut w = [1u32; MAX_ACTIONS];
            let mut total = 0u32;
            for k in 0..n {
                if let Action::PlayCard { hand, .. } = acts[k] {
                    let inst = s.cards[s.hand[hand as usize] as usize];
                    let mut c = 0u32;
                    for j in 0..s.n_hand as usize {
                        if s.cards[s.hand[j] as usize] == inst {
                            c += 1;
                        }
                    }
                    w[k] = c.max(1);
                }
                total += w[k];
            }
            let mut r = rng.below(total as usize) as u32;
            for k in 0..n {
                if r < w[k] {
                    return k;
                }
                r -= w[k];
            }
            n - 1
        }
    }
}

fn entry(args: &Args) -> State {
    let mut s = State::new(68, if args.shuffled_entry { 7 } else { args.state_seed });
    if !args.shuffled_entry {
        s.player.max_hp = 80;
    }
    s.ascension = args.ascension;
    // card slots: 0..=4 Strike, 5..=8 Defend, 9 Bash
    for _ in 0..5 {
        s.add_card(card::STRIKE, 0, 0);
    }
    for _ in 0..4 {
        s.add_card(card::DEFEND, 0, 0);
    }
    s.add_card(card::BASH, 0, 0);
    s.add_enemy(enemy::TOADPOLE, 26);
    s.add_enemy(enemy::TOADPOLE, 25);
    let mut s = begin_combat(s);
    if !args.shuffled_entry {
        // Overwrite the piles with the fixture's (begin_combat shuffled + drew 5).
        // Hand order as in the fixture: S S D S S.
        let hand = [0u8, 1, 5, 2, 3];
        // sts2core's draw-pile top is the END of `draw` (`State::pop_draw_top`);
        // fixture top-first S D D Bash D -> bottom..top D Bash D D S.
        let draw = [8u8, 9, 7, 6, 4];
        s.hand[..5].copy_from_slice(&hand);
        s.n_hand = 5;
        s.draw[..5].copy_from_slice(&draw);
        s.n_draw = 5;
        s.n_disc = 0;
        s.n_exh = 0;
        s.n_draw_known = 0;
    }
    s
}

fn describe_entry(s: &State) -> String {
    let name = |ix: u8| sts2core::content::card(s.cards[ix as usize].id).name;
    let hand: Vec<&str> = (0..s.n_hand as usize).map(|i| name(s.hand[i])).collect();
    let draw_top_first: Vec<&str> = (0..s.n_draw as usize).rev().map(|i| name(s.draw[i])).collect();
    let mons: Vec<String> = (0..s.n_enemies as usize)
        .map(|e| {
            let d = enemy_def(s.enemy_def[e]);
            let mv = d.moves.get(current_move_ix(d, s, e)).map_or("?", |m| m.name);
            format!("slot{e} {}#{} {}/{} next={}", d.name, s.enemy_def[e], s.enemies[e].hp, s.enemies[e].max_hp, mv)
        })
        .collect();
    format!(
        "ascension {} | player {}/{} energy {} turn {} | hand {:?} | draw(top first) {:?} | disc {} exh {} | {}",
        s.ascension, s.player.hp, s.player.max_hp, s.energy, s.turn, hand, draw_top_first, s.n_disc, s.n_exh, mons.join("; ")
    )
}

#[derive(Default)]
struct M {
    transitions: u64,
    enumerations: u64,
    legal_seen: u64,
    terminal: u64,
    budget: u64,
    no_legal: u64,
    wins: u64,
    ended_at_cap: u64,
    checksum: u64,
}

impl M {
    fn new() -> M {
        M { checksum: 0xCBF2_9CE4_8422_2325, ..Default::default() }
    }
    #[inline(always)]
    fn fold(&mut self, word: u64) {
        for b in word.to_le_bytes() {
            self.checksum ^= u64::from(b);
            self.checksum = self.checksum.wrapping_mul(0x0000_0100_0000_01B3);
        }
    }
}

#[inline(never)]
fn play_one(entry: &State, seed: u64, max_actions: u32, policy: Policy, m: &mut M) {
    let mut rng = PolicyRng(seed);
    let mut s = *entry;
    for _ in 0..max_actions {
        if s.combat_over {
            m.terminal += 1;
            m.wins += u64::from(!s.player_dead);
            return;
        }
        let (acts, n) = legal_actions(&s);
        m.enumerations += 1;
        m.legal_seen += n as u64;
        if n == 0 {
            m.no_legal += 1;
            return;
        }
        let a = acts[pick(&s, &acts, n, policy, &mut rng)];
        let word = match a {
            Action::PlayCard { hand, target } => (1u64 << 56) | (u64::from(hand) << 16) | u64::from(target),
            Action::UsePotion { slot, target } => (2u64 << 56) | (u64::from(slot) << 16) | u64::from(target),
            Action::EndTurn => 3u64 << 56,
            Action::Choose { hand } => (4u64 << 56) | u64::from(hand),
        };
        m.fold(word);
        s = step(s, a);
        m.transitions += 1;
    }
    // sansb's stop tally: a playout that used the whole cap is `budget`, even when its
    // last action ended the fight. Wins are counted for every playout that ended.
    m.budget += 1;
    if s.combat_over {
        m.ended_at_cap += 1;
        m.wins += u64::from(!s.player_dead);
    }
}

fn main() {
    let args = parse_args();
    let e = entry(&args);
    println!("entry: {}", describe_entry(&e));
    let mut warm = M::new();
    for t in 0..args.warmup {
        play_one(&e, trajectory_seed(args.seed, t), args.actions, args.policy, &mut warm);
    }
    let mut m = M::new();
    let t0 = Instant::now();
    for t in 0..args.trajectories {
        play_one(&e, trajectory_seed(args.seed, t), args.actions, args.policy, &mut m);
    }
    let secs = t0.elapsed().as_secs_f64();
    let n = f64::from(args.trajectories);
    println!(
        "{{\"bench\":\"xsim_w2_toadpoles\",\"engine\":\"sts2core\",\"workload\":\"W2 ironclad_toadpoles_uniform_v1\",\"entry\":\"{}\",\"ascension\":{},\"policy\":\"{}\",\"state_seed\":{},\"state_bytes\":{},\"trajectories\":{},\"actions_cap\":{},\"seed\":\"0x{:016x}\",\"warmup\":{},\"transitions\":{},\"enumerations\":{},\"mean_branching_factor\":{:.3},\"terminal\":{},\"budget\":{},\"budget_but_ended\":{},\"no_legal_actions\":{},\"wins\":{},\"win_rate\":{:.4},\"win_rate_of_ended\":{:.4},\"mean_actions_per_playout\":{:.2},\"checksum\":\"{:016x}\",\"wall_seconds\":{:.4},\"transitions_per_second\":{:.0},\"playouts_per_second\":{:.0},\"complete_playouts_per_second\":{:.0},\"nanos_per_transition\":{:.1}}}",
        if args.shuffled_entry { "shuffled" } else { "fixture" },
        e.ascension,
        if args.policy == Policy::Engine { "engine" } else { "instance" },
        if args.shuffled_entry { 7 } else { args.state_seed },
        std::mem::size_of::<State>(),
        args.trajectories,
        args.actions,
        args.seed,
        args.warmup,
        m.transitions,
        m.enumerations,
        m.legal_seen as f64 / m.enumerations.max(1) as f64,
        m.terminal,
        m.budget,
        m.ended_at_cap,
        m.no_legal,
        m.wins,
        m.wins as f64 / n,
        m.wins as f64 / (m.terminal + m.ended_at_cap).max(1) as f64,
        m.transitions as f64 / n,
        m.checksum,
        secs,
        m.transitions as f64 / secs,
        n / secs,
        m.terminal as f64 / secs,
        secs * 1e9 / m.transitions.max(1) as f64,
    );
}
