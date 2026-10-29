// Infra.cs -- transcript writer, reflective BridgeServer facade, scripted TCP
// client, async-call probes, Harmony hooks for game-side calls.
//
// Determinism rules (see README):
//  * Nothing background-threaded writes to the transcript directly. Socket
//    reader threads and server-side hooks only BUFFER; the scenario thread
//    records buffered items at explicit sync points (Expect/Drain/Hooks.Drain).
//  * Timing is recorded as a class ("immediate" < 100 ms, otherwise
//    "blocked"), never as a number.
//  * Ports / endpoints never appear in the transcript (placeholders only).

using System.Collections.Concurrent;
using System.Diagnostics;
using System.Net;
using System.Net.Sockets;
using System.Reflection;
using System.Security.Cryptography;
using System.Text;
using System.Text.Encodings.Web;
using System.Text.Json;
using System.Text.Json.Nodes;
using HarmonyLib;

namespace BridgeHarness;

static class T
{
    static StreamWriter? _w;
    public static string Scn = "";
    static readonly object Gate = new();
    public static readonly JsonSerializerOptions Opts = new()
    {
        Encoder = JavaScriptEncoder.UnsafeRelaxedJsonEscaping,
    };

    public static void Open(string path) => _w = new StreamWriter(path, false, new UTF8Encoding(false)) { NewLine = "\n" };
    public static void Close() { lock (Gate) { _w?.Flush(); _w?.Dispose(); _w = null; } }

    public static void Scenario(string name, string what)
    {
        Scn = name;
        Ev("scenario", new { name, what });
    }

    public static void Ev(string ev, object? data = null)
    {
        var o = new JsonObject { ["scn"] = Scn, ["ev"] = ev };
        if (data != null)
        {
            JsonNode? node = JsonSerializer.SerializeToNode(data, data.GetType(), Opts);
            if (node is JsonObject obj)
                foreach (var kv in obj) o[kv.Key] = kv.Value?.DeepClone();
            else o["data"] = node?.DeepClone();
        }
        lock (Gate)
        {
            _w!.WriteLine(o.ToJsonString(Opts));
            _w.Flush();
        }
    }

    /// Long strings are summarized (length + sha256 + head/tail) so the
    /// transcript stays readable; short ones are kept verbatim.
    public static object? Str(string? s)
    {
        if (s == null) return null;
        if (s.Length <= 1500) return s;
        string sha = Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(s))).ToLowerInvariant();
        return new { len = s.Length, sha256 = sha, head = s[..60], tail = s[^60..] };
    }

    /// Only for synchronous API calls: did the call return promptly, or did
    /// it block the caller (> 1 s)? Async calls are characterised by
    /// completed_on_return + explicit probe checkpoints instead of durations.
    public static string Classify(TimeSpan t) => t.TotalMilliseconds < 1000 ? "returned" : "blocked_over_1s";
}

/// Log sink for SpireMentor's Logger.Log (kept OUT of the transcript: log text
/// is not wire behaviour and a rewrite may legitimately change it).
static class LogSink
{
    static readonly object Gate = new();
    static StreamWriter? _w;
    public static readonly List<(string from, string to)> Normalizers = new();
    public static void Open(string path) => _w = new StreamWriter(path, false, new UTF8Encoding(false)) { AutoFlush = true };
    public static void Write(string msg)
    {
        foreach (var (f, t) in Normalizers) msg = msg.Replace(f, t);
        msg = System.Text.RegularExpressions.Regex.Replace(msg, @"127\.0\.0\.1:\d+", "127.0.0.1:<EP>");
        lock (Gate) _w?.WriteLine($"[{T.Scn}] {msg}");
    }
}

/// Reflective facade over SpireMentor.BridgeServer.Instance. Only the members the
/// rest of the mod uses are touched (see README "API surface").
static class B
{
    static object _inst = null!;
    static MethodInfo _start = null!, _stop = null!, _send = null!, _sendWait = null!,
        _wait = null!, _advice = null!, _cancelAdvice = null!;
    static PropertyInfo _conn = null!;
    public static readonly Dictionary<int, string> PortNames = new();

    public static void Init(Assembly asm)
    {
        Type t = asm.GetType("SpireMentor.BridgeServer", throwOnError: true)!;
        const BindingFlags S = BindingFlags.Static | BindingFlags.Public | BindingFlags.NonPublic;
        const BindingFlags I = BindingFlags.Instance | BindingFlags.Public | BindingFlags.NonPublic;
        _inst = t.GetField("Instance", S)?.GetValue(null) ?? t.GetProperty("Instance", S)!.GetValue(null)!;
        MethodInfo M(string name, int nparams) =>
            t.GetMethods(I).Single(m => m.Name == name && m.GetParameters().Length == nparams);
        _start = M("Start", 1);
        _stop = M("Stop", 0);
        _send = M("SendState", 1);
        _sendWait = M("SendStateAndWaitForActionAsync", 3);
        _wait = M("WaitForActionAsync", 2);
        _advice = M("SendAdviceRequest", 2);
        _cancelAdvice = M("CancelAdviceRequest", 1);
        _conn = t.GetProperty("IsClientConnected", I)!;
        // v5 coach lane: optional, so the harness still runs on an older DLL
        // (its transcript then simply lacks the coach_received hooks).
        EventInfo? coach = t.GetEvent("CoachReceived", I);
        if (coach != null)
        {
            Action<JsonElement> onCoach = rec =>
                Hooks.Q.Enqueue(new { hook = "coach_received", coach = T.Str(rec.GetRawText()), thread = "server" });
            coach.AddEventHandler(_inst, onCoach);
        }
    }

    public static string PortName(int p) => PortNames.TryGetValue(p, out var n) ? n : "<PORT?>";

    static (object? res, Exception? ex, TimeSpan el) Inv(Func<object?> f)
    {
        var sw = Stopwatch.StartNew();
        try { var r = f(); return (r, null, sw.Elapsed); }
        catch (TargetInvocationException tie) { return (null, tie.InnerException, sw.Elapsed); }
        catch (Exception e) { return (null, e, sw.Elapsed); }
    }

    public static object? ExDesc(Exception? e) => e == null ? null : new
    {
        type = e.GetType().Name,
        socket_error = (e as SocketException)?.SocketErrorCode.ToString(),
    };

    static void Rec(string call, object? args, (object? res, Exception? ex, TimeSpan el) r)
        => T.Ev("api", new { call, args, result = r.res is string s ? T.Str(s) : r.res, exception = ExDesc(r.ex), timing = T.Classify(r.el) });

    public static bool IsClientConnectedRaw() => (bool)_conn.GetValue(_inst)!;

    public static void IsClientConnected() => Rec("IsClientConnected", null, Inv(() => _conn.GetValue(_inst)));
    public static void Start(int port) => Rec("Start", new { port = PortName(port) }, Inv(() => _start.Invoke(_inst, new object[] { port })));
    public static void Stop() => Rec("Stop", null, Inv(() => _stop.Invoke(_inst, null)));
    public static void SendState(string json) => Rec("SendState", new { stateJson = T.Str(json) }, Inv(() => _send.Invoke(_inst, new object[] { json })));
    public static void SendStateQuiet(string json) => _send.Invoke(_inst, new object[] { json });
    public static void CancelAdviceRequest(string id) => Rec("CancelAdviceRequest", new { requestId = id }, Inv(() => _cancelAdvice.Invoke(_inst, new object[] { id })));

    /// Returns the request id the server handed back (null on failure).
    public static string? SendAdviceRequest(string json, string label, bool throwInCallback = false)
    {
        Action<JsonElement> cb = root =>
        {
            Hooks.Q.Enqueue(new { hook = "advice_callback", label, root = T.Str(root.GetRawText()), thread = "server" });
            if (throwInCallback) throw new InvalidOperationException("harness: callback throws on purpose");
        };
        var r = Inv(() => _advice.Invoke(_inst, new object[] { json, cb }));
        Rec("SendAdviceRequest", new { stateJson = T.Str(json), label }, r);
        return r.res as string;
    }

    public static Probe SendStateAndWait(string id, string json, TimeSpan timeout, CancellationToken ct = default, string? tokenDesc = null)
        => Probe.Start(id, "SendStateAndWaitForActionAsync",
            new { stateJson = T.Str(json), timeout_ms = timeout.TotalMilliseconds, token = tokenDesc ?? "none" },
            () => _sendWait.Invoke(_inst, new object[] { json, timeout, ct }));

    public static Probe WaitForAction(string id, TimeSpan timeout, CancellationToken ct = default, string? tokenDesc = null)
        => Probe.Start(id, "WaitForActionAsync",
            new { timeout_ms = timeout.TotalMilliseconds, token = tokenDesc ?? "none" },
            () => _wait.Invoke(_inst, new object[] { timeout, ct }));
}

/// Tracks one in-flight async API call. All observations are explicit.
sealed class Probe
{
    public readonly string Id;
    Task? _task;
    Exception? _syncEx;
    bool _reported;

    Probe(string id) { Id = id; }

    public static Probe Start(string id, string call, object args, Func<object?> invoke)
    {
        var p = new Probe(id);
        try { p._task = (Task?)invoke(); }
        catch (TargetInvocationException tie) { p._syncEx = tie.InnerException; }
        catch (Exception e) { p._syncEx = e; }
        if (p._syncEx != null)
        {
            T.Ev("api_start", new { id, call, args, completed_on_return = (bool?)null });
            T.Ev("api_result", new { id, after = "sync_throw", result = (object?)null, exception = B.ExDesc(p._syncEx) });
            p._reported = true;
        }
        else
        {
            // Whether the returned Task was already complete when the call
            // returned (e.g. "no client -> null" paths) -- deterministic,
            // unlike a measured duration.
            T.Ev("api_start", new { id, call, args, completed_on_return = p._task?.IsCompleted });
        }
        return p;
    }

    (object? res, Exception? ex) Outcome()
    {
        var t = _task!;
        if (t.IsCanceled) return (null, new TaskCanceledException());
        if (t.IsFaulted) return (null, t.Exception!.InnerException);
        object? r = t.GetType().GetProperty("Result")?.GetValue(t);
        return (r, null);
    }

    /// Observe the state now (after a short grace window so a completion
    /// that is already scheduled is seen as completed).
    public void Check(string at, int graceMs = 150)
    {
        if (_reported) { T.Ev("probe", new { id = Id, at, state = "already_reported" }); return; }
        bool done = _task!.Wait(graceMs) || _task.IsCompleted;
        if (!done) { T.Ev("probe", new { id = Id, at, state = "pending" }); return; }
        Report(at);
    }

    public void Await(string after, int timeoutMs = 4000)
    {
        if (_reported) { T.Ev("probe", new { id = Id, at = after, state = "already_reported" }); return; }
        bool done;
        try { done = _task!.Wait(timeoutMs); } catch (AggregateException) { done = true; }
        if (!done) { T.Ev("probe", new { id = Id, at = after, state = "still_pending" }); return; }
        Report(after);
    }

    void Report(string after)
    {
        try { _task!.Wait(); } catch { }
        var (res, ex) = Outcome();
        T.Ev("api_result", new { id = Id, after, result = res is string s ? T.Str(s) : res, exception = B.ExDesc(ex) });
        _reported = true;
    }
}

/// Scripted raw-socket client. A reader thread buffers; the scenario thread
/// records at Expect/Drain points.
sealed class Client
{
    public readonly string Name;
    Socket? _s;
    readonly BlockingCollection<(string kind, byte[]? data)> _q = new();
    volatile bool _closedLocally;

    public Client(string name) { Name = name; }

    public bool Connect(int port)
    {
        _s = new Socket(AddressFamily.InterNetwork, SocketType.Stream, ProtocolType.Tcp) { NoDelay = true };
        try
        {
            _s.Connect(IPAddress.Loopback, port);
            T.Ev("client_connect", new { client = Name, port = B.PortName(port), ok = true });
            new Thread(ReadLoop) { IsBackground = true, Name = "client-" + Name }.Start();
            return true;
        }
        catch (SocketException e)
        {
            T.Ev("client_connect", new { client = Name, port = B.PortName(port), ok = false, error = e.SocketErrorCode.ToString() });
            _closedLocally = true;
            return false;
        }
    }

    void ReadLoop()
    {
        var buf = new byte[65536];
        var acc = new MemoryStream();
        try
        {
            while (true)
            {
                int n = _s!.Receive(buf);
                if (n == 0)
                {
                    if (acc.Length > 0) _q.Add(("partial", acc.ToArray()));
                    _q.Add(("eof", null));
                    return;
                }
                for (int i = 0; i < n; i++)
                {
                    if (buf[i] == (byte)'\n') { _q.Add(("line", acc.ToArray())); acc.SetLength(0); }
                    else acc.WriteByte(buf[i]);
                }
            }
        }
        catch (Exception e)
        {
            if (_closedLocally) return;
            if (acc.Length > 0) _q.Add(("partial", acc.ToArray()));
            _q.Add(("error", Encoding.UTF8.GetBytes(e is SocketException se ? se.SocketErrorCode.ToString() : e.GetType().Name)));
        }
    }

    static string Dec(byte[] b) => Encoding.UTF8.GetString(b);

    void RecordItem((string kind, byte[]? data) it)
    {
        switch (it.kind)
        {
            case "line": T.Ev("s2c", new { client = Name, line = T.Str(Dec(it.data!)) }); break;
            case "partial": T.Ev("s2c_partial_line_at_eof", new { client = Name, data = T.Str(Dec(it.data!)) }); break;
            case "eof": T.Ev("s2c_eof", new { client = Name }); break;
            default: T.Ev("s2c_error", new { client = Name, error = Dec(it.data!) }); break;
        }
    }

    /// Wait for n lines (records each). Stops early at EOF/error.
    public List<string> Expect(int n, int timeoutMs = 3000)
    {
        var lines = new List<string>();
        var sw = Stopwatch.StartNew();
        while (lines.Count < n)
        {
            int left = timeoutMs - (int)sw.ElapsedMilliseconds;
            if (left <= 0 || !_q.TryTake(out var it, left))
            {
                T.Ev("s2c_timeout", new { client = Name, expected = n, got = lines.Count });
                break;
            }
            RecordItem(it);
            if (it.kind == "line") lines.Add(Dec(it.data!));
            else break;
        }
        return lines;
    }

    /// Wait for n lines but record only a summary (for bursts).
    public List<string> ExpectQuiet(int n, int timeoutMs = 5000)
    {
        var lines = new List<string>();
        var sw = Stopwatch.StartNew();
        while (lines.Count < n)
        {
            int left = timeoutMs - (int)sw.ElapsedMilliseconds;
            if (left <= 0 || !_q.TryTake(out var it, left)) break;
            if (it.kind == "line") lines.Add(Dec(it.data!));
            else { RecordItem(it); break; }
        }
        return lines;
    }

    /// Record everything that arrives within the window ("s2c_none" if nothing).
    public List<string> Drain(int windowMs = 300)
    {
        var lines = new List<string>();
        var sw = Stopwatch.StartNew();
        bool any = false;
        while (true)
        {
            int left = windowMs - (int)sw.ElapsedMilliseconds;
            if (left <= 0 || !_q.TryTake(out var it, left)) break;
            any = true;
            RecordItem(it);
            if (it.kind == "line") lines.Add(Dec(it.data!));
            else break;
        }
        if (!any) T.Ev("s2c_none", new { client = Name });
        return lines;
    }

    /// Wait for the server to close the connection (records lines on the way).
    /// resetIsClose: the server closes with client data still unread, so the OS
    /// may send RST instead of FIN depending on how much of it the server had
    /// read: record both as one "s2c_closed" event so the transcript is stable.
    public void ExpectEof(int timeoutMs = 3000, bool resetIsClose = false)
    {
        var sw = Stopwatch.StartNew();
        while (true)
        {
            int left = timeoutMs - (int)sw.ElapsedMilliseconds;
            if (left <= 0 || !_q.TryTake(out var it, left))
            {
                T.Ev("s2c_timeout", new { client = Name, expected = "eof" });
                return;
            }
            if (resetIsClose && (it.kind == "eof" || (it.kind == "error" && Dec(it.data!) == "ConnectionReset")))
            {
                T.Ev("s2c_closed", new { client = Name, how = "eof_or_reset" });
                return;
            }
            RecordItem(it);
            if (it.kind != "line") return;
        }
    }

    public void Send(string text, string? note = null)
    {
        T.Ev("c2s", new { client = Name, data = T.Str(text), note });
        SendRaw(Encoding.UTF8.GetBytes(text));
    }

    public void SendBytes(byte[] data, string desc)
    {
        T.Ev("c2s", new { client = Name, bytes = desc, len = data.Length });
        SendRaw(data);
    }

    /// Each chunk is a separate write with a gap so it lands in its own TCP
    /// segment / server read.
    public void SendChunks(IList<byte[]> chunks, int gapMs = 120, string? note = null)
    {
        T.Ev("c2s_chunks", new
        {
            client = Name,
            note,
            gap = "separate_segments",
            chunks = chunks.Select(c => (object?)new { utf8 = T.Str(Encoding.UTF8.GetString(c)), len = c.Length, hex = Strict(c) ? null : Convert.ToHexString(c) }).ToList(),
        });
        for (int i = 0; i < chunks.Count; i++)
        {
            SendRaw(chunks[i]);
            if (i < chunks.Count - 1) Thread.Sleep(gapMs);
        }
    }

    static bool Strict(byte[] b)
    {
        try { new UTF8Encoding(false, true).GetString(b); return true; } catch { return false; }
    }

    void SendRaw(byte[] b)
    {
        try { _s!.Send(b); }
        catch (SocketException e) { T.Ev("c2s_error", new { client = Name, error = e.SocketErrorCode.ToString() }); }
    }

    public void Close()
    {
        _closedLocally = true;
        T.Ev("client_close", new { client = Name });
        try { _s?.Shutdown(SocketShutdown.Both); } catch { }
        _s?.Close();
    }

    /// Close with RST (linger 0).
    public void Reset()
    {
        _closedLocally = true;
        T.Ev("client_reset", new { client = Name });
        try { _s!.LingerState = new LingerOption(true, 0); } catch { }
        _s?.Close();
    }

    /// Half-close: FIN our direction, keep reading.
    public void ShutdownSend()
    {
        T.Ev("client_shutdown_send", new { client = Name });
        _s!.Shutdown(SocketShutdown.Send);
    }

    /// Discard anything still buffered without recording (used only after a
    /// client was intentionally abandoned).
    public void Forget() { _closedLocally = true; try { _s?.Close(); } catch { } }
}

/// Harmony interception of the game-side calls BridgeServer makes, plus the
/// mod's Logger.
static class Hooks
{
    public static readonly ConcurrentQueue<object> Q = new();

    public static void Install(Assembly asm, List<string> notes)
    {
        var h = new Harmony("bridgeharness");
        void Pre(string type, string method, string prefix)
        {
            var t = asm.GetType(type);
            var m = t == null ? null : AccessTools.Method(t, method);
            if (m == null) { notes.Add($"hook target missing: {type}.{method}"); return; }
            h.Patch(m, prefix: new HarmonyMethod(typeof(Hooks).GetMethod(prefix, BindingFlags.Static | BindingFlags.NonPublic)));
            notes.Add($"patched {type}.{method} -> {prefix}");
        }
        Pre("SpireMentor.Logger", "Log", nameof(LogPrefix));
        Pre("SpireMentor.SimCombatUndo", "RequestUndoTurn", nameof(UndoPrefix));
        Pre("SpireMentor.SimRestore", "RequestRestore", nameof(RestorePrefix));
    }

    static bool LogPrefix(string message) { LogSink.Write(message); return false; }

    /// Platform shim (on by default, --no-platform-shim disables): Windows
    /// accepts SO_RCVTIMEO/SO_SNDTIMEO = -1 (TcpClient.ReceiveTimeout =
    /// Timeout.Infinite passes -1 straight through), Linux setsockopt rejects
    /// it with EINVAL, which makes BridgeServer's accept path throw for EVERY
    /// client. Map -1 -> 0 (both mean "infinite") so the test sees the
    /// Windows behaviour the mod actually ships on.
    public static int ShimHits;
    public static void InstallPlatformShim(List<string> notes)
    {
        var h = new Harmony("bridgeharness.shim");
        var m = typeof(System.Net.Sockets.Socket).GetMethod("SetSocketOption",
            new[] { typeof(System.Net.Sockets.SocketOptionLevel), typeof(System.Net.Sockets.SocketOptionName), typeof(int) })!;
        h.Patch(m, prefix: new HarmonyMethod(typeof(Hooks).GetMethod(nameof(SockOptPrefix), BindingFlags.Static | BindingFlags.NonPublic)));
        notes.Add("platform shim: Socket.SetSocketOption(level, Send/ReceiveTimeout, -1) -> 0");
    }

    static void SockOptPrefix(System.Net.Sockets.SocketOptionName optionName, ref int optionValue)
    {
        if (optionValue == -1 && (optionName == System.Net.Sockets.SocketOptionName.ReceiveTimeout ||
                                  optionName == System.Net.Sockets.SocketOptionName.SendTimeout))
        {
            optionValue = 0;
            Interlocked.Increment(ref ShimHits);
        }
    }

    static bool UndoPrefix(object[] __args)
    {
        Q.Enqueue(new { hook = "SimCombatUndo.RequestUndoTurn", targetTurn = __args[0], tapeId = __args.Length > 1 ? __args[1] : null });
        return false;
    }

    static bool RestorePrefix(object[] __args)
    {
        Q.Enqueue(new { hook = "SimRestore.RequestRestore", snapshotId = __args[0] });
        return false;
    }

    public static void Drain()
    {
        while (Q.TryDequeue(out var o)) T.Ev("hook", o);
    }

    /// Seed SimSnapshots' in-memory store (private statics of SimSnapshots,
    /// NOT of BridgeServer) so list_snapshots has deterministic content.
    public static object SeedSnapshots(Assembly asm)
    {
        try
        {
            Type st = asm.GetType("SpireMentor.SimSnapshots", true)!;
            Type mt = asm.GetType("SpireMentor.SimSnapshotMeta", true)!;
            const BindingFlags S = BindingFlags.Static | BindingFlags.NonPublic | BindingFlags.Public;
            var metas = (System.Collections.IList)st.GetField("Metas", S)!.GetValue(null)!;
            object Mk(string id, int seq, int floor, int act, bool post, bool tape, int reloads)
            {
                object m = Activator.CreateInstance(mt, true)!;
                void P(string n, object v) => mt.GetProperty(n)!.SetValue(m, v);
                P("Id", id); P("Seq", seq); P("Ts", "2026-01-02T03:04:05Z"); P("Floor", floor); P("Act", act);
                P("StartTime", 1760000000L); P("PostCombat", post); P("NumReloads", reloads); P("HasTape", tape);
                return m;
            }
            metas.Add(Mk("1760000000_0001", 1, 1, 1, false, false, 0));
            metas.Add(Mk("1760000000_0002", 2, 1, 1, true, true, 1));
            st.GetField("_currentStartTime", S)!.SetValue(null, 1760000000L);
            return new { seeded = 2 };
        }
        catch (Exception e)
        {
            return new { seeded = 0, error = e.GetType().Name + ": " + e.Message };
        }
    }
}
