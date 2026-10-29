// Compile-only API stub of BaseLib v3.4.1 -- see BaseLibStub.csproj.
// Only the members SpireMentorModConfig.cs touches; every body is a no-op.
// Signatures follow the call sites, so a mod change that needs more of
// BaseLib fails here first: add the member, never a behaviour.

using System.Reflection;
using Godot;
using MegaCrit.Sts2.addons.mega_text;

namespace BaseLib.Config
{
    [AttributeUsage(AttributeTargets.Property)]
    public sealed class ConfigIgnoreAttribute : Attribute { }

    [AttributeUsage(AttributeTargets.Property)]
    public sealed class ConfigSliderAttribute : Attribute
    {
        public ConfigSliderAttribute(double min, double max, double step) { }
        public string? Format { get; set; }
    }

    public abstract class ModConfig
    {
        public event EventHandler? ConfigChanged;
        protected string ModPrefix => "";
        public void ConfigReloaded() { }
        public void SaveDebounced() { }
        public abstract void SetupConfigUI(Control optionContainer);
    }

    public abstract class SimpleModConfig : ModConfig
    {
        protected Control CreateSectionHeader(string text, bool alignToTop = false) => null!;
        protected Control CreateRawTickboxControl(PropertyInfo property) => null!;
        protected Control CreateRawSliderControl(PropertyInfo property) => null!;
        protected Control CreateRawLineEditControl(PropertyInfo property) => null!;
        protected MegaRichTextLabel CreateRawLabelControl(string text, int fontSize) => null!;
        protected Control CreateDividerControl() => null!;
        protected void AddRestoreDefaultsButton(Control container) { }
        protected void SetupFocusNeighbors(Control container) { }
    }

    public static class ModConfigRegistry
    {
        public static void Register(string modId, ModConfig config) { }
    }
}

namespace BaseLib.Config.UI
{
    public partial class NConfigOptionRow : HBoxContainer
    {
        public NConfigOptionRow(string modPrefix, string name, Control label, Control control) { }
    }
}

namespace BaseLib.Extensions
{
    public static class ControlExtensions
    {
        public static void ClearFocusNeighbors(this Control control) { }
    }
}
