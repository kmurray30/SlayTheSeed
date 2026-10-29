// SimTopBarLayout.cs -- shared "stay clear of the game's top bar" probe.
//
// Extracted from SimHud.RepositionPanels/TopBar so the toolbar and the HUD
// agree on where the safe top edge is. Measured from the portrait/HP/gold
// widgets, NOT from NTopBar itself: the bar node is anchored full-rect (its
// own GetGlobalRect is the whole viewport), so clearing *it* threw the chip
// off the bottom of the screen. Only ever pushes DOWN: on a screen with no
// run in progress there is no bar and callers keep their corner.

using System;
using Godot;
using MegaCrit.Sts2.Core.AutoSlay.Helpers;
using MegaCrit.Sts2.Core.Nodes;
using MegaCrit.Sts2.Core.Nodes.CommonUi;
using MegaCrit.Sts2.Core.Nodes.Relics;

namespace SpireMentor;

internal static class SimTopBarLayout
{
    /// <summary>Y when no top bar is on screen (main menu, character select).</summary>
    public const float FallbackTop = 16f;

    /// <summary>Breathing room between the game's top bar and our panels.</summary>
    public const float TopBarClearance = 12f;

    private static NTopBar? _topBar;
    private static int _topBarSearchCooldown;

    /// <summary>
    /// The lowest safe Y below the top-bar widgets, clamped to the top
    /// quarter of the screen (whatever the layout does, panels stay in the
    /// top quarter rather than sliding off it). <paramref name="viewportSource"/>
    /// is any control in the tree, used only to read the viewport size.
    /// </summary>
    public static float SafeTopY(Control viewportSource)
    {
        float top = FallbackTop;
        try
        {
            NTopBar? bar = TopBar();
            if (bar != null && bar.IsVisibleInTree())
            {
                foreach (Control? widget in new Control?[] { bar.Portrait, bar.Hp, bar.Gold })
                {
                    if (widget == null || !GodotObject.IsInstanceValid(widget) ||
                        !widget.IsVisibleInTree())
                    {
                        continue;
                    }
                    Rect2 rect = widget.GetGlobalRect();
                    top = Math.Max(top, rect.Position.Y + rect.Size.Y + TopBarClearance);
                }
                // The relic tray hangs BELOW the top bar on the left -- exactly
                // where "under the bar widgets" is. Clear its lowest holder too
                // (it wraps to more rows as relics accumulate), same idiom the
                // game itself uses (NTopBar.cs: GlobalUi.RelicInventory.RelicNodes).
                NRelicInventory? relics = NRun.Instance?.GlobalUi?.RelicInventory;
                if (relics != null && GodotObject.IsInstanceValid(relics) &&
                    relics.IsVisibleInTree())
                {
                    foreach (NRelicInventoryHolder holder in relics.RelicNodes)
                    {
                        if (holder == null || !GodotObject.IsInstanceValid(holder) ||
                            !((Control)holder).IsVisibleInTree())
                        {
                            continue;
                        }
                        Rect2 rect = ((Control)holder).GetGlobalRect();
                        top = Math.Max(top, rect.Position.Y + rect.Size.Y + TopBarClearance);
                    }
                }
                // Relic rows can legitimately push past the old quarter-screen
                // clamp late in a run; allow up to a third before giving up.
                float ceiling = viewportSource.GetViewportRect().Size.Y * 0.33f;
                top = Math.Min(top, Math.Max(FallbackTop, ceiling));
            }
        }
        catch (Exception ex)
        {
            Logger.Log($"[SimTopBarLayout] SafeTopY failed: {ex.Message}");
        }
        return top;
    }

    /// <summary>
    /// The run's top bar, cached. There is no NTopBar.Instance, so this
    /// falls back to a whole-tree walk -- throttled to once a second, because
    /// outside a run the walk would otherwise run 20x/s and never find one.
    /// </summary>
    public static NTopBar? TopBar()
    {
        if (_topBar != null && GodotObject.IsInstanceValid(_topBar) && _topBar.IsInsideTree())
        {
            return _topBar;
        }
        if (--_topBarSearchCooldown > 0)
        {
            return null;
        }
        _topBarSearchCooldown = 20;
        _topBar = Engine.GetMainLoop() is SceneTree tree
            ? UiHelper.FindFirst<NTopBar>(tree.Root)
            : null;
        return _topBar;
    }
}
