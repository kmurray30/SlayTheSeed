//! Silent Sidestep — v0.111.0-only (GH #793). Hand-authored C#-first port.
use super::prelude::*;

/// Sidestep (Uncommon, 0 energy, Skill, Self) — v0.111.0 `Cards/Sidestep.cs`:
///
/// [decompiled C# omitted — see the cited path]
///
/// Takes Scare's slot in the v0.111.0 `SilentCardPool` (see
/// `crate::cards::v0111::pool_substitute`).
pub(crate) fn play_sidestep(state: &mut CombatState, _t: Option<CreatureUid>) -> EffectResult {
    // v0.111.0: EnergyNextTurn 1 (upg 2) (decomp Sidestep.cs:21-24).
    let amt = if current_card_upgraded(state) { 2 } else { 1 };
    apply_power_to_player(state, PowerKind::EnergyNextTurn, amt);
    EffectResult::Resolved
}

#[must_use]
pub(crate) const fn spec() -> CardSpec {
    CardSpec {
        id: CardId::Sidestep,
        name: "SIDESTEP",
        cost: 0,
        card_type: CardType::Skill,
        target_type: TargetType::Self_,
        rarity: CardRarity::Uncommon,
        base_damage: 0,
        base_block: 0,
        keywords: CardKeywords::NONE,
        ..CardSpec::base()
    }
}

#[allow(clippy::unnecessary_wraps, dead_code)]
pub(crate) fn upgrade() -> Option<CardUpgradeDelta> {
    // Energy var only — read in the handler.
    None
}
