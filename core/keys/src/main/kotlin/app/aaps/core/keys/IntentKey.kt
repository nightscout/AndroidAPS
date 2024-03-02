package app.aaps.core.keys

enum class IntentKey(
    override val key: Int,
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false
) : PreferenceKey {

    ApsLinkToDocs(key = R.string.key_openaps_link_to_docs),
    SmsOtpSetup(key = R.string.key_smscommunicator_otp_setup, dependency = BooleanKey.SmsAllowRemoteCommands),
    OverviewQuickWizardSettings(key = R.string.key_overview_quick_wizard_settings),
    OverviewCopySettingsFromNs(key = R.string.key_statuslights_copy_ns, dependency = BooleanKey.OverviewShowStatusLights),
    TidepoolTestLogin(key = R.string.key_tidepool_test_login),
    XdripInfo(key = R.string.key_xdrip_info),
}