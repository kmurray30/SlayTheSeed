//! Defect Hibernate — v0.111.0-only, MP-only (GH #793).
use super::prelude::*;

/// Hibernate (Uncommon, 2 energy, Skill, Self) — v0.111.0
/// `Cards/Hibernate.cs`, `MultiplayerConstraint => MultiplayerOnly`:
///
/// [decompiled C# omitted — see the cited path]
pub(crate) fn play_hibernate(state: &mut CombatState, _t: Option<CreatureUid>) -> EffectResult {
    // v0.111.0: HibernatePower 1, then channel 2 (upg 3) Frost
    // (decomp Hibernate.cs:27-33). The power's behaviour is S8b's (#794).
    apply_power_to_player(state, PowerKind::Hibernate, 1);
    let n = if current_card_upgraded(state) { 3 } else { 2 };
    for _ in 0..n {
        orbs::channel_orb(state, Orb::frost());
    }
    EffectResult::Resolved
}

#[must_use]
pub(crate) const fn spec() -> CardSpec {
    CardSpec {
        id: CardId::HibernateCard,
        name: "HIBERNATE_CARD",
        cost: 2,
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
    None
}
