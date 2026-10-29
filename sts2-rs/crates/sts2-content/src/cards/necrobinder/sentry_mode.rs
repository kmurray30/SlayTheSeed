//! Hand-authored (C#-first port). Keep the prelude import.
use super::prelude::*;

/// SentryMode (Rare, 2 energy, Power, Self). Apply 1 Sentry Mode.
// C# source: ../sts2-wiki/decompiled/v0.103.2/MegaCrit.Sts2.Core.Models.Cards/SentryMode.cs
//
// C# `OnPlay`: `PowerCmd.Apply<SentryModePower>(Owner.Creature,
// DynamicVars["SentryModePower"].BaseValue, Owner.Creature, this)` — apply
// `PowerVar<SentryModePower>(1)` stacks of `SentryModePower` to the player.
// The "Cast" anim (`CreatureCmd.TriggerAnim`) is cosmetic (ignored).
// `ExtraHoverTips` (SweepingGaze) is cosmetic.
//
// `SentryModePower` (SentryModePower.cs) is a Buff/Counter whose only
// behavioral hook is `BeforeHandDraw`: for the owning player it generates
// `Amount` `SweepingGaze` cards into the hand at the start of each turn's
// draw (`combatState.CreateCard<SweepingGaze>` +
// `CardPileCmd.AddGeneratedCardToCombat(..., Hand, addedByPlayer: true)`).
// That listener is NOT yet implemented (Necrobinder powers bead
// `sts2-rs-bt9`), and `SweepingGaze` is a generated card not yet ported —
// see the structured result's powers_required / generated_cards_required.
// This handler faithfully applies the power stack; the per-turn draw rider
// fires once `SentryModePower` + `SweepingGaze` land.
//
// `OnUpgrade` does `EnergyCost.UpgradeBy(-1)` (cost 2 → 1); that delta
// already lives in the central `card_upgrades_generated.rs` table
// (CardId::SentryMode, bead `sts2-rs-eiznx`), so `upgrade()` returns `None`.
// The power amount is not upgraded, so no `current_card_upgraded` branch.
pub(crate) fn play_sentry_mode(
    state: &mut CombatState,
    _target: Option<CreatureUid>,
) -> EffectResult {
    apply_power_to_player(state, PowerKind::SentryMode, 1);
    EffectResult::Resolved
}

#[must_use]
pub(crate) const fn spec() -> CardSpec {
    CardSpec {
        id: CardId::SentryMode,
        name: "SENTRY_MODE",
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
    // OnUpgrade `EnergyCost.UpgradeBy(-1)` (2 → 1) lives in the central
    // `card_upgrades_generated.rs` table (CardId::SentryMode), so no
    // per-card delta here.
    None
}
