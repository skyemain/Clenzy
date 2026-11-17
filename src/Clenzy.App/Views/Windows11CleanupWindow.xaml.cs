using System.ComponentModel;
using System.IO;
using System.Runtime.CompilerServices;
using System.Text.Json;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using Clenzy.Core.Windows11;
using Clenzy.Windows.Windows11;

namespace Clenzy.App.Views;

public partial class Windows11CleanupWindow : UserControl
{
    private IOptimizationBackend _backend = new WindowsOptimizationBackend();
    private OptimizationEngine _engine = null!;
    private OptimizationAudit? _audit;
    private List<Choice> _choices = new();
    private CancellationTokenSource? _operationCts;
    private bool _busy;
    private bool _started;
    private bool _reportShown;
    private bool _administratorRequested;

    public Action? ExitRequested { get; set; }
    public bool AdministratorRequested => _administratorRequested;
    public bool IsBusy => _busy;

    private string ReportsRoot => Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "Clenzy", "Windows11", _backend.Sandbox ? "sandbox" : "live");

    public Windows11CleanupWindow()
    {
        InitializeComponent();
        _engine = new OptimizationEngine(_backend, ReportsRoot);
    }

    private async void PageLoaded(object sender, RoutedEventArgs e)
    {
        if (_started || Environment.GetCommandLineArgs().Contains("--windows11-smoke")) return;
        _started = true;
        await ScanAsync();
    }

    private void SetBusy(bool value)
    {
        _busy = value;
        Findings.IsEnabled = !value;
        Back.IsEnabled = !value;
        Rescan.Visibility = value || _reportShown ? Visibility.Collapsed : Visibility.Visible;
        CancelOperation.Visibility = value ? Visibility.Visible : Visibility.Collapsed;
        CancelOperation.Content = _audit is null ? "Cancel scan" : "Cancel apply";
        Progress.Visibility = value ? Visibility.Visible : Visibility.Collapsed;
        Apply.IsEnabled = !value && !_reportShown && _audit is { IsWindows11: true } && _choices.Any(x => x.Selected);
    }

    private async Task ScanAsync()
    {
        if (_busy) return;
        _reportShown = false;
        _audit = null;
        _choices.Clear();
        Findings.ItemsSource = null;
        Findings.Visibility = Visibility.Visible;
        Report.Visibility = Visibility.Collapsed;
        EmptyState.Visibility = Visibility.Collapsed;
        Apply.Visibility = Visibility.Visible;
        RunAsAdmin.Visibility = Visibility.Collapsed;
        SelectionCount.Text = "0 selected";
        SelectionHint.Text = "Nothing changes during the scan.";
        Status.Text = "Checking Windows apps, privacy options, startup items and optional services…";
        Rescan.Visibility = Visibility.Collapsed;

        var cts = new CancellationTokenSource();
        _operationCts = cts;
        SetBusy(true);
        try
        {
            _audit = await Task.Run(() => _backend.AuditAsync(cts.Token), CancellationToken.None);
            cts.Token.ThrowIfCancellationRequested();
            _choices = _audit.Findings.Where(x => x.Available).Select(x => new Choice(x, OnSelectionChanged)).ToList();
            Findings.ItemsSource = _choices;
            EmptyState.Visibility = _choices.Count == 0 ? Visibility.Visible : Visibility.Collapsed;
            Directory.CreateDirectory(ReportsRoot);
            await File.WriteAllTextAsync(
                Path.Combine(ReportsRoot, "audit-" + DateTime.UtcNow.ToString("yyyyMMdd-HHmmss-fff") + ".json"),
                JsonSerializer.Serialize(_audit),
                cts.Token);

            Status.Text = _audit.IsWindows11
                ? $"{_choices.Count:N0} available changes · nothing selected"
                : _audit.Summary;
            SelectionHint.Text = "Review the effect of each setting before applying it.";
            if (!_audit.IsAdministrator && _audit.Findings.Any(x =>
                    !x.Available && x.BlockedReason?.Contains("administrator", StringComparison.OrdinalIgnoreCase) == true))
            {
                RunAsAdmin.Visibility = Visibility.Visible;
                SelectionHint.Text = "Some changes need administrator access. Run as administrator to see them.";
            }
        }
        catch (OperationCanceledException)
        {
            _audit = null;
            _choices.Clear();
            Findings.ItemsSource = null;
            Status.Text = "Scan canceled. Nothing was changed.";
            SelectionHint.Text = "Choose Scan again when you’re ready.";
        }
        catch (Exception ex)
        {
            _audit = null;
            _choices.Clear();
            Findings.ItemsSource = null;
            Status.Text = "Scan could not complete.";
            SelectionHint.Text = ex.Message;
        }
        finally
        {
            if (ReferenceEquals(_operationCts, cts)) _operationCts = null;
            cts.Dispose();
            SetBusy(false);
            Rescan.Visibility = _reportShown ? Visibility.Collapsed : Visibility.Visible;
        }
    }

    private void OnSelectionChanged()
    {
        var count = _choices.Count(x => x.Selected);
        SelectionCount.Text = $"{count:N0} selected";
        SelectionHint.Text = count == 0
            ? "Choose only the changes you want."
            : "A verified restore point and settings backup are required before changes.";
        Apply.Content = count == 0 ? "Create restore point & apply" : $"Create restore point & apply ({count:N0})";
        Apply.IsEnabled = !_busy && !_reportShown && _audit is { IsWindows11: true } && count > 0;
    }

    private void CancelClick(object sender, RoutedEventArgs e)
    {
        Status.Text = "Canceling safely…";
        _operationCts?.Cancel();
    }

    private async void RescanClick(object sender, RoutedEventArgs e) => await ScanAsync();

    private async void ApplyClick(object sender, RoutedEventArgs e)
    {
        if (_busy || _audit is null) return;
        var selected = _choices.Where(x => x.Selected).Select(x => x.Finding.Action).ToArray();
        if (selected.Length == 0) return;

        var destructive = selected.Any(x => x.Irreversible || MaintenanceCatalog.Get(x.Id).Destructive);
        if (destructive)
        {
            var dialog = new ConfirmDialog(
                "Confirm app removal",
                "These selections may uninstall apps and permanently delete their app data or saved Recall snapshots. Export anything you need first. A restore point cannot recover deleted app data. Continue?",
                "Continue with removals",
                "Keep apps")
            {
                Owner = Window.GetWindow(this)
            };
            if (dialog.ShowDialog() != true) return;
        }

        var selectedIds = selected.Select(x => x.Id).ToArray();
        var previousTransactions = _engine.Transactions().ToHashSet(StringComparer.Ordinal);
        var cts = new CancellationTokenSource();
        _operationCts = cts;
        SetBusy(true);
        Status.Text = "Creating and verifying a restore point, then applying your choices…";
        SelectionHint.Text = "Keep Clenzy open until the report appears.";
        OptimizationTransaction? transaction = null;
        string? failure = null;
        try
        {
            transaction = await Task.Run(
                () => _engine.ApplyAsync(_audit, selectedIds, OptimizationProfile.Advanced, true, destructive, cts.Token, requireRecoveryPoint: true),
                CancellationToken.None);
        }
        catch (OperationCanceledException)
        {
            failure = "Canceled. Any changes completed before cancellation are listed in the report.";
        }
        catch (Exception ex)
        {
            failure = ex.Message;
        }
        finally
        {
            if (ReferenceEquals(_operationCts, cts)) _operationCts = null;
            cts.Dispose();
        }

        if (transaction is null)
        {
            var id = _engine.Transactions().FirstOrDefault(x => !previousTransactions.Contains(x));
            if (id is not null)
            {
                try
                {
                    transaction = JsonSerializer.Deserialize<OptimizationTransaction>(
                        await File.ReadAllTextAsync(Path.Combine(ReportsRoot, id + ".json")));
                }
                catch (Exception readError)
                {
                    failure = string.IsNullOrWhiteSpace(failure) ? readError.Message : failure + " Report could not be read: " + readError.Message;
                }
            }
        }

        ShowReport(transaction, selectedIds, failure);
        _audit = null;
        SetBusy(false);
        Apply.Visibility = Visibility.Collapsed;
        CancelOperation.Visibility = Visibility.Collapsed;
        RunAsAdmin.Visibility = Visibility.Collapsed;
        Rescan.Visibility = Visibility.Collapsed;
    }

    private void ShowReport(OptimizationTransaction? transaction, string[] selected, string? failure)
    {
        var changed = transaction?.Changes.Count(x => x.Status == "Verified") ?? 0;
        Status.Text = failure is null
            ? $"Finished · {changed:N0} changes verified."
            : "Stopped · " + failure;

        var lines = new List<string>();
        foreach (var id in selected)
        {
            var change = transaction?.Changes.FirstOrDefault(x => x.Id == id);
            var outcome = change?.Status == "Verified"
                ? "Changed · verified"
                : change is null || change.Status == "Prepared"
                    ? "Not changed · not applied"
                    : "Not verified · " + change.Status;
            lines.Add(outcome + " — " + OptimizationCatalog.Get(id).Title);
        }

        foreach (var choice in _choices.Where(x => !selected.Contains(x.Finding.Action.Id, StringComparer.Ordinal)))
        {
            lines.Add("Not changed · not selected — " + choice.Title);
        }

        lines.Add("A local report and settings backup are saved on this PC. Removed app data and Recall snapshots cannot be restored from the settings backup. Some Windows settings may need sign-out or restart.");
        ReportText.Text = string.Join(Environment.NewLine + Environment.NewLine, lines);
        Findings.Visibility = Visibility.Collapsed;
        EmptyState.Visibility = Visibility.Collapsed;
        Report.Visibility = Visibility.Visible;
        _reportShown = true;

        try
        {
            Directory.CreateDirectory(ReportsRoot);
            File.WriteAllText(
                Path.Combine(ReportsRoot, "report-" + DateTime.UtcNow.ToString("yyyyMMdd-HHmmss-fff") + ".txt"),
                Status.Text + Environment.NewLine + Environment.NewLine + ReportText.Text);
        }
        catch (Exception ex) when (ex is IOException or UnauthorizedAccessException)
        {
            SelectionHint.Text = "The on-screen report is available, but could not be saved: " + ex.Message;
        }
    }

    private void RunAsAdminClick(object sender, RoutedEventArgs e)
    {
        _administratorRequested = true;
        RequestExit();
    }

    public void RequestExit()
    {
        if (!_busy) ExitRequested?.Invoke();
    }

    public void ShowNavigationBlocked()
    {
        Status.Text = "Finish or cancel the current operation before leaving this page.";
    }

    private void DoneClick(object sender, RoutedEventArgs e) => RequestExit();

    public async Task RunSandboxSmokeAsync(string screenshot)
    {
        _started = true;
        _backend = new SandboxOptimizationBackend();
        _engine = new OptimizationEngine(_backend, ReportsRoot);
        await ScanAsync();
        if (_choices.Count == 0 || _choices.Any(x => !x.Finding.Available))
            throw new InvalidOperationException("Available-only scan failed.");

        var transaction = await _engine.ApplyAsync(_audit!, new[] { "advertising" }, OptimizationProfile.Advanced, true, false,
            CancellationToken.None, requireRecoveryPoint: true);
        ShowReport(transaction, new[] { "advertising" }, null);
        if (!ReportText.Text.Contains("Changed · verified", StringComparison.Ordinal))
            throw new InvalidOperationException("Report missing verified result.");
        await _engine.RestoreAsync(transaction.Id, CancellationToken.None);

        var content = (FrameworkElement)Content;
        Content = null;
        var surface = new Border { Background = (Brush)FindResource("WindowBackgroundBrush"), Child = content };
        if (surface.Background is not SolidColorBrush { Color.A: 255 })
            throw new InvalidOperationException("Embedded page must be opaque.");
        var host = new Window { Width = 1040, Height = 780, Content = surface };
        host.Show();
        host.UpdateLayout();
        var bitmap = new RenderTargetBitmap(1040, 780, 96, 96, PixelFormats.Pbgra32);
        bitmap.Render(host);
        var encoder = new PngBitmapEncoder();
        encoder.Frames.Add(BitmapFrame.Create(bitmap));
        using (var file = File.Create(screenshot)) encoder.Save(file);
        host.Content = null;
        surface.Child = null;
        host.Close();
        Content = content;
    }

    private sealed class Choice : INotifyPropertyChanged
    {
        private bool _selected;
        private readonly Action _selectionChanged;

        public Choice(OptimizationFinding finding, Action selectionChanged)
        {
            Finding = finding;
            _selectionChanged = selectionChanged;
        }

        public event PropertyChangedEventHandler? PropertyChanged;
        public OptimizationFinding Finding { get; }
        public bool IsSelectable => true;
        public string Title => Finding.Action.Title;
        public string Description => Finding.Action.Category + " · " + Finding.Action.Impact;
        public string ChangeKind => Finding.Action.Irreversible || MaintenanceCatalog.Get(Finding.Action.Id).Destructive
            ? "Permanent"
            : "Reversible";
        public bool Selected
        {
            get => _selected;
            set
            {
                if (_selected == value) return;
                _selected = value;
                PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(nameof(Selected)));
                _selectionChanged();
            }
        }
    }
}
