# Clenzy Lite

Clenzy Lite lives on `andriod-lite` and installs separately from Clenzy. It uses Android's media library and video player, with no AI model or native dependencies in the APK. The release workflow publishes a single signed APK in GitHub Releases.

- Allow access to your gallery on first launch. Android 14 and later can grant access to all photos and videos or just selected items. Older supported versions offer the Android media permission for the library.
- Tap **Play** to watch a video. Swipe right for the next item or left to move the current item to Android Trash. Android may ask for confirmation.
- The top star marks an item so it stays out of the regular queue after reopening. Press and hold the photo or video to switch between Regular and Starred, change theme, or open About.
- Undo restores the last two successful Trash actions, newest first, during the current review session. Find it in the brief banner or the long-press menu. Android may also confirm restores.

Requires Android 11 or later for system Trash. Compile with SDK 36, JDK 17, and Gradle 8.11.1. Run `gradle testDebugUnitTest lintDebug assembleRelease` and run `gradle connectedDebugAndroidTest` on an Android 16 emulator. Advance `VERSION` for the next product release. Signing uses the existing Clenzy encrypted GitHub Actions secrets. No key files belong in the branch.
