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

Two of the three missing pieces are now in, and both were small:

1. **A Ktor engine on `jvmMain`** - done. `ktor-client-core` is the API only and finds its engine
   with a `ServiceLoader` when the client is built, so a missing one is not a compile error: it
   throws `Failed to find HTTP client engine implementation` on the first real call. OkHttp rather
   than CIO, because `:core:nssdk` already brings OkHttp to this target for Nightscout, so the
   desktop gains an engine binding and not a second HTTP stack. `DesktopHttpEngineTest` builds a
   bare `HttpClient()` so the gap cannot come back through a merge.
2. **An `AuthBrowser` for the JVM** - done, as `DesktopAuthBrowser` in `jvmMain`. It is deliberately
   *not* in `jvmSharedMain`: AWT does not exist on Android, which still wants a Custom Tab. The
   interface's KDoc argues for a window presented over the app, and that argument is a mobile one -
   a phone suspends a backgrounded app within seconds and takes the listener with it. A desktop is
   not suspended, so the system browser is the better answer and the user signs in where they can
   see the address bar. `Desktop.browse` first, then `xdg-open`/`open`/`rundll32`, because AWT
   browse is missing on more Linux sessions than its name suggests - WSLg among them.
3. **The wiring** - still to do, and deliberately. Nothing constructs the shared
   `GoogleDriveProvider` on any platform yet; Android still runs its own `GoogleDriveManager`.
   Desktop should not be first: let Android or iOS prove the shared path against a real Google
   account, because a sign in that half works is worse than a stub that says no.

So desktop is now waiting only on that last step, and on nothing of its own.

Two traps already paid for, worth not re-learning: the stored key names must stay the ones Android
writes (`google_drive_refresh_token`, `google_drive_folder_id` and the rest), or a user keeps their
sign in and silently loses the folder they picked; and a 401 from Drive must be retried with a forced
token refresh before it is believed, or a clock a few minutes fast throws away a working sign in.

## Client builds, and the two folders

Desktop builds one client at a time: `./gradlew :desktop:shell:run -Pclient=2`, and 1 when nothing is
passed. A build property rather than a runtime flag because it settles what a running app cannot -
the installer's name and its icon are written by jpackage at package time. `GenerateBuildInfoTask`
carries the number into `GeneratedBuildInfo.CLIENT`, and `DesktopClientConfig` turns it into the
`AAPSCLIENT1/2/3` flags, the application id and the app name.

The split that matters, and it mirrors a phone exactly:

| | Android | Desktop |
|---|---|---|
| Per client | each client is its own app, with its own private data dir | `~/.aaps`, `~/.aaps2`, `~/.aaps3` |
| Shared | `Documents/AAPS` - exports, `extra` markers | `~/AAPS` - `preferences`, `exports`, `extra` |

So two clients follow two Nightscout sites at once with separate databases, preferences and keys,
while a backup exported by one is visible to the other. Client 1 keeps the unsuffixed `~/.aaps`, so
an install that already exists is never moved.

Everything per client goes through `DesktopFolders.data`. That includes the database: the path is
built in `DesktopAppGraph` and passed to `JvmAppDatabaseBuilder`, which used to construct `~/.aaps`
itself - the one piece of per-client state that could not follow the client, in a module that has no
business knowing where a user's home directory is.

`Main.kt` sets `DesktopFolders.client` as its very first statement, before the single-instance lock
and before the start up logger, because both read a path. Only one instance of the *same* client can
run; a different client is unaffected, because its lock lives in its own directory.

**Still open:** the installer icons. The *window* icon is already per client at runtime
(`ic_yellowowl`, `ic_blueowl`, `ic_greenowl`), but `nativeDistributions` sets no `iconFile` at all,
so every packaged build carries the jpackage default. It needs `.ico` for Windows and `.icns` for
macOS; only `.png` exists today.

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
