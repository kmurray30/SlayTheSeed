//! GH #793 (S8a, part 2): v0.111.0 card behaviour reworks and new cards.
//!
//! Each `*_v0111` test drives the real play path (`combat_loop::play_card`)
//! on a `CombatState` whose `game_version` is v0.111.0, with the cards built
//! through the build-aware factory (`v0111::card_pod_for`).
//! Where the v0.107.1 behaviour is the contrast, a `*_v0107` twin pins it.
//! Source of truth: `sts2-wiki/decompiled/v0.111.0/MegaCrit.Sts2.Core.Models.Cards/`.

use smallvec::SmallVec;
use std::num::NonZeroU32;

use sts2_content::cards::dispatch as content_dispatch;
use sts2_content::cards::v0111::card_pod_for as card_pod_from_id_for;
use sts2_core::cards::{CardId, KeywordOverride};
use sts2_core::potions::CharacterId;
use sts2_core::{
    CombatPhase, CombatState, Creature, CreatureUid, GameVersion, IntentHistory, MonsterId,
    MonsterKind, Power, PowerKind, PowerUid,
};

const V107: GameVersion = GameVersion::V0_107_1;
const V111: GameVersion = GameVersion::V0_111_0;

fn add_enemy(state: &mut CombatState, raw: u32, hp: i32) -> CreatureUid {
    let id = NonZeroU32::new(raw).expect("nz");
    state.creatures.push(Creature {
        id,
        current_hp: hp,
        max_hp: hp,
        block: 0,
        powers: SmallVec::new(),
        species: Some(MonsterKind::Nibbit),
        intent: None,
        intent_history: IntentHistory::default(),
        monster_id: MonsterId("Nibbit"),
        ai: None,
    });
    state.enemy_uids.push(CreatureUid(id));
    CreatureUid(id)
}

/// One 100-HP Nibbit; player with 5 energy in the play phase.
fn fresh(version: GameVersion, character: CharacterId) -> CombatState {
    content_dispatch::ensure_installed();
    sts2_content::relics::dispatch::ensure_installed();
    let mut state = CombatState {
        game_version: version,
        ..CombatState::default()
    };
    let _ = add_enemy(&mut state, 1, 100);
    state.player.current_hp = 80;
    state.player.max_hp = 80;
    state.player.energy = 5;
    state.player.max_energy = 3;
    state.player.character = Some(character);
    let _ = state.init_player_creature();
    state.phase = CombatPhase::PlayerPlayPhase;
    state
}

fn new_card(state: &mut CombatState, id: CardId, upgraded: bool) -> sts2_core::CardUid {
    let raw = u32::try_from(state.cards.len() + 1).expect("u32");
    let uid = sts2_core::CardUid::new(raw).expect("nz");
    let mut card = card_pod_from_id_for(id, upgraded, state.game_version).expect("spec");
    card.uid = uid;
    state.cards.push(card);
    uid
}

fn push_hand(state: &mut CombatState, id: CardId, upgraded: bool) -> u8 {
    let uid = new_card(state, id, upgraded);
    state.player.hand.push(uid);
    u8::try_from(state.player.hand.len() - 1).expect("u8")
}

fn push_draw(state: &mut CombatState, id: CardId) {
    let uid = new_card(state, id, false);
    state.player.draw.push(uid);
}

fn enemy_hp(state: &CombatState) -> i32 {
    state.creatures[0].current_hp
}

fn add_player_power(state: &mut CombatState, raw: u32, kind: PowerKind, amount: i32) {
    let pid = NonZeroU32::new(raw).expect("nz");
    state.powers.push(Power::new(pid, kind, amount));
    state.player.powers.push(PowerUid(pid));
}

fn enemy_power(state: &CombatState, uid: CreatureUid, kind: PowerKind) -> i32 {
    let c = state
        .creatures
        .iter()
        .find(|c| c.id == uid.0)
        .expect("creature");
    c.powers
        .iter()
        .filter_map(|p| state.powers.get_by_id(p.0))
        .find(|p| p.kind == kind)
        .map_or(0, |p| p.amount)
}

fn add_enemy_power(
    state: &mut CombatState,
    uid: CreatureUid,
    raw: u32,
    kind: PowerKind,
    amount: i32,
) {
    let pid = NonZeroU32::new(raw).expect("nz");
    state.powers.push(Power::new(pid, kind, amount));
    state
        .creatures
        .iter_mut()
        .find(|c| c.id == uid.0)
        .expect("creature")
        .powers
        .push(PowerUid(pid));
}

fn first_enemy(state: &CombatState) -> CreatureUid {
    state.enemy_uids[0]
}

fn hp_of(state: &CombatState, uid: CreatureUid) -> i32 {
    state
        .creatures
        .iter()
        .find(|c| c.id == uid.0)
        .map_or(0, |c| c.current_hp)
}

// ── Hyperbeam ──────────────────────────────────────────────────────────────

#[test]
fn hyperbeam_v0107() {
    let mut state = fresh(V107, CharacterId::Defect);
    let i = push_hand(&mut state, CardId::Hyperbeam, false);
    let _ = sts2_core::combat_loop::play_card(&mut state, i, None);
    assert_eq!(enemy_hp(&state), 72, "28 damage");
    assert_eq!(state.player_power_amount(PowerKind::Focus), -3);
    assert_eq!(state.player_power_amount(PowerKind::HyperbeamFocusDown), 0);
}

#[test]
fn hyperbeam_v0111() {
    let mut state = fresh(V111, CharacterId::Defect);
    let i = push_hand(&mut state, CardId::Hyperbeam, false);
    let _ = sts2_core::combat_loop::play_card(&mut state, i, None);
    assert_eq!(enemy_hp(&state), 76, "24 damage (Hyperbeam.cs:22)");
    assert_eq!(state.player_power_amount(PowerKind::Focus), -3);
    assert_eq!(
        state.player_power_amount(PowerKind::HyperbeamFocusDown),
        3,
        "the Focus loss is a temporary HyperbeamFocusDownPower (Hyperbeam.cs:56)"
    );
}

#[test]
fn hyperbeam_upgrade_v0111() {
    let mut state = fresh(V111, CharacterId::Defect);
    let i = push_hand(&mut state, CardId::Hyperbeam, true);
    let _ = sts2_core::combat_loop::play_card(&mut state, i, None);
    assert_eq!(enemy_hp(&state), 70, "24 + 6 (Hyperbeam.cs:61)");
}

/// An in-run / in-combat upgrade of a v0.111.0 card applies that build's
/// delta (+6), not v0.107.1's (+8).
#[test]
fn hyperbeam_upgrade_for_v0111() {
    content_dispatch::ensure_installed();
    sts2_content::relics::dispatch::ensure_installed();
    let mut card = card_pod_from_id_for(CardId::Hyperbeam, false, V111).expect("pod");
    assert!(card.upgrade_for(V111));
    assert_eq!(card.base_damage, 30);
    let mut old = card_pod_from_id_for(CardId::Hyperbeam, false, V107).expect("pod");
    assert!(old.upgrade_for(V107));
    assert_eq!(old.base_damage, 36);
}

/// A Confused roll held in `cost` (`CostAbsolutelySet`) that lands on Rend's
/// v0.107.1 base (2) must survive an in-combat upgrade (Bone Tea); the
/// upgrade's build normalization must not read it as an unmoved base.
fn confused_rend_upgraded_cost(version: GameVersion) -> i8 {
    content_dispatch::ensure_installed();
    sts2_content::relics::dispatch::ensure_installed();
    let mut card = card_pod_from_id_for(CardId::Rend, false, version).expect("pod");
    card.cost = 2;
    card.set_combat_var(sts2_core::cards::VarKey::CostAbsolutelySet, 1);
    assert!(card.upgrade_for(version));
    card.cost
}

#[test]
fn confused_rend_keeps_rolled_cost_on_upgrade_v0107() {
    assert_eq!(confused_rend_upgraded_cost(V107), 2);
}

/// Necrobinder v2 fuzz (full, seed 2823): Fake Snecko Eye + Bone Tea, Rend
/// rolled 2; C# keeps 2 (Rend.cs:25 base 1, ConfusedPower `SetThisCombat`).
#[test]
fn confused_rend_keeps_rolled_cost_on_upgrade_v0111() {
    assert_eq!(confused_rend_upgraded_cost(V111), 2);
}

// ── Expect a Fight ─────────────────────────────────────────────────────────

#[test]
fn expect_a_fight_v0111() {
    let mut state = fresh(V111, CharacterId::Ironclad);
    add_player_power(&mut state, 900, PowerKind::Strength, 2);
    let i = push_hand(&mut state, CardId::ExpectAFight, false);
    let _ = sts2_core::combat_loop::play_card(&mut state, i, None);
    assert_eq!(state.player.energy, 2, "costs 3 (ExpectAFight.cs:29)");
    assert_eq!(state.player.block, 25, "15 + 5 x 2 Strength");
    assert_eq!(state.player_power_amount(PowerKind::NoEnergyGain), 0);
}

#[test]
fn expect_a_fight_upgrade_v0111() {
    let mut state = fresh(V111, CharacterId::Ironclad);
    add_player_power(&mut state, 900, PowerKind::Strength, 2);
    let i = push_hand(&mut state, CardId::ExpectAFight, true);
    let _ = sts2_core::combat_loop::play_card(&mut state, i, None);
    assert_eq!(
        state.player.energy, 2,
        "the upgrade no longer lowers the cost"
    );
    assert_eq!(state.player.block, 32, "16 + 8 x 2 Strength");
}

#[test]
fn expect_a_fight_v0107() {
    let mut state = fresh(V107, CharacterId::Ironclad);
    let i = push_hand(&mut state, CardId::ExpectAFight, false);
    let _ = push_hand(&mut state, CardId::StrikeIronclad, false);
    let _ = sts2_core::combat_loop::play_card(&mut state, i, None);
    assert_eq!(state.player.energy, 4, "cost 2, +1 per Attack in hand");
    assert_eq!(state.player.block, 0);
}

// ── Outbreak ───────────────────────────────────────────────────────────────

#[test]
fn outbreak_v0111() {
    let mut state = fresh(V111, CharacterId::Silent);
    let second = add_enemy(&mut state, 50, 50);
    let first = first_enemy(&state);
    let i = push_hand(&mut state, CardId::OutbreakCard, false);
    let _ = sts2_core::combat_loop::play_card(&mut state, i, None);
    assert_eq!(state.player.energy, 2, "cost 3 (Outbreak.cs:23)");
    for uid in [first, second] {
        assert_eq!(
            enemy_power(&state, uid, PowerKind::Poison),
            8,
            "9 Poison, then one tick"
        );
    }
    assert_eq!(hp_of(&state, first), 91);
    assert_eq!(hp_of(&state, second), 41);
    assert_eq!(state.player_power_amount(PowerKind::Outbreak), 0);
    assert!(
        state.player.discard.len() == 1,
        "a Skill now, discarded after play"
    );
}

#[test]
fn outbreak_v0107() {
    let mut state = fresh(V107, CharacterId::Silent);
    let i = push_hand(&mut state, CardId::OutbreakCard, false);
    let _ = sts2_core::combat_loop::play_card(&mut state, i, None);
    assert_eq!(state.player_power_amount(PowerKind::Outbreak), 11);
    assert_eq!(
        enemy_power(&state, first_enemy(&state), PowerKind::Poison),
        0
    );
}

// ── Tracking ───────────────────────────────────────────────────────────────

#[test]
fn tracking_v0111() {
    let mut state = fresh(V111, CharacterId::Silent);
    let i = push_hand(&mut state, CardId::Tracking, false);
    let _ = sts2_core::combat_loop::play_card(&mut state, i, None);
    let j = push_hand(&mut state, CardId::Tracking, false);
    let _ = sts2_core::combat_loop::play_card(&mut state, j, None);
    assert_eq!(
        state.player_power_amount(PowerKind::Tracking),
        100,
        "50 per play"
    );
}

#[test]
fn tracking_v0107() {
    let mut state = fresh(V107, CharacterId::Silent);
    let i = push_hand(&mut state, CardId::Tracking, false);
    let _ = sts2_core::combat_loop::play_card(&mut state, i, None);
    let j = push_hand(&mut state, CardId::Tracking, false);
    let _ = sts2_core::combat_loop::play_card(&mut state, j, None);
    assert_eq!(
        state.player_power_amount(PowerKind::Tracking),
        3,
        "2 then 1"
    );
}

// ── Forgotten Ritual ───────────────────────────────────────────────────────

#[test]
fn forgotten_ritual_v0111() {
    let mut state = fresh(V111, CharacterId::Ironclad);
    let i = push_hand(&mut state, CardId::ForgottenRitual, false);
    let _ = sts2_core::combat_loop::play_card(&mut state, i, None);
    assert_eq!(state.player.energy, 7, "cost 1, +3 with nothing exhausted");
}

#[test]
fn forgotten_ritual_v0107() {
    let mut state = fresh(V107, CharacterId::Ironclad);
    let i = push_hand(&mut state, CardId::ForgottenRitual, false);
    let _ = sts2_core::combat_loop::play_card(&mut state, i, None);
    assert_eq!(state.player.energy, 4, "no exhaust this turn → no energy");
}

// ── Guiding Star ───────────────────────────────────────────────────────────

#[test]
fn guiding_star_v0111() {
    let mut state = fresh(V111, CharacterId::Regent);
    state.player.stars = 1;
    push_draw(&mut state, CardId::StrikeRegent);
    push_draw(&mut state, CardId::StrikeRegent);
    let i = push_hand(&mut state, CardId::GuidingStar, false);
    let hand_before = state.player.hand.len();
    let _ = sts2_core::combat_loop::play_card(&mut state, i, Some(0));
    assert_eq!(state.player.stars, 0, "star cost 1 (GuidingStar.cs:22)");
    assert_eq!(state.player.hand.len(), hand_before - 1, "no draw now");
    assert_eq!(
        state.player_power_amount(PowerKind::DrawCardsNextTurn),
        2,
        "the draw moved to next turn (GuidingStar.cs:50)"
    );
}

#[test]
fn guiding_star_star_cost_v0107() {
    let mut state = fresh(V107, CharacterId::Regent);
    let i = push_hand(&mut state, CardId::GuidingStar, false);
    let uid = state.player.hand[usize::from(i)];
    let card = state.cards[uid.0.get() as usize - 1];
    assert_eq!(sts2_core::effective_star_cost_in_combat(&state, &card), 2);
}

// ── Expertise ──────────────────────────────────────────────────────────────

#[test]
fn expertise_v0111() {
    let mut state = fresh(V111, CharacterId::Silent);
    for _ in 0..4 {
        push_draw(&mut state, CardId::StrikeSilent);
    }
    let i = push_hand(&mut state, CardId::Expertise, false);
    let _ = sts2_core::combat_loop::play_card(&mut state, i, None);
    assert_eq!(
        state.player.hand.len(),
        2,
        "draws exactly 2 (Expertise.cs:12)"
    );
    for uid in state.player.hand.clone() {
        assert!(
            state.player.retain_this_turn.contains(&uid),
            "drawn cards Retain this turn"
        );
    }
}

#[test]
fn expertise_v0107() {
    let mut state = fresh(V107, CharacterId::Silent);
    for _ in 0..8 {
        push_draw(&mut state, CardId::StrikeSilent);
    }
    let i = push_hand(&mut state, CardId::Expertise, false);
    let _ = sts2_core::combat_loop::play_card(&mut state, i, None);
    assert_eq!(state.player.hand.len(), 6, "draws up to 6 in hand");
}

// ── Eidolon ────────────────────────────────────────────────────────────────

#[test]
fn eidolon_v0111() {
    let mut state = fresh(V111, CharacterId::Necrobinder);
    // An Ethereal attack in the exhaust pile, and a plain one beside it.
    let ethereal = new_card(&mut state, CardId::StrikeNecrobinder, false);
    state.cards[ethereal.0.get() as usize - 1].keyword_override =
        Some(KeywordOverride::NONE.with(KeywordOverride::ETHEREAL));
    state.player.exhaust.push(ethereal);
    let plain = new_card(&mut state, CardId::StrikeNecrobinder, false);
    state.player.exhaust.push(plain);
    let i = push_hand(&mut state, CardId::Eidolon, false);
    let eidolon = state.player.hand[usize::from(i)];
    let _ = sts2_core::combat_loop::play_card(&mut state, i, None);
    assert_eq!(enemy_hp(&state), 94, "the Ethereal Strike was auto-played");
    assert!(state.player.exhaust.contains(&plain), "non-Ethereal stays");
    assert!(
        state.player.exhaust.contains(&eidolon),
        "Eidolon is Exhaust now (Eidolon.cs:15)"
    );
}

// ── Sidestep ───────────────────────────────────────────────────────────────

#[test]
fn sidestep_v0111() {
    let mut state = fresh(V111, CharacterId::Silent);
    let i = push_hand(&mut state, CardId::Sidestep, false);
    let _ = sts2_core::combat_loop::play_card(&mut state, i, None);
    assert_eq!(state.player.energy, 5, "cost 0");
    assert_eq!(state.player_power_amount(PowerKind::EnergyNextTurn), 1);
    let j = push_hand(&mut state, CardId::Sidestep, true);
    let _ = sts2_core::combat_loop::play_card(&mut state, j, None);
    assert_eq!(
        state.player_power_amount(PowerKind::EnergyNextTurn),
        3,
        "upgraded +2"
    );
}

// ── Rocket Punch ───────────────────────────────────────────────────────────

#[test]
fn rocket_punch_cost_reduction_stacks_v0111() {
    let mut state = fresh(V111, CharacterId::Defect);
    let i = push_hand(&mut state, CardId::RocketPunch, false);
    let uid = state.player.hand[usize::from(i)];
    let dazed = card_pod_from_id_for(CardId::Dazed, false, V111).expect("dazed");
    let _ = sts2_core::card_effects::spawn_card_into_hand_with_origin(&mut state, dazed, true);
    assert_eq!(
        sts2_core::hooks::effective_card_cost(&state, uid),
        1,
        "2 - 1 until played (RocketPunch.cs:49 AddUntilPlayed(-1))"
    );
    let _ = sts2_core::card_effects::spawn_card_into_hand_with_origin(&mut state, dazed, true);
    assert_eq!(
        sts2_core::hooks::effective_card_cost(&state, uid),
        0,
        "stacks"
    );
    let _ = sts2_core::card_effects::spawn_card_into_hand_with_origin(&mut state, dazed, true);
    assert_eq!(
        sts2_core::hooks::effective_card_cost(&state, uid),
        0,
        "floored at 0"
    );
}

#[test]
fn rocket_punch_free_until_played_v0107() {
    let mut state = fresh(V107, CharacterId::Defect);
    let i = push_hand(&mut state, CardId::RocketPunch, false);
    let uid = state.player.hand[usize::from(i)];
    let dazed = card_pod_from_id_for(CardId::Dazed, false, V107).expect("dazed");
    let _ = sts2_core::card_effects::spawn_card_into_hand_with_origin(&mut state, dazed, true);
    assert_eq!(
        sts2_core::hooks::effective_card_cost(&state, uid),
        0,
        "SetUntilPlayed(0)"
    );
    let card = state.cards[uid.0.get() as usize - 1];
    assert_eq!(
        card.combat_var(sts2_core::cards::VarKey::CostReductionUntilPlayed),
        0,
        "the v0.111.0 var is never set at v0.107.1"
    );
}

// ── Midnight / The Ball (MP-only, sweepable) ───────────────────────────────

#[test]
fn midnight_costs_one_less_per_exhaust_v0111() {
    let mut state = fresh(V111, CharacterId::Ironclad);
    let i = push_hand(&mut state, CardId::Midnight, false);
    let uid = state.player.hand[usize::from(i)];
    let fodder = new_card(&mut state, CardId::StrikeIronclad, false);
    state.player.hand.push(fodder);
    let _ = sts2_core::card_effects::exhaust_card_uid(&mut state, fodder);
    assert_eq!(state.cards[uid.0.get() as usize - 1].cost, 11);
}

#[test]
fn the_ball_grows_per_play_v0111() {
    let mut state = fresh(V111, CharacterId::Ironclad);
    let i = push_hand(&mut state, CardId::TheBall, false);
    let uid = state.player.hand[usize::from(i)];
    let _ = sts2_core::combat_loop::play_card(&mut state, i, Some(0));
    assert_eq!(enemy_hp(&state), 90);
    assert_eq!(state.cards[uid.0.get() as usize - 1].base_damage, 20);
}

// ── Abundance ──────────────────────────────────────────────────────────────

#[test]
fn abundance_offers_three_upgraded_powers_v0111() {
    let mut state = fresh(V111, CharacterId::Ironclad);
    let i = push_hand(&mut state, CardId::Abundance, false);
    let _ = sts2_core::combat_loop::play_card(&mut state, i, None);
    let pc = state
        .pending_choice
        .as_ref()
        .expect("a choose-a-card screen");
    assert_eq!(pc.options.len(), 3);
    assert!(
        !pc.can_skip,
        "FromChooseACardScreen defaults canSkip to false"
    );
    for opt in &pc.options {
        assert!(opt.upgraded);
        let spec = sts2_content::cards::spec_index::spec_for_card_id(opt.card_id).expect("spec");
        assert_eq!(spec.card_type, sts2_core::cards::CardType::Power);
    }
}

// ── Misery ─────────────────────────────────────────────────────────────────

#[test]
fn misery_folds_temporary_strength_v0111() {
    let mut state = fresh(V111, CharacterId::Necrobinder);
    let other = add_enemy(&mut state, 50, 100);
    let target = first_enemy(&state);
    // Target: drained by a Piercing Wail (Strength -6 + PiercingWail 6).
    add_enemy_power(&mut state, target, 800, PowerKind::Strength, -6);
    add_enemy_power(&mut state, target, 801, PowerKind::PiercingWail, 6);
    let i = push_hand(&mut state, CardId::Misery, false);
    let _ = sts2_core::combat_loop::play_card(&mut state, i, Some(0));
    assert_eq!(enemy_power(&state, other, PowerKind::PiercingWail), 6);
    assert_eq!(
        enemy_power(&state, other, PowerKind::Strength),
        -6,
        "Strength -6 + 6 folds to 0 and is skipped; the temporary power takes its 6 itself"
    );
}

/// A Strength-UP target with a temporary Strength-down: Strength reads as a
/// buff, so it is not snapshotted and nothing absorbs the temporary power's
/// amount — at v0.111.0 the copied PiercingWail takes its 6 Strength itself.
#[test]
fn misery_temporary_debuff_without_strength_entry_v0111() {
    let mut state = fresh(V111, CharacterId::Necrobinder);
    let other = add_enemy(&mut state, 50, 100);
    let target = first_enemy(&state);
    add_enemy_power(&mut state, target, 800, PowerKind::Strength, 3);
    add_enemy_power(&mut state, target, 801, PowerKind::PiercingWail, 6);
    let i = push_hand(&mut state, CardId::Misery, false);
    let _ = sts2_core::combat_loop::play_card(&mut state, i, Some(0));
    assert_eq!(enemy_power(&state, other, PowerKind::PiercingWail), 6);
    assert_eq!(enemy_power(&state, other, PowerKind::Strength), -6);
}

/// The same board at v0.107.1: `IgnoreNextInstance` swallows the copy's
/// Strength loss, so the other enemy keeps its Strength.
#[test]
fn misery_temporary_debuff_without_strength_entry_v0107() {
    let mut state = fresh(V107, CharacterId::Necrobinder);
    let other = add_enemy(&mut state, 50, 100);
    let target = first_enemy(&state);
    add_enemy_power(&mut state, target, 800, PowerKind::Strength, 3);
    add_enemy_power(&mut state, target, 801, PowerKind::PiercingWail, 6);
    let i = push_hand(&mut state, CardId::Misery, false);
    let _ = sts2_core::combat_loop::play_card(&mut state, i, Some(0));
    assert_eq!(enemy_power(&state, other, PowerKind::PiercingWail), 6);
    assert_eq!(enemy_power(&state, other, PowerKind::Strength), 0);
}

// ── Dowsing quest (run layer) ──────────────────────────────────────────────

fn dowsing_run(version: GameVersion) -> sts2_core::runstate::RunState {
    content_dispatch::ensure_installed();
    sts2_content::relics::dispatch::ensure_installed();
    let mut rs = sts2_core::runstate::RunState {
        game_version: version,
        ..sts2_core::runstate::RunState::default()
    };
    rs.master_deck
        .push(card_pod_from_id_for(CardId::StrikeSilent, false, version).expect("strike"));
    rs.master_deck
        .push(card_pod_from_id_for(CardId::Dowsing, false, version).expect("dowsing"));
    rs.master_deck
        .push(card_pod_from_id_for(CardId::DefendSilent, false, version).expect("defend"));
    rs
}

#[test]
fn dowsing_transforms_into_abundance_after_five_unknown_rooms_v0111() {
    let mut rs = dowsing_run(V111);
    for n in 1..5 {
        rs.tick_dowsing_quests_v0111();
        let d = rs
            .master_deck
            .iter()
            .find(|c| c.card_id == CardId::Dowsing)
            .expect("still a quest card");
        assert_eq!(d.combat_var(sts2_core::cards::VarKey::Generic), n);
    }
    rs.tick_dowsing_quests_v0111();
    let ids: Vec<CardId> = rs.master_deck.iter().map(|c| c.card_id).collect();
    assert_eq!(
        ids,
        vec![
            CardId::StrikeSilent,
            CardId::DefendSilent,
            CardId::Abundance
        ],
        "Dowsing leaves its slot; Abundance is appended at the deck tail (CardCmd.cs:416-447)"
    );
}

// ── Draw-pile grids sort by the build's rarity (review finding a) ─────────

/// Stratagem's reshuffle grid with `[Footwork, Accelerant]` in the discard:
/// the option-0 card. `CardSelectCmd.FromCombatPile` sorts the Draw pile
/// `orderby c.Rarity, c.Id` (v0.111.0 Commands/CardSelectCmd.cs:490-505) on
/// the LIVE rarity, and v0.111.0 made Accelerant Uncommon (Accelerant.cs:21),
/// so it ties with Footwork and wins on the Id.
fn stratagem_grid_first_option(version: GameVersion) -> CardId {
    let mut state = fresh(version, CharacterId::Silent);
    add_player_power(&mut state, 50, PowerKind::Stratagem, 1);
    for id in [CardId::Footwork, CardId::Accelerant] {
        let uid = new_card(&mut state, id, false);
        state.player.discard.push(uid);
    }
    let _ = state.draw_into_hand(1);
    let pc = state.pending_choice.as_ref().expect("Stratagem grid opens");
    assert_eq!(pc.kind, sts2_core::ChoiceKind::AddFromDrawToHand);
    pc.options[0].card_id
}

#[test]
fn stratagem_grid_sorts_by_build_rarity_v0111() {
    assert_eq!(stratagem_grid_first_option(V111), CardId::Accelerant);
}

#[test]
fn stratagem_grid_sorts_by_build_rarity_v0107() {
    // v0.107.1: Accelerant is Rare (4), Footwork Uncommon (3).
    assert_eq!(stratagem_grid_first_option(V107), CardId::Footwork);
}

/// The core rarity-rank hook ranks by the build's rarity: Outbreak is Rare
/// in v0.111.0 (Outbreak.cs:23), Uncommon in v0.107.1.
#[test]
fn rarity_rank_hook_is_build_aware_v0111() {
    content_dispatch::ensure_installed();
    let rank = sts2_core::lookup_card_rarity_rank().expect("registered");
    assert_eq!(rank(CardId::OutbreakCard, V111), 4);
    assert_eq!(rank(CardId::OutbreakCard, V107), 3);
    assert_eq!(rank(CardId::Accelerant, V111), 3);
    assert_eq!(rank(CardId::Accelerant, V107), 4);
}

// ── Upgrade sites use the build's delta (review finding b) ───────────────

/// Play `id` (un-upgraded) holding RazorTooth, which upgrades the played
/// Attack/Skill (`RazorTooth.cs`); return the card afterwards.
fn razor_tooth_upgraded(version: GameVersion, id: CardId) -> sts2_core::cards::Card {
    let mut state = fresh(version, CharacterId::Defect);
    state.relics.push(sts2_core::Relic {
        id: NonZeroU32::new(1).expect("nz"),
        kind: sts2_core::RelicKind::RazorTooth,
        counter: sts2_core::RelicKind::RazorTooth.initial_counter(),
        is_wax: false,
        is_melted: false,
    });
    let i = push_hand(&mut state, id, false);
    let uid = state.player.hand[usize::from(i)];
    let _ = sts2_core::combat_loop::play_card(&mut state, i, None);
    state.cards[uid.0.get() as usize - 1]
}

#[test]
fn razor_tooth_upgrades_with_build_delta_v0111() {
    // Hyperbeam: 24 + 6 (Hyperbeam.cs:61), not v0.107.1's +8.
    let hb = razor_tooth_upgraded(V111, CardId::Hyperbeam);
    assert!(hb.upgraded);
    assert_eq!(hb.base_damage, 30);
    // Expect a Fight: the v0.111.0 upgrade no longer lowers the cost
    // (ExpectAFight.cs:39-43).
    let eaf = razor_tooth_upgraded(V111, CardId::ExpectAFight);
    assert!(eaf.upgraded);
    assert_eq!(eaf.cost, 3);
}

#[test]
fn razor_tooth_upgrades_with_build_delta_v0107() {
    let hb = razor_tooth_upgraded(V107, CardId::Hyperbeam);
    assert_eq!(hb.base_damage, 36);
    let eaf = razor_tooth_upgraded(V107, CardId::ExpectAFight);
    assert_eq!(eaf.cost, 1);
}

// ── Normalizing a combat-set cost ──────────────────────────────────────────

/// A Confused roll (`EnergyCost.SetThisCombat`, ConfusedPower.cs:42) that
/// lands on BeaconOfHope's v0.107.1 cost (1) survives an in-combat upgrade
/// (Bellows → `upgrade_for`) under v0.111.0; only a still-v0.107.1-minted
/// cost is re-based to the build's 2 (Ironclad mid seed 1809).
#[test]
fn normalize_keeps_a_confused_cost_v0111() {
    content_dispatch::ensure_installed();
    let mut rolled = card_pod_from_id_for(CardId::BeaconOfHope, false, V111).expect("pod");
    assert_eq!(rolled.cost, 2, "BeaconOfHope.cs:18");
    rolled.cost = 1;
    rolled.set_combat_var(sts2_core::cards::VarKey::CostAbsolutelySet, 1);
    assert!(rolled.upgrade_for(V111));
    assert_eq!(rolled.cost, 1, "the absolute roll is kept");

    let mut minted = card_pod_from_id_for(CardId::BeaconOfHope, false, V107).expect("pod");
    assert_eq!(minted.cost, 1);
    minted.normalize_for_version(V111);
    assert_eq!(minted.cost, 2, "a v0.107.1-minted base cost is re-based");
}

// ── BeatDown ───────────────────────────────────────────────────────────────

/// BeatDown auto-plays a Smog-afflicted Strike out of discard while the
/// player holds Smoggy, so `ShouldPlay` vetoes the play. Returns the
/// `combat_targets` reads and the enemies' HP lost.
fn beat_down_vetoed_targets_read(version: GameVersion) -> (u32, i32) {
    let mut state = fresh(version, CharacterId::Ironclad);
    let _ = add_enemy(&mut state, 9, 100);
    add_player_power(&mut state, 50, PowerKind::Smoggy, 1);
    let strike = new_card(&mut state, CardId::StrikeIronclad, false);
    state.cards[strike.0.get() as usize - 1].affliction =
        Some(sts2_core::card_afflictions::CardAffliction::new(
            sts2_core::card_afflictions::CardAfflictionKind::Smog,
            1,
        ));
    state.player.discard.push(strike);
    let i = push_hand(&mut state, CardId::BeatDown, false);
    let before = state.run_rng.combat_targets.counter();
    let _ = sts2_core::combat_loop::play_card(&mut state, i, None);
    let enemies = state.enemy_uids.clone();
    let lost = 200 - enemies.iter().map(|u| hp_of(&state, *u)).sum::<i32>();
    (state.run_rng.combat_targets.counter() - before, lost)
}

/// v0.107.1 port: the target roll sits after the `ShouldPlay` gate.
#[test]
fn beat_down_vetoed_play_rolls_no_target_v0107() {
    assert_eq!(beat_down_vetoed_targets_read(V107), (0, 0));
}

/// v0.111.0: BeatDown rolls the `AnyEnemy` target before `CardCmd.AutoPlay`
/// (decomp Cards/BeatDown.cs:32-35), so the read happens though the play is
/// vetoed. Parity fuzz Regent seed 272 (ChainsOfBinding's Bound, GH #798).
#[test]
fn beat_down_vetoed_play_rolls_target_v0111() {
    assert_eq!(beat_down_vetoed_targets_read(V111), (1, 0));
}
