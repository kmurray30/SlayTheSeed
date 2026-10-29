use super::prelude::*;

/// Ambergris — **v0.111.0-only** Event potion (`Potions/Ambergris.cs`,
/// GH #792), procured by the NeowsSacrifice Neow relic:
///
/// [decompiled C# omitted — see the cited path]
///
/// `HealPercent` = 50; the heal truncates like BloodPotion's. In
/// single-player the only `AnyPlayer` target is the drinker.
///
/// `AmbergrisPower` (GH #794) is the extra-turn power: the owner takes an extra
/// turn while `Amount > 0` (`hooks::ambergris_should_take_extra_turn_v0111`)
/// and each extra turn spends a stack. A `CombatState` drink is always in
/// combat, so `IsInProgress` holds unless the combat already ended.
pub(crate) fn drink_ambergris(state: &mut CombatState, _target: Option<u8>) -> EffectResult {
    let Some(uid) = state.player.creature_uid else {
        return EffectResult::Resolved;
    };
    let max_hp = state
        .creatures
        .iter()
        .find(|c| c.id == uid.0)
        .map_or(0, |c| c.max_hp);
    let amount = max_hp.saturating_mul(AMBERGRIS_HEAL_PERCENT) / 100;
    if amount > 0 {
        state.creature_heal(uid, amount);
    }
    // `PowerCmd.Apply<AmbergrisPower>(choiceContext, target, 1m, base.Owner.Creature, null)`
    // inside `if (CombatManager.Instance.IsInProgress)` (decomp Ambergris.cs).
    if !state.is_over {
        sts2_core::card_effects::apply_power_to_creature(state, uid, PowerKind::Ambergris, 1);
    }
    EffectResult::Resolved
}

/// `Ambergris.CanonicalVars` `HealPercent` (`Ambergris.cs`).
const AMBERGRIS_HEAL_PERCENT: i32 = 50;
