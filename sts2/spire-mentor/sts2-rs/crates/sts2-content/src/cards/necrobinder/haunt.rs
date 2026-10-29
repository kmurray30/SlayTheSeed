//! Necrobinder — Haunt.
use super::prelude::*;

/// Haunt (Uncommon, 1 energy, Power). Applies HauntPower (Counter) to self
/// with amount = HpLoss base value (6, upgraded 8). HauntPower then deals that
/// much unblockable+unpowered damage to a random hittable enemy each time the
/// owner plays a Soul.
// C# source: ../sts2-wiki/decompiled/v0.103.2/MegaCrit.Sts2.Core.Models.Cards/Haunt.cs
pub(crate) fn play_haunt(state: &mut CombatState, _target: Option<CreatureUid>) -> EffectResult {
    // C# Haunt.OnPlay: PowerCmd.Apply<HauntPower>(Owner, DynamicVars.HpLoss.BaseValue).
    // HpLossVar(6m); OnUpgrade → UpgradeValueBy(2m) ⇒ 8 (gated, not a stat field).
    // TargetType::Self_ — no enemy target.
    // v0.111.0: HpLoss 6 -> 7, upgrade still +2 (decomp Haunt.cs:14,31).
    let base = if crate::cards::v0111::is_v0111(state.game_version) {
        7
    } else {
        6
    };
    let amount = if current_card_upgraded(state) {
        base + 2
    } else {
        base
    };
    apply_power_to_player(state, PowerKind::Haunt, amount);
    EffectResult::Resolved
}

#[must_use]
pub(crate) const fn spec() -> CardSpec {
    CardSpec {
        id: CardId::Haunt,
        name: "HAUNT",
        cost: 1,
        card_type: CardType::Power,
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
    // C# OnUpgrade only bumps the HpLoss PowerVar amount (6→8); that is applied
    // in the handler via current_card_upgraded, not as a damage/block/cost stat.
    None
}
