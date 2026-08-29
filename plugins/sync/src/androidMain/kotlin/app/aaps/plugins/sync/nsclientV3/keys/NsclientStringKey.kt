package app.aaps.plugins.sync.nsclientV3.keys

import app.aaps.core.keys.interfaces.StringNonPreferenceKey
import app.aaps.core.nssdk.remotemodel.LastModified
import kotlinx.serialization.json.Json

enum class NsclientStringKey(
    override val key: String,
    override val defaultValue: String,
    override val exportable: Boolean = true
) : StringNonPreferenceKey {

    V3LastModified("ns_client_v3_last_modified", Json.encodeToString(LastModified.serializer(), LastModified(LastModified.Collections())))
}