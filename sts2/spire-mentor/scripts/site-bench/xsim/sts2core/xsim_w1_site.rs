//! Cross-engine benchmark W1 "site" on lain-wood/sts2core (NOT part of upstream sts2core).
//!
//! Mirrors spire-mentor's `site_random_bench` (M1, `sts2-rs/crates/sts2-bench/examples/
//! site_random_bench.rs`) as faithfully as this engine allows:
//!
//! * Ironclad, ascension 0, starter deck (5 Strike, 4 Defend, 1 Bash) at 80/80 HP,
//!   plus the relic Burning Blood (`--relics none` drops it);
//! * one Act-1 Overgrowth encounter per seed, real monster AI. Encounter list = the
//!   22 ids of spire-mentor's `ACT1_ENCOUNTERS` in THAT registry order (seed `s` gets
//!   entry `s % len`), mapped explicitly to sts2core's encounter keys (`MAP` below);
//!   `--encounters A,B,...` restricts the list (for intersection runs);
//! * start state built ONCE per seed (untimed) through sts2core's own constructor
//!   `synth::build` (the L3 path: deck + relics + rolled enemy HP + `begin_combat`);
//! * each fight = copy the start state (TIMED; `State` is POD `Copy`, so a clone is a
//!   3,880-byte memcpy) + uniformly random legal actions until the fight ends
//!   (cap `--max-steps`, default 5000, as in M1), `--reps` fights per seed;
//! * one thread, release profile of the sts2core crate (opt-level 3, fat LTO,
//!   codegen-units 1, panic=abort, no target-cpu, system allocator).
//!
//! Deviations that cannot be removed (documented in the README next to this file):
//! * sts2core uses its own splitmix64 RNG streams (shuffle / enemy / gen), NOT the
//!   game's; enemy HP is rolled from sts2core's [source] HP ranges with its own stream;
//!   random encounter rosters (FlyconidNormal, SlimesWeak, RubyRaidersNormal,
//!   SlitheringStranglerNormal) are drawn from sts2core's hand-enumerated
//!   distribution table (`data/encounters_overrides.json`) with a pick derived from
//!   the seed. So per-seed rosters/HP/draw order differ from spire-mentor; the
//!   distributions are meant to match the game's.
//! * sts2core does not roll a monster's RANDOM opening move: it takes a deterministic
//!   representative (`step::initial_move`). Affects LeafSlimeS, Flyconid, KinFollower
//!   (both Kin followers open with the same move; in the game one opens with Dance).
//! * sts2core's `legal_actions` collapses identical hand cards into ONE action
//!   (spire-mentor's engine lists every card instance). Uniform play over the
//!   engine's own set (`--policy engine`, default) therefore ends turns more often.
//!   `--policy instance` weights each PlayCard action by the number of identical
//!   copies in hand, which reproduces "uniform over card instances" (spire-mentor's
//!   distribution) without changing the engine.
//!
//! Output: human-readable lines, then ONE JSON object on the last stdout line.
//! `fights`, `actions`, `wins`, `digest` depend only on the engine + flags; two runs
//! with the same flags must print identical values.
//!
//! Build (from the sts2core checkout):
//!   CARGO_TARGET_DIR=<dir> cargo build --release --bin xsim_w1_site
//! Run (one pinned core):
//!   taskset -c 20 <dir>/release/xsim_w1_site --seeds 2200 --reps 50
//!   <dir>/release/xsim_w1_site --quick          # smoke (44 seeds x 5 reps)
//!   <dir>/release/xsim_w1_site --describe       # resolution / gaps / content counts, no timing

use std::time::Instant;

use sts2core::content::{card, enemy_def, ENCHANTS, ENEMIES, POWERS, RELICS};
use sts2core::ops::POTIONS;
use sts2core::state::{St, State};
use sts2core::step::{current_move_ix, legal_actions, step, Action, MAX_ACTIONS};
use sts2core::synth::encounters::Table;
use sts2core::synth::{build, DeckCard, EnemySpec, FightSpec, RelicSpec};

/// spire-mentor `ACT1_ENCOUNTERS` order (sts2-content/src/encounters/act1_overgrowth.rs)
/// -> sts2core `data/encounters.json` key. Explicit on purpose.
const MAP: &[(&str, &str)] = &[
    ("SHRINKER_BEETLE_WEAK", "ShrinkerBeetleWeak"),
    ("FUZZY_WURM_CRAWLER_WEAK", "FuzzyWurmCrawlerWeak"),
    ("NIBBITS_WEAK", "NibbitsWeak"),
    ("SLIMES_WEAK", "SlimesWeak"),
    ("CUBEX_CONSTRUCT_NORMAL", "CubexConstructNormal"),
    ("FLYCONID_NORMAL", "FlyconidNormal"),
    ("FOGMOG_NORMAL", "FogmogNormal"),
    ("INKLETS_NORMAL", "InkletsNormal"),
    ("MAWLER_NORMAL", "MawlerNormal"),
    ("NIBBITS_NORMAL", "NibbitsNormal"),
    ("OVERGROWTH_CRAWLERS", "OvergrowthCrawlers"),
    ("RUBY_RAIDERS_NORMAL", "RubyRaidersNormal"),
    ("SLIMES_NORMAL", "SlimesNormal"),
    ("SLITHERING_STRANGLER_NORMAL", "SlitheringStranglerNormal"),
    ("SNAPPING_JAXFRUIT_NORMAL", "SnappingJaxfruitNormal"),
    ("VINE_SHAMBLER_NORMAL", "VineShamblerNormal"),
    ("BYGONE_EFFIGY_ELITE", "BygoneEffigyElite"),
    ("BYRDONIS_ELITE", "ByrdonisElite"),
    ("PHROG_PARASITE_ELITE", "PhrogParasiteElite"),
    ("VANTOM_BOSS", "VantomBoss"),
    ("CEREMONIAL_BEAST_BOSS", "CeremonialBeastBoss"),
    ("THE_KIN_BOSS", "TheKinBoss"),
];

#[derive(Clone, Copy, PartialEq, Eq)]
enum Policy {
    /// uniform over sts2core's own `legal_actions` (identical hand cards collapsed)
    Engine,
    /// uniform over card INSTANCES: each PlayCard weighted by its identical copies in hand
    Instance,
}

struct Args {
    seeds: u64,
    seed_start: u64,
    reps: u64,
    max_steps: u64,
    burning_blood: bool,
    policy: Policy,
    encounters: Vec<&'static str>,
    data_dir: String,
    describe: bool,
}

fn usage() -> ! {
    eprintln!(
        "usage: xsim_w1_site [--seeds N=2200] [--seed-start K=0] [--reps R=50] [--max-steps M=5000]\n\
         \x20                   [--relics burning-blood|none] [--policy engine|instance]\n\
         \x20                   [--encounters ID,ID,...] [--data-dir DIR] [--quick] [--describe]\n\
         encounter ids are spire-mentor ids: {}",
        MAP.iter().map(|(a, _)| *a).collect::<Vec<_>>().join(",")
    );
    std::process::exit(2)
}

fn parse_args() -> Args {
    let mut a = Args {
        seeds: 2200,
        seed_start: 0,
        reps: 50,
        max_steps: 5000,
        burning_blood: true,
        policy: Policy::Engine,
        encounters: MAP.iter().map(|(ours, _)| *ours).collect(),
        data_dir: concat!(env!("CARGO_MANIFEST_DIR"), "/data").to_string(),
        describe: false,
    };
    let argv: Vec<String> = std::env::args().skip(1).collect();
    let mut i = 0;
    let val = |i: usize| -> String { argv.get(i + 1).cloned().unwrap_or_else(|| usage()) };
    let num = |s: String| -> u64 { s.parse().unwrap_or_else(|_| usage()) };
    while i < argv.len() {
        match argv[i].as_str() {
            "--seeds" => { a.seeds = num(val(i)); i += 2; }
            "--seed-start" => { a.seed_start = num(val(i)); i += 2; }
            "--reps" => { a.reps = num(val(i)); i += 2; }
            "--max-steps" => { a.max_steps = num(val(i)); i += 2; }
            "--quick" => { a.seeds = 44; a.reps = 5; i += 1; }
            "--describe" => { a.describe = true; i += 1; }
            "--data-dir" => { a.data_dir = val(i); i += 2; }
            "--relics" => {
                a.burning_blood = match val(i).as_str() {
                    "burning-blood" => true,
                    "none" => false,
                    _ => usage(),
                };
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
            "--encounters" => {
                let list = val(i);
                a.encounters = list
                    .split(',')
                    .filter(|s| !s.is_empty())
                    .map(|id| {
                        MAP.iter().find(|(ours, _)| ours.eq_ignore_ascii_case(id)).map(|(o, _)| *o).unwrap_or_else(|| {
                            eprintln!("unknown encounter id {id:?}");
                            usage()
                        })
                    })
                    .collect();
                if a.encounters.is_empty() {
                    usage();
                }
                i += 2;
            }
            _ => usage(),
        }
    }
    a
}

fn sts2core_key(ours: &str) -> &'static str {
    MAP.iter().find(|(o, _)| *o == ours).map(|(_, k)| *k).expect("mapped")
}

/// splitmix64 finalizer, used to derive the roster pick from the seed.
fn mix64(mut z: u64) -> u64 {
    z = z.wrapping_add(0x9E37_79B9_7F4A_7C15);
    z = (z ^ (z >> 30)).wrapping_mul(0xBF58_476D_1CE4_E5B9);
    z = (z ^ (z >> 27)).wrapping_mul(0x94D0_49BB_1331_11EB);
    z ^ (z >> 31)
}

/// Policy RNG: xoshiro256++ seeded through SplitMix64 (same construction as
/// `rand_xoshiro::Xoshiro256PlusPlus::seed_from_u64`, which spire-mentor's M1 uses),
/// bounded draw by widening multiply. Not bit-identical to rand's `gen_range`; the
/// engines' game RNGs differ anyway, so per-fight sequences are not comparable.
struct PolicyRng([u64; 4]);

impl PolicyRng {
    fn new(seed: u64) -> PolicyRng {
        let mut sm = seed;
        let mut next = || {
            sm = sm.wrapping_add(0x9E37_79B9_7F4A_7C15);
            let mut z = sm;
            z = (z ^ (z >> 30)).wrapping_mul(0xBF58_476D_1CE4_E5B9);
            z = (z ^ (z >> 27)).wrapping_mul(0x94D0_49BB_1331_11EB);
            z ^ (z >> 31)
        };
        PolicyRng([next(), next(), next(), next()])
    }
    #[inline(always)]
    fn next_u64(&mut self) -> u64 {
        let s = &mut self.0;
        let result = s[0].wrapping_add(s[3]).rotate_left(23).wrapping_add(s[0]);
        let t = s[1] << 17;
        s[2] ^= s[0];
        s[3] ^= s[1];
        s[1] ^= s[2];
        s[0] ^= s[3];
        s[2] ^= t;
        s[3] = s[3].rotate_left(45);
        result
    }
    #[inline(always)]
    fn below(&mut self, n: usize) -> usize {
        ((self.next_u64() as u128 * n as u128) >> 64) as usize
    }
}

/// Pick an action index. `Engine`: uniform over the engine's list. `Instance`: each
/// PlayCard weighted by how many identical CardInst copies sit in hand (sts2core keeps
/// only the first of identical hand cards, `step::legal_actions`).
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

fn starter_deck() -> Vec<DeckCard> {
    let mut d = Vec::new();
    for _ in 0..5 {
        d.push(DeckCard::new(card::STRIKE, false));
    }
    for _ in 0..4 {
        d.push(DeckCard::new(card::DEFEND, false));
    }
    d.push(DeckCard::new(card::BASH, false));
    d
}

struct Start {
    state: State,
    enc: usize,
    gaps: Vec<String>,
}

fn start_state(table: &Table, args: &Args, deck: &[DeckCard], seed: u64) -> Start {
    let enc = (seed % args.encounters.len() as u64) as usize;
    let key = sts2core_key(args.encounters[enc]);
    // Random rosters: sts2core's own weighted distribution; pick derived from the seed.
    let enemies: Vec<EnemySpec> = table
        .resolve_sampled(key, mix64(seed ^ 0x5157_5349_4D57_3100))
        .unwrap_or_else(|e| panic!("encounter {key} does not resolve on sts2core: {e}"));
    let relics_bb = [RelicSpec::new("BURNING_BLOOD")];
    let relics: &[RelicSpec] = if args.burning_blood { &relics_bb } else { &[] };
    let mut spec = FightSpec::new(deck, 80, &enemies, seed);
    spec.max_hp = 80;
    spec.relics = relics;
    spec.ascension = 0;
    spec.potion_slots = 3;
    spec.boss_room = table.is_boss(key);
    let built = build(&spec);
    let gaps = built.gaps.iter().map(|g| g.to_string()).collect();
    Start { state: built.state, enc, gaps }
}

/// One random fight on a copy of `start`: (actions applied, won, ended).
#[inline(never)]
fn play(start: &State, rep_seed: u64, policy: Policy, max_steps: u64) -> (u64, bool, bool) {
    let mut s = *start; // timed clone: POD memcpy
    let mut rng = PolicyRng::new(rep_seed);
    let mut steps = 0u64;
    while !s.combat_over && steps < max_steps {
        let (acts, n) = legal_actions(&s);
        if n == 0 {
            break;
        }
        let k = pick(&s, &acts, n, policy, &mut rng);
        s = step(s, acts[k]);
        steps += 1;
    }
    (steps, s.combat_over && !s.player_dead, s.combat_over)
}

fn json_str(s: &str) -> String {
    let mut o = String::from("\"");
    for ch in s.chars() {
        match ch {
            '"' => o.push_str("\\\""),
            '\\' => o.push_str("\\\\"),
            '\n' => o.push_str("\\n"),
            c if (c as u32) < 0x20 => o.push_str(&format!("\\u{:04x}", c as u32)),
            c => o.push(c),
        }
    }
    o.push('"');
    o
}

fn describe(table: &Table, args: &Args, deck: &[DeckCard]) {
    println!("# sts2core content tables (counted from the compiled tables, not from docs)");
    let modelled = RELICS.iter().filter(|r| r.modelled).count();
    let ench_modelled = ENCHANTS.iter().filter(|e| e.modelled).count();
    println!(
        "CARDS={} ENEMIES={} POWERS(trigger rules)={} RELICS={} (modelled {}) POTIONS(slots incl. NONE)={} ENCHANTS={} (modelled {}) size_of::<State>={}",
        sts2core::content::CARDS.len(),
        ENEMIES.len(),
        POWERS.len(),
        RELICS.len(),
        modelled,
        POTIONS.len(),
        ENCHANTS.len(),
        ench_modelled,
        std::mem::size_of::<State>()
    );
    for act in &table.acts {
        let (ok, n) = table.coverage(act);
        let missing: Vec<&String> = act.encounters.iter().filter(|k| !table.can_open(k)).collect();
        println!("coverage {} (index {}): {ok}/{n} openable; not openable: {:?}", act.name, act.index, missing);
    }
    let bb = sts2core::content::relic_by_id("BURNING_BLOOD");
    println!(
        "BURNING_BLOOD: in table={} modelled={} synth_gap={:?}",
        bb.is_some(),
        bb.map_or(false, |r| r.modelled),
        sts2core::content::synth_gap("BURNING_BLOOD")
    );
    println!("# W1 encounters (spire-mentor id -> sts2core key): first seed of each, its roster/HP/opening move");
    for (ix, ours) in args.encounters.iter().enumerate() {
        let key = sts2core_key(ours);
        let e = table.encounters.get(key);
        let kind = match e {
            None => "MISSING".to_string(),
            Some(e) if e.exact => format!("exact {:?}", e.monsters.clone().unwrap_or_default()),
            Some(e) => format!("distribution of {} variants {:?}", e.variants.len(), e.variants),
        };
        let seed = args.seed_start + ((ix as u64 + args.encounters.len() as u64
            - (args.seed_start % args.encounters.len() as u64))
            % args.encounters.len() as u64);
        let st = start_state(table, args, deck, seed);
        let s = &st.state;
        let mons: Vec<String> = (0..s.n_enemies as usize)
            .map(|e| {
                let d = enemy_def(s.enemy_def[e]);
                let mv = d.moves.get(current_move_ix(d, s, e)).map_or("?", |m| m.name);
                format!("{}#{} hp {}/{} opens {}", d.name, s.enemy_def[e], s.enemies[e].hp, s.enemies[e].max_hp, mv)
            })
            .collect();
        println!(
            "[{ix:2}] {ours} -> {key} | {kind}\n      seed {seed}: {} | player hp {}/{} BurningBlood status {} | hand {} draw {} | gaps {:?}",
            mons.join("; "),
            s.player.hp,
            s.player.max_hp,
            s.player.get(St::BurningBlood),
            s.n_hand,
            s.n_draw,
            st.gaps
        );
    }
}

fn main() {
    let args = parse_args();
    let table = Table::load(&args.data_dir).unwrap_or_else(|e| panic!("encounter table: {e}"));
    let deck = starter_deck();
    let mut ok = true;
    for ours in &args.encounters {
        let key = sts2core_key(ours);
        if !table.can_open(key) {
            eprintln!("encounter {ours} -> {key}: sts2core cannot open it");
            ok = false;
        }
    }
    if !ok {
        std::process::exit(1);
    }
    if args.describe {
        describe(&table, &args, &deck);
        println!(
            "{{\"bench\":\"xsim_w1_site\",\"engine\":\"sts2core\",\"mode\":\"describe\",\"n_encounters\":{}}}",
            args.encounters.len()
        );
        return;
    }

    // Start states: built once, outside the timed region.
    let starts: Vec<Start> =
        (args.seed_start..args.seed_start + args.seeds).map(|s| start_state(&table, &args, &deck, s)).collect();
    let mut gap_kinds: Vec<String> = Vec::new();
    let mut starts_with_gaps = 0u64;
    for st in &starts {
        if !st.gaps.is_empty() {
            starts_with_gaps += 1;
        }
        for g in &st.gaps {
            if !gap_kinds.contains(g) {
                gap_kinds.push(g.clone());
            }
        }
    }
    let bb_status = starts[0].state.player.get(St::BurningBlood);
    let _ = play(&starts[0].state, 1, args.policy, args.max_steps); // warm-up

    let n_enc = args.encounters.len();
    let mut per_fights = vec![0u64; n_enc];
    let mut per_wins = vec![0u64; n_enc];
    let mut per_actions = vec![0u64; n_enc];
    let mut fights = 0u64;
    let mut wins = 0u64;
    let mut actions = 0u64;
    let mut anomalies = 0u64;
    let mut digest: u64 = 0xcbf2_9ce4_8422_2325;
    let t0 = Instant::now();
    for (i, st) in starts.iter().enumerate() {
        for r in 0..args.reps {
            let (steps, won, ended) = play(&st.state, (i as u64) * 1_000_003 + r, args.policy, args.max_steps);
            fights += 1;
            wins += u64::from(won);
            actions += steps;
            anomalies += u64::from(!ended);
            per_fights[st.enc] += 1;
            per_wins[st.enc] += u64::from(won);
            per_actions[st.enc] += steps;
            digest = (digest ^ ((steps << 1) | u64::from(won))).wrapping_mul(0x0100_0000_01b3);
        }
    }
    let secs = t0.elapsed().as_secs_f64();

    println!("encounters (spire-mentor id -> sts2core key | fights wins actions/fight):");
    let mut per_json = Vec::new();
    for (ix, ours) in args.encounters.iter().enumerate() {
        let f = per_fights[ix].max(1) as f64;
        println!(
            "  [{ix:2}] {ours} -> {} | {} {} {:.1}",
            sts2core_key(ours),
            per_fights[ix],
            per_wins[ix],
            per_actions[ix] as f64 / f
        );
        per_json.push(format!(
            "{{\"id\":{},\"sts2core_key\":{},\"fights\":{},\"wins\":{},\"actions\":{}}}",
            json_str(ours),
            json_str(sts2core_key(ours)),
            per_fights[ix],
            per_wins[ix],
            per_actions[ix]
        ));
    }
    println!("starts with constructor gaps: {starts_with_gaps}; distinct gaps: {gap_kinds:?}");
    let enc_list: Vec<String> = args.encounters.iter().map(|e| json_str(e)).collect();
    let gap_list: Vec<String> = gap_kinds.iter().map(|g| json_str(g)).collect();
    println!(
        "{{\"bench\":\"xsim_w1_site\",\"engine\":\"sts2core\",\"workload\":\"W1\",\"policy\":\"{}\",\"relics\":\"{}\",\"burning_blood_status\":{},\"state_bytes\":{},\"n_encounters\":{},\"encounters\":[{}],\"seeds\":{},\"seed_start\":{},\"reps\":{},\"max_steps\":{},\"fights\":{},\"wins\":{},\"win_rate\":{:.4},\"actions\":{},\"actions_per_fight\":{:.2},\"anomalies\":{},\"digest\":\"{:016x}\",\"starts_with_gaps\":{},\"gaps\":[{}],\"per_encounter\":[{}],\"elapsed_s\":{:.4},\"fights_per_s\":{:.1},\"actions_per_s\":{:.0}}}",
        if args.policy == Policy::Engine { "engine" } else { "instance" },
        if args.burning_blood { "burning-blood" } else { "none" },
        bb_status,
        std::mem::size_of::<State>(),
        n_enc,
        enc_list.join(","),
        args.seeds,
        args.seed_start,
        args.reps,
        args.max_steps,
        fights,
        wins,
        wins as f64 / fights.max(1) as f64,
        actions,
        actions as f64 / fights.max(1) as f64,
        anomalies,
        digest,
        starts_with_gaps,
        gap_list.join(","),
        per_json.join(","),
        secs,
        fights as f64 / secs,
        actions as f64 / secs,
    );
}
