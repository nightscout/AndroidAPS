package app.aaps.plugins.sync.tidepool.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.plugins.sync.R

enum class TidepoolBooleanKey(
    override val key: String,
    override val defaultValue: Boolean,
    override val titleResId: Int,
    override val summaryResId: Int? = null,
    override val calculatedDefaultValue: Boolean = false,
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false,
    override val engineeringModeOnly: Boolean = false,
    override val exportable: Boolean = true
) : BooleanPreferenceKey {

    UseTestServers("tidepool_dev_servers", false, titleResId = R.string.title_tidepool_dev_servers, summaryResId = R.string.summary_tidepool_dev_servers),
}