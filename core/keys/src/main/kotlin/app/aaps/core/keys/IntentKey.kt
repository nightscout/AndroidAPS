package app.aaps.core.keys

enum class IntentKey(
    override val key: String,
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false
) : IntentPreferenceKey {

    ApsLinkToDocs(key = "link_to_docs"),
    SmsOtpSetup(key = "smscommunicator_otp_setup", dependency = BooleanKey.SmsAllowRemoteCommands),
    OverviewQuickWizardSettings(key = "overview_quickwizard_settings"),
    XdripInfo(key = "xdrip_info"),
}