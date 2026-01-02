package app.aaps.core.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.IntentPreferenceKey

enum class IntentKey(
    override val key: String,
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false,
    override val exportable: Boolean = false
) : IntentPreferenceKey {

    ApsLinkToDocs(key = "link_to_docs"),
    SmsOtpSetup(key = "smscommunicator_otp_setup", dependency = BooleanKey.SmsAllowRemoteCommands),
    OverviewQuickWizardSettings(key = "overview_quickwizard_settings"),
    XdripInfo(key = "xdrip_info"),
}