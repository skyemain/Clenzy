using System.Diagnostics;
using System.IO;
using System.Net;
using System.Runtime.InteropServices;
using System.Text;
using System.Windows.Automation;

namespace Clenzy.App.Views;

internal static class BrowserRepairPage
{
    [DllImport("user32.dll")]
    private static extern IntPtr GetForegroundWindow();

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern int GetWindowText(IntPtr window, StringBuilder title, int count);

    [DllImport("user32.dll")]
    private static extern uint GetWindowThreadProcessId(IntPtr window, out uint processId);

    internal static string Address(bool edge, string? extensionId)
    {
        if (extensionId != null && (extensionId.Length != 32 || extensionId.Any(c => c < 'a' || c > 'p')))
            throw new ArgumentException("Invalid browser extension ID.");
        return (edge ? "edge://" : "chrome://") + (extensionId == null
            ? "settings/searchEngines" : "extensions/?id=" + extensionId);
    }

    internal static async Task<string> OpenAsync(BrowserInventory.Profile profile, string label, string? extensionId)
    {
        var address = Address(profile.Edge, extensionId);
        var marker = "Clenzy " + Guid.NewGuid().ToString("N");
        // A file URL survives Chromium's command-line URL filter and identifies this specific window.
        var directory = Directory.CreateDirectory(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "Clenzy", "BrowserRepair"));
        // One reusable page per destination. No profile contents are included.
        var file = Path.Combine(directory.FullName, (profile.Edge ? "edge-" : "chrome-") + (extensionId ?? "search") + ".html");
        var instruction = extensionId == null ? "Target search provider: " + (profile.Edge ? "Bing." : "Google.") : "Review this extension and choose Remove to uninstall it.";
        var html = $$"""
            <!doctype html><meta charset="utf-8"><title>{{marker}}</title>
            <style>body{font:18px system-ui;max-width:38rem;margin:12vh auto;padding:24px}input,button{font:inherit;padding:10px}input{width:95%}</style>
            <h1>{{WebUtility.HtmlEncode(label)}}</h1><p>{{WebUtility.HtmlEncode(profile.Title)}}</p>
            <p>If this page stays open, copy the address below. Press <b>Ctrl+L</b>, paste it, and press <b>Enter</b>.</p>
            <input id="address" readonly value="{{address}}" onclick="this.select()"><p><button onclick="const a=document.getElementById('address');a.select();this.textContent=document.execCommand('copy')?'Copied':'Press Ctrl+C'">Copy address</button></p>
            <p>{{instruction}}</p>
            """;
        await File.WriteAllTextAsync(file, html);
        var start = new ProcessStartInfo(profile.Executable) { UseShellExecute = false };
        start.ArgumentList.Add("--profile-directory=" + profile.Directory);
        start.ArgumentList.Add("--new-window");
        start.ArgumentList.Add(new Uri(file).AbsoluteUri);
        using var process = Process.Start(start);
        for (int attempt = 0; attempt < 40; attempt++)
        {
            await Task.Delay(150);
            var window = GetForegroundWindow();
            var title = new StringBuilder(512);
            GetWindowText(window, title, title.Capacity);
            if (!title.ToString().StartsWith(marker, StringComparison.Ordinal)) continue;
            GetWindowThreadProcessId(window, out var pid);
            using var browser = Process.GetProcessById((int)pid);
            if (!browser.ProcessName.Equals(Path.GetFileNameWithoutExtension(profile.Executable), StringComparison.OrdinalIgnoreCase)) continue;
            if (window != GetForegroundWindow()) break;
            System.Windows.Forms.SendKeys.SendWait("^l");
            await Task.Delay(180);
            if (window != GetForegroundWindow()) break;
            var edit = AutomationElement.FocusedElement;
            if (edit == null || edit.Current.ProcessId != pid || edit.Current.ControlType != ControlType.Edit ||
                !edit.TryGetCurrentPattern(ValuePattern.Pattern, out var pattern)) break;
            var value = (ValuePattern)pattern;
            if (!value.Current.Value.Contains(Path.GetFileName(file), StringComparison.OrdinalIgnoreCase)) break;
            value.SetValue(address);
            if (window != GetForegroundWindow() || !edit.Current.HasKeyboardFocus || value.Current.Value != address) break;
            System.Windows.Forms.SendKeys.SendWait("{ENTER}");
            return await Task.Run(() => CompleteAction(window, edit, address, profile.Edge, extensionId));
        }
        return "Follow the address-bar instructions in the " + profile.Title + " tab.";
    }
    private static AutomationElement[] Elements(AutomationElement root, Condition condition) =>
        root.FindAll(TreeScope.Descendants, condition).Cast<AutomationElement>().ToArray();

    private static bool Invoke(AutomationElement element)
    {
        if (!element.Current.IsEnabled) return false;
        if (element.TryGetCurrentPattern(InvokePattern.Pattern, out var action))
        {
            ((InvokePattern)action).Invoke();
            return true;
        }
        return false;
    }

    private static string CompleteAction(IntPtr window, AutomationElement omnibox, string address, bool edge, string? extensionId)
    {
        var host = AutomationElement.FromHandle(window);
        var provider = edge ? "Bing" : "Google";
        bool menuOpened = false, resetRequested = false;
        for (int attempt = 0; attempt < 36; attempt++)
        {
            Thread.Sleep(200);
            if (GetForegroundWindow() != window) break;
            try
            {
                var docs = Elements(host, new PropertyCondition(AutomationElement.ControlTypeProperty, ControlType.Document));
                var doc = docs.FirstOrDefault(d => d.TryGetCurrentPattern(ValuePattern.Pattern, out var v) &&
                    string.Equals(((ValuePattern)v).Current.Value.TrimEnd('/'), address.TrimEnd('/'), StringComparison.Ordinal));
                if (doc == null && docs.Length == 1 && omnibox.TryGetCurrentPattern(ValuePattern.Pattern, out var current) &&
                    ((ValuePattern)current).Current.Value.TrimEnd('/').Equals(address.TrimEnd('/'), StringComparison.Ordinal))
                    doc = docs[0];
                if (doc == null) continue;
                if (extensionId != null)
                {
                    var remove = Elements(doc, Condition.TrueCondition).FirstOrDefault(e =>
                        e.Current.AutomationId == "remove-extension" ||
                        (e.Current.ControlType == ControlType.Button &&
                         new[] { "Remove extension", "Remove from Microsoft Edge", "Remove from Chrome" }.Contains(e.Current.Name, StringComparer.OrdinalIgnoreCase)));
                    if (remove != null && Invoke(remove)) return "Confirm removal in the browser, then refresh profiles.";
                    continue;
                }
                if (menuOpened && !resetRequested)
                {
                    var item = Elements(doc, Condition.TrueCondition).FirstOrDefault(e =>
                        e.Current.IsEnabled && !e.Current.IsOffscreen &&
                        (e.Current.AutomationId is "makeDefault" or "makeDefaultOption" ||
                        e.Current.Name.Equals("Make default", StringComparison.OrdinalIgnoreCase)));
                    if (item != null && Invoke(item)) resetRequested = true;
                    continue;
                }
                var buttons = Elements(doc, new PropertyCondition(AutomationElement.ControlTypeProperty, ControlType.Button));
                foreach (var button in buttons)
                {
                    var name = button.Current.Name;
                    if (!name.Contains("more", StringComparison.OrdinalIgnoreCase) && !name.Contains("actions", StringComparison.OrdinalIgnoreCase)) continue;
                    AutomationElement? row = button;
                    for (int level = 0; level < 7 && row != null; level++, row = TreeWalker.ControlViewWalker.GetParent(row))
                    {
                        if (row.Current.ControlType != ControlType.DataItem && row.Current.ControlType != ControlType.Group && row.Current.ControlType != ControlType.Custom) continue;
                        var cells = Elements(row, Condition.TrueCondition).Select(e => e.Current.Name.Trim()).ToArray();
                        if (Elements(row, new PropertyCondition(AutomationElement.ControlTypeProperty, ControlType.Button)).Count(e => e.Current.Name == name || e.Current.Name.Contains("more actions", StringComparison.OrdinalIgnoreCase)) != 1) continue;
                        if (!cells.Any(n => n.Equals(provider + ".com", StringComparison.OrdinalIgnoreCase)) ||
                            !cells.Any(n => n.Equals(provider,StringComparison.OrdinalIgnoreCase) || n.Equals(provider + " (Default)",StringComparison.OrdinalIgnoreCase))) continue;
                        if (cells.Any(n => n.Equals("Default",StringComparison.OrdinalIgnoreCase) || n.Equals(provider + " (Default)",StringComparison.OrdinalIgnoreCase)))
                            return provider + " is the default search engine.";
                        if (!resetRequested && Invoke(button)) menuOpened = true;
                        break;
                    }
                    if (menuOpened && !resetRequested) break;
                }
            }
            catch (ElementNotAvailableException) { /* The browser re-renders after navigation and changes. */ }
        }
        if (resetRequested) return "Requested " + provider + " as the default. The browser did not confirm the change.";
        return "Could not complete the browser action. The control may be managed, localized or unavailable in this browser version.";
    }

}
