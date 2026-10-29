//! GH #796 (S8d): v0.111.0 monster changes, driven through the run's real
//! combat-entry path (`run_bridge::enter_combat_from_roster` → `spawn_roster`)
//! so the `game_version` the run carries is what selects the values.
//!
//! Each `*_v0111` test sits beside a v0.107.1 twin that pins the old value:
//! the default build must not move.

use sts2_content::monster_dispatch::{build_ai_for_version, MonsterSpawnArgs, SpawnPosition};
use sts2_content::run_bridge::enter_combat_from_roster;
use sts2_content::run_callbacks::{initialize_run_setup, new_run_manager_for_character};
use sts2_core::cards::CardId;
use sts2_core::monster_ai::MonsterState;
use sts2_core::monsters::{roll_unique_max_hps_for, Intent};
use sts2_core::runstate::{RoomType, RunState};
use sts2_core::{apply, Action, CharacterId, CombatState, GameVersion, MonsterKind, PowerKind};

fn run_state(version: GameVersion, ascension: u8) -> RunState {
    let mut rm = new_run_manager_for_character(7, CharacterId::Ironclad).with_game_version(version);
    rm.run_state.ascension_level = ascension;
    initialize_run_setup(&mut rm);
    rm.run_state
}

/// A one-monster combat entered the run's way, at `version` / `ascension`.
fn solo_combat(kind: MonsterKind, hp: i32, version: GameVersion, ascension: u8) -> CombatState {
    let rs = run_state(version, ascension);
    let combat =
        enter_combat_from_roster(&rs, &[(kind, MonsterSpawnArgs::None, hp)], RoomType::Elite);
    assert_eq!(combat.game_version, version);
    combat
}

fn enemy(state: &CombatState) -> &sts2_core::Creature {
    let uid = state.enemy_uids[0];
    state
        .creatures
        .iter()
        .find(|c| c.id == uid.0)
        .expect("enemy")
}

fn enemy_power(state: &CombatState, kind: PowerKind) -> i32 {
    let e = enemy(state);
    e.powers
        .iter()
        .filter_map(|pu| state.powers.iter().find(|p| p.id == pu.0 && p.kind == kind))
        .map(|p| p.amount)
        .sum()
}

fn first_attack(ai: &sts2_core::monster_ai::MonsterAi, id: &str) -> Option<i32> {
    match ai.states.get(id)? {
        MonsterState::Move(m) => m.intents.iter().find_map(|i| match i {
            Intent::Attack { damage } | Intent::MultiAttack { damage, .. } => Some(*damage),
            _ => None,
        }),
        _ => None,
    }
}

fn ai_for(
    kind: MonsterKind,
    ascension: u8,
    version: GameVersion,
) -> sts2_core::monster_ai::MonsterAi {
    build_ai_for_version(
        kind,
        MonsterSpawnArgs::None,
        SpawnPosition::default(),
        ascension,
        version,
    )
}

// ── TorchHeadAmalgam ─────────────────────────────────────────────────────

#[test]
fn torch_head_amalgam_opens_on_tackle_18() {
    let ai = ai_for(MonsterKind::TorchHeadAmalgam, 0, GameVersion::V0_107_1);
    assert_eq!(ai.current_move().id, "TACKLE_1");
    assert_eq!(first_attack(&ai, "TACKLE_1"), Some(18));
    let a9 = ai_for(MonsterKind::TorchHeadAmalgam, 9, GameVersion::V0_107_1);
    assert_eq!(first_attack(&a9, "TACKLE_1"), Some(19));
    assert_eq!(first_attack(&a9, "TACKLE_3"), Some(15));
}

/// v0.111.0 `TorchHeadAmalgam.cs:51-62`: STRONG_TACKLE (26 / A9 32) opens,
/// then TACKLE_2 (18 / A9 22) → BEAM → TACKLE_3 (14 / A9 16) → TACKLE_4 → BEAM.
#[test]
fn torch_head_amalgam_opens_on_strong_tackle_v0111() {
    let mut ai = ai_for(MonsterKind::TorchHeadAmalgam, 0, GameVersion::V0_111_0);
    assert_eq!(ai.current_move().id, "STRONG_TACKLE");
    assert!(
        !ai.states.contains_key("TACKLE_1"),
        "the 0.107 opener is gone"
    );
    let mut order = vec![ai.current_move().id];
    for _ in 0..6 {
        ai.on_move_performed();
        order.push(ai.roll_move_seeded(0).id);
    }
    assert_eq!(
        order,
        [
            "STRONG_TACKLE",
            "TACKLE_2",
            "BEAM",
            "TACKLE_3",
            "TACKLE_4",
            "BEAM",
            "TACKLE_3"
        ]
    );
    let a0 = ai_for(MonsterKind::TorchHeadAmalgam, 0, GameVersion::V0_111_0);
    let a9 = ai_for(MonsterKind::TorchHeadAmalgam, 9, GameVersion::V0_111_0);
    for (id, base, deadly) in [
        ("STRONG_TACKLE", 26, 32),
        ("TACKLE_2", 18, 22),
        ("TACKLE_3", 14, 16),
        ("TACKLE_4", 14, 16),
        ("BEAM", 8, 8),
    ] {
        assert_eq!(first_attack(&a0, id), Some(base), "{id} A0");
        assert_eq!(first_attack(&a9, id), Some(deadly), "{id} A9");
    }
}

// ── MechaKnight ──────────────────────────────────────────────────────────

fn burns_in_hand(state: &CombatState) -> usize {
    state
        .player
        .hand
        .iter()
        .filter(|u| state.cards[u.0.get() as usize - 1].card_id == CardId::Burn)
        .count()
}

/// Walk to the end of turn 2's enemy phase (CHARGE on turn 1, FLAMETHROWER
/// on turn 2) and report (player HP lost to FLAMETHROWER, Burns in hand).
fn flamethrower_outcome(version: GameVersion) -> (i32, usize) {
    let mut state = solo_combat(MonsterKind::MechaKnight, 300, version, 0);
    assert_eq!(
        enemy(&state).ai.as_ref().unwrap().current_move().id,
        "CHARGE"
    );
    let _ = apply(&mut state, Action::EndTurn);
    assert_eq!(
        enemy(&state).ai.as_ref().unwrap().current_move().id,
        "FLAMETHROWER",
        "turn 2 telegraphs FLAMETHROWER"
    );
    // Clear the turn-1 Burns (none expected) and the block, then end turn 2.
    let hp_before = state.player.current_hp;
    let burns_before = burns_in_hand(&state);
    let _ = apply(&mut state, Action::EndTurn);
    // Burns are added to the hand during the enemy turn; the next player
    // turn's draw leaves them there (the hand is not discarded before draw).
    let burns = burns_in_hand(&state) - burns_before;
    (hp_before - state.player.current_hp, burns)
}

#[test]
fn mecha_knight_flamethrower_only_adds_burns() {
    let ai = ai_for(MonsterKind::MechaKnight, 0, GameVersion::V0_107_1);
    match ai.states.get("FLAMETHROWER") {
        Some(MonsterState::Move(m)) => assert_eq!(m.intents.as_slice(), &[Intent::StatusCard]),
        other => panic!("FLAMETHROWER missing: {other:?}"),
    }
    let (hp_lost, burns) = flamethrower_outcome(GameVersion::V0_107_1);
    assert_eq!(hp_lost, 0, "v0.107.1 FLAMETHROWER deals no damage");
    assert_eq!(burns, 4);
}

/// v0.111.0 `MechaKnight.cs:85,134-140`: FLAMETHROWER attacks for
/// `FlamethrowerDamage` (8 / A9 12), THEN adds the 4 Burns.
#[test]
fn mecha_knight_flamethrower_attacks_then_burns_v0111() {
    let a0 = ai_for(MonsterKind::MechaKnight, 0, GameVersion::V0_111_0);
    match a0.states.get("FLAMETHROWER") {
        Some(MonsterState::Move(m)) => assert_eq!(
            m.intents.as_slice(),
            &[Intent::Attack { damage: 8 }, Intent::StatusCard]
        ),
        other => panic!("FLAMETHROWER missing: {other:?}"),
    }
    let a9 = ai_for(MonsterKind::MechaKnight, 9, GameVersion::V0_111_0);
    assert_eq!(first_attack(&a9, "FLAMETHROWER"), Some(12));
    // CHARGE / HEAVY_CLEAVE are unchanged.
    assert_eq!(first_attack(&a0, "CHARGE"), Some(25));
    assert_eq!(first_attack(&a9, "HEAVY_CLEAVE"), Some(40));
    let (hp_lost, burns) = flamethrower_outcome(GameVersion::V0_111_0);
    assert_eq!(
        hp_lost, 8,
        "v0.111.0 FLAMETHROWER hits for 8 (no player block)"
    );
    assert_eq!(burns, 4);
}

// ── Plain value rows ─────────────────────────────────────────────────────

#[test]
fn reworked_attack_values_v0107() {
    for (kind, id, base, deadly) in [
        (MonsterKind::Aeonglass, "EBB_MOVE", 26, 32),
        (MonsterKind::Axebot, "HAMMER_UPPERCUT", 12, 14),
        (MonsterKind::Axebot, "ONE_TWO", 9, 10),
        (MonsterKind::SoulFysh, "DE_GAS", 16, 17),
    ] {
        assert_eq!(
            first_attack(&ai_for(kind, 0, GameVersion::V0_107_1), id),
            Some(base)
        );
        assert_eq!(
            first_attack(&ai_for(kind, 9, GameVersion::V0_107_1), id),
            Some(deadly)
        );
    }
}

/// v0.111.0: Aeonglass.cs:40 EbbDamage (.., 26, 22); Axebot.cs:42,46
/// OneTwo (.., 11, 10) and HammerUppercut (.., 18, 14); SoulFysh.cs:54 DeGas
/// (.., 18, 16).
#[test]
fn reworked_attack_values_v0111() {
    for (kind, id, base, deadly) in [
        (MonsterKind::Aeonglass, "EBB_MOVE", 22, 26),
        (MonsterKind::Aeonglass, "EYE_LASERS_MOVE", 11, 12),
        (MonsterKind::Axebot, "HAMMER_UPPERCUT", 14, 18),
        (MonsterKind::Axebot, "ONE_TWO", 10, 11),
        (MonsterKind::SoulFysh, "DE_GAS", 16, 18),
    ] {
        assert_eq!(
            first_attack(&ai_for(kind, 0, GameVersion::V0_111_0), id),
            Some(base),
            "{kind:?} {id} A0"
        );
        assert_eq!(
            first_attack(&ai_for(kind, 9, GameVersion::V0_111_0), id),
            Some(deadly),
            "{kind:?} {id} A9"
        );
    }
}

// ── HP rows ──────────────────────────────────────────────────────────────

#[test]
fn exoskeleton_and_entomancer_hp_v0107() {
    assert_eq!(
        MonsterKind::Exoskeleton.min_initial_hp_for(8, GameVersion::V0_107_1),
        25
    );
    assert_eq!(
        MonsterKind::Entomancer.min_initial_hp_for(8, GameVersion::V0_107_1),
        155
    );
}

/// v0.111.0 Exoskeleton.cs:30-32 (A8 26-30) and Entomancer.cs:23-25 (A8 165).
#[test]
fn exoskeleton_and_entomancer_hp_v0111() {
    let v = GameVersion::V0_111_0;
    assert_eq!(MonsterKind::Exoskeleton.min_initial_hp_for(0, v), 24);
    assert_eq!(MonsterKind::Exoskeleton.min_initial_hp_for(8, v), 26);
    assert_eq!(MonsterKind::Entomancer.min_initial_hp_for(0, v), 145);
    assert_eq!(MonsterKind::Entomancer.min_initial_hp_for(8, v), 165);
    // A8 Exoskeleton rolls land in 26..=30 and can reach 30.
    let mut seen_max = false;
    for seed in 0..200 {
        let mut rng = sts2_core::dotnet_rng::Rng::new(seed);
        let hp = roll_unique_max_hps_for(&[MonsterKind::Exoskeleton], &mut rng, 8, v)[0];
        assert!((26..=30).contains(&hp), "seed {seed}: {hp}");
        seen_max |= hp == 30;
    }
    assert!(seen_max, "the 0.111 A8 ceiling 30 is reachable");
    // A0 is unchanged between builds.
    for seed in 0..50 {
        let mut a = sts2_core::dotnet_rng::Rng::new(seed);
        let mut b = sts2_core::dotnet_rng::Rng::new(seed);
        assert_eq!(
            roll_unique_max_hps_for(&[MonsterKind::Exoskeleton], &mut a, 0, v),
            roll_unique_max_hps_for(
                &[MonsterKind::Exoskeleton],
                &mut b,
                0,
                GameVersion::V0_107_1
            ),
        );
    }
}

// ── Spawn / move powers ──────────────────────────────────────────────────

#[test]
fn globe_head_galvanic_is_flat_six() {
    let a9 = solo_combat(MonsterKind::GlobeHead, 158, GameVersion::V0_107_1, 9);
    assert_eq!(enemy_power(&a9, PowerKind::Galvanic), 6);
}

/// v0.111.0 GlobeHead.cs:39,46: `GalvanicPowerAmount =>
/// GetValueIfAscension(DeadlyEnemies, 8, 6)`.
#[test]
fn globe_head_galvanic_scales_at_a9_v0111() {
    let a0 = solo_combat(MonsterKind::GlobeHead, 148, GameVersion::V0_111_0, 0);
    assert_eq!(enemy_power(&a0, PowerKind::Galvanic), 6);
    let a9 = solo_combat(MonsterKind::GlobeHead, 158, GameVersion::V0_111_0, 9);
    assert_eq!(enemy_power(&a9, PowerKind::Galvanic), 8);
}
