// SimProtocolHelpers.cs -- phase 1b shared inbound-parsing / error / stale-state
// helpers. New file, not present before phase 1b.
//
// Design: phase1-target.md section 4, item 5 ("stale-state-guard pattern...
// the single most valuable idiom in the whole HermesBridge dispatcher") and
// item 8/10 (structured, never-silent errors). Centralizes what was
// previously ~11 independently-duplicated `ReadChoiceIndex(string)` copies
// (see phase1-current.md section 2) into one parser, so every handler gets
// the same state_id guard and the same structured-error behavior for free
// instead of re-implementing it per file.
//
// Policy (never-guess, see SimAgentGate.cs / bridge plan deviation #3): an
// invalid/stale/malformed inbound action NEVER silently no-ops and NEVER
// falls back to a default choice. It always emits a `{"type":"error",...}`
// notification (fire-and-forget, no request_id -- see BridgeServer.cs
// "game_over"/"run_complete" precedent) and the caller's existing retry loop
// re-sends the current decision message (a fresh SimAgentGate.RequestActionAsync
// call, new request_id/state_id) on the next iteration.

using System;
using System.Text.Json;
using MegaCrit.Sts2.Core.Entities.Players;
using MegaCrit.Sts2.Core.Runs;

namespace SpireMentor;

internal static class SimProtocolHelpers
{
    private static long _stateCounter;

    /// <summary>
    /// Monotonically increasing id assigned to every outbound decision
    /// message by SimStateExtractor.ApplyEnvelope. Mirrors BridgeServer's
    /// request_id counter but at the model/business layer: request_id
    /// guards transport-level correlation, state_id lets a client assert
    /// "the choice I'm sending was computed against exactly this state" and
    /// have the server reject (rather than silently misapply) a decision
    /// made against stale information.
    /// </summary>
    public static long NextStateId() => System.Threading.Interlocked.Increment(ref _stateCounter);

    /// <summary>
    /// Sends a structured, fire-and-forget error notification (no
    /// request_id -- same as game_over/run_complete, see BridgeServer.cs
    /// section 0). Never throws; a failure to serialize/send is logged and
    /// swallowed since this is already an error-reporting path.
    /// </summary>
    public static void SendError(string code, string detail, RunState? runState, Player? player)
    {
        try
        {
            var msg = new System.Collections.Generic.Dictionary<string, object>
            {
                ["type"] = NonCombatBridgeProtocol.ErrorState,
                ["error"] = code,
                ["detail"] = detail,
            };
            SimStateExtractor.ApplyEnvelope(msg, runState, player);
            BridgeServer.Instance.SendState(JsonSerializer.Serialize(msg));
        }
        catch (Exception ex)
        {
            Logger.Log($"[SimProtocolHelpers] Failed to send error '{code}': {ex.Message}");
        }
    }

    /// <summary>
    /// Parses the raw agent response into a JsonElement. On malformed JSON,
    /// sends a "malformed_action" error and returns false -- caller should
    /// treat this exactly like an invalid choice (loop, ask again).
    ///
    /// Phase 4 (real-game bridge overlay): this is also the single choke
    /// point every inbound action's raw JSON passes through (every handler
    /// either calls TryParse directly or via ReadChoiceIndex, which itself
    /// calls TryParse) -- so it's where an optional top-level "annotations"
    /// object (see PROTOCOL.md "annotations") is picked off and forwarded to
    /// SimHud. Fire-and-forget: this can never fail the parse itself or
    /// affect the returned root/bool.
    /// </summary>
    public static bool TryParse(string responseJson, RunState? runState, Player? player, out JsonElement root)
    {
        try
        {
            using var doc = JsonDocument.Parse(responseJson);
            root = doc.RootElement.Clone();
            TryApplyAnnotations(root, player);
            TryRecordAction(root, runState);
            return true;
        }
        catch (Exception ex)
        {
            SendError("malformed_action", $"Could not parse response JSON: {ex.Message}", runState, player);
            root = default;
            return false;
        }
    }

    /// <summary>
    /// Looks for an optional "annotations" object on the parsed action and,
    /// if present, hands it to SimHud along with a short summary of the
    /// action it rode in on. Never throws and never affects action handling
    /// -- see SimHud.cs's file header "never break gameplay" policy.
    /// </summary>
    private static void TryApplyAnnotations(JsonElement root, Player? player)
    {
        try
        {
            if (root.ValueKind != JsonValueKind.Object)
                return;
            if (!root.TryGetProperty("annotations", out JsonElement annotations) ||
                annotations.ValueKind != JsonValueKind.Object)
                return;

            SimHud.Instance.ApplyAnnotations(annotations, SummarizeAction(root), player);
        }
        catch (Exception ex)
        {
            Logger.Log($"[SimProtocolHelpers] Ignoring malformed annotations: {ex.Message}");
        }
    }

    /// <summary>
    /// Phase 4 (action log): every parsed inbound action becomes a log
    /// entry -- who acted is "AI" on this path (the autopilot/advisor
    /// backend sent it); human actions are recorded by the phase-6 advisor
    /// loop instead. Never throws, never affects action handling.
    /// </summary>
    private static void TryRecordAction(JsonElement root, RunState? runState)
    {
        try
        {
            if (root.ValueKind != JsonValueKind.Object)
                return;

            string? note = null;
            if (root.TryGetProperty("annotations", out JsonElement ann) &&
                ann.ValueKind == JsonValueKind.Object &&
                ann.TryGetProperty("note", out JsonElement noteEl) &&
                noteEl.ValueKind == JsonValueKind.String)
            {
                note = noteEl.GetString();
            }

            SimActionLog.Record(new SimActionLogEntry
            {
                Actor = "AI",
                Act = (runState?.CurrentActIndex ?? -1) + 1,
                Floor = runState?.TotalFloor ?? 0,
                Action = SummarizeAction(root),
                Advice = note,
            });
        }
        catch (Exception ex)
        {
            Logger.Log($"[SimProtocolHelpers] Could not record action-log entry: {ex.Message}");
        }
    }

    /// <summary>
    /// Human-readable one-liner for the overlay's status panel, e.g.
    /// `play card_index=2 target_index=0`. Best-effort only -- never throws.
    /// </summary>
    private static string SummarizeAction(JsonElement root)
    {
        string action = root.TryGetProperty("action", out JsonElement actionProp) && actionProp.ValueKind == JsonValueKind.String
            ? actionProp.GetString() ?? "?"
            : "?";

        var parts = new System.Collections.Generic.List<string> { action };
        foreach (string field in new[] { "card_index", "target_index", "slot", "index" })
        {
            if (root.TryGetProperty(field, out JsonElement el) && el.ValueKind == JsonValueKind.Number)
                parts.Add($"{field}={el}");
        }
        return string.Join(" ", parts);
    }

    /// <summary>
    /// Optional stale-state guard (phase1-target.md item 5). If the inbound
    /// message carries a "state_id" field and it does not match the
    /// state_id of the decision message currently being answered, sends a
    /// "stale_state" error and returns false. If the field is absent,
    /// executes as before (backward compatible) and returns true.
    /// </summary>
    public static bool CheckStateId(JsonElement root, long expectedStateId, RunState? runState, Player? player)
    {
        if (root.ValueKind == JsonValueKind.Object &&
            root.TryGetProperty("state_id", out JsonElement sidProp) &&
            sidProp.ValueKind == JsonValueKind.Number &&
            sidProp.TryGetInt64(out long gotStateId) &&
            gotStateId != expectedStateId)
        {
            SendError("stale_state",
                $"state_id {gotStateId} does not match current state_id {expectedStateId}",
                runState, player);
            return false;
        }
        return true;
    }

    /// <summary>
    /// The common "choose"/"skip" index reader shared by every non-combat
    /// room/screen handler, now folding in the state_id guard and
    /// structured-error emission. Returns null if the response should be
    /// treated as "no valid choice, ask again" (error already sent to the
    /// client in every such case -- malformed JSON, stale state_id, unknown
    /// action, or "choose" with no/invalid "index").
    /// Skip resolves to -1 (callers interpret -1 as "skip"/"leave" per their
    /// own convention, matching the pre-phase-1b behavior).
    /// </summary>
    public static int? ReadChoiceIndex(
        string responseJson, long expectedStateId, RunState? runState, Player? player,
        bool skipIsValid = false)
    {
        if (!TryParse(responseJson, runState, player, out JsonElement root))
            return null;

        if (!CheckStateId(root, expectedStateId, runState, player))
            return null;

        // A non-object root or a non-string "action" is an unknown action,
        // not an exception out of the handler's ask loop.
        string action = root.ValueKind == JsonValueKind.Object &&
                        root.TryGetProperty("action", out JsonElement actionProp) &&
                        actionProp.ValueKind == JsonValueKind.String
            ? actionProp.GetString() ?? ""
            : "";

        if (action == NonCombatBridgeProtocol.ChooseAction)
        {
            if (root.TryGetProperty("index", out JsonElement indexProp) &&
                indexProp.ValueKind == JsonValueKind.Number && indexProp.TryGetInt32(out int idx))
            {
                return idx;
            }
            SendError("malformed_action", "\"choose\" requires an integer \"index\"", runState, player);
            return null;
        }

        if (action == NonCombatBridgeProtocol.SkipAction && skipIsValid)
        {
            return -1;
        }

        SendError("unknown_action", $"Unrecognized action \"{action}\" for this decision", runState, player);
        return null;
    }
}
