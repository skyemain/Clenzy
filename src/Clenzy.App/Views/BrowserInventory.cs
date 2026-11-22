using System.IO;
using System.Text.Json;
using System.Text.RegularExpressions;

namespace Clenzy.App.Views;

internal static class BrowserInventory
{
    internal record Extension(string Id, string Name);
    internal record Profile(string Title, string Directory, string Executable, bool Edge, List<Extension> Extensions);

    internal static List<Profile> Read()
    {
        var profiles = new List<Profile>();
        var local = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
        foreach (bool edge in new[] { false, true })
        {
            string product = edge ? @"Microsoft\Edge" : @"Google\Chrome";
            var root = Path.Combine(local, product, "User Data");
            if (!Directory.Exists(root)) continue;
            var exe = new[] { Environment.SpecialFolder.ProgramFiles, Environment.SpecialFolder.ProgramFilesX86, Environment.SpecialFolder.LocalApplicationData }
                .Select(folder => Path.Combine(Environment.GetFolderPath(folder), product, "Application", edge ? "msedge.exe" : "chrome.exe"))
                .FirstOrDefault(File.Exists);
            if (exe is null) continue;
            foreach (var folder in Directory.GetDirectories(root).Order())
            {
                if (!File.Exists(Path.Combine(folder, "Preferences"))) continue;
                var name = Path.GetFileName(folder);
                profiles.Add(new((edge ? "Edge · " : "Chrome · ") + name, name, exe, edge, ReadExtensions(folder)));
            }
        }
        return profiles;
    }

    internal static List<Extension> ReadExtensions(string profile)
    {
        var result = new List<Extension>();
        var root = Path.Combine(profile, "Extensions");
        if (!Directory.Exists(root)) return result;
        foreach (var directory in Directory.GetDirectories(root))
        {
            var id = Path.GetFileName(directory);
            if (!Regex.IsMatch(id, "^[a-p]{32}$")) continue;
            string name = id;
            try
            {
                var manifest = Directory.GetDirectories(directory).Select(d => Path.Combine(d, "manifest.json"))
                    .Where(File.Exists).OrderByDescending(File.GetLastWriteTimeUtc).FirstOrDefault();
                if (manifest is null) continue;
                using var json = JsonDocument.Parse(File.ReadAllText(manifest));
                name = json.RootElement.GetProperty("name").GetString() ?? id;
                if (name.StartsWith("__MSG_") && name.EndsWith("__") && name.Length > 8)
                {
                    var key = name[6..^2];
                    name = id;
                    var locale = json.RootElement.GetProperty("default_locale").GetString();
                    if (locale != null && Regex.IsMatch(locale, "^[a-zA-Z0-9_-]+$"))
                    {
                        using var messages = JsonDocument.Parse(File.ReadAllText(Path.Combine(Path.GetDirectoryName(manifest)!, "_locales", locale, "messages.json")));
                        foreach (var entry in messages.RootElement.EnumerateObject())
                            if (entry.Name.Equals(key, StringComparison.OrdinalIgnoreCase))
                                name = entry.Value.GetProperty("message").GetString() ?? id;
                    }
                }
            }
            catch (Exception e) when (e is IOException or UnauthorizedAccessException or JsonException or KeyNotFoundException or InvalidOperationException) { name = id; }
            result.Add(new(id, name));
        }
        return result.OrderBy(extension => extension.Name).ToList();
    }
}
