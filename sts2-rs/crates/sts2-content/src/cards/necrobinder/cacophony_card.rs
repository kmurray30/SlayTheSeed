//! Necrobinder Cacophony — v0.111.0-only, MP-only (GH #793).
use super::prelude::*;

/// Cacophony (Rare, 2 energy, Power, Self) — v0.111.0 `Cards/Cacophony.cs`,
/// `MultiplayerConstraint => MultiplayerOnly`:
///
/// [decompiled C# omitted — see the cited path]
///
/// The `DamageVar` is the power's amount, not a hit, so the spec carries no
/// `base_damage`.
pub(crate) fn play_cacophony(state: &mut CombatState, _t: Option<CreatureUid>) -> EffectResult {
    // v0.111.0: CacophonyPower 66 (upg 99) (decomp Cacophony.cs:23-26). The
    // power's behaviour is S8b's (#794).
    let amt = if current_card_upgraded(state) { 99 } else { 66 };
    apply_power_to_player(state, PowerKind::Cacophony, amt);
    EffectResult::Resolved
}

#[must_use]
pub(crate) const fn spec() -> CardSpec {
    CardSpec {
        id: CardId::CacophonyCard,
        name: "CACOPHONY_CARD",
        cost: 2,
        card_type: CardType::Power,
        target_type: TargetType::Self_,
        rarity: CardRarity::Rare,
        base_damage: 0,
        base_block: 0,
        keywords: CardKeywords::NONE,
        ..CardSpec::base()
    }
}

#[allow(clippy::unnecessary_wraps, dead_code)]
pub(crate) fn upgrade() -> Option<CardUpgradeDelta> {
    None
}
