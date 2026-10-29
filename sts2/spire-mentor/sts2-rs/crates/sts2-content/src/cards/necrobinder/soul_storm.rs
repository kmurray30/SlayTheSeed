//! Soul Storm (Necrobinder). Hand-authored C#-first port via the character-port skill.
//! C# source: ../sts2-wiki/decompiled/v0.103.2/MegaCrit.Sts2.Core.Models.Cards/SoulStorm.cs
use super::prelude::*;

/// Soul Storm (Rare, 1 energy, Attack, AnyEnemy → Enemy). Deal damage equal to
/// `9 + Extra × (number of Soul cards in your exhaust pile)`, where `Extra` is 2
/// (3 upgraded).
//
// C# OnPlay:
//   DamageCmd.Attack(DynamicVars.CalculatedDamage).FromCard(this).Targeting(target)...
//
// CanonicalVars:
//   CalculationBaseVar(9m)                                   → base 9
//   ExtraDamageVar(2m)                                       → extra 2
//   CalculatedDamageVar(Move).WithMultiplier(card,_ =>
//       card.Owner.PlayerCombatState.ExhaustPile.Cards
//           .Count(c => c is Soul))                          → multiplier = #Souls in exhaust
//   CalculatedVar.Calculate = Base + Extra × multiplier      (CalculatedVar.cs)
//
// OnUpgrade: ExtraDamage.UpgradeValueBy(1m)                  → extra 2 → 3.
// The calculated damage is not a plain DamageVar spec field, so it is computed
// in the handler; the OnUpgrade bump is gated on current_card_upgraded.
pub(crate) fn play_soul_storm(
    state: &mut CombatState,
    target: Option<CreatureUid>,
) -> EffectResult {
    let t = target.expect("Soul Storm: Enemy-targeted");

    let damage = resolving_preview(state, preview_soul_storm, target);

    let _ = deal_damage_player_to_enemy(state, t, damage);
    EffectResult::Resolved
}

/// `CalculatedDamage`: `CalculationBase(9) + ExtraDamage x <Souls in your
/// Exhaust Pile>`. Shared with the card face — see `crate::cards::preview`.
pub(crate) fn preview_soul_storm(
    state: &CombatState,
    card: &Card,
    _target: Option<CreatureUid>,
) -> i32 {
    let soul_count: i32 = state
        .player
        .exhaust
        .iter()
        .filter(|uid| {
            let idx = uid.0.get() as usize - 1;
            state.cards[idx].card_id == CardId::Soul
        })
        .count()
        .try_into()
        .unwrap_or(i32::MAX);
    // v0.111.0: ExtraDamage per Soul 2 -> 4, upgrade +1 -> +2 (decomp SoulStorm.cs:22,41).
    let extra = match (
        crate::cards::v0111::is_v0111(state.game_version),
        card.upgraded,
    ) {
        (false, false) => 2,
        (false, true) => 3,
        (true, false) => 4,
        (true, true) => 6,
    };
    9 + extra * soul_count
}

#[must_use]
pub(crate) const fn spec() -> CardSpec {
    CardSpec {
        id: CardId::SoulStorm,
        name: "SOUL_STORM",
        cost: 1,
        card_type: CardType::Attack,
        target_type: TargetType::Enemy,
        rarity: CardRarity::Rare,
        base_damage: 9,
        base_block: 0,
        keywords: CardKeywords::NONE,
        ..CardSpec::base()
    }
}

#[allow(clippy::unnecessary_wraps, dead_code)]
pub(crate) fn upgrade() -> Option<CardUpgradeDelta> {
    // C# OnUpgrade: ExtraDamage.UpgradeValueBy(1) (2 → 3). The Extra multiplier
    // feeds the calculated damage computed in the handler (gated on
    // current_card_upgraded), not a plain spec field, so this returns None to
    // keep one source of truth.
    None
}
