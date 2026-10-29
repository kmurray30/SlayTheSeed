//! Abundance — v0.111.0-only Ancient card (GH #793), the Dowsing quest reward.
use super::prelude::*;

/// Abundance (Ancient, 1 energy, Skill, Exhaust, Self) — v0.111.0
/// `Cards/Abundance.cs` (in `EventCardPool`):
///
/// [decompiled C# omitted — see the cited path]
///
/// The same shape as PowerPotion, except the three options are upgraded and
/// the screen cannot be skipped (`canSkip` defaults to `false`).
pub(crate) fn play_abundance(state: &mut CombatState, _t: Option<CreatureUid>) -> EffectResult {
    let Some(character) = state.player.character else {
        return EffectResult::Resolved;
    };
    // v0.111.0: 3 distinct character-pool Powers off CombatCardGeneration,
    // upgraded, choose one, free this turn (decomp Abundance.cs:26-38).
    let ids = distinct_character_card_ids(state, character, Some(CardType::Power), 3);
    if ids.is_empty() {
        return EffectResult::Resolved;
    }
    let options: Vec<sts2_core::ChoiceOption> = ids
        .iter()
        .map(|&card_id| sts2_core::ChoiceOption {
            card_id,
            upgraded: true,
            card_uid: None,
            source_pile: None,
        })
        .collect();
    state.request_card_choice(
        sts2_core::ChoiceKind::AddGeneratedFreeToHand,
        options,
        1,
        1,
        false,
    );
    EffectResult::Resolved
}

#[must_use]
pub(crate) const fn spec() -> CardSpec {
    CardSpec {
        id: CardId::Abundance,
        name: "ABUNDANCE",
        cost: 1,
        card_type: CardType::Skill,
        target_type: TargetType::Self_,
        rarity: CardRarity::Ancient,
        base_damage: 0,
        base_block: 0,
        keywords: CardKeywords::EXHAUST,
        ..CardSpec::base()
    }
}

#[allow(clippy::unnecessary_wraps, dead_code)]
pub(crate) fn upgrade() -> Option<CardUpgradeDelta> {
    // `EnergyCost.UpgradeBy(-1)` — the row lives in `card_upgrades_generated`.
    None
}
