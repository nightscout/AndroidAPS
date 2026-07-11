# NFC Commands Plugin

Allows AAPS to execute command cascades by scanning a registered NFC tag or by manually triggering execution from the My Tags screen.

## Screens

### My Tags tab
Lists all registered NFC tags. Each card shows the tag name, command icons for quick identification, and the last scanned timestamp.
- **▶ Play**: Manual execution of the command chain.
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
- **Tag Name**: Optional name, defaults to the first command if left blank.
- **Commands**: Commands can be added, edited or removed.
- **Pump Compatibility**: Basal commands (Absolute vs Percent) are automatically filtered based on the active pump's driver capabilities.

---

## Available Actions by Category

| Category | Command | Description |
| :--- | :--- | :--- |
| **LOOP** | `LoopStopAction` | Stops the loop. |
| | `LoopResumeAction` | Resumes the loop. |
| | `LoopSuspendAction` | Suspends the loop for a specified duration. |
| | `LoopCloseAction` | Switches to Closed Loop mode. |
| | `LoopLgsAction` | Switches to Low Glucose Suspend mode. |
| **PUMP** | `PumpConnectAction` | Connects/Resumes the pump. |
| | `PumpDisconnectAction` | Disconnects/Suspends the pump for a duration. |
| **BASAL** | `BasalCancelAction` | Cancels any active temporary basal. |
| | `TempBasalAbsoluteAction` | Sets an absolute temporary basal rate (if supported). |
| | `TempBasalPercentAction` | Sets a percentage temporary basal rate (if supported). |
| **TREATMENTS** | `BolusAction` | Delivers a standard bolus (optionally marked as Meal). |
| | `CarbsAction` | Records a carb entry. |
| | `BolusWizardAction` | Launches BolusWizard on predefined parameters |
| | `ExtendedSetAction` | Starts an extended bolus. |
| | `ExtendedCancelAction` | Cancels an active extended bolus. |
| **PROFILE**| `ProfileSwitchAction` | Switches the active profile (with percentage). |
| **SCENES** | `RunSceneAction` | Runs a scene |
| **TARGETS**| `TempTargetMealAction` | Sets "Eating Soon" temporary target. |
| | `TempTargetActivityAction` | Sets "Activity" temporary target. |
| | `TempTargetHypoAction` | Sets "Hypo" temporary target. |
| | `TempTargetManuelAction` | Sets a "Manual" temporary target (with Glucose and duration) |
| | `TempTargetCancelAction` | Cancels active temporary target. |
| **SYSTEM** | `AAPSCLIENT_RESTART`| Triggers an immediate synchronization with Nightscout. (command disabled) |
| | `RESTART` | Restarts the AAPS application. (command disabled) |

---

## Technical Details

### User Entry Logging (UEL)
Every successful execution is logged in the AAPS Treatments history:
- **Source**: `Sources.NfcCommands`.
- **Note**: Contains the **Tag Name** for traceability (allows identifying which physical tag was used).
- **Color**: Matches the `userEntry` theme color.

### Intent Handling
- **NDEF_DISCOVERED**: For tags written by AAPS.
- **TAG_DISCOVERED**: Fallback for unregistered/blank tags or finished sensors whose UID is manually registered.

### Intent Filter Configuration
The plugin requires a `nfc_tech_filter.xml` (usually in `app/src/main/res/xml/`) to handle various tag technologies (IsoDep, NfcA, NfcB, etc.).

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
- **NFC foreground priority**: Prioritizes AAPS for NFC scans over other apps (e.g. LibreLink) when AAPS is in the foreground.
- **Clear Log**: Wipes the NFC interaction history.
