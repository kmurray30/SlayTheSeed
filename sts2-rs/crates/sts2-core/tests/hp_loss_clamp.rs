//! GH #791 (S6): the HP-loss amount never goes negative.
//!
//! v0.111.0 `Creature.LoseHpInternal` clamps the amount to `[0, 999999999]`
//! (decomp `Entities.Creatures/Creature.cs:450`):
//!
//! [decompiled C# omitted — see the cited path]
//!
//! v0.107.1 only capped the top (`Math.Min(amount, 999999999m)`), so a
//! negative amount — e.g. an over-drawn HardenedShell cap,
//! `Min(amount, Amount - damageReceivedThisTurn)` — would have HEALED. No
//! v0.107.1 path drives that cap negative (the accumulator only counts HP lost
//! through the same cap), and the Rust pipeline already clamps at 0 for both
//! builds; this pins the v0.111.0 contract on a hand-forced negative cap.

use smallvec::SmallVec;
use std::num::NonZeroU32;

use sts2_core::damage::apply_damage;
use sts2_core::hooks::{CreatureRef, ValueProps};
use sts2_core::{
    CombatState, Creature, CreatureUid, GameVersion, IntentHistory, MonsterId, MonsterKind,
    PowerKind,
};

#[test]
fn overdrawn_hardened_shell_neither_damages_nor_heals_v0111() {
    let mut state = CombatState {
        game_version: GameVersion::V0_111_0,
        ..CombatState::default()
    };
    state.player.current_hp = 70;
    state.player.max_hp = 70;
    let _ = state.init_player_creature();
    let id = NonZeroU32::new(50).expect("nz");
    state.creatures.push(Creature {
        id,
        current_hp: 75,
        max_hp: 75,
        block: 0,
        powers: SmallVec::new(),
        species: Some(MonsterKind::SkulkingColony),
        intent: None,
        intent_history: IntentHistory::default(),
        monster_id: MonsterId("SkulkingColony"),
        ai: None,
    });
    let uid = CreatureUid(id);
    state.enemy_uids.push(uid);
    sts2_core::card_effects::apply_power_to_creature(&mut state, uid, PowerKind::HardenedShell, 20);
    // 25 received against a 20 budget: the cap is 20 - 25 = -5.
    for p in &mut state.powers {
        if p.kind == PowerKind::HardenedShell {
            p.counter = 25;
        }
    }
    let loss = apply_damage(
        &mut state,
        CreatureRef::Player,
        uid,
        9,
        ValueProps::MOVE,
        None,
    );
    assert_eq!(loss, 0);
    let hp = state
        .creatures
        .iter()
        .find(|c| c.id == id)
        .map(|c| c.current_hp);
    assert_eq!(
        hp,
        Some(75),
        "a negative cap loses nothing and heals nothing"
    );
}
