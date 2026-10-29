// GameVersionFormat.cs -- the wire spelling of a game build ("v0.111.0").
//
// Deliberately free of game types so tests/unit can compile it on its own.

namespace SpireMentor;

internal static class GameVersionFormat
{
    /// <summary>
    /// The build the advisor assumes when the envelope carries no
    /// <c>game_version</c> (every mod before the field). PROTOCOL.md section 1.
    /// </summary>
    public const string Default = "v0.107.1";

    /// <summary>
    /// <c>ReleaseInfo.Version</c> (release_info.json <c>"version"</c>, e.g.
    /// <c>"v0.111.0"</c>) in the wire form: a lowercase <c>v</c> and three
    /// dot-separated integers without leading zeros, so <c>"0.111.0"</c>,
    /// <c>" V0.111.0 "</c> and <c>"v0.111.0"</c> all become <c>"v0.111.0"</c>.
    /// Null for null or blank input. Any other shape (a suffix, a fourth
    /// component) is passed through trimmed and <c>v</c>-prefixed rather than
    /// guessed at, so the advisor can name the exact build it refuses.
    /// </summary>
    public static string? Normalize(string? raw)
    {
        if (string.IsNullOrWhiteSpace(raw))
        {
            return null;
        }
        string v = raw.Trim();
        if (v[0] == 'v' || v[0] == 'V')
        {
            v = v.Substring(1);
        }
        string[] parts = v.Split('.');
        if (parts.Length == 3)
        {
            var nums = new long[3];
            bool ok = true;
            for (int i = 0; i < 3 && ok; i++)
            {
                ok = parts[i].Length > 0
                    && parts[i].Length <= 9
                    && parts[i].All(char.IsAsciiDigit)
                    && long.TryParse(parts[i], out nums[i]);
            }
            if (ok)
            {
                return $"v{nums[0]}.{nums[1]}.{nums[2]}";
            }
        }
        return "v" + v;
    }
}
