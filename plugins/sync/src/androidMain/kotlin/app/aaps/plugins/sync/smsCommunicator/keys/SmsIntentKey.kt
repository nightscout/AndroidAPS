package app.aaps.plugins.sync.smsCommunicator.keys

import app.aaps.plugins.sync.SyncStrings
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.PreferenceType
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.IntentPreferenceKey
import app.aaps.core.keys.interfaces.TextRef

enum class SmsIntentKey(
    override val key: String,
    override val title: TextRef,
    override val summary: TextRef? = null,
    override val preferenceType: PreferenceType = PreferenceType.ACTIVITY,
    override val dependency: BooleanPreferenceKey? = null,
    override val exportable: Boolean = false
) : IntentPreferenceKey {

    OtpSetup(
        key = "smscommunicator_otp_setup",
        title = SyncStrings.smscommunicator_tab_otp_label,
        dependency = BooleanKey.SmsAllowRemoteCommands
    )
    ;

}
