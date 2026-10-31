package app.aaps.implementation.maintenance

/**
 * What the user calls this device, stamped into an export so two backups can be told apart.
 *
 * The one genuinely per-platform value in the whole export path - everything else about a file is
 * shared. iOS asks `UIDevice`, a desktop asks the machine, and Android has its own richer answer in
 * `ImportExportPrefsImpl.detectUserName`, which reaches for the user's chosen device nickname before
 * falling back to the model.
 *
 * Shown to the user and never matched on, so a platform that cannot find a good answer may return
 * something plain rather than inventing one.
 */
expect fun platformDeviceName(): String
