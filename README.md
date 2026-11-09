# Clenzy Lite

Clenzy Lite lives on `andriod-lite` and installs separately from Clenzy. It uses Android's media library and video player, with no AI model or native dependencies in the APK. The release workflow publishes a single signed APK in GitHub Releases.

- First launch guides you through gallery access and, on Android 12+, Media management access before swiping. Existing installs see the new setup once. Permission must be approved in Android; continuing without it keeps system confirmations. Android 14 and later can grant access to all photos and videos or just selected items. Older supported versions offer the Android media permission for the library.
- Tap **Play** to watch a video. Swipe right for the next item or left to move the current item to Android Trash. Android may ask for confirmation. On Android 12 or later, long-press and choose **Swipe without prompts** to grant optional Media management access. Android decides whether the permission and your gallery access allow prompt-free Trash.
- Swipe hints animate on the first three media items. Right swipes are remembered locally so reopening starts with unreviewed items and moves previously reviewed items to the end.
- The top star marks an item so it stays out of the regular queue after reopening. Press and hold the photo or video to switch between Regular and Starred, change theme, or open About.
- Undo restores the last two successful Trash actions, newest first, during the current review session. Use the persistent compact Undo icon and count badge at the lower left, or the long-press menu. Successful actions use haptic/accessibility feedback without popups. Both the small outline star and Undo retain 48dp touch targets. Android may also confirm restores.

Requires Android 11 or later for system Trash. Compile with SDK 36, JDK 17, and Gradle 8.11.1. Run `gradle testDebugUnitTest lintDebug assembleRelease` and run `gradle connectedDebugAndroidTest` on an Android 16 emulator. Advance `VERSION` for the next product release. Signing uses the existing Clenzy encrypted GitHub Actions secrets. No key files belong in the branch.
