package app.aaps.plugins.sync.nfcCommands.keys

import app.aaps.core.keys.PreferenceType
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.IntentPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.sync.SyncStrings

/**
 * Clickable rows on the NFC preference screen.
 *
 * The click handler is attached where the screen is built, with `withClick { }`, because it needs
 * the plugin instance. Same pattern as [app.aaps.plugins.sync.xdrip.keys.XdripIntentKey] and
 * `SmsIntentKey`.
 */
enum class NfcIntentKey(
    override val key: String,
    override val title: TextRef,
    override val summary: TextRef? = null,
    override val preferenceType: PreferenceType = PreferenceType.CLICK,
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false,
    override val exportable: Boolean = false
) : IntentPreferenceKey {

    ClearLog(
        key = "nfccommunicator_clear_log",
        title = SyncStrings.clear_log,
        summary = SyncStrings.nfccommands_clear_log_summary
    )
    ;
}
