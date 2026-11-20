# NFC Commands plugin - how it is built

Companion to `_docs/NFC_COMMANDS.md`, which describes what the plugin does for the user. This file
describes how it is wired into the project, and records the few decisions that the code alone does
not explain.

Everything below is the state of the code, not a plan.

---

## 1. Where the code lives

The plugin is Android only, inside the multiplatform `:plugins:sync` module:

| Path | Content |
|---|---|
| `plugins/sync/src/androidMain/kotlin/app/aaps/plugins/sync/nfcCommands/` | 42 files, about 5100 lines |
| `plugins/sync/src/androidHostTest/kotlin/app/aaps/plugins/sync/nfcCommands/` | 6 files, about 1600 lines, 93 tests |
| `plugins/sync/src/androidMain/res/xml/nfc_tech_filter.xml` | the tag technologies the activity accepts |

Inside `nfcCommands/`: `actions/` holds 25 action classes plus their base class, `compose/` holds the
four screen files, and `keys/` holds the one clickable preference key.

### Why `androidMain` and not `commonMain`

This is the decision most likely to be questioned, so the reason is recorded here.

Android delivers a tag scan through **NDEF intent dispatch**, which can start or wake the app. iOS
has Core NFC (`NFCNDEFReaderSession`, `NFCTagReaderSession`) but **no equivalent of that dispatch**:
a tag scan cannot launch the app. "Tap a tag and AAPS acts" therefore does not exist on iOS. On top
of that, the iOS client is a follower, and every NFC action does the opposite of following - it
boluses, sets temp basals, switches profiles.

So the plugin lives beside the other Android-only parts of `:plugins:sync` (the WorkManager
workers, `NSClientV3Service`, the wear service). Five files hold all 23 `android.*` imports:
`NfcControlActivity`, `NfcForegroundDispatch`, `NfcVibration`, `NfcCommandsPlugin` and
`compose/NfcBuildScreen`.

Nothing here blocks a later move to `commonMain`. The parts that would have to change are known -
`System.currentTimeMillis()` in 9 places, `TimeUnit` in 5 - and they are small. What is missing is a
reason, not effort.

---

## 2. Dependency injection

Metro, with no Dagger and no `javax.inject`:

- `NfcCommandsPlugin` is `@Inject`-constructed (`dev.zacsweers.metro.Inject`), scoped
  `@SingleIn(AppScope::class)`, and **registers itself** with `@ContributesIntoMap`, `@IntKey(380)`
  and `@NotNSClient`. The `:app` module holds no NFC-specific wiring.
- `NfcTagStore`, `NfcRuntimeState` and `NfcActionFactory` are injected singletons in the same scope.
- `NfcControlActivity` is a plain `FragmentActivity`, not `MetroAppCompatActivity`: it is declared
  with `Theme.Translucent.NoTitleBar`, and an AppCompat activity needs an AppCompat theme and would
  throw at start up. It injects its ten members through the binding in
  `plugins/sync/src/androidMain/kotlin/app/aaps/plugins/sync/di/SyncMemberInjectors.kt`.
- The activity is declared in the plugin module's own `AndroidManifest.xml`, not in `:app`.

Actions are **not** injected. They are built from what is stored on a tag, so they cannot come out
of a graph; `NfcActionFactory` owns the command-code-to-constructor mapping and passes each action
the dependencies it declares.

---

## 3. Strings

No `R.string` and no `@StringRes` anywhere in the plugin. Text is named, not numbered:

- `SyncStrings`, `CoreUiStrings` and `InterfacesStrings` - generated from each module's
  `res/values/strings.xml` by `GenerateKeyStringsTask` - give a `TextRef` per string name.
- An action's user-visible label is `abstract val label: TextRef`. So are `NfcCategory.label`,
  `NfcUiCategory.label` and the `NfcIntentKey` title and summary.
- Outside Compose, text is resolved with `TextResolver.gs(ref)`. The plugin injects `TextResolver`,
  not `ResourceHelper`, because it needs nothing Android-specific from it.
- Inside Compose, `app.aaps.core.ui.compose.stringResource(ref)` - not the `androidx` one.

**One consequence for tests.** A Mockito stub written as `whenever(rh.gs(eq(X), any()))` never
matches: the mock is only ever asked for the single-argument `gs(ref)`, and the reference arrives
with no arguments attached. Stub the single-argument form and match by name.
`NfcCommandsPluginTest.refNamed()` does this.

---

## 4. What a command is, and where it is stored

A command is a typed `NfcCommand(code, params)` serialized with `kotlinx.serialization`:

```json
{"code":"BOLUS","params":{"insulin":1.0,"isMeal":true}}
```

`NfcParams` has one named, nullable field per kind of value - `insulin`, `carbs`, `glucose`,
`percent`, `duration`, `rate`, `profileName`, `sceneId`, `isMeal` - so a new action usually needs no
new parameter. A tag can hold a chain of commands, and the chain stops at the first failure.

**The tag itself carries no data.** This is easy to get wrong when reading the code, so it is worth
stating plainly: registering a tag writes an **empty** NDEF record whose only job is to identify AAPS
as the owner (`NdefRecord.createMime("application/vnd.app.aaps.command", ByteArray(0))`). The
commands, the tag name and the activity log all live in AAPS preferences, in `NfcTagStore`, keyed by
the tag's **UID**. A read-only tag - an old FreeStyle sensor, for instance - cannot take even that
marker and is registered by UID alone.

Two things follow. A lost tag gives away nothing, because there is nothing on it. And the stored
format can be changed without migrating anything in the field: no version of this plugin has ever
shipped, and even if it had, nothing lives on the tags.

### A missing value is refused, not invented

This is the decision most worth keeping. Each action used to supply its own fallback when a
parameter was absent - `params.insulin ?: 0.0`, `params.carbs ?: 0`. A command that had lost a field
therefore became a dose of zero, or of whatever that action happened to fall back to, while the
screen showed something else.

For insulin and carbs that is not an acceptable way to fail. `NfcAction.executeIfComplete()`
compares the action's declared `argType` list against `params` and **refuses to run** if anything is
missing, telling the user to save the command again. `NfcCommandsPlugin.routeAction()` is the only
production caller of `execute()`, and it calls the gate instead, so no action can be run past the
check by accident.

---

## 5. How a scan becomes a command

1. `NfcControlActivity` receives the tag intent. `NfcAdapter.ACTION_NDEF_DISCOVERED` is accepted only
   when the first record is a MIME record carrying the AAPS type; `ACTION_TECH_DISCOVERED` and
   `ACTION_TAG_DISCOVERED` are accepted as the fallback for tags that hold no marker.
2. Both paths reduce the tag to its UID in hex and look it up with `NfcTagStore.findTagByUid()`. An
   unknown tag is refused with "not registered in AAPS", which is also what happens when the plugin
   is disabled.
3. `routeAction()` checks `NfcAllowRemoteCommands`, then runs the tag's chain through
   `executeIfComplete()`.
4. Each action does its own work through its own dependencies, writes a `UserEntry` with
   `Sources.NfcCommands`, and returns an `NfcExecutionResult(success, message)`.

Results reach the user as **snackbar events on the `RxBus`** (`EventShowSnackbar`). The plugin holds
no `Context`, no `Toast` and no `Handler(Looper.getMainLooper())`.

---

## 6. UI

The four Compose files live in `compose/`. `NfcBuildStateHolder` holds the build screen's state, so
`NfcBuildScreen` is a function of its state rather than an owner of it. The wizard preview is stored
in `NfcRuntimeState` as a typed `WizardBolusExecutor.PrepareResult`, so nothing is cast from `Any`.

Every default value the screens offer comes from `NfcDefaults`. Before that the same numbers were
written as literals in four different places, 15 values in all.

---

## 7. Foreground dispatch, and the one preference that needs care

While AAPS is in the foreground it can claim **all** NFC tags, which is what makes a tag tap work
reliably. The cost is that other apps - notably LibreLink reading a Libre sensor - cannot read a tag
while AAPS is open.

So it is opt-in: `BooleanKey.NfcForegroundPriority`, off by default, and the preference screen warns
what it does. `NfcForegroundDispatch` is driven from `ComposeMainActivity` through six lines
(`onResume`, `onPause`, `onNewIntent`, the lazy field, its import, and `observeWarning`).

The other preference is `BooleanKey.NfcAllowRemoteCommands`, which gates command execution.

---

## 8. What the plugin adds outside its own folder

48 of the 71 changed files are the plugin and its tests. The other 23 are one- or two-line
touchpoints:

| Module | What it gets |
|---|---|
| `core:interfaces` | `ElementType.NFC`, `LTag.NFC` |
| `core:data`, `database` | `Sources.NfcCommands`, its converter and its presentation entry |
| `core:keys` | `NfcAllowRemoteCommands`, `NfcForegroundPriority` |
| `core:ui` | `IcPluginNfc`, an element colour, four `ElementTypeStyle` branches, two strings |
| `appshell` | `ElementType.NFC` in the non-searchable group of `ElementNavigation` |
| `app` | the six `ComposeMainActivity` hooks, and `380` in `ContributedPluginsTest` |
| `plugins/sync` | manifest entry, member injector, strings, `nfc_tech_filter.xml`, the `kotlinx-serialization` plugin |

**Why a plugin reaches into five unrelated modules.** `ElementType` and `Sources` are enums in shared
code, and the `when` expressions over them are exhaustive with no `else`. Adding a value therefore
forces every one of those `when`s to name it. That is the project's own design and it earns its
keep: when `navigate(ElementType)` moved out of `ComposeMainActivity` into `appshell`, the missing
NFC branch was a compile error rather than a silently dead menu row.

---

## 9. Tests

93 tests in `:plugins:sync:testAndroidHostTest`, no instrumented test:

| File | Tests | Covers |
|---|---|---|
| `NfcCommandsPluginTest` | 36 | every command code end to end, the remote-command gate, the refusal of an incomplete command |
| `NfcTagStoreTest` | 31 | the stored blobs, the tag list, the activity log |
| `NfcForegroundDispatchTest` | 15 | enable and disable, intent filters, the warning |
| `NfcControlActivityTest` | 8 | payload and UID reading, unknown tag, missing extras |
| `NfcBuildScreenTest` | 3 | the build screen state holder |
| `TestNfcPreferences` | - | preference double used by the others |

Run them with `./gradlew :plugins:sync:testAndroidHostTest --tests "app.aaps.plugins.sync.nfcCommands.*"`.

---

## 10. Authorship - do not squash

The NFC communication is Jens Heuschkel's work. He has **16 commits** on this branch, under two
different author names with the same email, so a case-sensitive search finds only 4 of them:

```
git log -i --author=heuschkel --format="%h %an <%ae> %s"
```

Forge attribution is already correct, since the email matches either way; only the displayed name
differs, and a `.mailmap` would unify it without rewriting history.

**Never squash across those commits.** `git rebase -i` with `squash` or `fixup` keeps only the first
commit's author and drops the rest. His commits are interleaved with the later work, so a
squash-rebase would erase most of the attribution. If collapsing is ever unavoidable, add
`Co-authored-by: Jens Heuschkel <jens.heuschkel@h-da.de>` trailers.

---

## 11. Open item: twelve strings the SMS communicator already owns

Twelve NFC strings have the same English text as a string `:plugins:sync` already has, all from the
SMS communicator:

`nfccommands_another_bolus_in_queue`, `nfccommands_loop_has_been_disabled`,
`nfccommands_loop_resumed`, `nfccommands_profile_switch_created`, `nfccommands_reconnect`,
`nfccommands_remote_bolus_not_allowed`, `nfccommands_remote_command_not_allowed`,
`nfccommands_remote_command_not_possible`, `nfccommands_restarting`, `nfccommands_tt_set`,
`nfccommands_unknown_command`, `nfccommands_wrong_duration`.

They are kept on purpose. The existing key is named after the feature that owns it
(`smscommunicator_...`), so pointing NFC at it would leave NFC reading a string named after SMS. The
right fix is one generic key used by both, with the two feature-named keys retired - a change to
shared code that is better proposed once this plugin has landed and both callers are visible side by
side.

Five other duplicates were removed rather than kept, because an identical string already existed
under a neutral name: `nfccommands` now uses `CoreUiStrings.nfccommands`, `nfccommands_cmd_loop_lgs`
uses `CoreUiStrings.lowglucosesuspend`, `nfccommands_log_action_manual` uses `CoreUiStrings.manual`,
`nfccommands_clear_log` uses `SyncStrings.clear_log`, and the two NFC keys that both said "Cancel
extended bolus" are now one.

Three near matches are **not** duplicates and were left alone: `:core:ui` writes its user-entry-log
labels in capitals, so `uel_cancel_extended_bolus`, `uel_loop_resumed` and `carbs_g` differ from ours
in case. Reusing one would put shouting text in the action picker. Compare case sensitively.
