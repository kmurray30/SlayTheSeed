// SimCoachReports -- the two v5.1 / coach-2 reports the mod sends the advisor
// in Advisor mode: `combat_ended` (triggers the fight recap) and `game_over`
// (triggers the death autopsy). PROTOCOL.md section 3.
//
// Game-free on purpose: callers pass plain values and add the envelope
// themselves (SimStateExtractor.ApplyEnvelope), so this file links into the
// unit tests and the once-per-run latch is tested without a game.

using System.Collections.Generic;
using System.Runtime.CompilerServices;

namespace SpireMentor;

internal static class SimCoachReports
{
    /// <summary>The advisor capability both reports need (coach 2).</summary>
    public const int RequiredCoachCapability = 2;

    // The run the last game_over was sent for. Weak, so a finished run's
    // state can still be collected; a new run is a new object, which is what
    // resets the latch (no run-start hook needed).
    private static readonly ConditionalWeakTable<object, object> GameOverSent = new();
    private static readonly object Gate = new();

    /// <summary>`combat_ended` payload, without the envelope.</summary>
    public static Dictionary<string, object> CombatEnded(
        bool victory, int round, int hp, int maxHp, string roomType) => new()
    {
        ["type"] = "combat_ended",
        ["victory"] = victory,
        ["round"] = round,
        ["hp"] = hp,
        ["max_hp"] = maxHp,
        ["room_type"] = roomType,
    };

    /// <summary>`game_over` payload, without the envelope. Same shape the
    /// autopilot has always sent, plus the v5.1 `victory` bool.</summary>
    public static Dictionary<string, object> GameOver(bool victory) => new()
    {
        ["type"] = "game_over",
        ["result"] = victory ? "victory" : "defeat",
        ["message"] = "Game over",
        ["victory"] = victory,
    };

    /// <summary>Claims the one `game_over` of <paramref name="run"/>: true the
    /// first time per run object, false afterwards (and for a null run, which
    /// cannot be told apart from the next one). Shared by the Advisor-mode
    /// report and the autopilot, so a mode change on the game-over screen
    /// never sends it twice.</summary>
    public static bool TryClaimGameOver(object? run)
    {
        if (run == null)
        {
            return false;
        }
        lock (Gate)
        {
            if (GameOverSent.TryGetValue(run, out _))
            {
                return false;
            }
            GameOverSent.Add(run, Gate);
            return true;
        }
    }
}
