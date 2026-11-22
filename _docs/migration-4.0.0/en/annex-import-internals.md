# Annex · How 4.0.0 imports a 3.x file

A user-level summary of what the code at tag `4.0.0` does. The full developer explanation is in [`_docs/IMPORT.md`](../../IMPORT.md); the migrations are in `implementation/src/commonMain/kotlin/app/aaps/implementation/maintenance/migration/PreferenceMigrations.kt`.

## The same migrations on start-up and on import

3.3 exported every setting unfiltered and under old names such as `LocalProfile_0_isf` or `ConfigBuilder_PUMP_DanaRSPlugin_Enabled`. 4.0.0 no longer recognises those names, so it runs the content through a chain of migrations before using it. The same chain runs over the phone's settings on the first start after updating and over the file when you import, so both paths give the same result. The current order is:

`overviewMarks → dynamicIsfRetired → dynIsfAdjust → smsOtpPassword → widgetUseBlack → objectives → configBuilder → localProfile → insulinPlugins → tidepool → apsMode → tempTargetPresets → activityMonitorRetired`

What matters to you:

- **Profiles, selected plugins, objectives and loop mode** are converted to the new names. Without this step they would be lost.
- **The loop mode** from a 3.3 backup (`aps_mode`) becomes a *running mode* record. Without it the loop would come back switched off.
- **Insulin** is no longer a plugin: its old Config Builder rows are removed and the insulin the old records were delivered with is worked out separately.
- **Temp target presets** with a value of zero (something 3.3 could leave behind) are dropped so the default applies. An import never invents presets over yours.
- A value that cannot be converted is logged and left as it is, never guessed.

## What an import never applies

- **Device-bound settings** (39 keys marked non-exportable): pump identity, Nightscout, xDrip, Tidepool and OpenHumans sync cursors, local alarm deadlines and the currently running scene.
- **Pump settings**, unless you tick *Also replace pump settings*. They are still counted so the confirm screen can say how many would change.
- **Names this version does not recognise**, for example from a newer version. They are left alone and logged, not deleted.
- **Values identical** to what the phone already has, so the change stays small and reviewable.
- On a **client** phone, synced settings, because they belong to the main phone.

## A detail on the confirm screen

The preview counts are computed from the file's original names, before migration (migrations only run on apply). With a 3.3 file the preview can show **fewer changes than will actually be applied**. That is expected.
