namespace Godot.Collections;

// Godot Array<T> wrapper
public class Array<T> : List<T>
{
    public Array() { }
    public Array(IEnumerable<T> items) : base(items) { }

    // Real GodotSharp declares GetEnumerator returning IEnumerator<T>; the
    // inherited List<T>.GetEnumerator returns List<T>.Enumerator, which does
    // NOT satisfy the game's member token (return type is part of the
    // signature) — foreach over a Godot array then throws
    // MissingMethodException at JIT time (GodotTreeExtensions.FreeChildren).
    public new IEnumerator<T> GetEnumerator() => ((List<T>)this).GetEnumerator();
}

// Godot Dictionary
public class Dictionary<TKey, TValue> : System.Collections.Generic.Dictionary<TKey, TValue>
    where TKey : notnull
{
    public Dictionary() { }
}

// Non-generic Array
public class Array : List<Variant>
{
    public Array() { }
}

// Non-generic Dictionary (OS.GetMemoryInfo, MetricUtilities)
public class Dictionary : System.Collections.Generic.Dictionary<Variant, Variant>
{
    public Dictionary() { }
}
