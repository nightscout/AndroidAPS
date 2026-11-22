# Step 4 · The new interface: where everything is

4.0.0 drops the row of tabs. The main screen is **Overview**, with a bottom bar offering three sheets: **Treatments**, **Scenes** and **Manage**.

## 3.x → 4.0.0 map

| Before (3.x) | Now (4.0.0) |
| --- | --- |
| Row of tabs (Overview, Actions, Treatments…) | Overview as main screen + bottom bar Treatments / Scenes / Manage |
| Config Builder | Renamed **Configuration** |
| Eye checkbox to show a plugin as a tab | Removed |
| **Actions** tab and plugin | Gone; its functions moved (see below) |
| **Automation** plugin | No longer a plugin; lives in **Manage** |
| **Food** plugin | No longer a plugin; lives in **Manage** |
| **Treatments** tab | **Treatments** sheet in the bottom bar |
| History browser and Statistics (TDD) | Menu (☰) |

**Treatments sheet:** bolus wizard, insulin (manual bolus), carbs, treatment (insulin and carbs together), calibration, CGM (opens your CGM app if configured) and QuickWizards.

**Manage sheet:** profile, insulin settings, temp target, QuickWizard presets, scenes, automation, food, site rotation, pump, authorized clients and the pump actions from the old Actions tab (extended bolus, cancel temp basal, prime/fill, sensor insert, pump battery change).

## Where the old Actions tab functions are

| Function | 3.x | 4.0.0 |
| --- | --- | --- |
| Profile switch and percentage | Actions, or long-press the profile name | Tap the profile name on the main screen, then the round ▶ button (bottom right), or Manage → Profile |
| Temp target | Actions | Tap the target on the main screen, or Manage → Temp Target |
| Prime/fill, sensor insert, BG check, other entries | Actions | Expanded status panel, Manage or QuickLaunch buttons |

## Behaviour changes worth knowing

- **New profile switch gesture:** long-press no longer opens the dialog. Tap the name, then ▶.
- **The active insulin cannot be edited:** the one marked ACTIVE is greyed out and cannot be edited or deleted. To change DIA or peak, add a new insulin with +, adjust it, save and tap ▶ Activate.
- **Protection covers more screens:** besides settings, it locks Profile, Insulin settings, Temp Target and QuickWizard management, which become read-only. To edit, tap the ✏️ pencil and enter the password or use biometrics.
- **New things that did not exist before:** Scenes (save and run several actions together), global search, infusion site rotation, new widgets and press-and-hold on the graph to read values.

On the watch, treatment entry is in the watch's own AAPS menu (wizard, eCarbs, treatment…).

Next: [Step 5 · Post-update settings](05-post-update-settings.md)
