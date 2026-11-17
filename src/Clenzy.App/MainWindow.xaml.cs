using System.ComponentModel;
using System.Windows;
using System.Windows.Input;
using System.Windows.Interop;
using Clenzy.Presentation.ViewModels;

namespace Clenzy.App;

public partial class MainWindow : Window
{
    private readonly MainViewModel _viewModel;
    private readonly Func<bool> _minimizeToTray;
    private Views.Windows11CleanupWindow? _debloatPage;
    private bool _sessionEnding;

    public MainWindow(MainViewModel viewModel, Func<bool> minimizeToTray)
    {
        _viewModel = viewModel;
        _minimizeToTray = minimizeToTray;
        DataContext = viewModel;
        InitializeComponent();
        SourceInitialized += (_, _) => ApplyTitleBarTheme(ThemeManager.IsDark);
        ThemeManager.ThemeChanged += (_, dark) => ApplyTitleBarTheme(dark);
        _viewModel.PropertyChanged += OnViewModelPropertyChanged;
        PreviewKeyDown += OnPreviewKeyDown;
        StateChanged += OnStateChanged;
        Closing += OnClosing;
        UpdateNavigationState();
    }

    private void OnViewModelPropertyChanged(object? sender, PropertyChangedEventArgs e)
    {
        if (e.PropertyName is nameof(MainViewModel.CurrentPage) or nameof(MainViewModel.IsHome))
        {
            UpdateNavigationState();
        }
    }

    private void UpdateNavigationState()
    {
        var selected = DebloatSurface.Visibility == Visibility.Visible ? DebloatNav
            : _viewModel.CurrentPage is SettingsViewModel ? SettingsNav
            : _viewModel.CurrentPage is HistoryPageViewModel ? HistoryNav
            : _viewModel.CurrentPage is null ? HomeNav
            : CleanupNav;
        foreach (var button in new[] { HomeNav, CleanupNav, DebloatNav, HistoryNav, SettingsNav })
        {
            var active = ReferenceEquals(button, selected);
            button.Background = active ? (System.Windows.Media.Brush)FindResource("AccentSoftBrush") : System.Windows.Media.Brushes.Transparent;
            button.Foreground = active ? (System.Windows.Media.Brush)FindResource("TextBrush") : (System.Windows.Media.Brush)FindResource("SecondaryTextBrush");
        }
    }

    private void NavigateHomeClick(object sender, RoutedEventArgs e)
    {
        if (!TryCloseDebloatRoute())
        {
            return;
        }

        _viewModel.NavigateHome();
        UpdateNavigationState();
    }

    private void NavigateCleanupClick(object sender, RoutedEventArgs e)
    {
        if (!TryCloseDebloatRoute()) return;
        _viewModel.NavigateHome();
        if (_viewModel.PrimaryCommand.CanExecute(null))
        {
            _viewModel.PrimaryCommand.Execute(null);
        }
    }

    private void NavigateHistoryClick(object sender, RoutedEventArgs e)
    {
        if (!TryCloseDebloatRoute()) return;
        if (_viewModel.OpenHistoryCommand.CanExecute(null)) _viewModel.OpenHistoryCommand.Execute(null);
    }

    private void NavigateSettingsClick(object sender, RoutedEventArgs e)
    {
        if (!TryCloseDebloatRoute()) return;
        if (_viewModel.OpenSettingsCommand.CanExecute(null)) _viewModel.OpenSettingsCommand.Execute(null);
    }

    private bool TryCloseDebloatRoute()
    {
        if (DebloatSurface.Visibility != Visibility.Visible) return true;
        if (_debloatPage?.IsBusy == true)
        {
            _debloatPage.ShowNavigationBlocked();
            return false;
        }
        _debloatPage?.RequestExit();
        return true;
    }

    private void DebloatClick(object sender, RoutedEventArgs e)
    {
        if (_viewModel.IsBusy || DebloatSurface.Visibility == Visibility.Visible)
        {
            return;
        }

        _viewModel.NavigateHome();
        var page = new Views.Windows11CleanupWindow();
        _debloatPage = page;
        page.ExitRequested = () =>
        {
            DebloatHost.Content = null;
            DebloatSurface.Visibility = Visibility.Collapsed;
            _debloatPage = null;
            UpdateNavigationState();
            if (page.AdministratorRequested && _viewModel.RestartAsAdministratorCommand.CanExecute(null))
            {
                _viewModel.RestartAsAdministratorCommand.Execute(null);
            }
        };
        DebloatHost.Content = page;
        DebloatSurface.Visibility = Visibility.Visible;
        UpdateNavigationState();
    }

    public void MarkSessionEnding() => _sessionEnding = true;

    public void BringToFront()
    {
        if (!IsVisible) Show();
        if (WindowState == WindowState.Minimized) WindowState = WindowState.Normal;
        Activate();
        Topmost = true;
        Topmost = false;
        Focus();
    }

    private void OnPreviewKeyDown(object sender, KeyEventArgs e)
    {
        if (e.Key != Key.Escape) return;
        if (DebloatSurface.Visibility == Visibility.Visible)
        {
            _debloatPage?.RequestExit();
            e.Handled = true;
            return;
        }

        if (_viewModel.CurrentPage is PageViewModel page && page.BackCommand.CanExecute(null))
        {
            page.BackCommand.Execute(null);
            e.Handled = true;
        }
    }

    private void OnStateChanged(object? sender, EventArgs e)
    {
        if (WindowState == WindowState.Minimized && _minimizeToTray()) Hide();
    }

    private void OnClosing(object? sender, CancelEventArgs e)
    {
        if (_sessionEnding || (!_viewModel.IsBusy && _debloatPage?.IsBusy != true)) return;
        e.Cancel = true;
        var dialog = new ConfirmDialog("Clenzy is working", "Cancel the current operation and wait for it to stop before closing Clenzy.",
            "OK", null) { Owner = this };
        dialog.ShowDialog();
    }

    private void ApplyTitleBarTheme(bool dark)
    {
        try
        {
            var handle = new WindowInteropHelper(this).Handle;
            if (handle == IntPtr.Zero) return;
            var value = dark ? 1 : 0;
            if (Clenzy.Windows.DwmInterop.SetAttribute(handle, 20, value) != 0)
                Clenzy.Windows.DwmInterop.SetAttribute(handle, 19, value);
        }
        catch (Exception ex) when (ex is DllNotFoundException or EntryPointNotFoundException)
        {
            // Native title-bar theming is an optional visual enhancement.
        }
    }
}
