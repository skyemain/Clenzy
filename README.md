# Clenzy for Android

The Android release version is set in `VERSION`. Commit Android changes only to `android`. Keep this as the only Markdown file. Main remains the approved production catalog.

## Shared-storage workflow
Scan my phone requests one optional all-files grant (Android 11+) and scans primary shared storage automatically. Category shortcuts open their sheet even when empty; empty results explain the primary-storage scope and provide access repair/rescan. Videos include every size. Screenshot detection supports Screen captures and spaced/hyphenated names. Downloads scans recognize both Download and Downloads. Root listing failures fail the scan; unreadable subfolders and traversal limits are reported as partial scans. All-files access is rechecked at scan execution. Traversal is breadth-first to reduce deep-folder starvation. The results screen always shows duplicate copies, videos, Downloads and screenshots with real counts, including zero. Other large files appear when found. Categories can overlap; summary bytes and deletion count each URI once. Duplicate originals stay protected in every category. No folder picker is needed for this flow. Android/data and Android/obb are excluded; Android/media is included. Other apps’ private caches remain inaccessible. Android 10 uses media-only fallback; Android 8/9 use storage runtime permission.

Home offers direct Large videos, Screenshots, Duplicate copies and Downloads shortcuts; each scans shared storage then opens the matching category. Category sheets provide filename search, largest/oldest/newest/name sorting, live selected-byte totals, Select all / Select extra copies and Clear. Bulk actions affect only the filtered rows. Image previews and Android 10+ video thumbnails load off the main thread; unsupported files use type icons. Duplicate originals are kept and cannot be selected in the main flow. Deletion still requires explicit confirmation. Shared-file deletes are permanent. Scans bound traversal at 100,000 entries and duplicate hashing at 16 GiB per scan; unmatched/unhashed files are never presented as verified duplicates. Only primary shared storage is currently scanned. Apps lists visible launcher apps sorted by measured app+data size with optional Usage access. Cache is shown separately without double counting. Last-use records are queried for 90 days; missing records are labeled unknown. Android Settings handles cache removal, and Android confirms uninstall.

All-files access requires separate Google Play eligibility review; this is a sideload candidate, not a claim of Play approval or complete CCleaner parity. Native tests cover automatic discovery, duplicate bulk selection, verified deletion, Downloads scope, stale files and symlink/private-data exclusions.

## Build and signing

Use JDK 17, Gradle 8.11.1, Android SDK 36 and AGP 8.9.2. Run `gradle testDebugUnitTest lintDebug assembleDebug assembleRelease`. With an emulator/device, run `gradle connectedDebugAndroidTest`.

CI restores the permanent release key from encrypted repository secrets CLENZY_KEYSTORE_BASE64 and CLENZY_STORE_PASSWORD. PKCS12 alias: clenzy. Never commit the key or password; keep an independent secure backup. Local signing accepts CLENZY_KEYSTORE (absolute path) and CLENZY_STORE_PASSWORD environment variables. Missing secrets produce an unsigned release and prevent publication. Version codes increase with the workflow run number. The signed candidate is published under release/ after successful checks. Switching from the old debug candidate requires a one-time uninstall; subsequent upgrades must retain the same release key.

## Architecture and additional tools
Native Java/Material 3/AppCompat UI with AndroidX ViewModel and RecyclerView. HomeActivity shows measured available storage in a ring, Scan my phone, Settings and persistent Home/Apps navigation. Apps are shown in their own page. CleanActivity implements the approved guided scan, category sheets, confirmation and verified results, using real scan data and indeterminate progress. MainActivity retains the additional SAF/media/cache tools reachable from Settings. CleanupViewModel owns background work and retained state. SharedStorageCleaner handles primary shared storage; DocumentCleaner handles SAF; MediaCleaner handles MediaStore; OwnCacheCleaner deletes only Clenzy’s unchanged cache files older than 24 hours. ReviewRules handles filters and keeper selection.

Settings provides Photos & videos and Clenzy cache as additional tools. Media access can be partial; Android 11+ trash operations use the system confirmation and report verified trash counts, never claimed reclaimed bytes. SAF supports authorized providers; folder deletion may affect synced copies. Folder scanning caps at 5,000 entries/12 levels; media at 10,000 items. Their default duplicate hashing budget is 512 MiB, 64 MiB per file; legacy deep scanning allows 4 GiB/1 GiB. Shared storage uses the separate limits above.

No internet, analytics, root, Accessibility service or task killing. Optional all-files and Usage access are granted through Android Settings. Model state survives Activity recreation; process death requires a fresh scan. Cleanup history stores up to 20 verified folder/cache/shared-file results locally. File size/time and duplicate hashes are rechecked before deletion; symlinks and unscanned paths are rejected. A retained duplicate keeper must still exist and match before extra copies are deleted.

## Remaining limits
No automatic private app-cache cleaning, residual-file classification, empty-directory deletion, similar-photo detection, or removable-volume scanning. Other-app cache cleaning opens Android app settings. Physical-device/OEM behavior, revoked permission and cloud-provider edge cases require more testing before production. Do not claim exact CCleaner parity. Google Play broad-storage/media permission eligibility is separate from successful builds.

## Verification
Unit and Android 35 instrumentation cover review rules, provider/media/cache operations, real automatic shared-storage scan and bulk duplicate deletion, retained-copy checks, changed files, Downloads scope, symlinks, private Android directories and UI recreation. The signed candidate is published only after CI passes.

Platform references: [all-files access](https://developer.android.com/training/data-storage/manage-all-files), [SAF](https://developer.android.com/training/data-storage/shared/documents-files), [media access](https://developer.android.com/training/data-storage/shared/media), [StorageStatsManager](https://developer.android.com/reference/android/app/usage/StorageStatsManager).


## Minimal native design
The native UI follows the interactive prototype with minimal text, compact category cards, native line icons and no promotional or demo-cleanup copy. The shipped app uses real filesystem totals and operations, not prototype fixtures. Native instrumentation captures Home, category review, selection sheets and results; verifies automatic discovery, duplicate bulk selection, keeper protection, rotation and actual deletion. The category total counts removable extras rather than originals. Personal files are not preselected. Remaining platform limits above still apply.

Validated 1.7.0: [CI 41945998280](https://github.com/skyemain/VCleaner/actions/runs/41945998280) passed unit tests, lint, APK/AAB builds, signature checks and all 17 Android 35 instrumentation tests. New tests launch Downloads, Screenshots and Large videos shortcuts with disposable shared-storage files, including DCIM/Screen captures and Downloads paths, and verify a missing scan root fails rather than returning empty success. Existing tests cover filtered bulk selection and original protection. [Download APK](release/Clenzy-1.7.0-android-candidate.apk). SHA256: `2e285fd19f6f6a59cfe7adac2666d9aa66bbacd84c3d1c1131fa2e5d2f836438`. Signing identity unchanged. User reports a Samsung phone; model, Android version and actual All files access state are not yet known. Physical Samsung verification remains pending. This fixes confirmed shortcut/empty-state and scan-reporting defects; it does not establish the exact cause of the user's missing screenshots or claim whole-device/private-app access.

## Analyze with AI (Android candidate)
Analyze with AI opens AI Smart Clean from Home. The scan reads accessible MediaStore content URIs, including selected-photo access. Bundled ML Kit models run Latin-script OCR, generic labeling, barcode and face detection on bounded images and sampled video frames. No media or OCR text is sent to a server or written to the analysis database. Semantic categories use rules, with English promotional, greeting and important-document vocabulary. Shared origin and low quality are review signals. SHA-256 identifies byte matches; perceptual hashes identify possible visual matches. The private cache reuses unchanged analyses and stores keep decisions. Scans use a cancellable WorkManager coroutine. System Trash requires Android 11 or later; older phones can review and preview files. Trash does not immediately reclaim capacity. No item starts selected.

## Android 2.0 image analysis research
Galaxy S24 target. Google's ML Kit GenAI supported-device list excludes S24 and background inference, so this build bundles an independent SigLIP 2 image encoder with precomputed text-description vectors. Model output ranks visual categories, supplemented by OCR, QR, faces and generic labels. Ranking thresholds are conservative engineering gates, not calibrated accuracy percentages. Borderline cases remain manual review. Personal-media and document evidence overrides cleanup suggestions. Latin and Devanagari OCR run locally; matching translated cleanup phrases currently covers English and limited Hindi. EXIF rotation and mirroring are applied before analysis. Video samples span the timeline, with stronger agreement required across frames.

Model provenance: Google SigLIP 2 base patch16-224, Apache-2.0. ONNX conversion by onnx-community, revision ba1f3b0843f24bc5417d38e19c37b287d719b2f4. Quantized vision SHA256 5f2b401c1a4fc095702a5d45348e17ad46c4f87064085365b43c6e8eaa5c0070. Build downloads and checks the model, then bundles it for offline use. Text vectors derive from the matching text encoder, with lowercase prompts padded to 64 tokens, pooler_output, per-prompt L2 normalization, then averaged and normalized per category.

Research sources:
- https://developers.google.com/ml-kit/genai
- https://huggingface.co/google/siglip2-base-patch16-224
- https://huggingface.co/onnx-community/siglip2-base-patch16-224-ONNX
- https://developers.google.com/ml-kit/vision/text-recognition/v2/android
- https://onnxruntime.ai/docs/get-started/with-java

Evaluation currently uses public scikit-image astronaut (NASA/public domain) and Chelsea cat (Stefan van der Walt, CC0) plus synthetic receipt, promotion, screen and greeting graphics. These are smoke tests and false-positive checks, not a representative benchmark or a measured Galaxy S24 accuracy percentage. APK size alone does not establish accuracy. Model labels, OCR and handcrafted rules can all make mistakes. All AI results are manually selectable; Android Trash confirmation remains required. Protection and media locks were removed in 2.2.0, including legacy stored locks.

## Android 2.2.0 UI and scan continuity

Aurora glass surfaces share narrow horizontal margins, transparent navigation icons and themed action menus. Home has Scan and Scan with AI. Settings exposes device storage, cleanup history, live shared-storage/media permission status, Clenzy cache and About. Old automatic protection and user locks no longer block selection or cleanup; old result categories migrate to manual review without rescanning.

AI scanning uses a WorkManager foreground worker with an ongoing processed-count notification and a completion notification (requires notification permission). Results survive navigation and verified trash operations. Time remaining is an estimate based on at least five newly analyzed files; video complexity and thermal throttling can change it. Android scheduling and battery controls still apply. A new Scan again starts a fresh enumeration and analysis; retries only reuse the same run's saved work.

Scan navigation: Back to Home (including in-app Back while scanning) cancels the active work and clears its active run ID. Switching apps or turning the screen off does not cancel it. The foreground worker no longer pauses/retries itself every seven minutes; OS-interrupted runs retain per-file checkpoints. Force-stop and device battery restrictions can still stop or delay work.

## Android 1.0 interaction audit

The public version comes from `VERSION`; Android versionCode remains monotonic so this is an update to earlier 2.x candidates. Shared AuroraDialog handles app-owned confirmations, sorting, filters, history and explanations, with bounded scrolling for longer text/lists. Runtime permission dialogs, MediaStore Trash consent, Android Settings, document providers and external media viewers belong to Android or the selected app and retain their native interfaces.

Fixed: default app AlertDialogs; legacy category spinner; redundant cache duplicate-selection controls; inconsistent margins/result insets; silent AI preview failure; duplicate Recommended/Largest sort choices; media access off versus partial status; and missing AI cleanup-history entries. Automated journeys cover category shortcuts, sorting/filtering, select/clear, cleanup confirmation/cancel, cache deletion, Settings navigation, Android Trash handoff, result persistence, background completion and scan cancellation. These are emulator checks, not an exhaustive test of every Samsung One UI/Android version or external gallery app.


## Android distribution

Publish and share one signed universal APK for direct installation. It bundles ARM64, ARMv7, x86 and x86-64 support and the offline AI model. The minimum install version is Android 8. MediaStore Trash and same-name media replacement require Android 11 or later.

Advance `VERSION` for each new product release (1.1, 1.2, 1.3 and so on) until the user requests a reset. Android's internal version code keeps increasing so an update can install over the previous signed release.

## Gallery tools

Settings opens Swipe review directly on accessible gallery photos. Left swipes queue photos for Android Trash confirmation, right swipes keep them, and Undo reverses a pending swipe. The gesture hint runs on the first three photos. AI results hide unclassified items and let users browse detected people, animals, landscapes and objects without preselecting them for cleanup.

On Android 11 and later, Compress media prepares an on-device preview for JPEG, PNG and supported SDR MP4 files. It compares the real output size and replaces the same gallery item and filename only when the output is smaller and the user approves. Quality and embedded metadata can change. A private recovery copy is held until the write is verified. If a replacement is interrupted, reopen Compress media to recover the original.
