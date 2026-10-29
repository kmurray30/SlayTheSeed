# Importing a live game state

Copy a position out of the running game, paste it into the workbench, and play
it — same seed, same deck, same floor, and (in a fight) the same hand, piles,
powers and intents. From there: branch it by hand, or hand it to SimLab and get
distributions instead of anecdotes.

## The loop

1. **In the game** — press **Copy state** on the Spire Mentor strip, or
   `Ctrl+Shift+C`. The position goes to the clipboard and to
   `%LOCALAPPDATA%\SpireMentor\state-<ts>.json`.
2. **In the workbench** — **📋 Import state**, paste, and pick a lane. The
   dialog names the run and reports what the import will and will not
   reproduce *before* it replaces the run you have open.
3. **Explore** — the imported position is an ordinary live session: play,
   undo, branch, hint, search tree, regret review.
4. **Sandbox** — **🧪 SimLab from here** opens the SimLab sandbox on this
   exact board (hand, pile order, powers, telegraphs) in a new tab. Edit
   anything there, then play it out as a distribution or compare first moves.
   The import dialog's **open in SimLab instead** does the same straight from
   a paste, without replacing the run you have open.

## The two lanes

| Lane | What you get | Ends at |
|---|---|---|
| **combat** | The fight, bit-exact: hand, draw/discard/exhaust ORDER, powers, intents, energy, block, and the game's RNG cursor | the fight's resolution — there is no run behind a board |
| **run** | The run position: this seed's real maps and boss, with deck, relics, potions, gold, HP and RNG cursors grafted on, parked on the node you were standing on | the run's own end |

A paste taken during a fight can fill either. `auto` prefers the fight.

## What is exact, and what is not

Exact, and asserted by tests:

- **Run identity** — seed, character, ascension. The run is *constructed* the
  same way, so the act maps, the boss and the pre-rolled encounter queue are
  the live game's, not a look-alike. (`act1` is not guessed: the run is built
  with `act1="roll"`, which reproduces the game's own seed-driven pick between
  Overgrowth and Underdocks — GH #544.)
- **Position** — the node and the visited path, taken verbatim off the wire.
  A coord that is not on this seed's map is a hard error, because it means the
  paste and the engine disagree about what run this is.
- **Deck, relics, potions, gold, HP** — including which copy of a card is
  upgraded.
- **The combat board** — `state_from_dict` round-trip, the same path the live
  advisor uses every few seconds.
- **RNG cursors** — every stream Spire Mentor publishes is fast-forwarded to its
  recorded count, so the next card reward, shop stock, event roll and monster
  intent are the ones your run was about to get. Without this an import shares
  only its seed and every downstream roll belongs to a different run; the
  dialog says so when a paste has no `rng` block.

Not carried by the wire, and therefore not reproduced — see `PROTOCOL.md` §6:
relic latch state, per-event "already seen" flags, and other undisclosed hidden
state. These are reported, never silently assumed.

The run lane parks at **`map_choice` on the current node** — "you are standing
here with this deck, choose where to go". Re-materializing the exact screen you
were on (a shop with its live stock, an event with its live options) is not
implemented; with the cursors restored, the *next* roll matches anyway.

## The SimLab sandbox

`/simlab.html` is a position editor: setup (character, ascension, turn,
encounter, seed), the player (HP, block, energy, stars, orb slots, relics,
potions, powers), every card by pile — draw pile **in order**, the order the
engine deals — and each enemy with HP, block, powers and its telegraphed move
(the engine's own move-state ids, so a picked intent is what the AI performs).
The position is a compact JSON document (`simlab-position/1`,
`python/simlab/position.py`); the engine wire dict is derived from it on the
server, and the **Position** card says whether the engine accepted it
("Built.") or exactly which field it refused.

A position travels in the URL hash (`#p=<base64url JSON>`): **Copy link**
gives a link that reopens the sandbox on the same board, and the workbench's
**SimLab from here** (`GET /api/live/position`) is that link opened for you.
The **Position JSON** box also takes the mod's Copy-state payload directly
(`POST /api/simlab/position/import` reads it with the same parser as the
workbench import).

Two analyses: **play it out** is a start-state sweep — trials per strategy from
this board, optionally one cell per legal first move, so the results table
reads "play Bash: mean HP lost X, play Strike: Y, end turn: Z" with the usual
distribution / CDF / box charts; **search** is the workbench's MCTS on the
board, win probability per first move. There is no exhaustive solver: the
engine's search is Monte-Carlo, so "attained / converged" certificates are not
something it can promise.

## Sweeping a position in SimLab

`POST /api/live/simlab/sweep` takes branch-tree node ids and contributes one
SimLab cell per node. That is the counterfactual comparison: play each
alternative out one move, name both nodes, and the resulting distributions
differ by that decision alone rather than by one lucky playout each.

Under the hood a scenario may carry `start_state` (the engine wire as JSON)
instead of an encounter and a deck. Every trial rebuilds that exact board and
then **re-seeds the RNG streams from the trial seed**. Both halves matter: the
copy is what makes it *this* position, and the reseed is what makes it a sample
rather than eleven replays of one frozen stream.

Because `start_state` fixes the encounter, the deck and the ascension, it
replaces those sweep axes — what remains is `start_states × strategies`.
Labels are the bucket key, so they must be unique: two positions under one
label would merge into a single distribution, which is exactly what a
counterfactual must not do.

## Shapes the importer accepts

Deliberately tolerant, because the JSON reaches you by whatever route is to
hand:

- the mod's Copy-state payload (an `advice_request`),
- any raw wire message (`combat_action`, `map_select`, a screen…),
- one record out of a log (`{step, ts, type, data}`),
- a whole pasted JSONL blob — the newest position in it wins,
- a clipped line at either end (skipped, not fatal).

Refused, with a reason: an engine wire dict and a headless `combat_play`
decision. Both look importable and are not — neither carries a run (no seed,
no deck, no map), so there is nothing to rebuild.

## Where the code lives

| Piece | File |
|---|---|
| Clipboard producer | `spire-mentor-mod/SimStateCopy.cs` (`PROTOCOL.md` §8) |
| Paste parser | `python/workbench/state_import.py` |
| Combat lane | `python/workbench/backends/imported_combat.py` |
| Run lane | `RustGame.start_imported` in `python/workbench/backends/rust_game.py` |
| Session | `BranchingLiveSession.from_imported_combat` / `.start_imported` |
| Endpoints | `/api/live/import-state[/preview]`, `/api/live/simlab/{sweep,scenario}` |
| SimLab handoff | `python/workbench/simlab_bridge.py`, `ScenarioSpec.start_state` |
| SimLab sandbox | `python/simlab/position.py` (schema ↔ wire), `python/simlab/catalog.py` (ids → names, monster moves, encounters), `web/src/simlab/` |
| SimLab browser port (sandbox) | `sts2-rs/crates/sts2-workbench/src/simlab.rs` (position ↔ wire) + `simlab_catalog.rs` (catalog / tables); the offline workbench answers `/api/simlab/*` from the wasm. The distribution sweeps and the combat-lane fight resume are desktop-only. See `docs/browser-workbench.md` § "SimLab sandbox". |
| Sandbox endpoints | `/api/live/position`, `/api/simlab/catalog`, `/api/simlab/position/{build,new,import,moves,mcts}` |
| Engine affordances | `RunManager::debug_place_on_map`, `PyRunManager.debug_set_rng_counters` |
| Browser port (run lane) | `sts2-rs/crates/sts2-workbench/src/state_import.rs` (parser) + `Game::start_imported` (graft) — the offline workbench rebuilds the RUN lane only; the combat lane needs the adapter it does not carry. See `docs/browser-workbench.md` § "State import". |
