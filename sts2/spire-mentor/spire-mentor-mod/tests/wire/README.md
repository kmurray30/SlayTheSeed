# BridgeServer wire test

A black-box differential test of Spire Mentor's TCP server. It hosts
`BridgeServer` from a built `SpireMentor.dll` in-process (no game, no Godot: the
headless GodotSharp stub from `sts2-pycli/src/GodotStubs` stands in), drives it
over real loopback TCP with a scripted client, and records a normalized
transcript of everything on the wire. Two builds behave identically on the
wire if and only if their transcripts are identical.

```sh
# 1. build the mod (Windows: `dotnet build` in spire-mentor-mod/; elsewhere see
#    ../../README.md "Compile-only"), then:
spire-mentor-mod/tests/wire/run_wire_test.sh <path/to/SpireMentor.dll> /tmp/wire-out
# readable diff of any two runs:
python3 spire-mentor-mod/tests/wire/diff_transcripts.py A/transcript.jsonl B/transcript.jsonl [--semantic] [--only 11_] [--skip 13_]
```

Needs the .NET 9 SDK and the game's `sts2.dll` in `sts2-pycli/lib/` (`just
import-game <game> --tier oracle`) — the same inputs as the C# parity oracle.
The script builds the stub and the harness into `.build/`, runs ~20 s, writes
`<out>/transcript.jsonl` (+ `server.log`, `meta.json`, `harness.stderr`) and
diffs against `golden/transcript.jsonl`. Exit: 0 identical, 1 differs, 2+
build/harness failure. Overrides are in the script header.

**When it fails:** a difference is not automatically a bug — the diff names the
scenario and shows both sides. If the change is intended (a protocol fix),
re-record the golden: run with `BASELINE=none` and copy the new
`transcript.jsonl` to `golden/`, and say why in the commit message. Log text
(`server.log`) is deliberately not compared.

## Golden

`golden/transcript.jsonl` is the server as of the 2026-10 clean-room rewrite
plus its fixes. Against the server before the rewrite it differed only where
intended: UTF-8 characters split across TCP reads now survive; `Start()` works
again after a failed bind; `undo_turn` / `restore_snapshot` with wrong-typed
fields are rejected instead of guessed; a `{"action":5,"type":"PING"}` gets a
pong; and scenario 19 is new (an HTTP request and an endless unterminated line
are both dropped). The endless-line client's close is recorded as
`s2c_closed` rather than `s2c_eof`: the server closes with that client's data
still unread, so whether the client sees FIN or RST depends on timing (seen as
RST on Windows). Determinism: repeated and concurrent runs under CPU load
produce byte-identical transcripts.

## API surface used (the only coupling to the server)

All via reflection on `SpireMentor.BridgeServer` (no compile-time reference), i.e.
exactly what the rest of the mod calls (`MainFile`, `SimShutdown`,
`SimAgentGate`, `SimAdvisor`, `SimMode`, `SimSnapshots`/`SimRestore`/
`SimCombatUndo`/`SimProtocolHelpers` callers):

| Member | Used by the mod in |
|---|---|
| `static BridgeServer Instance` (field or property) | everywhere |
| `void Start(int port)` | MainFile |
| `void Stop()` | SimShutdown |
| `bool IsClientConnected { get; }` | SimMode, SimAgentGate, SimAdvisor, SimAdvisorProcess |
| `void SendState(string stateJson)` | SimSnapshots, SimRestore, SimCombatUndo, SimAdvisor, SimProtocolHelpers |
| `Task<string?> SendStateAndWaitForActionAsync(string, TimeSpan, CancellationToken)` | SimAgentGate |
| `Task<string?> WaitForActionAsync(TimeSpan, CancellationToken)` | (public, "used rarely") |
| `string? SendAdviceRequest(string, Action<JsonElement>)` | SimAdvisor |
| `void CancelAdviceRequest(string)` | SimAdvisor |
| `event Action<JsonElement> CoachReceived` (optional: absent on pre-v5 builds) | SimAdvisor |

Methods are looked up by name + parameter count; any awaitable `Task`
whose `Result` property is read works. Nothing private of `BridgeServer` is
touched.

## What is stubbed / intercepted

* **GodotSharp** → the headless stub (`sts2-pycli/src/GodotStubs`), copied to
  `build/godotstubs-src` and built there (repo untouched). One augmentation on
  the copy: `Callable.From<TResult>(Func<TResult>)` added (real Godot has it;
  `SimRestore.RequestRestore`/`SimCombatUndo.RequestUndoTurn` call
  `Callable.From(() => TaskHelper.RunSafely(...))`, and Harmony must resolve
  every token of a method it patches). sts2.dll = real v0.107.1;
  0Harmony/MonoMod = real (`sts2-pycli/lib`).
* Harmony prefixes (skip original):
  * `SpireMentor.Logger.Log` → `server.log`.
  * `SpireMentor.SimRestore.RequestRestore(string)` → recorded as a `hook` event
    (normally `CallDeferred` onto the Godot main thread → game restore).
  * `SpireMentor.SimCombatUndo.RequestUndoTurn(int, string?)` → `hook` event.
* `SimSnapshots`' in-memory store (`Metas`, `_currentStartTime` — private
  statics of SimSnapshots, not of BridgeServer) is seeded with two fixed
  snapshot entries midway through scenario 09, so `list_snapshots` has
  deterministic non-empty content (empty case is recorded first).
* Advice callbacks are harness delegates that record `advice_callback` hook
  events (one deliberately throws).
* **Platform shim (on by default):** Harmony prefix on
  `Socket.SetSocketOption(level, Send/ReceiveTimeout, int)` mapping `-1 → 0`.
  `BridgeServer` sets `TcpClient.ReceiveTimeout = Timeout.Infinite`;
  `TcpClient` passes `-1` straight to `setsockopt(SO_RCVTIMEO)`, which Windows
  accepts and Linux rejects with EINVAL — so on Linux/.NET 9 the current
  server fails *every* accept ("Accept error: Invalid argument", client socket
  left open and never read). The shim reproduces the Windows semantics the
  mod ships on. `NO_PLATFORM_SHIM=1` shows the raw Linux behaviour (slow:
  every expectation times out). The transcript's first event records whether
  the shim was on.

## Transcript format / normalization

One JSON object per line: `{"scn", "ev", ...}`. Events: `scenario`, `api`
(sync call: args, result, exception type/SocketError, `returned` vs
`blocked_over_1s`), `api_start` (async call + `completed_on_return`), `probe`
(explicit checkpoint: `pending`/result), `api_result` (+ the checkpoint label it
was observed after, e.g. `after_reply`, `timeout`, `after_client_close`),
`c2s`/`c2s_chunks` (exact bytes sent, chunk boundaries, hex if not valid
UTF-8), `s2c` (each `\n`-terminated server line, raw, incl. any `\r`),
`s2c_none` (nothing within a quiet window), `s2c_eof`, `s2c_timeout`,
`client_connect/close/reset/shutdown_send`, `is_client_connected` (polled
until expected value, max 3 s), `hook`, `burst_summary`.

Determinism rules: background threads (socket readers, server callbacks,
hooks) only buffer; the scenario thread records at explicit sync points
(`Expect`, `Drain`, barrier = ping→pong, which proves all earlier lines were
processed by the single read loop). No timestamps or durations are recorded —
async timing is expressed as "pending at checkpoint X / completed after event
Y". Ports are placeholders (`<PORT>`, `<PORT2>`…). Request ids are the
server's own counters (deterministic given the fixed script; they ARE wire
behaviour). Strings > 1500 chars are recorded as `{len, sha256, head, tail}`.

`--semantic` in the diff tool compares JSON payloads by value (hides key
order / escaping); the default byte-exact mode is the real test. Changed
server lines are annotated (`SAME JSON value; key ORDER differs`, `JSON
differs in keys: …`).

## Scenarios (what is covered)

| # | Covers |
|---|---|
| 00 pre_start | every API before `Start()`: `SendStateAndWait` → null immediately, `SendAdviceRequest` → null, `WaitForActionAsync` blocks until timeout |
| 01 start_connect | Start, connect, **no greeting/handshake** from server, `Start()` idempotent (same and different port: 2nd port not opened) |
| 02 outbound_types | `SendState` with every outbound type in PROTOCOL.md (combat_action … combat_undone, snapshots, pong, incl. the v5 `human_action` kinds: end turn, choice, skip) → one verbatim line each |
| 03 sendstate_framing | SendState writes payload verbatim + `\n`: pretty JSON → multiple wire lines, `""` → empty line, non-JSON, trailing `\n`/`\r\n`, unicode/HTML chars unescaped |
| 04 request_id_injection | `request_id` injection by re-serialization: appended at end / replaced in place (string or numeric existing id), whitespace compacted, number literals preserved, non-ASCII & `<>&'"` escaped to `\uXXXX`, duplicate keys collapse (last wins), `{}`/`null` → `{"request_id":"N"}`, non-object/invalid JSON sent unchanged *without* id (can only time out) |
| 05 correlation | wrong / missing / numeric / malformed reply dropped while pending; ping with pending id → pong, not consumed; advice verb with autopilot id not consumed; list_snapshots while pending; padded+CRLF reply trimmed; duplicate reply; supersede (2nd request cancels 1st → null; late reply to 1st dropped); every client verb (play, end_turn, potion, choose index/indices/indexes, skip, cancel, start_run, annotations, case) passed through verbatim; CancellationToken cancel; timeout; zero timeout & pre-cancelled token (request still sent, returns null); infinite timeout |
| 06 wait_for_action | uncorrelated wait gets any line verbatim (malformed, arrays, foreign request_id, `action` non-string); ping/advice/list_snapshots/blank lines not delivered; timeout; WaitForAction ⇄ SendStateAndWait supersede each other |
| 07 no_handler | lines with nothing pending: silently discarded |
| 08 ping_variants | `action` ping any case, legacy `type` PING any case, combos, near misses, non-object JSON, `\r` mid-line |
| 09 rewind_lane | list_snapshots (empty, seeded, case), restore_snapshot (ok/missing/empty/null/numeric id → error line or hook or silence), undo_turn (turn/tape_id variants, float, string, negative), rewind verbs while a request is pending |
| 10 advice_lane | id format `a<N>`, callback payload, overlapping replies out of order, `ADVICE` case, duplicate/unknown/missing/numeric id, cancel, throwing callback, advice ⇄ autopilot isolation, non-object payloads, 33-request backlog clear |
| 11 framing | line split over 3 segments (no reply until `\n`), 4 messages in one write, boundary inside 2nd message, CRLF/blank/padded lines, 50 pings in one write, **UTF-8 char split across segments**, invalid UTF-8, NUL, reply split in 3 segments |
| 12 oversized | 64 KiB and 1 MiB client lines, 1 MiB ping, 1 MiB `SendState`, 256 KiB `SendStateAndWait` |
| 13 concurrent_send | 8 threads × 25 `SendState`: all 200 lines intact, per-thread order kept |
| 14 disconnect_reconnect | client close / RST / half-close while a request is pending (→ null, server closes), no-client SendState/SendStateAndWait/SendAdviceRequest, WaitForAction survives no-client and is answered by a newly connected client, stale ids & advice from previous connection dropped, partial line + close |
| 15 second_client | second connection is OS-accepted but not served (queued ping unanswered, SendState goes to first client only) until the first leaves; then served; an already-closed queued client is skipped |
| 16 stop_pending | Stop() with request + advice pending: request → null, client EOF, listener closed (connect refused), all APIs after Stop, double Stop |
| 17 restart_after_stop | Start() on a new port after Stop() works end-to-end |
| 18 start_busy_port | Start() on a bound port throws `SocketException(AddressAlreadyInUse)`; a following Start() on a free port then serves normally |
| 19 hostile_clients | a browser-style HTTP POST (body = a rewind verb) is dropped before any line is routed; a 9 MiB line without a newline drops that client; a fresh client is served afterwards |
| 20 coach_lane | v5 `coach` messages raise `CoachReceived` with a copy of the record (`coach_received` hook), verb case-insensitive, non-object / missing `coach` dropped; neither an uncorrelated `WaitForActionAsync` nor a correlated request consumes them |

## Not covered (and why)

* **run_config / start_run handshake** — not in the server: it lives in
  `SimAutoSlayer` (game-side, via `SimAgentGate`), the server just forwards it
  like any other request (the `run_config` message and `start_run` verb are
  exercised as opaque payloads in 02/05).
* **Envelope fields (`state_id`, `seed`, `run_state`, `map`…), `error`
  semantics, stale-state guard, verb execution** — produced/checked by the
  handlers, not the transport; only their pass-through is tested.
* **`SimAgentGate` retry loop** — needs `SimConfig` (reads/writes a config
  file next to the DLL), `SimThinkFps` (Godot engine) and the AutoSlay
  watchdog; it is a caller, not the server.
* **Restore / undo outcomes** (`snapshot_restored`, `combat_undone`, their
  errors) — game-side; the harness records that the server routed the verb to
  `SimRestore.RequestRestore` / `SimCombatUndo.RequestUndoTurn` with which args.
* **Write back-pressure** (client stops reading → 5 s `WriteTimeout`,
  disconnect) — timing-dependent (socket buffer sizes), would be slow and
  flaky; not attempted.
* **Log output** — kept in `server.log`, deliberately not diffed.
* **Main-thread dispatch** — the server itself never dispatches to the Godot

## Behaviours a future change should decide on deliberately

* `SendStateAndWaitForActionAsync` re-serializes the payload: non-ASCII and
  HTML-sensitive characters become `\uXXXX`, duplicate keys collapse;
  non-object payloads go out without `request_id` and can never be answered.
* A numeric `request_id` in a reply (even if equal) is dropped; `ping` with a
  `request_id` is answered and does not consume the pending request.
* A zero timeout or pre-cancelled token still sends the request on the wire.
* `WaitForActionAsync` does not require a connected client.
* The server accepts any loopback client that speaks the protocol; it only
  rejects clients that open with an HTTP request line.

## Proof it detects changes

Built with two deliberate server changes (a stateful UTF-8 decoder; `request_id`
placed first in the object), the diff flagged 10 of 20 scenarios — every
`request_id`-bearing line annotated "same JSON, key order differs", plus the
UTF-8 fix; with `--semantic` only the UTF-8 and long-line scenarios remained.

## Files

* `run_wire_test.sh` — build + run + diff.
* `diff_transcripts.py` — readable per-scenario diff (stdlib only).
* `host/` — the harness: `Program.cs` (host + scenario script), `Infra.cs`
  (transcript, reflective facade over the server, TCP client, probes, hooks).
* `golden/transcript.jsonl` — the expected transcript.
