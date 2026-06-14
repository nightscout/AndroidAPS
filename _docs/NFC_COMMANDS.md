# NFC Commands Plugin

Allows AAPS to execute command cascades by scanning a registered NFC tag or by manually
triggering execution from the NFC Tags screen.

## Screens

### NFC Tags tab

Lists all registered NFC tags. Each card shows:

- Tag name
- Numbered command chain (e.g. `1. LOOP STOP`, `2. BASAL STOP`)
- Tag UID chip

A tag saved without a name defaults to the label of its first command
(e.g. a tag whose first command is `LOOP STOP` is named "Stop Loop").

**Card actions (left to right):**

| Icon | Action |
|------|--------|
| ▶ Play | Open the execute confirmation dialog |
| ✎ Edit | Rename the tag (small dialog, name only) |
| 🗑 Delete | Remove the tag from NFC Tags |

Tapping the card itself (outside the action icons) opens the Build screen in
edit mode for the tag's full command chain.

#### Renaming a tag

The ✎ edit icon opens a dialog with a single text field pre-filled with the
tag's current name. Saving with a blank name falls back to the default
(first command's label), same as leaving the name blank when first creating
the tag.

#### Manual execution

Tapping the play button shows a confirmation dialog:

```
Execute <tag name>?

Commands:
1. LOOP STOP
2. BASAL STOP
```

Pressing **Execute** runs the full cascade without requiring a physical NFC scan.
The result (success/failure + per-command messages) is written to the Log tab with
`action = "READ"`, identical to a real scan.

**Requirements:** _Allow commands via NFC_ must be enabled in plugin settings; the
same remote-command permission check applies to manual execution as to physical scans.

### Scanning a registered tag

By default, scanning a registered tag shows the same confirmation dialog as
manual execution (`Execute <tag name>?`) before running its command cascade.
Enabling **Auto-accept tags** (off by default, see Settings) skips this
dialog for scanned tags — the cascade runs immediately and the result is
logged with `action = "READ"`, identical to confirming the dialog manually.
This setting has no effect on manual execution via the play button on the
NFC Tags tab, which always shows the confirmation dialog.

### Log tab

Chronological history of all tag reads (physical scans and manual executions) and
tag writes. Each entry shows an action badge, tag name, timestamp, and the
execution result message.

The action badge is color-coded by action type:

| Action | Color |
|--------|-------|
| `Read` | Green |
| `Write` | Red |
| `Manual` | Yellow/amber |

Success or failure of the cascade is conveyed by the result message text, not
by the badge color.

### Build screen

Step-by-step wizard for assembling a command cascade and writing it to a physical
NFC tag. Navigate there via the **+** FAB on the NFC Tags tab.

---

## Command reference

| Prefix | Examples |
|--------|---------|
| `LOOP` | `LOOP STOP`, `LOOP RESUME`, `LOOP SUSPEND 30`, `LOOP CLOSED`, `LOOP LGS` |
| `PUMP` | `PUMP CONNECT`, `PUMP DISCONNECT 30` |
| `BASAL` | `BASAL 1.5 30`, `BASAL 75% 30`, `BASAL STOP` |
| `BOLUS` | `BOLUS 5.0`, `BOLUS 5.0 MEAL` |
| `EXTENDED` | `EXTENDED 2.0 60`, `EXTENDED STOP` |
| `CARBS` | `CARBS 30` |
| `TARGET` | `TARGET MEAL`, `TARGET ACTIVITY`, `TARGET HYPO`, `TARGET STOP` |
| `PROFILE` | `PROFILE 1`, `PROFILE 1 100` |
| `AAPSCLIENT` | `AAPSCLIENT RESTART` |
| `RESTART` | `RESTART` |

Cascades execute sequentially; the first failure stops the chain.

---

## Write cooldown

When a tag is written via the Build screen, Android hardware reads it back immediately
after the write completes. To prevent that read-back from triggering command execution,
`NfcTagStore.markJustWritten()` stamps the tag UID with the current timestamp.
`prepareExecution()` checks `isJustWritten()` first (5-second window) and returns an
error if the stamp is still fresh. Subsequent scans — after the tag is removed and
re-presented — execute normally.

---

## Registering arbitrary tags (blank tags, finished Libre sensors, …)

Any NFC tag can trigger a command chain — it does not need to carry AAPS NDEF data.
Use the **+** FAB → Build screen to create a command chain, then instead of writing
to a tag, copy the resulting UID from a physical scan and save the entry with that UID.
When the phone reads the tag, `ACTION_TAG_DISCOVERED` fires as a fallback and AAPS
looks up the UID in NFC Tags.

**Limitation:** `ACTION_TAG_DISCOVERED` matches every NFC tag the phone reads
(credit cards, transit cards, etc.). AAPS appears in the Android app-chooser for
all tags, not just AAPS-written ones. Unknown UIDs are silently ignored (no toast).

Enable **NFC foreground priority** (see Settings) to make AAPS intercept all tags
ahead of other apps while it is in the foreground.

---

## Key classes

| Class | Responsibility |
|-------|---------------|
| `NfcCommandsPlugin` | `executeCascade` / `executeCommand` — all execution logic (suspend); `executeWithFeedback` — unified entry point that runs the cascade, appends the log entry, vibrates, and shows a toast. Pump commands go through the suspend `CommandQueue` API and report the actual `PumpEnactResult` outcome (success/failure) rather than fire-and-forget |
| `NfcControlActivity` | Handles NFC scan intents (`NDEF_DISCOVERED` and `TAG_DISCOVERED` fallback); calls `prepareExecution` + `executeWithFeedback` from a `CoroutineScope(Dispatchers.IO)` |
| `NfcForegroundDispatch` | Manages `NfcAdapter.enableForegroundDispatch` lifecycle for `ComposeMainActivity`; forwards intercepted intents to `NfcControlActivity` and shows the warning dialog when the setting is enabled |
| `NfcCommandsScreen` | NFC Tags and Log UI; manual execution dialog |
| `NfcBuildScreen` | Command chain builder UI |
| `NfcTagStore` | `@Singleton` class injected with `SP`; persistence for tags and log; static companion methods for command templates, `buildCommand`, `buildCascade`, and `tagUidHex`; `logUpdates: Flow<Unit>` for reactive UI refresh |

---

## Settings

| Key | Description |
|-----|-------------|
| `BooleanKey.NfcAllowRemoteCommands` | Master switch — must be enabled for any command to execute |
| `BooleanKey.NfcForegroundPriority` | When enabled, AAPS intercepts all NFC tags via `enableForegroundDispatch` while the app is in the foreground, taking priority over other apps (e.g. LibreLink). A warning dialog is shown when the setting is first enabled. Dispatch is automatically disabled when AAPS moves to the background. |
| `BooleanKey.NfcAutoAcceptTags` | Off by default. When enabled, scanning a registered tag skips the confirmation dialog and executes its command cascade immediately. Manual execution via the play button always shows the confirmation dialog regardless of this setting. |

---

## Tests

| Test file | Coverage |
|-----------|---------|
| `NfcCommandsPluginTest` | All command processors, `executeCascade` (success, failure, empty list, early stop), write-cooldown rejection in `prepareExecution`, `autoAcceptEnabled` reflects `BooleanKey.NfcAutoAcceptTags`, `defaultTagName` (first command's label, empty list, unparseable command) |
| `NfcControlActivityTest` | NDEF and TAG_DISCOVERED intent handling, silent ignore for unknown UIDs, `executeWithFeedback` delegated after successful scan |
| `NfcForegroundDispatchTest` | `onResume`/`onPause` lifecycle (preference off, no adapter, enable/disable, idempotent disable), `onNewIntent` routing (NDEF, TAG_DISCOVERED, null action, unrelated action), `observeWarning` subscription and dialog send/suppress logic |
| `NfcTagStoreTest` | Tag persistence, log persistence (success, failure, pruning, ordering), `markJustWritten`/`isJustWritten` (fresh, expired, unknown UID, case-insensitive) |
