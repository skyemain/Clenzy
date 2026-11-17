using System.Collections.ObjectModel;
using Clenzy.Presentation.Infrastructure;

namespace Clenzy.Presentation.ViewModels;

public sealed class HistoryPageViewModel : PageViewModel
{
    public HistoryPageViewModel(PresentationContext context, Action back)
        : base("Activity", back)
    {
        Items = new ObservableCollection<HistoryItemViewModel>(
            context.History.GetRecent(50).Select(record => new HistoryItemViewModel(record)));
    }

    public ObservableCollection<HistoryItemViewModel> Items { get; }

    public bool HasItems => Items.Count > 0;

    public string EmptyMessage => "Your cleanup reports will appear here after your first scan.";
}
