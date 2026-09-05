# Keeping the desktop shell aligned

A standing task, not a one-off. The desktop client runs the real shared UI and the real shared logic,
so it drifts in a particular way: nobody uses it daily, nothing fails loudly when it falls behind, and
a feature that moves into `commonMain` for iOS's benefit often becomes available to desktop **for
free** without anyone noticing and finishing the job.

This file is where that noticing gets written down.

## The rule

**When you move something into `commonMain` or `jvmSharedMain`, check what it just unblocked on
desktop.** `jvmSharedMain` is the one to watch: it covers Android *and* desktop, so anything landing
there is already running on the desktop target, tested by `jvmTest`, and usually only missing its
platform edges.

The reverse also holds. When a desktop class refuses a call, its KDoc should say *why* - and that
reason has a habit of expiring. A refusal that cites a blocker which has since been removed is worse
than no comment, because the next reader believes it.

## What refuses today

`DesktopNotPortedYet.kt` and `DesktopNotYet.kt` hold the stubs. Each logs rather than pretending, so
nothing is silently half-done:

| Stub | Status |
|---|---|
| `DesktopAutotune` | Blocked: the algorithm itself is Android-only |
| `DesktopBgQualityCheck` | Not started |
| `DesktopMaintenance` | Partly - log export missing |
| `DesktopCloudDirectoryManager` | **Mostly unblocked - see below** |
| `DesktopHistoryScope` | Not started |

## Done: import and export of settings

Kept here as the worked example of how this goes, because the next one looks the same.

The old reason for refusing was the **format**, not the file dialogs: a phone has to read back what a
desktop writes, and a second implementation of an encrypted stored format is how two platforms
silently stop matching - surfacing as "your backup will not import" long after the export.

The move settled it. `PrefsFormatCodec` and `PrefsTransfer` went to `commonMain` and
`JvmCryptoPrimitives` to `jvmSharedMain`, so desktop compiles the same format code and the same
crypto Android runs - not a port of it, the same code. `LocalImportExportPrefs` then carried the
whole flow into `commonMain`, and desktop's entire contribution came to one file,
`JvmPrefsFileAccess`: a directory, a name and some bytes.

It is verified where it counts. `jvmTest` runs the codec tests, the transfer tests and the crypto
known-answer vectors on desktop's own target, including the fixture Android froze years ago - so
desktop's foundations are the best covered of the three, ahead of iOS, whose crypto has still never
executed in any automated gate.

## Next up: Google Drive backup

Same shape, one step behind. The `ios` merge brought a Drive client written for every platform, and
most of it is already running on desktop's target without anyone asking for it:

- `GoogleDriveApi`, `GoogleTokenClient`, `GoogleAuthRequest`, `GoogleDriveProvider` and
  `OAuthCallback` are all in `commonMain`, over Ktor rather than any Google SDK.
- `JvmAuthRedirectListener` is in **`jvmSharedMain`** - the loopback socket that catches the OAuth
  redirect, in plain `java.net`, already compiled into the desktop target.
- `DesktopSp` already implements `KeyValueStore`, which is where the tokens go.

Three things are missing, and none is the hard part:

1. **A Ktor engine on `jvmMain`.** `androidMain` gets OkHttp and `iosMain` gets Darwin; the desktop
   target has none, so nothing can construct an `HttpClient`. Compiles today only because
   `commonMain` takes one as a parameter and never builds one.
2. **An `AuthBrowser` for the JVM.** Note the interface's KDoc argues for an embedded window, and
   that argument is a mobile one - a phone suspends a backgrounded app within seconds and kills the
   listener with it. A desktop app is not suspended, so `java.awt.Desktop.browse()` to the system
   browser is correct here, and the loopback listener will still be there when the redirect lands.
3. **The wiring.** Nothing constructs the shared `GoogleDriveProvider` on any platform yet - Android
   still runs its own `GoogleDriveManager`. Desktop should not be first: let Android or iOS prove the
   shared path against a real Google account, because a sign in that half works is worse than a
   stub that says no.

Two traps already paid for, worth not re-learning: the stored key names must stay the ones Android
writes (`google_drive_refresh_token`, `google_drive_folder_id` and the rest), or a user keeps their
sign in and silently loses the folder they picked; and a 401 from Drive must be retried with a forced
token refresh before it is believed, or a clock a few minutes fast throws away a working sign in.

## Where desktop cannot follow, and that is fine

Not everything should be chased. Some of these are real platform limits and the honest answer is to
say so rather than fake it:

- **`AlertOverrideDoNotDisturb`** - AWT offers one system beep and no notion of silent mode, so
  desktop honours neither half of that setting. Recorded in `AlarmSoundPlayer`'s KDoc.
- **Paired Bluetooth devices** - there is no list to read, which is why `DesktopPairedBtDevices`
  returns an empty list rather than null. Null means "ask the user for a permission" and there is no
  permission to ask for.
- **Roaming and network names** - a `NetworkInterface` has neither, which is why desktop reports
  `ssidReadable = false`.

When a limit is real, write it in the KDoc of the class that hits it and, if a preference is involved,
give the key `platforms` so the row is not drawn where nothing can honour it.
