# Step 2 · Build and install 4.0.0

Build 4.0.0 with the same method you used the first time. The wiki lists four:

| Original method | What to do for 4.0.0 |
| --- | --- |
| Browser, **AAPS Builder** | In your private repository (usually `my-aaps`), open the *Build AAPS* page, pick version 4.0.0 and download the app from GitHub. There is no fork to sync. |
| Browser, **fork + preparation file** | In your fork (`AndroidAPS` in your account), sync with upstream and run the **AAPS-CI** workflow. The APK lands in your Google Drive. |
| **Android Studio** | Follow the Android Studio update instructions: pull the changes, switch to version 4.0.0 and generate the signed APK. |
| **Command line** | Same as your original CLI build, but pull the new changes with `git` (for example `git fetch --tags && git checkout 4.0.0`). |

In the code, tag `4.0.0` points to commit `f77abd9b82` (merge of PR #5448) and declares `appVersion = "4.0.0"`. The build uses Java 21 (`JavaVersion.VERSION_21`) and Gradle 9.8.2; if you build locally and get a Gradle JVM error, make sure Android Studio uses a JDK 21.

## Signing: the step that causes most trouble

Sign 4.0.0 with **the same keystore and the same alias** as the installed version. Android only allows installing over the old app when the signature matches, and that is the only way to keep the database and settings. If it does not match, Android forces an uninstall, which deletes everything in the app.

## Installing

- Install the new APK **over** the current version. **Do not uninstall** first. The wiki puts it plainly: to keep your history, always update, never uninstall and reinstall.
- If you use a watch, update the Wear app too, with the APK from the same build. The watch app was rewritten; see the wiki's "AAPS for Wear OS" page if it does not connect.

Next: [Step 3 · Check and import](03-verify-and-import.md)
