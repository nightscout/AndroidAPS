package app.aaps.ios.shell.missing

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.protection.ExportPasswordDataStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Placeholder. The store is reported as switched off, which is a state the screens already handle:
 * the user is asked for the export password every time instead of it being remembered.
 *
 * That is the one honest "off" in this package. Every other answer here would be a guess, but
 * "the password store is not enabled" is exactly true on iOS today - nothing stores one.
 *
 * The real work is not the four methods. `ExportPasswordDataStoreImpl` is ~300 lines of rules -
 * a validity window, a grace period, a reset on expiry, and a cross check of the stored secret
 * against the master password hash - over a storage layer that happens to be Jetpack DataStore.
 * Copying that to iOS would duplicate every rule. The request to move the rules to commonMain
 * behind a small storage port is in `_docs/ios_blockers.md`; the iOS side then needs only the
 * Keychain, which `AppleKeychain` already provides.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosExportPasswordDataStore @Inject constructor(
    private val aapsLogger: AAPSLogger
) : ExportPasswordDataStore {

    override fun exportPasswordStoreEnabled(): Boolean {
        aapsLogger.notOnIosYet("ExportPasswordDataStore.exportPasswordStoreEnabled")
        return false
    }

    override fun clearPasswordDataStore(): String {
        aapsLogger.notOnIosYet("ExportPasswordDataStore.clearPasswordDataStore")
        return ""
    }

    override fun putPasswordToDataStore(password: String): String {
        aapsLogger.notOnIosYet("ExportPasswordDataStore.putPasswordToDataStore")
        return ""
    }

    /** Empty password, expired, about to expire - the shape callers read as "nothing stored". */
    override fun getPasswordFromDataStore(): Triple<String, Boolean, Boolean> {
        aapsLogger.notOnIosYet("ExportPasswordDataStore.getPasswordFromDataStore")
        return Triple("", true, true)
    }
}
