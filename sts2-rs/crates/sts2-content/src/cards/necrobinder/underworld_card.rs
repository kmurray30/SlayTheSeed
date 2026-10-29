//! Necrobinder Underworld — v0.111.0-only, MP-only (GH #793).
use super::prelude::*;

/// Underworld (Uncommon, 2 energy, Skill, Exhaust, Self) — v0.111.0
/// `Cards/Underworld.cs`, `MultiplayerConstraint => MultiplayerOnly`:
///
/// [decompiled C# omitted — see the cited path]
pub(crate) fn play_underworld(state: &mut CombatState, _t: Option<CreatureUid>) -> EffectResult {
    // v0.111.0: UnderworldPower 1 (decomp Underworld.cs:22-25). The power's
    // behaviour is S8b's (#794).
    apply_power_to_player(state, PowerKind::Underworld, 1);
    EffectResult::Resolved
}

#[must_use]
pub(crate) const fn spec() -> CardSpec {
    CardSpec {
        id: CardId::UnderworldCard,
        name: "UNDERWORLD_CARD",
        cost: 2,
        card_type: CardType::Skill,
        target_type: TargetType::Self_,
        rarity: CardRarity::Uncommon,
        base_damage: 0,
        base_block: 0,
        keywords: CardKeywords::EXHAUST,
        ..CardSpec::base()
    }
}

#[allow(clippy::unnecessary_wraps, dead_code)]
pub(crate) fn upgrade() -> Option<CardUpgradeDelta> {
    // `RemoveKeyword(Exhaust)` — the row lives in `card_upgrades_generated`.
    None
}
