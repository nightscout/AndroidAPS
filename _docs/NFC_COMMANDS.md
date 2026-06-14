# NFC Commands Plugin

Allows AAPS to execute command cascades by scanning a registered NFC tag or by manually triggering execution from the My Tags screen.

## Screens

### My Tags tab
Lists all registered NFC tags. Each card shows the tag name, command icons for quick identification, and the last scanned timestamp.
- **▶ Play**: Manual execution of the command chain.
- **🗑 Delete**: Remove the tag from the database.
- **Tapping the card**: Opens Edit mode in the Build screen.

### Log tab
Chronological history of interactions:
- **Read**: Physical scan results.
- **Write**: New tag registration.
- **Manual**: Execution triggered from the UI.

Each entry shows the tag name, timestamp, success status (color-coded), and the execution result message.

### Build screen
Wizard for assembling command chains. 
- **Tag Name**: Optional name, defaults to the first command if left blank.
- **Command Chain**: Commands can be added, reordered, or removed.
- **Pump Compatibility**: Basal commands (Absolute vs Percent) are automatically filtered based on the active pump's driver capabilities.

---

## Available Actions by Category

| Category | Command | Description |
| :--- | :--- | :--- |
| **LOOP** | `LOOP_STOP` | Stops the loop. |
| | `LOOP_RESUME` | Resumes the loop. |
| | `LOOP_SUSPEND` | Suspends the loop for a specified duration. |
| | `LOOP_CLOSED` | Switches to Closed Loop mode. |
| | `LOOP_LGS` | Switches to Low Glucose Suspend mode. |
| **PUMP** | `PUMP_CONNECT` | Connects/Resumes the pump. |
| | `PUMP_DISCONNECT`| Disconnects/Suspends the pump for a duration. |
| **BASAL** | `BASAL_STOP` | Cancels any active temporary basal. |
| | `BASAL_ABS` | Sets an absolute temporary basal rate (if supported). |
| | `BASAL_PCT` | Sets a percentage temporary basal rate (if supported). |
| **BOLUS** | `BOLUS` | Delivers a standard bolus (optionally marked as Meal). |
| | `CARBS` | Records a carb entry. |
| | `EXTENDED_SET` | Starts an extended bolus. |
| | `EXTENDED_STOP` | Cancels an active extended bolus. |
| **PROFILE**| `PROFILE_SWITCH`| Switches the active profile (with percentage). |
| **TARGETS**| `TARGET_MEAL` | Sets "Eating Soon" temporary target. |
| | `TARGET_ACTIVITY`| Sets "Activity" temporary target. |
| | `TARGET_HYPO` | Sets "Hypo" temporary target. |
| | `TARGET_STOP` | Cancels active temporary target. |
| **SYSTEM** | `AAPSCLIENT_RESTART`| Triggers an immediate synchronization with Nightscout. |
| | `RESTART` | Restarts the AAPS application. |

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
| `NfcCommandCode` | Central Enum defining available commands, icons, and categories. |
| `NfcTagStore` | Handles JSON serialization and persistence of tags and logs in SharedPreferences. |
| `NfcForegroundDispatch`| Manages NFC foreground priority to intercept scans while AAPS is open. |
| `NfcBuildScreen` | UI for the command chain builder. |
| `NfcCommandsScreen` | Main UI container for the My Tags and Log tabs. |

---

## Settings
- **Allow commands via NFC**: Master switch for execution.
- **NFC foreground priority**: Prioritizes AAPS for NFC scans over other apps (e.g. LibreLink) when AAPS is in the foreground.
- **Clear Log**: Wipes the NFC interaction history.
