package app.aaps.plugins.sync.garmin.keys

import app.aaps.plugins.sync.SyncStrings
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.TextRef

enum class GarminBooleanKey(
    override val key: String,
    override val defaultValue: Boolean,
    override val title: TextRef,
    override val defaultedBySM: Boolean = false,
    override val hideParentScreenIfHidden: Boolean = false,
) : BooleanPreferenceKey {

    LocalHttpServer("communication_http", false, title = SyncStrings.garmin_local_http_server, defaultedBySM = true, hideParentScreenIfHidden = true),
    ;

}
