package app.aaps.pump.omnipod.omnipod5.keys

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.keys.PreferenceType
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.IntentPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.pump.omnipod.common.R

enum class O5IntentKey(
    override val key: String,
    override val title: TextRef,
    override val summary: TextRef? = null,
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

    CertificateStore(
        key = "omnipod_5_certificate_store",
        title = TextRef.AndroidRes(R.string.omnipod_5_certificate_store),
        summary = TextRef.AndroidRes(R.string.omnipod_5_certificate_store_summary)
    ),

    PodHistory(
        key = "omnipod_5_history",
        title = TextRef.AndroidRes(R.string.omnipod_common_pod_management_button_pod_history),
    );

    companion object {

        fun ConfigEnabledEntries(config: Config): List<O5IntentKey> = if (config.isEngineeringMode()) {
            listOf(CertificateStore, PodHistory)
        } else {
            listOf(PodHistory)
        }
    }
}
