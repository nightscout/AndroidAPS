# Step 1 · Export your settings on 3.x

The wiki asks you to export your settings before updating. If the install goes well you will not need it, because the settings stay on the phone, but it is your only backup: settings are stored encrypted and neither Android backup nor phone-to-phone transfer carries them.

## What to do, still on 3.x

1. Make sure you have a master password under **Settings → Protection** and that you remember it. The file is encrypted with it.
2. Open **Maintenance** (on 3.x, from the menu in the top corner) and tap **Export settings**. Enter the master password.
3. The file is saved to `Internal storage/AAPS/preferences/`, named like `YYYY-MM-DD_time_appname.json`.
4. Copy the file off the phone: to a computer and to cloud storage.

## What the export contains

According to the wiki: configuration settings, objective status, Nightscout and Tidepool communication settings, automations, profiles and, for tubeless pumps (Omnipod, Medtrum), the active pod's connection details. For Dana RS it holds the pump connection settings, which are only applied when you choose to replace pump settings on import.

## What it does not contain

- Treatment history and the AAPS database. The database is kept on update because you install over the old app with the same keystore.
- The APK and the keystore. Back them up separately (especially the keystore file and its passwords).

## Warnings

- **Objectives:** without an export, losing your settings means redoing every objective to get closed loop back.
- **Pods:** export right after every pod activation. Importing a file from an earlier pod can leave AAPS trying to reach an old pod and stop you from activating a new one.

Next: [Step 2 · Build and install](02-build-and-install.md)
