//! The Scythe (Necrobinder rare attack). Hand-authored C#-first port.
//! C# source: ../sts2-wiki/decompiled/v0.107.1/MegaCrit.Sts2.Core.Models.Cards/TheScythe.cs
use super::prelude::*;

/// The Scythe (Rare, 2 energy, Attack, AnyEnemy → Enemy, Exhaust).
///
/// C# `TheScythe.cs`: base damage 13. `OnPlay` deals `Damage.BaseValue`
/// (`TheScythe.cs:68`), then `BuffFromPlay(Increase)` runs
/// `IncreasedDamage += Increase; CurrentDamage = 13 + IncreasedDamage`
/// (`TheScythe.cs:86-95`), and the `CurrentDamage` setter writes
/// `Damage.BaseValue = CurrentDamage` (`TheScythe.cs:30-35`) so the next play
/// hits harder. `Increase` is `IntVar("Increase", 4)` (v0.107.1; was 3),
/// (`OnUpgrade` bumps it to 5 via `UpgradeValueBy(1m)`, `TheScythe.cs:76-79`).
///
/// Run-persistence: `CurrentDamage` and `IncreasedDamage` are `[SavedProperty]`
/// (`TheScythe.cs:23,38`), and `OnPlay` also buffs the run-master deck card via
/// `(base.DeckVersion as TheScythe)?.BuffFromPlay(intValue)` (`TheScythe.cs:73`),
/// so the scaling persists across combats for the whole run.
///
/// Rust modeling: `CurrentDamage` is exactly the card POD's run-persistent
/// `base_damage` — `current_damage` reads it, and `enter_combat` clones the
/// master-deck `Card` (`base_damage` included) into the combat draw pile and
/// never resets it at end-of-combat. So `OnPlay` mutates `base_damage` in place
/// (== C#'s `UpdateDamage`). The `DeckVersion.BuffFromPlay` run-master
/// propagation happens at `exit_combat`, which folds each combat Scythe's
/// accumulated `base_damage` back onto its master-deck card
/// (`run_bridge::propagate_scaling_cards_to_master_deck`).
pub(crate) fn play_the_scythe(
    state: &mut CombatState,
    target: Option<CreatureUid>,
) -> EffectResult {
    let t = target.expect("TheScythe: Enemy-targeted");
    // C# `OnPlay`: deal `Damage.BaseValue` (== `CurrentDamage` == our
    // run-persistent `base_damage`).
    let dmg = current_damage(state);
    let _ = deal_damage_player_to_enemy(state, t, dmg);
    // C# `BuffFromPlay`: `IncreasedDamage += Increase; CurrentDamage = 13 +
    // IncreasedDamage`. We hold the running total directly in `base_damage`,
    // so the buff is a straight `base_damage += Increase`. `IntVar("Increase",
    // 4m)` (v0.107.1; was 3m); `OnUpgrade UpgradeValueBy(1m)` → +5 upgraded.
    // v0.111.0: Increase 4 -> 5, upgrade +1 -> +2 (decomp TheScythe.cs:57,78).
    let increase = match (
        crate::cards::v0111::is_v0111(state.game_version),
        current_card_upgraded(state),
    ) {
        (false, false) => 4,
        (false, true) => 5,
        (true, false) => 5,
        (true, true) => 7,
    };
    if let Some(idx) = current_card_idx(state) {
        if let Some(card) = state.cards.get_mut(idx) {
            card.base_damage = card.base_damage.saturating_add(increase);
        }
    }
    EffectResult::Resolved
}

#[must_use]
pub(crate) const fn spec() -> CardSpec {
    CardSpec {
        id: CardId::TheScythe,
        name: "THE_SCYTHE",
        cost: 2,
        card_type: CardType::Attack,
        target_type: TargetType::Enemy,
        rarity: CardRarity::Rare,
        base_damage: 13,
        base_block: 0,
        keywords: CardKeywords::EXHAUST,
        ..CardSpec::base()
    }
}

#[allow(clippy::unnecessary_wraps, dead_code)]
pub(crate) fn upgrade() -> Option<CardUpgradeDelta> {
    // C# `OnUpgrade` bumps only the `Increase` IntVar (4 → 5); it does not
    // touch base damage, cost, block, or keywords. The per-play increment is
    // gated on `current_card_upgraded(state)` in the handler, so there is no
    // stat delta to apply here.
    None
}

#[cfg(test)]
mod tests {
    use super::*;

    fn mint_into_play(state: &mut CombatState, upgraded: bool) -> sts2_core::CardUid {
        let mut card =
            crate::cards::spec_index::card_pod_from_id(CardId::TheScythe, upgraded).expect("spec");
        let raw = u32::try_from(state.cards.len() + 1).expect("fits");
        let uid = sts2_core::CardUid::new(raw).expect("nz");
        card.uid = uid;
        state.cards.push(card);
        state.player.play.push(uid);
        uid
    }

    fn enemy_hp(state: &CombatState, uid: CreatureUid) -> i32 {
        state
            .creatures
            .iter()
            .find(|c| c.id == uid.0)
            .expect("enemy present")
            .current_hp
    }

    /// First play deals base 13 and grows `base_damage` to 17 (`+4`); the
    /// growth lives in the run-persistent POD field, so the same instance's
    /// second play deals 17 and grows to 21. C# `OnPlay` deals
    /// `Damage.BaseValue` then `BuffFromPlay(4)` (v0.107.1 `Increase` 4).
    #[test]
    fn the_scythe_grows_base_damage_per_play() {
        let mut state = CombatState::default();
        let enemy = state.spawn_enemy(sts2_core::MonsterKind::Nibbit);
        let uid = mint_into_play(&mut state, false);
        let idx = uid.0.get() as usize - 1;
        assert_eq!(state.cards[idx].base_damage, 13);

        let hp0 = enemy_hp(&state, enemy);
        play_the_scythe(&mut state, Some(enemy));
        assert_eq!(
            enemy_hp(&state, enemy),
            hp0 - 13,
            "first play deals base 13"
        );
        assert_eq!(state.cards[idx].base_damage, 17, "base grows +4 to 17");

        let hp1 = enemy_hp(&state, enemy);
        play_the_scythe(&mut state, Some(enemy));
        assert_eq!(enemy_hp(&state, enemy), hp1 - 17, "second play deals 17");
        assert_eq!(state.cards[idx].base_damage, 21, "base grows +4 to 21");
    }

    /// Upgraded Scythe grows by +5 per play (`Increase` IntVar 4 → 5).
    #[test]
    fn the_scythe_upgraded_grows_by_five() {
        let mut state = CombatState::default();
        let enemy = state.spawn_enemy(sts2_core::MonsterKind::Nibbit);
        let uid = mint_into_play(&mut state, true);
        let idx = uid.0.get() as usize - 1;
        assert_eq!(state.cards[idx].base_damage, 13);
        play_the_scythe(&mut state, Some(enemy));
        assert_eq!(state.cards[idx].base_damage, 18, "upgraded base grows +5");
    }
}
