package app.aaps.desktop.shell.platform

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.protection.ExportPasswordDataStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Remembering the export password is **off** on desktop, and this reports that rather than pretending.
 *
 * The feature exists so a user does not retype a long password during a session, and on Android it
 * leans on encrypted storage backed by the platform keystore. Desktop has no equivalent wired up yet:
 * `SecureEncrypt` is one of the bindings still missing, and the only place a password could go without
 * it is a plain file next to the database.
 *
 * Writing a master password to a plain file to save typing is not a trade worth making silently, so
 * [exportPasswordStoreEnabled] answers false and the store stays empty. Every screen that offers to
 * remember the password checks that flag first, so the option is simply not offered - the feature is
 * visibly absent rather than present and insecure.
 *
 * This is a deliberate answer, not a stub waiting to be filled: it should change when desktop gains a
 * real secure store, and not before.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class DesktopExportPasswordDataStore @Inject constructor(
    private val aapsLogger: AAPSLogger
) : ExportPasswordDataStore {

    override fun exportPasswordStoreEnabled(): Boolean = false

    override fun clearPasswordDataStore(): String = ""

    override fun putPasswordToDataStore(password: String): String {
        // Should be unreachable: callers check exportPasswordStoreEnabled() first. Recorded rather
        // than ignored, because reaching it means a caller skipped that check.
        aapsLogger.debug(LTag.CORE, "Export password store is off on desktop; password not kept")
        return ""
    }

    override fun getPasswordFromDataStore(): Triple<String, Boolean, Boolean> = Triple("", false, false)
}
