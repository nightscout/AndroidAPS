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
| `DesktopImportExportPrefs` | **Ready to build - see below** |
| `DesktopAutotune` | Blocked: the algorithm itself is Android-only |
| `DesktopBgQualityCheck` | Not started |
| `DesktopMaintenance` | Partly - log export missing |
| `DesktopCloudDirectoryManager` | No cloud provider on desktop |
| `DesktopHistoryScope` | Not started |

## Next up: import and export of settings

This is the one whose blocker has actually gone.

The old reason for refusing was the **format**, not the file dialogs: a phone has to read back what a
desktop writes, and a second implementation of an encrypted stored format is how two platforms
silently stop matching - surfacing as "your backup will not import" long after the export.

That argument no longer applies. As of the settings-export format move:

- `PrefsFormatCodec` and `PrefsTransfer` are in `commonMain`, so desktop compiles the same format
  code Android and iOS use. There is nothing left to reimplement.
- `JvmCryptoPrimitives` is in `jvmSharedMain`, so desktop already has the real crypto.
- All of it **runs on desktop's own target**: `jvmTest` executes the codec tests, the transfer tests
  and the crypto known-answer vectors, including the file Android froze years ago.

So desktop's foundations are, right now, the best verified of the three platforms - better than iOS,
whose crypto has never executed in any automated gate. What is missing is only the part that was
always genuinely per-platform: picking a file and reading and writing bytes.

`IosImportExportPrefs` is the worked example. A good part of it is Files-app plumbing a desktop does
not need, so expect less than that. It is much less than the 911 + 296 lines the old stub comment
warns about - that count was the Android version, over the Storage Access Framework and WorkManager,
and none of that is being ported.

One thing already done and worth keeping: `DesktopPrefsFileInfo` lists the export folder, so the
screen shows which files exist and then refuses to open one. Seeing the list is what confirms the
folder is right, so keep that behaviour when the refusal goes away.

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
