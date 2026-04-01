package app.aaps.plugins.sync.smsCommunicator.keys

import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.PreferenceType
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.IntentPreferenceKey
import app.aaps.plugins.sync.R

enum class SmsIntentKey(
    override val key: String,
    override val titleResId: Int = 0,
    override val summaryResId: Int? = null,
    override val preferenceType: PreferenceType = PreferenceType.ACTIVITY,
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false,
    override val exportable: Boolean = false
) : IntentPreferenceKey {

    OtpSetup(
        key = "smscommunicator_otp_setup",
        titleResId = R.string.smscommunicator_tab_otp_label,
        dependency = BooleanKey.SmsAllowRemoteCommands
    )
}
