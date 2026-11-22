# Step 3 · Check the version and import if needed

## Check the version

Open the menu (☰) at the top left and select **About**. It should say 4.0.0.

## Do you need to import?

If you installed over the old app with the same keystore, your settings are already on the phone: on start-up, 4.0.0 converts the 3.x setting names to the new ones by itself. In that case **do not import** out of habit; review your settings (steps 5 and 6) and keep the export as a spare backup.

Import the step 1 export only if:

- something is missing after the update, or
- you had to install from scratch (another phone, new keystore, uninstall).

An import is a **merge**: it writes the values in the file and leaves settings missing from the file untouched. It never deletes anything.

## How to import on 4.0.0

1. Menu (☰) → **Maintenance**. Under **File management**, pick the source with the **Local** or **Cloud** chip and tap **Import settings**. The newest file is at the top.
2. Pick the file, enter the master password and tap **Decrypt & Review**.
3. Decide on **Also replace pump settings**:
   - **Unticked (default):** your current pump, its pairing and its settings on this phone are kept. That is right when updating on the same phone, especially with a running pod.
   - **Ticked:** only when a new phone is taking over the same pump.
4. Tap **Import**. AAPS asks **Apply imported settings?** and writes nothing until you tap **OK**. **Cancel** leaves the phone unchanged.
5. Settings apply without restarting the app; only plugins whose settings changed restart. If the pump is busy, AAPS waits and offers a retry after one minute. Any treatment queued during the import is dropped and reported as cancelled.

## New phone

Install the APK first and select the AAPS directory again. If AAPS loses access to the directory (after a reinstall or clearing storage), it shows a notification and stops saving settings until you reselect it. With Dana RS you must pair the new phone and the pump manually.

Next: [Step 4 · The new interface](04-new-interface.md)
