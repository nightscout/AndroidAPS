package app.aaps.plugins.sync.nfcCommands.keys

import app.aaps.core.keys.PreferenceType
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.IntentPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.sync.R

/**
 * Clickable rows on the NFC preference screen.
 *
 * The click handler is attached where the screen is built, with `withClick { }`, because it needs
 * the plugin instance. Same pattern as [app.aaps.plugins.sync.xdrip.keys.XdripIntentKey] and
 * `SmsIntentKey`.
 */
enum class NfcIntentKey(
    override val key: String,
    private val titleResId: Int,
    private val summaryResId: Int? = null,
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
        titleResId = R.string.nfccommands_clear_log,
        summaryResId = R.string.nfccommands_clear_log_summary
    )
    ;

    override val title: TextRef = TextRef.AndroidRes(titleResId)
    override val summary: TextRef? = summaryResId?.let { TextRef.AndroidRes(it) }
}
