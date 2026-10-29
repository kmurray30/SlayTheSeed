// ChoiceInference.cs -- which option did the human pick? Decided from a
// before/after diff of plain strings (card / relic / potion ids, map coords).
//
// Deliberately free of game types so tests/unit can compile it on its own;
// SimChoiceCapture reads the game and feeds these.
//
// Return convention of the *Pick functions (PROTOCOL.md section 5, v5
// `human_action.choice`):
//   n >= 0   the option index, in the same order the options were sent
//   null     a skip (nothing was taken) -- only where a skip exists
//   Unknown  (-1) the diff does not name exactly one option: send NOTHING.
//            A guessed index would grade the player on a move they never
//            made; silence is always the safe answer.

using System;
using System.Collections.Generic;
using System.Linq;

namespace SpireMentor;

internal static class ChoiceInference
{
    /// <summary>The diff does not name exactly one option; report nothing.</summary>
    public const int Unknown = -1;

    /// <summary>
    /// The map node the player walked to: the offered option whose coordinate
    /// is the one newly visited. The map has no skip, so a missing or
    /// unoffered coordinate is <see cref="Unknown"/>.
    /// </summary>
    public static int? MapPick(IReadOnlyList<(int col, int row)> options, (int col, int row)? newlyVisited)
    {
        if (newlyVisited is not (int col, int row))
        {
            return Unknown;
        }
        for (int i = 0; i < options.Count; i++)
        {
            if (options[i].col == col && options[i].row == row)
            {
                return i;
            }
        }
        return Unknown;
    }

    /// <summary>The one coordinate in <paramref name="after"/> that is not in
    /// <paramref name="before"/>, or null when there are none or several.</summary>
    public static (int col, int row)? NewlyVisited(
        IReadOnlyList<(int col, int row)> before, IReadOnlyList<(int col, int row)> after)
    {
        List<(int col, int row)> added = MultisetAdded(before, after);
        return added.Count == 1 ? added[0] : null;
    }

    /// <summary>
    /// A card reward: which offered card joined the deck. Count-based, so a
    /// copy of a card the deck already holds still shows up. Nothing added is
    /// a skip; anything else that does not name exactly one offered card is
    /// <see cref="Unknown"/>. Two offers with the same label resolve to the
    /// first, the same rule as <see cref="IndexByLabel"/>.
    /// </summary>
    public static int? AddedCardPick(
        IReadOnlyList<string> optionLabels, IReadOnlyList<string> deckBefore, IReadOnlyList<string> deckAfter) =>
        AddedPick(optionLabels, deckBefore, deckAfter);

    /// <summary>A relic choice (boss relic, chest): same rule as
    /// <see cref="AddedCardPick"/>, on relic ids.</summary>
    public static int? AddedRelicPick(
        IReadOnlyList<string> optionLabels, IReadOnlyList<string> relicsBefore, IReadOnlyList<string> relicsAfter) =>
        AddedPick(optionLabels, relicsBefore, relicsAfter);

    /// <summary>
    /// The option carrying <paramref name="label"/>: trimmed, case-insensitive,
    /// first match wins -- the same rule as <c>SimAdvisor.ReconcileIndex</c>.
    /// Null when no option carries it.
    /// </summary>
    public static int? IndexByLabel(IReadOnlyList<string> optionLabels, string label)
    {
        string want = (label ?? "").Trim();
        for (int i = 0; i < optionLabels.Count; i++)
        {
            if (string.Equals((optionLabels[i] ?? "").Trim(), want, StringComparison.OrdinalIgnoreCase))
            {
                return i;
            }
        }
        return null;
    }

    /// <summary>
    /// A card bundle: the one bundle whose cards (as a multiset) all joined
    /// the deck. A bundle screen has no skip, so nothing added is
    /// <see cref="Unknown"/>, and so is a diff that two bundles both fit.
    /// </summary>
    public static int? BundlePick(
        IReadOnlyList<IReadOnlyList<string>> bundles, IReadOnlyList<string> deckBefore, IReadOnlyList<string> deckAfter)
    {
        List<string> added = MultisetAdded(deckBefore, deckAfter);
        if (added.Count == 0)
        {
            return Unknown;
        }
        int found = Unknown;
        for (int i = 0; i < bundles.Count; i++)
        {
            if (bundles[i].Count == 0 || !IsSubMultiset(bundles[i], added))
            {
                continue;
            }
            if (found != Unknown)
            {
                return Unknown;
            }
            found = i;
        }
        return found;
    }

    /// <summary>
    /// One shop purchase: options are (action, id) in the order sent
    /// (<c>buy_card</c> / <c>buy_relic</c> / <c>buy_potion</c> carry the
    /// item's id, <c>remove_card</c> the removal). Exactly one kind of change
    /// must have happened and it must name exactly one option; leaving the
    /// shop is not a purchase, so there is no skip here.
    /// </summary>
    public static int? ShopPick(
        IReadOnlyList<(string action, string id)> options,
        IReadOnlyList<string> deckBefore, IReadOnlyList<string> deckAfter,
        IReadOnlyList<string> relicsBefore, IReadOnlyList<string> relicsAfter,
        IReadOnlyList<string> potionsBefore, IReadOnlyList<string> potionsAfter)
    {
        List<string> cardsAdded = MultisetAdded(deckBefore, deckAfter);
        List<string> cardsRemoved = MultisetAdded(deckAfter, deckBefore);
        List<string> relicsAdded = MultisetAdded(relicsBefore, relicsAfter);
        List<string> potionsAdded = MultisetAdded(potionsBefore, potionsAfter);

        int kinds = (cardsAdded.Count > 0 ? 1 : 0) + (relicsAdded.Count > 0 ? 1 : 0) +
                    (potionsAdded.Count > 0 ? 1 : 0) +
                    (cardsRemoved.Count > 0 && cardsAdded.Count == 0 ? 1 : 0);
        if (kinds != 1)
        {
            return Unknown;
        }
        if (cardsAdded.Count == 1)
        {
            return OnlyOption(options, NonCombatAction.BuyCard, cardsAdded[0]);
        }
        if (relicsAdded.Count == 1)
        {
            return OnlyOption(options, NonCombatAction.BuyRelic, relicsAdded[0]);
        }
        if (potionsAdded.Count == 1)
        {
            return OnlyOption(options, NonCombatAction.BuyPotion, potionsAdded[0]);
        }
        if (cardsRemoved.Count == 1 && cardsAdded.Count == 0)
        {
            return OnlyOption(options, NonCombatAction.RemoveCard, null);
        }
        return Unknown;
    }

    /// <summary>The index of the single <c>true</c>, else <see cref="Unknown"/>
    /// (a deck screen where exactly one offered card was removed / upgraded /
    /// transformed).</summary>
    public static int? SingleChanged(IReadOnlyList<bool> changed)
    {
        int found = Unknown;
        for (int i = 0; i < changed.Count; i++)
        {
            if (!changed[i])
            {
                continue;
            }
            if (found != Unknown)
            {
                return Unknown;
            }
            found = i;
        }
        return found;
    }

    // ------------------------------------------------------------------

    /// <summary>Wire spellings of the shop's per-option actions
    /// (NonCombatBridgeProtocol), repeated here so this file stays game-free.</summary>
    private static class NonCombatAction
    {
        public const string BuyCard = "buy_card";
        public const string BuyRelic = "buy_relic";
        public const string BuyPotion = "buy_potion";
        public const string RemoveCard = "remove_card";
    }

    private static int? AddedPick(
        IReadOnlyList<string> optionLabels, IReadOnlyList<string> before, IReadOnlyList<string> after)
    {
        List<string> added = MultisetAdded(before, after);
        if (added.Count == 0)
        {
            // Nothing joined but something may have LEFT: that is not a skip.
            return MultisetAdded(after, before).Count == 0 ? null : Unknown;
        }
        int? found = null;
        foreach (string a in added.Distinct(StringComparer.OrdinalIgnoreCase))
        {
            int? idx = IndexByLabel(optionLabels, a);
            if (idx == null)
            {
                continue; // joined for another reason (a curse, a relic's card)
            }
            if (found != null)
            {
                return Unknown;
            }
            found = idx;
        }
        return found ?? Unknown;
    }

    private static int OnlyOption(IReadOnlyList<(string action, string id)> options, string action, string? id)
    {
        int found = Unknown;
        for (int i = 0; i < options.Count; i++)
        {
            if (!string.Equals(options[i].action, action, StringComparison.OrdinalIgnoreCase))
            {
                continue;
            }
            if (id != null && !string.Equals(options[i].id, id, StringComparison.OrdinalIgnoreCase))
            {
                continue;
            }
            if (found != Unknown)
            {
                return Unknown;
            }
            found = i;
        }
        return found;
    }

    /// <summary><paramref name="after"/> minus <paramref name="before"/>, as
    /// multisets.</summary>
    private static List<T> MultisetAdded<T>(IReadOnlyList<T> before, IReadOnlyList<T> after) where T : notnull
    {
        var counts = new Dictionary<T, int>();
        foreach (T b in before)
        {
            counts[b] = counts.TryGetValue(b, out int n) ? n + 1 : 1;
        }
        var added = new List<T>();
        foreach (T a in after)
        {
            if (counts.TryGetValue(a, out int n) && n > 0)
            {
                counts[a] = n - 1;
            }
            else
            {
                added.Add(a);
            }
        }
        return added;
    }

    private static bool IsSubMultiset(IReadOnlyList<string> part, IReadOnlyList<string> whole) =>
        MultisetAdded(whole, part).Count == 0;
}
