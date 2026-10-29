// SimVfxCap.cs -- bound the combat VFX containers so a leaked effect node
// cannot wedge the game.
//
// The failure this exists for, seen live on 2026-08-27 (runs 004 and 007 of
// logs/barrier-test2, both abandoned after four attempts):
//
//   Creating NCombatRoom with mode=VisualOnly encounter=PUNCH_OFF_EVENT_ENCOUNTER
//   [WARN] Asset not cached: res://scenes/creature_visuals/punch_construct.tscn
//   ERROR: Element limit reached.
//   ERROR: Parameter "mem" is null.
//   ERROR: Parameter "particles" is null.      <- then this, ~70,000 times a second
//
// PunchOff stages two Punch Constructs hitting each other in an UNBOUNDED
// loop (PunchOff.PunchEachOther), adding a fresh NHitSparkVfx child to
// NCombatRoom.CombatVfxContainer on every swing and stopping only when the
// player picks an option. Each of those nodes frees ITSELF, but only after
// awaiting its particle system's Finished signal (NHitSparkVfx.cs:100). Once
// the renderer refuses to hand out another element, `_specks` is null, that
// signal never arrives, and the node never frees. Every stuck node then errors
// once per frame, so the cost is quadratic in how long we stand there: a few
// hundred nodes at 60 fps is the 250 MB log our driver reads as a stall.
//
// It is a game-side leak, but WE make it fatal: SimRuntime.WaitMultiplier is
// 0.1 and MainFile's WaitSpeedPatch feeds it into Cmd.Wait, so the punch cycle
// runs at ~0.26 s instead of ~2.6 s -- ten times the spawn rate, into a
// container nothing empties. Same lesson as the 2026-08-10 speed patches: the
// delays we removed were doing work.
//
// The cap is deliberately a SAFETY NET rather than a fix for PunchOff
// specifically. Any unbounded VFX loop has this shape, and a cap costs one
// child count twice a second; special-casing one event would leave the next
// one to be found by another wedged overnight batch.
//
// Only effects that have sat in the container for MinAgeSeconds are freed.
// The first version trimmed oldest-first on the theory that whatever survives
// at the front has stopped animating. It has not: at autopilot speed a normal
// combat holds 65-83 children, and among them are live effects another node
// still holds a reference to. NCardFlyShuffleVfx keeps its NCardTrailVfx and
// calls FadeOut() on it when the card lands; freeing the trail first made that
// throw ObjectDisposedException (24 times in 14 of 26 live runs, 2026-10-12;
// none in the 82 runs before the cap existed). A leaked effect stays forever,
// so it still crosses the age bar within seconds; a live one never does.

using System;
using System.Collections.Generic;
using System.Linq;
using System.Threading;
using System.Threading.Tasks;
using Godot;
using MegaCrit.Sts2.Core.AutoSlay.Helpers;
using MegaCrit.Sts2.Core.Helpers;
using MegaCrit.Sts2.Core.Nodes;
using MegaCrit.Sts2.Core.Nodes.Rooms;

namespace SpireMentor;

internal static class SimVfxCap
{
    /// <summary>
    /// How many children either combat VFX container may hold before the
    /// oldest are freed.
    ///
    /// Meant as a backstop, not a budget -- but at autopilot speed an honest
    /// combat does exceed it (65-83 children seen live), which is why freeing
    /// is also gated on age (MinAgeSeconds).
    /// </summary>
    private const int MaxVfxChildren = 64;

    /// <summary>Poll period. The leak needs seconds of standing still to
    /// matter, and the check is one GetChildCount per container.</summary>
    private const double PollSeconds = 0.5;

    /// <summary>
    /// How long a child must have been in the container before it may be
    /// freed. Card trails and hit sparks finish in about a second; a leaked
    /// node never does. Five seconds is far past the one and well short of
    /// mattering for the other (PunchOff leaks ~8 nodes a second, so the
    /// container peaks near cap + 40 before the age bar lets trimming start).
    /// </summary>
    private const double MinAgeSeconds = 5.0;

    /// <summary>Per container: first time each child was seen, by instance
    /// id. Rebuilt every tick from the current children, so entries for freed
    /// nodes drop out on their own.</summary>
    private static readonly Dictionary<string, Dictionary<ulong, double>> _firstSeen = new();

    private static int _trimTicks;

    /// <summary>Census: effects added to the combat VFX containers this
    /// session, by type. Counted on ChildEnteredTree, so even effects that
    /// live for a frame are seen; logged every CensusSeconds.</summary>
    private static readonly Dictionary<string, int> _added = new();
    private static int _addedSinceLog;
    private static NCombatRoom? _censusRoom;
    private static double _lastCensusLog;
    private const double CensusSeconds = 60.0;
    private static bool _installed;

    /// <summary>
    /// Wait for the scene tree, then install.
    ///
    /// The first version of this called Install() straight from MainFile and
    /// silently did nothing: at mod-init time Engine.GetMainLoop() is not yet
    /// a SceneTree, and the guard below returned without a word. The cap was
    /// absent for a whole verification run and the log looked exactly as it
    /// does when the cap is present and simply never needed -- the same
    /// "silence reads as success" trap the settle barrier's missing log line
    /// set. Hence both the when-ready wait and the noisy guards.
    /// </summary>
    public static async Task InstallWhenReadyAsync()
    {
        try
        {
            var cts = new CancellationTokenSource(TimeSpan.FromSeconds(120));
            await WaitHelper.Until(
                () => NGame.Instance != null && Engine.GetMainLoop() is SceneTree,
                cts.Token, TimeSpan.FromSeconds(120), "scene tree not available");
            Callable.From(Install).CallDeferred();
        }
        catch (Exception ex)
        {
            Logger.Log($"[SimVfxCap] NOT INSTALLED, the VFX cap is off: {ex.Message}");
        }
    }

    public static void Install()
    {
        if (_installed)
        {
            return;
        }
        try
        {
            if (Engine.GetMainLoop() is not SceneTree tree)
            {
                Logger.Log("[SimVfxCap] NOT INSTALLED, the VFX cap is off: no scene tree.");
                return;
            }
            var timer = new Godot.Timer
            {
                Name = "SpireMentorVfxCap",
                WaitTime = PollSeconds,
                Autostart = true,
                OneShot = false,
            };
            timer.Connect(Godot.Timer.SignalName.Timeout, Callable.From(Tick));
            tree.Root.AddChildSafely(timer);
            _installed = true;
            Logger.Log(
                $"[SimVfxCap] armed: combat VFX containers capped at {MaxVfxChildren} children.");
        }
        catch (Exception ex)
        {
            Logger.Log($"[SimVfxCap] NOT INSTALLED, the VFX cap is off: {ex.Message}");
        }
    }

    private static void Tick()
    {
        try
        {
            NCombatRoom? room = NCombatRoom.Instance;
            if (room == null || !GodotObject.IsInstanceValid(room))
            {
                return;
            }
            WatchForCensus(room);
            Trim(room.CombatVfxContainer, "CombatVfxContainer");
            Trim(room.BackCombatVfxContainer, "BackCombatVfxContainer");
            LogCensusIfDue();
        }
        catch (Exception ex)
        {
            // Never break gameplay over a safety net.
            Logger.Log($"[SimVfxCap] tick: {ex.Message}");
        }
    }

    private static void Trim(Control? container, string which)
    {
        if (container == null || !GodotObject.IsInstanceValid(container))
        {
            return;
        }
        double now = Time.GetTicksMsec() / 1000.0;
        Godot.Collections.Array<Node> children = container.GetChildren();
        // Ages are tracked for every child, over the cap or not, so a node is
        // already old by the time the container fills up.
        _firstSeen.TryGetValue(which, out Dictionary<ulong, double>? previous);
        var seen = new Dictionary<ulong, double>(children.Count);
        foreach (Node child in children)
        {
            if (child == null || !GodotObject.IsInstanceValid(child))
            {
                continue;
            }
            ulong id = child.GetInstanceId();
            seen[id] = previous != null && previous.TryGetValue(id, out double t) ? t : now;
        }
        _firstSeen[which] = seen;

        int excess = children.Count - MaxVfxChildren;
        if (excess <= 0)
        {
            return;
        }
        // Snapshot the children rather than repeatedly reading index 0: a
        // freed node is not detached until the end of the frame, so walking
        // the live list would hand back the same node every iteration.
        int freed = 0;
        var freedTypes = new Dictionary<string, int>();
        foreach (Node child in children)
        {
            if (freed >= excess)
            {
                break;
            }
            if (child == null || !GodotObject.IsInstanceValid(child) ||
                child.IsQueuedForDeletion())
            {
                continue;
            }
            if (!seen.TryGetValue(child.GetInstanceId(), out double since) || now - since < MinAgeSeconds)
            {
                continue; // still animating, or might be: never free a live effect
            }
            // Pooled nodes (cards flying through the container) are reused
            // under the same instance id, so one that left and came back
            // between two ticks would look old. Leave them alone; the leak
            // this cap exists for is effects, which are not pooled.
            if (IsPooled(child))
            {
                continue;
            }
            string type = child.GetType().Name;
            freedTypes[type] = freedTypes.TryGetValue(type, out int c) ? c + 1 : 1;
            // QueueFreeSafely, never QueueFree: it detaches the node itself,
            // so there is no RemoveChild here to race with the effect's own
            // pending await.
            child.QueueFreeSafely();
            freed++;
        }
        if (freed == 0)
        {
            return;
        }
        // Every trim for the first 20, then one in 50: with the age bar a
        // trim means a real leak, and a leak that lasts is worth one line
        // every ~25 s, not two a second.
        if (_trimTicks < 20 || _trimTicks % 50 == 0)
        {
            string types = string.Join(", ", freedTypes.OrderByDescending(kv => kv.Value)
                .Select(kv => $"{kv.Key} x{kv.Value}"));
            Logger.Log(
                $"[SimVfxCap] {which} held {children.Count} children; freed {freed} " +
                $"older than {MinAgeSeconds:0}s (cap {MaxVfxChildren}): {types}");
        }
        _trimTicks++;
    }

    /// <summary>
    /// Whether the node implements the game's IPoolable (only NCard and
    /// NGridCardHolder do, v0.107.1). Checked by name to keep this file free of
    /// one more game-namespace import.
    /// </summary>
    private static bool IsPooled(Node node) => node.GetType().GetInterface("IPoolable") != null;

    /// <summary>Subscribe to a new combat room's two VFX containers. Each
    /// combat builds a fresh NCombatRoom, and its signals go with it.</summary>
    private static void WatchForCensus(NCombatRoom room)
    {
        if (ReferenceEquals(room, _censusRoom))
        {
            return;
        }
        _censusRoom = room;
        foreach (Control? container in new[] { room.CombatVfxContainer, room.BackCombatVfxContainer })
        {
            if (container != null && GodotObject.IsInstanceValid(container))
            {
                container.Connect(Node.SignalName.ChildEnteredTree, Callable.From<Node>(CountAdded));
            }
        }
    }

    private static void CountAdded(Node child)
    {
        string type = child.GetType().Name;
        _added[type] = _added.TryGetValue(type, out int c) ? c + 1 : 1;
        _addedSinceLog++;
    }

    private static void LogCensusIfDue()
    {
        double now = Time.GetTicksMsec() / 1000.0;
        if (_addedSinceLog == 0 || now - _lastCensusLog < CensusSeconds)
        {
            return;
        }
        _lastCensusLog = now;
        _addedSinceLog = 0;
        int total = _added.Values.Sum();
        string top = string.Join(", ", _added.OrderByDescending(kv => kv.Value).Take(12)
            .Select(kv => $"{kv.Key} {kv.Value}"));
        Logger.Log($"[SimVfxCap] census: {total} effects added to the combat VFX containers so far: {top}");
    }
}

/// <summary>
/// "Reduce combat effects" (SpireMentorModConfig.ReduceEffects): skip effects
/// that are pure decoration. Measured on a live A8 run (2026-10-12, census
/// above): of 6201 effects added to the combat VFX containers, card trails
/// were 1288 and the four buff/debuff flashes 702 -- a third of the clutter.
///
/// Each listed effect's static Create already returns null in the game's
/// test mode, and every caller checks for that (v0.107.1: NCardFlyVfx,
/// NCardFlyShuffleVfx, NCardFlyPowerVfx test `_vfx != null`; NCreature's
/// power effects test the result or hand it to AddChildSafely, which ignores
/// null). So skipping is the game's own path, not a new one. Left out on
/// purpose: NCardFlyShuffleVfx (calls the pile's InvokeCardAddFinished when it
/// lands), NCardFlyVfx (moves the real card), NDamageNumVfx (information),
/// NHitSparkVfx (no null path).
/// </summary>
internal static class SimReduceEffects
{
    private static readonly string[] Decorative =
    {
        "NCardTrailVfx",
        "NPowerAppliedVfx",
        "NPowerAppliedBuffVfx",
        "NPowerAppliedDebuffVfx",
        "NPowerFlashVfx",
    };

    public static void Install(HarmonyLib.Harmony harmony)
    {
        System.Reflection.Assembly game = typeof(NCombatRoom).Assembly;
        foreach (string name in Decorative)
        {
            try
            {
                Type type = game.GetTypes().Single(t => t.Name == name);
                var creates = type.GetMethods(System.Reflection.BindingFlags.Public | System.Reflection.BindingFlags.Static)
                    .Where(m => m.Name == "Create" && m.ReturnType == type)
                    .ToList();
                if (creates.Count == 0)
                {
                    Logger.Log($"  patch skipped: SimReduceEffects {name} (no Create)");
                    continue;
                }
                var prefix = new HarmonyLib.HarmonyMethod(
                    typeof(SimReduceEffects).GetMethod(nameof(SkipCreate))!.MakeGenericMethod(type));
                foreach (var create in creates)
                {
                    harmony.Patch(create, prefix: prefix);
                }
            }
            catch (Exception ex)
            {
                Logger.Log($"  patch skipped: SimReduceEffects {name} ({ex.Message})");
            }
        }
        Logger.Log($"  patch applied: SimReduceEffects ({Decorative.Length} effects, on when the setting is)");
    }

    /// <summary>Harmony prefix: with the setting on, return null without
    /// instantiating anything (a postfix would build the node and leak it).
    /// </summary>
    public static bool SkipCreate<T>(ref T? __result) where T : class
    {
        if (!SpireMentorModConfig.ReduceEffects)
        {
            return true;
        }
        __result = null;
        return false;
    }
}
