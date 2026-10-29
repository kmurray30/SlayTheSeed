using System.Runtime.CompilerServices;

namespace Godot;

public class GodotObject
{
    public class SignalName { }

    public static bool IsInstanceValid(GodotObject? obj) => obj != null;
    public virtual bool IsQueuedForDeletion() => false;

    // Real Godot signature: Error Connect(StringName, Callable, uint = 0). Referenced
    // by NDebugAudioManager.Play (Harmony-no-op'd in headless, but Harmony still
    // processes its IL, so this token must resolve on the stub).
    public Error Connect(StringName signal, Callable callable, uint flags = 0) => Error.Ok;

    // Real Godot signature: Variant CallDeferred(StringName, params Variant[]).
    // The RETURN TYPE is part of the method token, so a void stub does NOT
    // satisfy the reference — it surfaces as `MissingMethodException: Method not
    // found: 'Godot.Variant Godot.GodotObject.CallDeferred(...)'` the first time
    // the JIT compiles a method that references it. GodotTreeExtensions.
    // AddChildSafely (called by every VFX-spawning card OnPlay — Slice / Suppress
    // / FlashOfSteel etc.) carries this token in its non-main-thread branch, so
    // the whole method failed to JIT and the card play aborted mid-OnPlay (the
    // headless "VFX-NRE" parity artifact). Stubbing it on GodotObject with the
    // correct Variant return resolves the token once, for the whole class, so the
    // per-card OnPlay Harmony patches are no longer needed to dodge it.
    public Variant CallDeferred(StringName method, params Variant[] args) => default;

    // Signatures below must match real GodotSharp EXACTLY (including return
    // type — it is part of the member token). A missing/mismatched member is
    // a MissingMethodException at JIT time in every game method referencing
    // it; when that method sits in the combat pipeline the run livelocks
    // (Test Subject respawn: CanvasItem.SetSelfModulate, seeds 1007/1009).
    // These were found by the stubaudit sweep of sts2.dll member refs.
    public void Dispose() { }
    public Error EmitSignal(StringName signal, params Variant[] args) => Error.Ok;
    public void Disconnect(StringName signal, Callable callable) { }
    public Variant Call(StringName method, params Variant[] args) => default;
    public void Set(StringName property, Variant value) { }
    public virtual void _Notification(int what) { }

    // ToSignal - must be on GodotObject (not Node) to match real Godot
    public SignalAwaiter ToSignal(GodotObject source, StringName signal)
    {
        return new SignalAwaiter();
    }

    // Bridge methods overridden in generated code
    protected virtual void SaveGodotObjectData(GodotSerializationInfo info) { }
    protected virtual void RestoreGodotObjectData(GodotSerializationInfo info) { }
    protected virtual bool InvokeGodotClassMethod(in NativeInterop.godot_string_name method, NativeInterop.NativeVariantPtrArgs args, out NativeInterop.godot_variant ret) { ret = default; return false; }
    protected virtual bool HasGodotClassMethod(in NativeInterop.godot_string_name method) => false;
    protected virtual bool SetGodotClassPropertyValue(in NativeInterop.godot_string_name name, in NativeInterop.godot_variant value) => false;
    protected virtual bool GetGodotClassPropertyValue(in NativeInterop.godot_string_name name, out NativeInterop.godot_variant value) { value = default; return false; }
    protected virtual void RaiseGodotClassSignalCallbacks(in NativeInterop.godot_string_name signal, NativeInterop.NativeVariantPtrArgs args) { }
    protected virtual bool HasGodotClassSignal(in NativeInterop.godot_string_name signal) => false;
}

public class Node : GodotObject
{
    public enum InternalMode { Disabled, Front, Back }

    private Node? _parent;
    private readonly List<Node> _children = new();

    public class MethodName
    {
        public static readonly StringName AddChild = "AddChild";
        public static readonly StringName RemoveChild = "RemoveChild";
        public static readonly StringName QueueFree = "QueueFree";
        public static readonly StringName _Ready = "_Ready";
        public static readonly StringName MoveChild = "MoveChild";
        public static readonly StringName AddSibling = "AddSibling";
    }

    public class PropertyName { }
    public new class SignalName : GodotObject.SignalName
    {
        public static readonly StringName ProcessFrame = "ProcessFrame";
    }

    public virtual StringName Name { get; set; } = "";

    public Node? GetParent() => _parent;
    public NodePath GetPath() => new NodePath(Name.ToString() ?? "");
    public bool IsNodeReady() => true;
    public int GetIndex(bool includeInternal = false) =>
        _parent != null ? Math.Max(0, _parent._children.IndexOf(this)) : 0;

    public void Reparent(Node newParent, bool keepGlobalTransform = true)
    {
        _parent?._children.Remove(this);
        _parent = newParent;
        newParent._children.Add(this);
    }

    public void MoveChild(Node childNode, int toIndex)
    {
        if (_children.Remove(childNode))
            _children.Insert(Math.Clamp(toIndex, 0, _children.Count), childNode);
    }

    public void AddSibling(Node sibling, bool forceReadableName = false)
    {
        _parent?._children.Add(sibling);
    }

    public Godot.Collections.Array<Node> GetChildren(bool includeInternal = false)
    {
        return new Godot.Collections.Array<Node>(_children);
    }

    public T? GetNodeOrNull<T>(string path) where T : class => null;
    public T? GetNodeOrNull<T>(NodePath path) where T : class => null;
    public Node? GetNodeOrNull(NodePath path) => null;
    public T GetNode<T>(string path) where T : class => default!;
    public T GetNode<T>(NodePath path) where T : class => default!;

    public virtual void AddChild(Node child, bool forceReadableName = false, InternalMode mode = InternalMode.Disabled)
    {
        child._parent = this;
        _children.Add(child);
    }

    public virtual void RemoveChild(Node child)
    {
        child._parent = null;
        _children.Remove(child);
    }

    public void Reparent(Node newParent)
    {
        _parent?.RemoveChild(this);
        newParent.AddChild(this);
    }

    public virtual void QueueFree() { }

    public SceneTree GetTree() => Engine.GetMainLoop() as SceneTree ?? new SceneTree();

    public Tween CreateTween() => new Tween();
    public Viewport GetViewport() => new Viewport();
    public double GetProcessDeltaTime() => 0.016;
    public bool IsAncestorOf(Node node) => false;
    public bool IsInsideTree() => false;
    public int GetChildCount(bool includeInternal = false) => _children.Count;

    // CallDeferred is inherited from GodotObject (returns Variant to match the
    // real Godot API — see the note there); no Node-level void shadow.

    public virtual void _Ready() { }
    public virtual void _EnterTree() { }
    public virtual void _ExitTree() { }
    public virtual void _Process(double delta) { }
    public override void _Notification(int what) { }
    public virtual void _Input(InputEvent @event) { }
    public virtual void _UnhandledInput(InputEvent @event) { }
    public virtual void _UnhandledKeyInput(InputEvent @event) { }
}

public class SceneTree : MainLoop
{
    public new class SignalName : Node.SignalName
    {
        public static new readonly StringName ProcessFrame = "process_frame";
    }

    public SceneTreeTimer CreateTimer(double timeSec, bool processAlways = true, bool processInPhysics = false, bool ignoreTimeScale = false)
    {
        var timer = new SceneTreeTimer();
        // Immediately fire the timeout in headless mode
        timer.FireTimeout();
        return timer;
    }

    public Window Root { get; } = new Window();
}

public class SceneTreeTimer : GodotObject
{
    // CombatState.GodotTimerTask awaits ToSignal(timer, SceneTreeTimer.
    // SignalName.Timeout); without this nested class the field ref fails to
    // resolve and GetCreatureAsync's wait path dies with
    // MissingMethodException instead of its designed timeout exception.
    public new class SignalName : GodotObject.SignalName
    {
        public static readonly StringName Timeout = "Timeout";
    }

    public event Action? Timeout;

    internal void FireTimeout()
    {
        Timeout?.Invoke();
    }
}

public class MainLoop : GodotObject { }

public static class Engine
{
    private static readonly SceneTree _mainLoop = new();
    public static MainLoop GetMainLoop() => _mainLoop;
    public static bool IsEditorHint() => false;
}

public static class GD
{
    public static void Print(params object[] args) => Console.Error.WriteLine(string.Join("", args));
    public static void Print(string msg) => Console.Error.WriteLine(msg);
    public static void PrintErr(params object[] args) => Console.Error.WriteLine("[ERROR] " + string.Join("", args));
    public static void PrintErr(string msg) => Console.Error.WriteLine("[ERROR] " + msg);
    public static void PushError(params object[] args) => Console.Error.WriteLine("[ERROR] " + string.Join("", args));
    public static void PushError(string msg) => Console.Error.WriteLine("[ERROR] " + msg);
    public static void PushWarning(params object[] args) { }
    public static void PushWarning(string msg) { }
    public static void PrintRich(params object[] args) { }
    public static void PrintRich(string msg) { }
    public static Variant Str(params Variant[] args) => string.Join("", args.Select(a => a.ToString()));
}

public static class OS
{
    public static void ShellOpen(string uri) { }
    public static string GetLocale() => "en";
    public static string GetName() => "headless";
    public static string GetVersion() => "0.0";
    public static string GetExecutablePath() => "";
    public static bool HasFeature(string feature) => false;
    public static bool IsDebugBuild() => false;
    public static string GetDataDir() => ".";
    public static string GetUserDataDir() => ".";
    public static string[] GetCmdlineArgs() => Array.Empty<string>();
    public static Godot.Collections.Dictionary GetMemoryInfo() => new();
    public static ulong GetStaticMemoryUsage() => 0;
    public static void ShellShowInFileManager(string fileOrDirPath, bool openFolder = true) { }
}

// Godot string helper extensions (GodotSharp: Godot.StringExtensions).
// ActModel.DefeatedAllEnemiesAchievement / CharacterModel.RunWonAchievement
// build achievement enum names via Capitalize on the VICTORY path, so a
// missing type here breaks run-end processing.
public static class StringExtensions
{
    public static string Capitalize(this string instance)
    {
        var words = instance.Replace('_', ' ').Split(' ', StringSplitOptions.RemoveEmptyEntries);
        return string.Join(" ", words.Select(w =>
            char.ToUpperInvariant(w[0]) + w.Substring(1).ToLowerInvariant()));
    }

    /// <summary>
    /// Godot's <c>String.path_join</c>: append <paramref name="file"/> as a
    /// sub-path, inserting <c>/</c> only when neither side has one. v0.111.0's
    /// save-path helpers (UserDataPathProvider.cs:36) use it; without it
    /// <c>SaveManager.InitProfileId</c> throws MissingMethodException.
    /// </summary>
    public static string PathJoin(this string instance, string file)
    {
        if (instance.Length == 0) return file;
        if (instance[^1] == '/' || (file.Length > 0 && file[0] == '/')) return instance + file;
        return instance + "/" + file;
    }

    public static string GetBaseDir(this string path)
    {
        var idx = path.Replace('\\', '/').LastIndexOf('/');
        return idx <= 0 ? "" : path.Substring(0, idx);
    }

    public static string GetFile(this string path)
    {
        var norm = path.Replace('\\', '/');
        return norm.Substring(norm.LastIndexOf('/') + 1);
    }

    public static string GetExtension(this string path)
    {
        var file = path.GetFile();
        var idx = file.LastIndexOf('.');
        return idx < 0 ? "" : file.Substring(idx + 1);
    }

    public static string ToSnakeCase(this string instance)
    {
        var sb = new System.Text.StringBuilder();
        for (var i = 0; i < instance.Length; i++)
        {
            var c = instance[i];
            if (char.IsUpper(c) && i > 0 && instance[i - 1] != '_' && !char.IsUpper(instance[i - 1]))
                sb.Append('_');
            sb.Append(char.ToLowerInvariant(c));
        }
        return sb.ToString();
    }
}

public static class ProjectSettings
{
    public static string GlobalizePath(string path) => path;
    public static Variant GetSetting(string name, Variant @default = default) => @default;
    public static bool LoadResourcePack(string path) => false;
}

public static class ResourceLoader
{
    public enum CacheMode { Reuse, Replace, Ignore }
    public static T? Load<T>(string path, string? typeHint = null, CacheMode cacheMode = CacheMode.Reuse) where T : class => null;
    public static bool Exists(string path) => false;
    public static bool Exists(string path, string typeHint) => false;
    public static void AddResourceFormatLoader(ResourceFormatLoader formatLoader, bool atFront = false) { }
    public static Error LoadThreadedRequest(string path, string typeHint = "", bool useSubThreads = false, CacheMode cacheMode = CacheMode.Reuse) => Error.Ok;
    public static Resource? LoadThreadedGet(string path) => null;
    public enum ThreadLoadStatus { InvalidResource, InProgress, Failed, Loaded }
    public static ThreadLoadStatus LoadThreadedGetStatus(string path, Godot.Collections.Array? progress = null) => ThreadLoadStatus.Failed;
}

public static class Time
{
    public static ulong GetTicksMsec() => (ulong)Environment.TickCount64;
}

public class Window : Node
{
    public new class SignalName : Node.SignalName
    {
        public static readonly StringName SizeChanged = "SizeChanged";
    }
}

public class Viewport : Node
{
    public new class SignalName : Node.SignalName
    {
        public static readonly StringName GuiFocusChanged = "GuiFocusChanged";
    }

    public Vector2 GetMousePosition() => Vector2.Zero;
    public Rect2 GetVisibleRect() => new Rect2(0, 0, 1920, 1080);
}
