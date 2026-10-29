// SimThinkFps.cs -- drop the game's frame rate while an unattended agent
// thinks.
//
// Why: the searches that answer live-run decisions execute in the advisor /
// agent PROCESS, on this machine, while the game idles at a decision point
// rendering a static board at full frame rate. On the thermally-limited
// host that rendering costs the search ~5x single-threaded throughput
// (measured 2026-07: 4000-sim boss searches at 9-22 s live vs 0.66 s with
// the game closed) -- the single biggest term in "boss fights take
// forever". While the game is only WAITING for an answer nothing on screen
// matters, so cap Engine.MaxFps until the answer arrives.
//
// The game itself does exactly this when backgrounded
// (NBackgroundModeHandler: Engine.MaxFps = 30 on FocusOut), so a low cap is
// a game-sanctioned state; game-time progression is frame-delta based and
// unaffected in wall-clock terms.
//
// Scope: engaged ONLY from unattended contexts (autopilot's SimAgentGate
// wait, and the advisor lane's in-flight advice window when
// `advisor_autoplay` is set). A human's session never enters here.
//
// Threading: Begin/End may be called from any thread (SimAgentGate runs on
// worker threads); the actual Engine.MaxFps writes are marshalled to the
// main thread. The nesting count is only touched there, so no locks.

using System;
using Godot;

namespace SpireMentor;

internal static class SimThinkFps
{
    /// <summary>Nested Begin() calls currently active (main thread only).</summary>
    private static int _depth;

    /// <summary>MaxFps to restore when the last End() lands. Captured by the
    /// first Begin(); 0 = Godot's "uncapped".</summary>
    private static int _savedMaxFps;

    /// <summary>Cap the frame rate until the matching <see cref="End"/>.
    /// No-op when `think_fps` is 0 in SpireMentor.conf.</summary>
    public static void Begin()
    {
        int cap = SimConfig.Instance.ThinkFps;
        if (cap <= 0)
        {
            return;
        }
        Callable.From(() =>
        {
            _depth++;
            if (_depth == 1)
            {
                _savedMaxFps = Engine.MaxFps;
                Engine.MaxFps = cap;
            }
        }).CallDeferred();
    }

    /// <summary>Undo one <see cref="Begin"/>; restores the pre-cap MaxFps
    /// when the last nested think ends.</summary>
    public static void End()
    {
        if (SimConfig.Instance.ThinkFps <= 0)
        {
            return;
        }
        Callable.From(() =>
        {
            if (_depth == 0)
            {
                return; // unmatched End (e.g. config flipped mid-run): ignore
            }
            _depth--;
            if (_depth == 0)
            {
                Engine.MaxFps = _savedMaxFps;
            }
        }).CallDeferred();
    }
}
