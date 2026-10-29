// WorkbenchUrl.cs -- the "open in workbench" URL for one decision.
// Game-free so tests/unit can pin it; SimWorkbenchLink probes and opens it.
//
// The browser workbench's follow handler reads `follow=1`, `run=<log name>`
// and `step=<replay step index>` (sts2-pycli/web/src/followUrl.ts). A coach
// record carries `log_name` / `log_idx` (PROTOCOL.md section 5, v5.1).

using System;
using System.Collections.Generic;
using System.Globalization;

namespace SpireMentor;

internal static class WorkbenchUrl
{
    /// <summary>
    /// <paramref name="baseUrl"/> with <c>run</c> / <c>step</c> appended (with
    /// <c>&amp;</c> when it already has a query, else <c>?</c>). The workbench
    /// reads them only in follow mode, so <c>follow=1</c> is added too when a
    /// parameter is given and the URL does not say it already. No parameters:
    /// the URL unchanged.
    /// </summary>
    public static string WithFollowParams(string baseUrl, string? run, int? step)
    {
        var add = new List<string>();
        if (!string.IsNullOrEmpty(run) || step != null)
        {
            if (!HasParam(baseUrl, "follow"))
            {
                add.Add("follow=1");
            }
            if (!string.IsNullOrEmpty(run))
            {
                add.Add("run=" + Uri.EscapeDataString(run));
            }
            if (step is int s)
            {
                add.Add("step=" + s.ToString(CultureInfo.InvariantCulture));
            }
        }
        if (add.Count == 0)
        {
            return baseUrl;
        }
        string fragment = "";
        int hash = baseUrl.IndexOf('#');
        if (hash >= 0)
        {
            fragment = baseUrl.Substring(hash);
            baseUrl = baseUrl.Substring(0, hash);
        }
        char sep = baseUrl.Contains('?') ? (baseUrl.EndsWith("?") || baseUrl.EndsWith("&") ? '\0' : '&') : '?';
        return baseUrl + (sep == '\0' ? "" : sep.ToString()) + string.Join("&", add) + fragment;
    }

    private static bool HasParam(string url, string name)
    {
        int q = url.IndexOf('?');
        if (q < 0)
        {
            return false;
        }
        int hash = url.IndexOf('#', q);
        string query = hash >= 0 ? url.Substring(q + 1, hash - q - 1) : url.Substring(q + 1);
        foreach (string part in query.Split('&'))
        {
            string key = part.Split('=')[0];
            if (string.Equals(key, name, StringComparison.Ordinal))
            {
                return true;
            }
        }
        return false;
    }
}
