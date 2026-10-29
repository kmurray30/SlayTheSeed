//! Ironclad Outrage — v0.111.0-only, MP-only (GH #793).
use super::prelude::*;

/// Outrage (Uncommon, 0 energy, Attack, AnyEnemy) — v0.111.0
/// `Cards/Outrage.cs`, `MultiplayerConstraint => MultiplayerOnly`:
///
/// [decompiled C# omitted — see the cited path]
///
/// `GetTeammatesOf` includes the owner (`CombatState.cs:656-659`), so in
/// single-player one clone goes into the owner's discard pile — the Anger
/// shape.
pub(crate) fn play_outrage(state: &mut CombatState, target: Option<CreatureUid>) -> EffectResult {
    let t = target.expect("Outrage: Enemy-targeted");
    let _ = deal_damage_player_to_enemy(state, t, current_damage(state));
    // v0.111.0: a clone into the discard of every living player on the side —
    // the owner in SP (decomp Outrage.cs:30-38).
    if let Some(template) = sts2_core::card_effects::resolving_card_inst(state).copied() {
        let _ = spawn_card_into_pile(state, template, PileKind::Discard);
    }
    EffectResult::Resolved
}

#[must_use]
pub(crate) const fn spec() -> CardSpec {
    CardSpec {
        id: CardId::Outrage,
        name: "OUTRAGE",
        cost: 0,
        card_type: CardType::Attack,
        target_type: TargetType::Enemy,
        rarity: CardRarity::Uncommon,
        base_damage: 9,
        base_block: 0,
        keywords: CardKeywords::NONE,
        ..CardSpec::base()
    }
}

#[allow(clippy::unnecessary_wraps, dead_code)]
pub(crate) fn upgrade() -> Option<CardUpgradeDelta> {
    // Damage 9 -> 13 — the row lives in `card_upgrades_generated`.
    None
}
