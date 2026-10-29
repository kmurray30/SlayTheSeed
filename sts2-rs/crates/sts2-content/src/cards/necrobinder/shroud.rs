//! Shroud (Necrobinder). Hand-authored C#-first port via character-port skill.
//! C# source: ../sts2-wiki/decompiled/v0.103.2/MegaCrit.Sts2.Core.Models.Cards/Shroud.cs
use super::prelude::*;

/// Shroud (Uncommon, 1 energy, Power, Self). Apply 2 `Shroud` to self
/// (3 upgraded).
///
/// C# `Shroud.OnPlay`: `PowerCmd.Apply<ShroudPower>(Owner.Creature,
/// DynamicVars.Block.BaseValue, ..)` — the `BlockVar(2, ValueProp.Unpowered)`
/// is the ShroudPower amount, NOT card block. `OnUpgrade` bumps the block var
/// 2 → 3, which the central upgrade table (`card_upgrades_generated.rs`,
/// `CardId::Shroud`) applies to `base_block`; the handler reads it via
/// `current_block`.
///
/// NOTE: `ShroudPower` (C# `ShroudPower.AfterPowerAmountChanged`: when the owner
/// applies Doom to a creature, gain `Amount` block, Unpowered) is NOT yet
/// implemented as a Rust power listener — see `powers_required`. The card
/// correctly applies the `Shroud` power stacks, but the gain-block-on-Doom
/// trigger will not fire until that listener lands.
pub(crate) fn play_shroud(state: &mut CombatState, _target: Option<CreatureUid>) -> EffectResult {
    let amount = current_block(state);
    apply_power_to_player(state, PowerKind::Shroud, amount);
    EffectResult::Resolved
}

#[must_use]
pub(crate) const fn spec() -> CardSpec {
    CardSpec {
        id: CardId::Shroud,
        name: "SHROUD",
        cost: 1,
        card_type: CardType::Power,
        target_type: TargetType::Self_,
        rarity: CardRarity::Uncommon,
        base_damage: 0,
        base_block: 2,
        keywords: CardKeywords::NONE,
        ..CardSpec::base()
    }
}

#[allow(clippy::unnecessary_wraps, dead_code)]
pub(crate) fn upgrade() -> Option<CardUpgradeDelta> {
    // C# OnUpgrade: DynamicVars.Block.UpgradeValueBy(1m) → 2 → 3. The delta lives
    // in the central table (`card_upgrades_generated.rs`, `CardId::Shroud` =>
    // base_block: Some(3)), per the Necrobinder/newer-pool convention.
    None
}
