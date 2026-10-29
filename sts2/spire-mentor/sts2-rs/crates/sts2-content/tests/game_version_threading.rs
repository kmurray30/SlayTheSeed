//! GH #787: `GameVersion` is threaded exactly like ascension —
//! `RunManager` → `RunState` → every `CombatState` built from the run — and
//! the default build is untouched.

use sts2_content::run_callbacks::{initialize_run_setup, new_run_manager_for_character};
use sts2_core::run_manager::{Phase, RunManager};
use sts2_core::{run_hash, state_hash, CharacterId, GameVersion};

/// Take the first legal action until the run enters its first combat.
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

fn run_at(version: Option<GameVersion>) -> RunManager {
    let rm = new_run_manager_for_character(7, CharacterId::Ironclad);
    let mut rm = match version {
        Some(v) => rm.with_game_version(v),
        None => rm,
    };
    initialize_run_setup(&mut rm);
    rm
}

#[test]
fn default_run_and_combat_are_v0107() {
    let mut rm = run_at(None);
    assert_eq!(rm.game_version, GameVersion::V0_107_1);
    assert_eq!(rm.run_state.game_version, GameVersion::V0_107_1);
    walk_to_first_combat(&mut rm);
    let combat = rm.combat.as_ref().expect("in combat");
    assert_eq!(combat.game_version, GameVersion::V0_107_1);
}

#[test]
fn run_manager_version_propagates_into_combat() {
    let mut rm = run_at(Some(GameVersion::V0_111_0));
    assert_eq!(rm.game_version, GameVersion::V0_111_0);
    assert_eq!(rm.run_state.game_version, GameVersion::V0_111_0);
    walk_to_first_combat(&mut rm);
    let combat = rm.combat.as_ref().expect("in combat");
    assert_eq!(combat.game_version, GameVersion::V0_111_0);
    // Clones (the rollout / labeler path) carry it too.
    let cloned = rm.clone();
    assert_eq!(cloned.game_version, GameVersion::V0_111_0);
    assert_eq!(
        cloned.combat.as_ref().unwrap().game_version,
        GameVersion::V0_111_0
    );
}

#[test]
fn explicit_default_version_is_a_no_op() {
    let a = run_at(None);
    let b = run_at(Some(GameVersion::V0_107_1));
    assert_eq!(run_hash(&a), run_hash(&b));
    assert_eq!(a.run_state, b.run_state);
}

#[test]
fn digests_see_a_non_default_version() {
    let mut rm = run_at(None);
    walk_to_first_combat(&mut rm);
    let combat = rm.combat.clone().expect("in combat");
    let mut other = combat.clone();
    other.game_version = GameVersion::V0_111_0;
    assert_ne!(state_hash(&combat), state_hash(&other));
    let mut rm2 = rm.clone();
    rm2.game_version = GameVersion::V0_111_0;
    assert_ne!(run_hash(&rm), run_hash(&rm2));
}
