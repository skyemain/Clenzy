# Clenzy Lite for iOS

Native SwiftUI + PhotoKit, iOS 17 and newer, iPhone and iPad. Version 1.0 (1). This branch contains only the iOS Lite app; Android Lite remains on `andriod-lite`.

## Use

Grant all or selected Photos access on launch. Photos fit without cropping; videos show a centered Play button and reveal a seek bar after playback starts. Swipe right to move on; swipe left to queue for Trash. The compact Undo control reverses the last two swipes. Star an item to exclude it from the regular queue. Long-press media for Sort (Newest/Oldest/Size), Queue (Regular/Starred), Settings (Theme/Access check), and About.

Queue position, reviewed order, stars, theme, pending Trash and two undo actions persist locally before advancing. Unseen media comes before reviewed media. Size sorting measures locally available original resource bytes using public PhotoKit APIs; cloud-only sizes remain unknown and sort last. Measuring large libraries can take time. Photos/video preview can retrieve originals from the user's own iCloud library.

## iOS deletion behavior

Left swipe queues an item without changing Photos. The Trash control submits queued items in a single batch to the mandatory iOS confirmation. Undo works before that batch is committed. After deletion use Photos → Recently Deleted to recover; Apple provides no public app-level recovery API for that album. Deletion syncs through iCloud Photos when enabled. No media is sent to Clenzy servers. Limited Photos access only exposes the selected items and does not erase remembered progress for other items.

## Build and install

Open `ClenzyLite.xcodeproj` in full Xcode 15 or later, choose your signing Team, connect an iPhone, and run the ClenzyLite scheme. A free Personal Team can be used for personal device testing with Apple's provisioning limits. TestFlight/App Store distribution requires Apple Developer membership. CI builds unsigned device archives and simulator apps; neither is an installable signed IPA.

Regenerate the project with `python3 scripts/generate-xcodeproj.py`. CI on macOS builds and tests the simulator, captures launch UI, and archives the device app. Run tests locally with:

```
xcodebuild -project ClenzyLite.xcodeproj -scheme ClenzyLite -destination 'platform=iOS Simulator,name=iPhone 16' CODE_SIGNING_ALLOWED=NO test
```

## Development notes

`ReviewState.swift` owns pure queue rules and atomic JSON persistence. `LibraryModel.swift` bridges authorization, library changes, batch deletion and sizes. `MediaViewer.swift` handles PhotoKit previews/AVPlayer lifecycle; views use aspect fit. `GalleryView.swift` and `OptionsView.swift` implement the minimal interface. Tests cover sorting, persistence, stars, two-step Undo and external deletion reconciliation. Physical iPhone/iCloud and limited-library permission behavior still require device validation. No third-party packages or AI models are included.
