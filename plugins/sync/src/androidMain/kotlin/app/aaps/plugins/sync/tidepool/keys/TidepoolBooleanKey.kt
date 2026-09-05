package app.aaps.plugins.sync.tidepool.keys

import app.aaps.plugins.sync.SyncStrings
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.TextRef

enum class TidepoolBooleanKey(
    override val key: String,
    override val defaultValue: Boolean,
    override val title: TextRef,
    override val summary: TextRef? = null,
) : BooleanPreferenceKey {

    UseTestServers("tidepool_dev_servers", false, title = SyncStrings.title_tidepool_dev_servers, summary = SyncStrings.summary_tidepool_dev_servers),
    ;

}
