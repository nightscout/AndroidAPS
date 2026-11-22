# Step 6 · Final check and common problems

The wiki ends the process by asking you to confirm your settings are correct and to check the release notes for update-specific instructions. This list helps you do that in order; compare it with what you wrote down in step 0.

## Checklist

- [ ] **About** shows 4.0.0.
- [ ] The right pump is selected in **Configuration** (not the Virtual Pump) and connects.
- [ ] The active profile is the one you had and its values (basals, ISF, ratio, targets) match.
- [ ] The active insulin and its DIA are as expected (**Manage → Insulin settings**).
- [ ] Completed objectives are still completed.
- [ ] The loop mode is what you had. If it is in LGS, check the reason in the loop dialog (for example duplicate BG readings).
- [ ] BG readings arrive from your CGM source.
- [ ] Nightscout syncs through NSClientV3 with an access token.
- [ ] Automations (**Manage → Automation**) and QuickWizards are present and enabled.
- [ ] AAPSClient phones, if you use them, are paired.
- [ ] The watch, if you use one, connects and shows data.
- [ ] You made a **fresh export** from 4.0.0 (menu ☰ → Maintenance → Export settings). Optionally enable **Settings → Maintenance → Unattended Settings Export** and add an automation to export weekly or after every pod change.

## Common problems

| Symptom | What to do |
| --- | --- |
| Android refuses to install over the old app | The signature does not match. Use the original keystore. If you do not have it, see "Reinstalling AAPS (new keystore)" in the wiki: it means a fresh install and importing the step 1 file. |
| The Virtual Pump appears after an import | The file named a pump this version lacks. Pick your pump in **Configuration** and check its settings. |
| A profile cannot be activated after an import | Switch to Virtual Pump in **Configuration → Pump**, then back to your pump type. |
| AAPS says it cannot save settings | It lost access to the AAPS directory. Select it again. |
| A new pod cannot be activated after an import | You probably imported a file from an earlier pod. You may need to deactivate or discard it. Always export after activating a pod. |
| Profile or insulin cannot be edited | That is protection. Tap the ✏️ pencil. For the active insulin, create a new one and activate it. |
| Treatments entered on the Nightscout website do not arrive | With client control on they are no longer applied. Use a paired AAPSClient. |
| The loop drops to LGS by itself | Look for duplicate BG readings, remove them and switch back to closed loop by pressing and holding the loop icon. |

For anything else, the wiki's "Troubleshooting (start here)" page and the community on Discord.

Back to the [index](../README.md)
