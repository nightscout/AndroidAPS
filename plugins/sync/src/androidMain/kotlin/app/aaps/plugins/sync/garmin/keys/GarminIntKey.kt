package app.aaps.plugins.sync.garmin.keys

import app.aaps.plugins.sync.SyncStrings
import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.IntPreferenceKey
import app.aaps.core.keys.interfaces.TextRef

enum class GarminIntKey(
    override val key: String,
    override val defaultValue: Int,
    override val min: Int,
    override val max: Int,
    override val title: TextRef,
    override val defaultedBySM: Boolean = false,
    override val dependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false,
) : IntPreferenceKey {

    LocalHttpPort("communication_http_port", 28891, 1001, 65535, dependency = GarminBooleanKey.LocalHttpServer, title = SyncStrings.garmin_local_http_server_port, defaultedBySM = true, hideParentScreenIfHidden = true),
    ;

}
