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
            ? (edge ? "settings/search" : "settings/searchEngines") : "extensions/?id=" + extensionId);
    }

    internal static async Task<string> OpenAsync(BrowserInventory.Profile profile, string label, string? extensionId)
    {
        var address = Address(profile.Edge, extensionId);
        var marker = "Clenzy " + Guid.NewGuid().ToString("N");
        // A file URL survives Chromium's command-line URL filter and identifies this specific window.
        var directory = Directory.CreateDirectory(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "Clenzy", "BrowserRepair"));
        // One reusable page per destination. No profile contents are included.
        var file = Path.Combine(directory.FullName, (profile.Edge ? "edge-" : "chrome-") + (extensionId ?? "search") + ".html");
        var instruction = extensionId == null ? "Choose your preferred search engine and make it the default." : "Review this extension and choose Remove to uninstall it.";
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
            return "Review the change in " + profile.Title + ". Refresh profiles after removing an extension.";
        }
        return "Follow the address-bar instructions in the " + profile.Title + " tab.";
    }
}
