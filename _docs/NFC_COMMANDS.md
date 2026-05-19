# NFC Commands Plugin

Allows AAPS to execute command cascades by scanning a registered NFC tag or by manually
triggering execution from the My Tags screen.

## Screens

### My Tags tab

Lists all registered NFC tags. Each card shows:

- Tag name
- Numbered command chain (e.g. `1. LOOP STOP`, `2. BASAL STOP`)
- Tag UID chip

**Card actions (left to right):**

| Icon | Action |
|------|--------|
| ▶ Play | Open the execute confirmation dialog |
| ✎ Edit | Rename the tag |
| 🗑 Delete | Remove the tag from My Tags |

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

### Log tab

Chronological history of all tag reads (physical scans and manual executions) and
tag writes. Each entry shows action label (`Read` / `Write`), tag name, timestamp,
and the execution result message.

### Build screen

Step-by-step wizard for assembling a command cascade and writing it to a physical
NFC tag. Navigate there via the **+** FAB on the My Tags tab.

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
looks up the UID in My Tags.

**Limitation:** `ACTION_TAG_DISCOVERED` matches every NFC tag the phone reads
(credit cards, transit cards, etc.). AAPS appears in the Android app-chooser for
all tags, not just AAPS-written ones. Unknown UIDs are silently ignored (no toast).

Enable **NFC foreground priority** (see Settings) to make AAPS intercept all tags
ahead of other apps while it is in the foreground.

---

## Key classes

| Class | Responsibility |
|-------|---------------|
| `NfcCommandsPlugin` | `executeCascade` / `executeCommand` — all execution logic; `executeWithFeedback` — unified entry point that runs the cascade, appends the log entry, vibrates, and shows a toast |
| `NfcControlActivity` | Handles NFC scan intents (`NDEF_DISCOVERED` and `TAG_DISCOVERED` fallback); calls `prepareExecution` + `executeWithFeedback` |
| `NfcForegroundDispatch` | Manages `NfcAdapter.enableForegroundDispatch` lifecycle for `ComposeMainActivity`; forwards intercepted intents to `NfcControlActivity` and shows the warning dialog when the setting is enabled |
| `NfcCommandsScreen` | My Tags and Log UI; manual execution dialog |
| `NfcBuildScreen` | Command chain builder UI |
| `NfcTagStore` | `@Singleton` class injected with `SP`; persistence for tags and log; static companion methods for command templates, `buildCommand`, `buildCascade`, and `tagUidHex`; `logUpdates: Flow<Unit>` for reactive UI refresh |

---

## Settings

| Key | Description |
|-----|-------------|
| `BooleanKey.NfcAllowRemoteCommands` | Master switch — must be enabled for any command to execute |
| `BooleanKey.NfcForegroundPriority` | When enabled, AAPS intercepts all NFC tags via `enableForegroundDispatch` while the app is in the foreground, taking priority over other apps (e.g. LibreLink). A warning dialog is shown when the setting is first enabled. Dispatch is automatically disabled when AAPS moves to the background. |

---

## Tests

| Test file | Coverage |
|-----------|---------|
| `NfcCommandsPluginTest` | All command processors, `executeCascade` (success, failure, empty list, early stop), write-cooldown rejection in `prepareExecution` |
| `NfcControlActivityTest` | NDEF and TAG_DISCOVERED intent handling, silent ignore for unknown UIDs, `executeWithFeedback` delegated after successful scan |
| `NfcForegroundDispatchTest` | `onResume`/`onPause` lifecycle (preference off, no adapter, enable/disable, idempotent disable), `onNewIntent` routing (NDEF, TAG_DISCOVERED, null action, unrelated action), `observeWarning` subscription and dialog send/suppress logic |
| `NfcTagStoreTest` | Tag persistence, log persistence (success, failure, pruning, ordering), `markJustWritten`/`isJustWritten` (fresh, expired, unknown UID, case-insensitive) |
