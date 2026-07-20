# NFC Commands Plugin

Allows AAPS to execute command cascades by scanning a registered NFC tag or by manually triggering execution from the My Tags screen.

## Screens

### My Tags tab
Lists all registered NFC tags. Each card shows the tag name, command icons for quick identification, and the last scanned timestamp.
- **▶ Play**: Opens a confirmation dialog listing every queued command (icon, label, formatted parameters) before executing the chain — the same dialog shown for a physical tag scan.
- **Edit**:  Opens Edit mode in the Build screen.
- **🗑 Delete**: Remove the tag from the database.

### Log tab
Chronological history of interactions:
- **Read**: Physical scan results.
- **Write**: New tag registration.
- **Manual**: Execution triggered from the UI.

Each entry shows the tag name, timestamp, success status (color-coded), and the execution result message.

### Build screen
Wizard for assembling command chains. 
- **Tag Name**: Optional; leaving it blank prompts a "write anyway?" confirmation before saving (the tag is stored with a blank name).
- **Commands**: Commands can be added, edited or removed.
- **Pump Compatibility**: Basal commands (Absolute vs Percent) are automatically filtered based on the active pump's driver capabilities; `RunSceneAction` is likewise hidden when no scenes are configured.
- **Overwrite protection**: Writing to a UID that already has a registered tag shows a confirmation (old vs new name) before replacing it.

---

## Available Actions by Category

| Category | Command | Description |
| :--- | :--- | :--- |
| **LOOP** | `LoopStopAction` | Stops the loop. |
| | `LoopResumeAction` | Resumes the loop. |
| | `LoopSuspendAction` | Suspends the loop for a specified duration. |
| | `LoopClosedAction` | Switches to Closed Loop mode. |
| | `LoopLgsAction` | Switches to Low Glucose Suspend mode. |
| **PUMP** | `PumpConnectAction` | Connects/Resumes the pump. |
| | `PumpDisconnectAction` | Disconnects/Suspends the pump for a duration. |
| **BASAL** | `BasalCancelAction` | Cancels any active temporary basal. |
| | `TempBasalAbsoluteAction` | Sets an absolute temporary basal rate (if supported). |
| | `TempBasalPercentAction` | Sets a percentage temporary basal rate (if supported). |
| **TREATMENTS** | `BolusAction` | Delivers a bolus; the optional "Meal" flag also starts an "Eating Soon" temporary target. |
| | `CarbsAction` | Records a carb entry. |
| | `BolusWizardAction` | Computes and previews a calculator-based bolus from stored parameters (BG/IOB/COB/trend toggles), delivering it via the shared wizard executor on confirmation. |
| | `ExtendedSetAction` | Starts an extended bolus. |
| | `ExtendedCancelAction` | Cancels an active extended bolus. |
| **PROFILE**| `ProfileSwitchAction` | Switches the active profile (with percentage). |
| **SCENES** | `RunSceneAction` | Runs a scene |
| **TARGETS**| `TempTargetMealAction` | Sets "Eating Soon" temporary target. |
| | `TempTargetActivityAction` | Sets "Activity" temporary target. |
| | `TempTargetHypoAction` | Sets "Hypo" temporary target. |
| | `TempTargetManualAction` | Sets a "Manual" temporary target (with Glucose and duration) |
| | `TempTargetCancelAction` | Cancels active temporary target. |
| **SYSTEM** | `AAPSCLIENT_RESTART`| Triggers an immediate synchronization with Nightscout. *(commented out of the command registry — not currently selectable)* |
| | `RESTART` | Restarts the AAPS application. *(commented out of the command registry — not currently selectable)* |

---

## Technical Details

### User Entry Logging (UEL)
Every successful execution is logged in the AAPS Treatments history:
- **Source**: `Sources.NfcCommands`.
- **Note**: Contains the **Tag Name** for traceability (allows identifying which physical tag was used).
- **Color**: Matches the `userEntry` theme color.

### Execution Confirmation
Before any chain runs — whether triggered by a physical scan or the "▶ Play" button — `NfcExecutionConfirmationDialog` (`NfcCommonUi.kt`) shows the tag name and each queued command's icon, label, and formatted parameters, requiring explicit confirmation. If the confirmed chain includes a `BOLUS` or `BOLUS_WIZARD` command, a bolus delivery progress dialog (with stop/dismiss controls) is shown while the pump processes it.

### Intent Handling
- **NDEF_DISCOVERED**: For tags written by AAPS.
- **TECH_DISCOVERED / TAG_DISCOVERED**: Fallback for unregistered/blank tags or finished sensors whose UID is manually registered.

### Intent Filter Configuration
The plugin requires `nfc_tech_filter.xml` (`app/src/main/res/xml/`) to declare which tag technologies trigger `TAG_DISCOVERED`/`TECH_DISCOVERED`. It currently declares only `android.nfc.tech.NfcV`; extend it if support for other tag technologies is added.

### Cooldowns & Safety Guards
- **Rewrite cooldown**: After a write, the tag's UID is flagged "just written" for 5 seconds (`NfcTagStore.markJustWritten`/`isJustWritten`) so the reader doesn't immediately re-process its own write as a scan.
- **Remote bolus cooldown**: `BolusAction`/`BolusWizardAction` respect `Constants.remoteBolusMinDistance`, the same minimum-interval guard used by other remote-bolus sources (SMS, Wear, etc.).
- **Cross-pump duration rounding**: Basal durations stored in a tag are rounded up to the currently active pump's basal step (`NfcCommandsPlugin.roundUpToStep`), so a tag written for one pump driver still works after switching to another.

---

## Key Files & Responsibilities

| File | Core Responsibility |
| :--- | :--- |
| `NfcCommandsPlugin` | Main entry point; handles lifecycle, command routing, and feedback (vibration/toast). |
| `NfcControlActivity`| Translucent activity that intercepts System Intents and displays the confirmation dialog. |
| `NfcAction` | Abstract base class for all individual command logic. |
| `NfcCommandCode` | Central Enum defining available commands, and categories. |
| `NfcTagStore` | Handles JSON serialization and persistence of tags and logs in SharedPreferences. |
| `NfcForegroundDispatch`| Manages NFC foreground priority to intercept scans while AAPS is open. |
| `NfcBuildScreen` | UI for the command chain builder. |
| `NfcCommandsScreen` | Main UI container for the My Tags and Log tabs. |
| `NfcCommonUI` | Common UI (confirmation Popup, Commmand Icon display) |
| `NfcJsonKeys` | List of json keys for command parameters |

---

## Settings
- **Allow commands via NFC**: Master switch for execution.
- **NFC foreground priority**: Prioritizes AAPS for NFC scans over other apps (e.g. LibreLink) when AAPS is in the foreground. Enabling it shows a one-time warning dialog (`NfcForegroundDispatch.observeWarning`).
- **Clear Log**: Wipes the NFC interaction history.
