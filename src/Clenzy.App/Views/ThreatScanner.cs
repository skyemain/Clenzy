using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Net.Http;
using System.Security.Cryptography;

namespace Clenzy.App.Views;

internal static class ThreatScanner
{
    private const string Release = "1.5.5";
    private const string Digest = "90169F5E551010BFBD0208B50D9B2FA8988EF396CCCBD46B0CC8CAE03E22A4A4";
    private static readonly string Root = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "Clenzy", "Scanner");
    private static readonly HttpClient Client = new() { Timeout = TimeSpan.FromMinutes(10) };
    internal record Finding(string File, string Signature)
    {
        public override string ToString() => Signature + "  ·  " + File;
    }

    private static async Task<string> EngineAsync(CancellationToken token)
    {
        Directory.CreateDirectory(Root);
        var install = Path.Combine(Root, Release);
        if (!Directory.Exists(install))
        {
            var stage = Directory.CreateDirectory(Path.Combine(Root, Guid.NewGuid().ToString("N")));
            var payload = Path.Combine(Root, Guid.NewGuid().ToString("N"));
            try
            {
                var zip = Path.Combine(stage.FullName, "engine.zip");
                using var source = await Client.GetStreamAsync($"https://github.com/Cisco-Talos/clamav/releases/download/clamav-{Release}/clamav-{Release}.win.x64.zip", token);
                using (var dest = File.Create(zip)) await source.CopyToAsync(dest, token);
                using (var file = File.OpenRead(zip))
                    if (Convert.ToHexString(await SHA256.HashDataAsync(file, token)) != Digest) throw new IOException("Engine download failed integrity verification.");
                ZipFile.ExtractToDirectory(zip, payload);
                token.ThrowIfCancellationRequested();
                try { Directory.Move(payload, install); }
                catch (IOException) when (Directory.Exists(install)) { }
            }
            finally { stage.Delete(true); if (Directory.Exists(payload)) Directory.Delete(payload,true); }
        }
        return Path.GetDirectoryName(Directory.GetFiles(install,"clamscan.exe",SearchOption.AllDirectories).Single())!;
    }

    private static async Task<int> Run(string exe, IEnumerable<string> args, Action<string> output, CancellationToken token)
    {
        var info = new ProcessStartInfo(exe) { UseShellExecute = false, CreateNoWindow = true, RedirectStandardOutput = true, RedirectStandardError = true };
        foreach (var arg in args) info.ArgumentList.Add(arg);
        using var process = Process.Start(info) ?? throw new IOException("Scanner could not start.");
        using var registration = token.Register(() => { try { if (!process.HasExited) process.Kill(true); } catch (InvalidOperationException) { } });
        async Task Read(StreamReader stream)
        {
            while (await stream.ReadLineAsync() is string line) { lock (info) output(line); }
        }
        await Task.WhenAll(Read(process.StandardOutput), Read(process.StandardError), process.WaitForExitAsync());
        token.ThrowIfCancellationRequested();
        return process.ExitCode;
    }

    internal static async Task<string> Scan(string[] targets, bool remove, IProgress<string> progress, Action<Finding> found, CancellationToken token)
    {
        progress.Report("Preparing ClamAV. First use downloads the verified Windows engine.");
        var bin = await EngineAsync(token);
        var db = Directory.CreateDirectory(Path.Combine(Root, "signatures")).FullName;
        var config = Path.Combine(Root, "freshclam.conf");
        await File.WriteAllTextAsync(config, "DatabaseMirror database.clamav.net\n", token);
        progress.Report("Checking for current threat signatures…");
        string error = "";
        if (await Run(Path.Combine(bin,"freshclam.exe"), ["--config-file="+config,"--datadir="+db], line => error=line, token) != 0)
            throw new IOException("Could not update threat signatures. " + error);
        var args = new List<string> { "--database="+db, "--recursive", "--infected", "--detect-pua=yes" };
        if (remove) args.Add("--remove=yes");
        args.AddRange(targets);
        var summary = new List<string>();
        progress.Report(remove ? "Rechecking the selected file before removal…" : "Scanning files for malware and potentially unwanted applications…");
        var code = await Run(Path.Combine(bin,"clamscan.exe"), args, line =>
        {
            if (line.EndsWith(" FOUND",StringComparison.Ordinal))
            {
                var split = line.LastIndexOf(": ",StringComparison.Ordinal);
                if (split > 0) found(new(line[..split], line[(split+2)..^6]));
            }
            if (line.StartsWith("Scanned files:") || line.StartsWith("Infected files:") || line.StartsWith("Total errors:")) summary.Add(line);
            if (line.Contains("ERROR",StringComparison.OrdinalIgnoreCase)) error=line;
        }, token);
        return (code > 1 ? "Scan incomplete. " + error + "  " : "") + string.Join("  ·  ",summary);
    }
}
