//! GH #788: run seeding follows the run's game build end to end — the
//! v0.111.0 master seed is the 64-bit XxHash64 of the seed string, every
//! stream / per-content RNG derives from it in `ulong` arithmetic, and the
//! v0.107.1 path is bit-identical to the unversioned constructors.

use sts2_content::run_callbacks::{
    initialize_run_setup, new_run_manager_for_character,
    new_run_manager_for_character_from_string_seed,
    new_run_manager_for_character_from_string_seed_for,
};
use sts2_core::run_manager::{Phase, RunManager};
use sts2_core::{run_hash, CharacterId, GameVersion, RunRngSet};

/// `xxh64("3ACCFREXKR", 0)` (reference `xxhsum -H1`).
const M: u64 = 0xCCD0_8772_EAB0_03D2;

fn walk_to_first_combat(rm: &mut RunManager) {
    for _ in 0..64 {
        if rm.phase() == Phase::Combat {
            return;
        }
        let actions = rm.get_available_actions();
        assert!(!actions.is_empty(), "stuck in {:?}", rm.phase());
        let _ = rm.take_action(actions[0]);
    }
    panic!("never reached a combat (phase {:?})", rm.phase());
}

#[test]
fn v0111_string_seed_master_is_xxh64_of_the_canonical_seed() {
    // Lower-case + `O`/`I` folding is the game's canonicalisation; the hash
    // is of the canonical string.
    let rm = new_run_manager_for_character_from_string_seed_for(
        GameVersion::V0_111_0,
        "3accfrexkr",
        CharacterId::Ironclad,
    );
    assert_eq!(rm.game_version, GameVersion::V0_111_0);
    assert_eq!(rm.run_state.rng.game_version, GameVersion::V0_111_0);
    assert_eq!(rm.run_state.rng.seed, M);
    // Every stream is the v0.111.0 derivation from M. Construction consumed
    // the act-1 map rng (map generation) and `run_manager` (the act-1 boss
    // pre-roll); every other stream is untouched.
    let mut fresh = RunRngSet::new_for(GameVersion::V0_111_0, M);
    assert_eq!(
        fresh.run_manager.seed(),
        rm.run_state.rng.run_manager.seed()
    );
    fresh.map_rngs = rm.run_state.rng.map_rngs.clone();
    fresh.run_manager = rm.run_state.rng.run_manager.clone();
    assert_eq!(rm.run_state.rng, fresh);
    // `up_front` = M + xxh64("up_front"), by hand.
    assert_eq!(rm.run_state.rng.up_front.seed(), 0xE75B_02D3_C4D4_4252);
}

#[test]
fn v0107_versioned_factory_is_the_legacy_factory() {
    for seed in ["3ACCFREXKR", "FUZZSMOKE1", "0"] {
        let a = new_run_manager_for_character_from_string_seed(seed, CharacterId::Silent);
        let b = new_run_manager_for_character_from_string_seed_for(
            GameVersion::V0_107_1,
            seed,
            CharacterId::Silent,
        );
        assert_eq!(a.seed, b.seed, "{seed}");
        assert_eq!(a.run_state, b.run_state, "{seed}");
        assert_eq!(run_hash(&a), run_hash(&b), "{seed}");
    }
}

/// Switching to v0.111.0 and back re-derives everything from the master
/// seed, landing exactly on a never-switched v0.107.1 run.
#[test]
fn version_round_trip_restores_the_default_run() {
    let fresh = new_run_manager_for_character(-12_345, CharacterId::Ironclad);
    let switched = new_run_manager_for_character(-12_345, CharacterId::Ironclad)
        .with_game_version(GameVersion::V0_111_0)
        .with_game_version(GameVersion::V0_107_1);
    assert_eq!(fresh.run_state, switched.run_state);
    assert_eq!(run_hash(&fresh), run_hash(&switched));
}

#[test]
fn v0111_changes_the_run_but_not_its_shape() {
    let mut a = new_run_manager_for_character(7, CharacterId::Ironclad);
    let mut b = new_run_manager_for_character(7, CharacterId::Ironclad)
        .with_game_version(GameVersion::V0_111_0);
    initialize_run_setup(&mut a);
    initialize_run_setup(&mut b);
    // Same master seed (a `uint`), different stream arithmetic.
    assert_eq!(a.run_state.rng.seed, b.run_state.rng.seed);
    assert_ne!(
        a.run_state.rng.up_front.seed(),
        b.run_state.rng.up_front.seed()
    );
    assert_ne!(run_hash(&a), run_hash(&b));
    assert_eq!(
        a.run_state.generated_rooms.len(),
        b.run_state.generated_rooms.len()
    );
}

/// The run-start Neow offers come off `EventModel`'s per-event RNG —
/// `Rng(M + 0 + xxh64("NEOW"))` in v0.111.0.
#[test]
fn v0111_neow_offers_use_the_v0111_event_rng() {
    let rm = new_run_manager_for_character_from_string_seed_for(
        GameVersion::V0_111_0,
        "3ACCFREXKR",
        CharacterId::Ironclad,
    )
    .with_neow_start();
    let mut rng = sts2_core::DotNetRng::from_seed(0xE669_242D_3E60_623F);
    let expected = sts2_core::roll_neow_offers(&mut rng);
    assert_eq!(rm.run_state.neow_offers, Some(expected));
}

/// A v0.111.0 run plays: setup, map, and the first combat (whose encounter
/// roster rolls off `Rng(M + TotalFloor + xxh64(Id.Entry))`) all run, and the
/// combat inherits the build's RNG set.
#[test]
fn v0111_run_reaches_its_first_combat() {
    let mut rm = new_run_manager_for_character_from_string_seed_for(
        GameVersion::V0_111_0,
        "3ACCFREXKR",
        CharacterId::Ironclad,
    );
    initialize_run_setup(&mut rm);
    walk_to_first_combat(&mut rm);
    let combat = rm.combat.as_ref().expect("in combat");
    assert_eq!(combat.game_version, GameVersion::V0_111_0);
    assert_eq!(combat.run_rng.game_version, GameVersion::V0_111_0);
    assert_eq!(combat.run_rng.seed, M);
}

/// `with_run_seed` at v0.107.1 keeps only the low 32 bits (a `uint` seed).
#[test]
fn v0107_with_run_seed_truncates_to_uint() {
    let a = new_run_manager_for_character(0x2345_6789, CharacterId::Ironclad);
    let b = new_run_manager_for_character(1, CharacterId::Ironclad).with_run_seed(0x1_2345_6789);
    assert_eq!(b.seed, 0x2345_6789);
    assert_eq!(a.run_state, b.run_state);
}
