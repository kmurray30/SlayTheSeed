//! `RolloutPolicyKind::Learned` (GH #805): the fitted-table rollout arm.
//!
//! Pins the three properties the book sweep relies on: an empty table
//! reproduces the deployed Heuristic rollout draw for draw, a table entry
//! actually moves the pick, and `rollout_pricing` prices actions the way
//! the sampler does.

mod common;

use common::build_vertical_slice_hp;
use rand::SeedableRng;
use rand_xoshiro::Xoshiro256PlusPlus;
use sts2_core::{apply, is_terminal, legal_actions, Action, CombatState};
use sts2_mcts::expert::{
    pick_rollout_action_learned, pick_rollout_action_with, play_ordering, rollout_pricing,
    LearnedPriorities, END_TURN_PRIORITY,
};

/// A handful of mid-fight states with real hands, reached by always
/// taking the first legal action from the starter slice.
fn positions() -> Vec<CombatState> {
    let mut out = Vec::new();
    let mut state = build_vertical_slice_hp(3, 80, 80);
    // The slice carries no character; the table is keyed by one.
    state.player.character = Some(sts2_core::CharacterId::Ironclad);
    for _ in 0..40 {
        if is_terminal(&state) {
            break;
        }
        let acts = legal_actions(&state);
        if acts.is_empty() {
            let _ = apply(&mut state, Action::EndTurn);
            continue;
        }
        if acts
            .iter()
            .filter(|a| matches!(a, Action::PlayCard { .. }))
            .count()
            >= 2
        {
            out.push(state.clone());
        }
        let _ = apply(&mut state, acts[0].clone());
    }
    assert!(
        out.len() >= 3,
        "slice produced too few multi-card positions"
    );
    out
}

fn table(json: &str) -> LearnedPriorities {
    LearnedPriorities::from_json(json).expect("table parses")
}

#[test]
fn an_empty_table_is_the_heuristic_rollout_draw_for_draw() {
    let empty = table(r#"{"tables": {}}"#);
    for state in positions() {
        let acts = legal_actions(&state);
        let mut a = Xoshiro256PlusPlus::seed_from_u64(7);
        let mut b = Xoshiro256PlusPlus::seed_from_u64(7);
        for _ in 0..200 {
            assert_eq!(
                pick_rollout_action_with(&mut a, &state, &acts, END_TURN_PRIORITY),
                pick_rollout_action_learned(&mut b, &state, &acts, END_TURN_PRIORITY, &empty),
            );
        }
    }
}

#[test]
fn a_table_entry_moves_the_pick() {
    // Push every card in the starter deck far down except Bash, so a
    // position with Bash in hand plays it essentially always.
    let state = positions()
        .into_iter()
        .find(|s| {
            s.player.hand.iter().any(|uid| {
                s.cards[uid.0.get() as usize - 1].card_id == sts2_core::cards::CardId::Bash
            })
        })
        .expect("a position with Bash in hand");
    let t = table(
        r#"{"tables": {"ironclad": {"BASH": -500, "STRIKE_IRONCLAD": 500, "DEFEND_IRONCLAD": 500}}}"#,
    );
    let acts = legal_actions(&state);
    let mut rng = Xoshiro256PlusPlus::seed_from_u64(1);
    for _ in 0..100 {
        let i = pick_rollout_action_learned(&mut rng, &state, &acts, END_TURN_PRIORITY, &t);
        let Action::PlayCard { hand_index, .. } = &acts[i] else {
            panic!("picked a non-card action {:?}", acts[i]);
        };
        let uid = state.player.hand[*hand_index as usize];
        assert_eq!(
            state.cards[uid.0.get() as usize - 1].card_id,
            sts2_core::cards::CardId::Bash
        );
    }
}

#[test]
fn unknown_names_are_rejected() {
    assert!(LearnedPriorities::from_json(r#"{"tables": {"watcher": {}}}"#).is_err());
    assert!(
        LearnedPriorities::from_json(r#"{"tables": {"ironclad": {"NOT_A_CARD": 1}}}"#).is_err()
    );
}

#[test]
fn pricing_reports_the_ladder_base_and_tracks_it() {
    for state in positions() {
        let acts = legal_actions(&state);
        let pricing = rollout_pricing(&state, &acts, END_TURN_PRIORITY);
        assert_eq!(pricing.actions.len(), acts.len());
        for (a, p) in acts.iter().zip(&pricing.actions) {
            match a {
                Action::PlayCard { hand_index, .. } => {
                    let uid = state.player.hand[*hand_index as usize];
                    let id = state.cards[uid.0.get() as usize - 1].card_id;
                    assert_eq!(p.card, Some(id));
                    assert_eq!(p.base, play_ordering(id, state.player.character));
                    if p.tracks_base {
                        // Moving the base by d moves a tracking priority by d.
                        let t = table(&format!(
                            r#"{{"tables": {{"ironclad": {{"{}": {}}}}}}}"#,
                            id.to_python_name(),
                            p.base + 3
                        ));
                        assert_eq!(t.base(id, state.player.character), Some(p.base + 3));
                    }
                }
                Action::EndTurn => assert_eq!(p.priority, END_TURN_PRIORITY),
                _ => assert!(p.card.is_none()),
            }
        }
    }
}
