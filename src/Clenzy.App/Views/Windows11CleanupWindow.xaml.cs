using System.IO;
using System.Text.Json;
using System.Windows;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using Clenzy.Core.Windows11;
using Clenzy.Windows.Windows11;
namespace Clenzy.App.Views;
public partial class Windows11CleanupWindow : Window
{
    private IOptimizationBackend backend = new WindowsOptimizationBackend();
    private OptimizationEngine engine = null!;
    private OptimizationAudit? audit;
    private List<Choice> choices = new();
    private bool busy;
    public Window? EmbeddedOwner { get; set; }
    public Action? ExitRequested { get; set; }
    public bool DiskCleanupRequested => false;
    public bool AdministratorRequested => false;
    private string ReportsRoot => Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Clenzy", "Windows11", backend.Sandbox ? "sandbox" : "live");
    public Windows11CleanupWindow()
    {
        InitializeComponent(); engine = new(backend, ReportsRoot);
        Closing += (_, e) => { if (busy) e.Cancel = true; };
    }
    private void MalwareClick(object sender, RoutedEventArgs e)
    {
        ScanMenu.Visibility = Visibility.Collapsed;
        MalwarePanel.Visibility = Visibility.Visible;
        Status.Text = "Malware cleaner";
    }

    private CancellationTokenSource? scanCancellation;

    private void QuickClick(object sender, RoutedEventArgs e)
    {
        var targets = new[] { Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "Downloads"), Environment.GetFolderPath(Environment.SpecialFolder.Startup), Environment.GetFolderPath(Environment.SpecialFolder.CommonStartup) };
        _ = RunThreatAsync(targets.Where(Directory.Exists).ToArray(), false);
    }

    private void FolderClick(object sender, RoutedEventArgs e)
    {
        var picker = new Microsoft.Win32.OpenFolderDialog { Title = "Choose a folder to scan" };
        if (picker.ShowDialog() == true) _ = RunThreatAsync([picker.FolderName], false);
    }

    private void CancelThreat(object sender, RoutedEventArgs e) => scanCancellation?.Cancel();
    private void ThreatSelected(object sender, RoutedEventArgs e)
    {
        if (RemoveThreat != null) RemoveThreat.IsEnabled = !busy && ThreatResults.SelectedItem != null;
    }
    private void RemoveClick(object sender, RoutedEventArgs e)
    {
        if (ThreatResults.SelectedItem is ThreatScanner.Finding item && MessageBox.Show("Recheck and permanently remove this file if it still matches a threat signature?\n\n" + item.File,
            "Remove detection", MessageBoxButton.YesNo, MessageBoxImage.Warning, MessageBoxResult.No) == MessageBoxResult.Yes)
            _ = RunThreatAsync([item.File], true);
    }

    private async Task RunThreatAsync(string[] targets, bool remove)
    {
        if (busy || targets.Length == 0) { if (!busy) Status.Text = "Choose a folder to scan."; return; }
        SetBusy(true);
        if (!remove) ThreatResults.Items.Clear();
        QuickThreat.IsEnabled = FolderThreat.IsEnabled = RemoveThreat.IsEnabled = false;
        StopThreat.Visibility = Visibility.Visible;
        using var cancel = new CancellationTokenSource();
        scanCancellation = cancel;
        var progress = new Progress<string>(message => Status.Text = message);
        try
        {
            var summary = await Task.Run(() => ThreatScanner.Scan(targets, remove, progress, item =>
            {
                if (!remove) Dispatcher.Invoke(() => ThreatResults.Items.Add(item));
            }, cancel.Token));
            Status.Text = summary;
            if (remove && !File.Exists(targets[0]))
            {
                var stale = ThreatResults.Items.OfType<ThreatScanner.Finding>().FirstOrDefault(f => f.File == targets[0]);
                if (stale != null) ThreatResults.Items.Remove(stale);
                Status.Text = "File is no longer present. " + summary;
            }
        }
        catch (OperationCanceledException) { Status.Text = "Scan canceled. Results shown are partial."; }
        catch (Exception ex) { Status.Text = "Scan incomplete. " + ex.Message; }
        finally
        {
            scanCancellation = null;
            SetBusy(false);
            QuickThreat.IsEnabled = FolderThreat.IsEnabled = true;
            StopThreat.Visibility = Visibility.Collapsed;
            ThreatSelected(this, new RoutedEventArgs());
        }
    }

    private void Launch(System.Diagnostics.ProcessStartInfo start, string message)
    {
        try { System.Diagnostics.Process.Start(start); Status.Text = message; }
        catch (Exception ex) { Status.Text = ex.Message; }
    }

    private void ThreatClick(object sender, RoutedEventArgs e)
    {
        var path = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles), "Malwarebytes", "Anti-Malware", "Malwarebytes.exe");
        bool installed = File.Exists(path);
        Launch(new(installed ? path : "https://www.malwarebytes.com/mwb-download") { UseShellExecute = true },
            installed ? "Review scan results and quarantine in Malwarebytes." : "Install Malwarebytes, then choose Open Malwarebytes again.");
    }

    private void AdwareClick(object sender, RoutedEventArgs e) =>
        Launch(new("https://www.malwarebytes.com/adwcleaner") { UseShellExecute = true }, "Open the downloaded AdwCleaner and choose Scan Now.");

    private void ComputerClick(object sender, RoutedEventArgs e) =>
        Launch(new(Path.Combine(Environment.SystemDirectory, "msinfo32.exe")) { UseShellExecute = false }, "System Information shows your model, processor, memory, BIOS and components.");

    private async void BrowserClick(object sender, RoutedEventArgs e)
    {
        if (busy) return;
        ScanMenu.Visibility = Visibility.Collapsed;
        BrowserPanel.Visibility = Visibility.Visible;
        ProfileRows.Children.Clear();
        SetBusy(true);
        Status.Text = "Reading browser profiles…";
        try
        {
            var profiles = await Task.Run(BrowserInventory.Read);
            foreach (var profile in profiles)
            {
                ProfileRows.Children.Add(new System.Windows.Controls.TextBlock { Text = profile.Title, FontSize = 20, Margin = new Thickness(0,18,0,6) });
                AddBrowserAction(profile, "Change default search engine", profile.Edge ? "settings/search" : "settings/searchEngines");
                foreach (var extension in profile.Extensions)
                    AddBrowserAction(profile, extension.Name + " · Review / remove", "extensions/?id=" + extension.Id);
            }
            Status.Text = profiles.Count == 0 ? "No Chrome or Edge profiles found." : "Choose an extension to confirm removal in its browser. Search settings let you choose Google or Bing. Refresh after making changes.";
        }
        catch (Exception ex) { Status.Text = "Could not read browser profiles. " + ex.Message; }
        finally { SetBusy(false); }
    }

    private void AddBrowserAction(BrowserInventory.Profile profile, string label, string page)
    {
        var button = new System.Windows.Controls.Button { Content = label, HorizontalAlignment = HorizontalAlignment.Left, Margin = new Thickness(0,3,0,3) };
        button.Click += (_, _) =>
        {
            var start = new System.Diagnostics.ProcessStartInfo(profile.Executable) { UseShellExecute = false };
            start.ArgumentList.Add("--profile-directory=" + profile.Directory);
            start.ArgumentList.Add((profile.Edge ? "edge://" : "chrome://") + page);
            Launch(start, "Complete the change in " + profile.Title + ", then refresh profiles.");
        };
        ProfileRows.Children.Add(button);
    }

    private void SetBusy(bool value)
    {
        busy = value; Findings.IsEnabled = Back.IsEnabled = !value;
        Apply.IsEnabled = !value && audit is { IsWindows11: true } && choices.Any(x => x.Selected);
        Categories.IsEnabled = Rescan.IsEnabled = !value;
        Apply.Content = $"Apply changes ({choices.Count(x => x.Selected)})";
        Progress.Visibility = value ? Visibility.Visible : Visibility.Collapsed;
    }
    private void SelectionClick(object sender, RoutedEventArgs e) => SetBusy(busy);
    private void FilterChanged(object sender, System.Windows.Controls.SelectionChangedEventArgs e)
    {
        Findings.ItemsSource = Categories.SelectedIndex <= 0 ? choices : choices.Where(x => x.Finding.Action.Category == Categories.SelectedItem as string);
    }
    private async void ScanClick(object sender, RoutedEventArgs e) { if (!busy) await ScanAsync(); }
    private async Task ScanAsync()
    {
        ScanMenu.Visibility = Visibility.Collapsed;
        ReviewPanel.Visibility = Rescan.Visibility = Visibility.Visible;
        audit = null;
        choices.Clear();
        Findings.ItemsSource = null;
        Categories.ItemsSource = null;
        Report.Visibility = Visibility.Collapsed;
        Findings.Visibility = Apply.Visibility = Visibility.Visible;
        SetBusy(true);
        try {
            Status.Text = "Scanning installed apps, privacy settings, startup and optional services…";
            audit = await Task.Run(() => backend.AuditAsync(default));
            choices = audit.Findings.Where(x => x.Available).Select(x => new Choice(x)).ToList(); Categories.ItemsSource = new[] { "All changes" }.Concat(choices.Select(x => x.Finding.Action.Category).Distinct().OrderBy(x => x)).ToArray();
            Categories.SelectedIndex = 0;
            Directory.CreateDirectory(ReportsRoot);
            await File.WriteAllTextAsync(Path.Combine(ReportsRoot, "audit-" + DateTime.UtcNow.ToString("yyyyMMdd-HHmmss-fff") + ".json"), JsonSerializer.Serialize(audit));
            Status.Text = audit.IsWindows11 ? $"{choices.Count} available changes. Select the ones you want to apply." : audit.Summary;
        } catch (Exception ex) { Status.Text = "Scan could not complete: " + ex.Message; }
        finally { SetBusy(false); }
    }
    private async void ApplyClick(object sender, RoutedEventArgs e) => await ApplySelectedAsync();
    private async Task ApplySelectedAsync()
    {
        if (busy || audit is null) return;
        var selected = choices.Where(x => x.Selected).Select(x => x.Finding.Action).ToArray();
        if (selected.Length == 0) { Status.Text = "Select at least one change."; return; }
        var destructive = selected.Any(x => x.Irreversible || MaintenanceCatalog.Get(x.Id).Destructive);
        if (destructive && MessageBox.Show(EmbeddedOwner ?? this, "Selected changes can uninstall apps and delete their data or saved Recall snapshots. Export anything you need first. Continue with these selected changes?", "Confirm selected removals", MessageBoxButton.YesNo, MessageBoxImage.Warning) != MessageBoxResult.Yes) return;
        var old = engine.Transactions().ToHashSet();
        SetBusy(true); Status.Text = "Backing up settings and applying selected changes…";
        OptimizationTransaction? transaction = null; string? failure = null;
        try {
            transaction = await Task.Run(() => engine.ApplyAsync(audit, selected.Select(x => x.Id), OptimizationProfile.Advanced, true, destructive, default, requireRecoveryPoint: selected.Any(x => MaintenanceCatalog.Get(x.Id).Machine)));
        } catch (Exception ex) {
            failure = ex.Message;
            var id = engine.Transactions().FirstOrDefault(x => !old.Contains(x));
            if (id is not null) {
                try { transaction = JsonSerializer.Deserialize<OptimizationTransaction>(await File.ReadAllTextAsync(Path.Combine(ReportsRoot,id+".json"))); }
                catch (Exception readError) { failure += " Backup report could not be read: " + readError.Message; }
            }
        }
        ShowReport(transaction, selected.Select(x => x.Id).ToArray(), failure);
        audit = null; SetBusy(false); Apply.Visibility = Visibility.Collapsed;
    }
    private void ShowReport(OptimizationTransaction? tx, string[] selected, string? failure)
    {
        var changed = tx?.Changes.Count(x => x.Status == "Verified") ?? 0;
        Status.Text = failure is null ? $"Finished · {changed} changes verified." : "Stopped · " + failure;
        var lines = new List<string>();
        foreach (var id in selected) {
            var c = tx?.Changes.FirstOrDefault(x => x.Id == id);
            var outcome = c?.Status == "Verified" ? "Changed · verified" : c is null || c.Status == "Prepared" ? "Not changed · not applied" : "Not verified · " + c.Status;
            lines.Add(outcome + " — " + OptimizationCatalog.Get(id).Title);
        }

        lines.Add("\nSettings backups and the full audit are saved locally. Removed app data and Recall snapshots cannot be restored from the settings backup. Windows may require sign-out or restart.");
        ReportText.Text = string.Join("\n\n", lines); Findings.Visibility = Visibility.Collapsed; Report.Visibility = Visibility.Visible;
        try { File.WriteAllText(Path.Combine(ReportsRoot,"report-"+DateTime.UtcNow.ToString("yyyyMMdd-HHmmss-fff")+".txt"),Status.Text+"\n\n"+ReportText.Text); }
        catch (Exception ex) { Status.Text += " Report could not be saved: " + ex.Message; }
    }
    private void DoneClick(object sender, RoutedEventArgs e) { if (!busy && ScanMenu.Visibility != Visibility.Visible) { ReviewPanel.Visibility = BrowserPanel.Visibility = MalwarePanel.Visibility = Apply.Visibility = Rescan.Visibility = Visibility.Collapsed; ScanMenu.Visibility = Visibility.Visible; Status.Text = "Choose a scan."; return; }
        if (!busy) { if (ExitRequested is not null) ExitRequested(); else Close(); } }
    public async Task RunSandboxSmokeAsync(string screenshot)
    {
        if (ScanMenu.Visibility != Visibility.Visible || ReviewPanel.Visibility == Visibility.Visible) throw new InvalidOperationException("Scan chooser missing.");
        MalwareClick(this, new RoutedEventArgs());
        DoneClick(this, new RoutedEventArgs());
        if (ScanMenu.Visibility != Visibility.Visible) throw new InvalidOperationException("Malware back navigation failed.");
        backend = new SandboxOptimizationBackend(); engine = new(backend, ReportsRoot);
        await ScanAsync();
        if (choices.Count == 0 || choices.Any(x => !x.Finding.Available)) throw new InvalidOperationException("Available-only scan failed.");
        if (Apply.IsEnabled) throw new InvalidOperationException("Empty selection enabled Apply.");
        choices.Single(x => x.Finding.Action.Id == "advertising").Selected = true;
        SetBusy(false);
        if (!Apply.IsEnabled) throw new InvalidOperationException("Selected change cannot be applied.");
        await ApplySelectedAsync();
        if (!ReportText.Text.Contains("Changed · verified")) throw new InvalidOperationException("Report missing verified result.");
        await engine.RestoreAsync(engine.Transactions().First(),default);
        await ScanAsync();
        if (Apply.IsEnabled || Report.Visibility == Visibility.Visible) throw new InvalidOperationException("Rescan retained stale results.");
        var content = (FrameworkElement)Content; Content = null;
        var surface = new System.Windows.Controls.Border { Background=(Brush)FindResource("WindowBackgroundBrush"), Child=content };
        if (surface.Background is not SolidColorBrush { Color.A:255 }) throw new InvalidOperationException("Embedded page must be opaque.");
        var host = new Window {Width=1040,Height=780,Content=surface}; host.Show(); host.UpdateLayout();
        var bitmap = new RenderTargetBitmap(1040,780,96,96,PixelFormats.Pbgra32); bitmap.Render(host);
        var encoder = new PngBitmapEncoder(); encoder.Frames.Add(BitmapFrame.Create(bitmap));
        using (var file=File.Create(screenshot)) encoder.Save(file);
        host.Content=null; surface.Child=null; host.Close(); Content=content;
    }
    private sealed class Choice
    {
        public OptimizationFinding Finding { get; }
        public bool Selected { get; set; }
        public string Title => Finding.Action.Title;
        public string Description => Finding.Action.Category + " · " + Finding.Action.Impact;
        public Choice(OptimizationFinding finding) { Finding=finding; }
    }
}
