package app.aaps.plugins.sync.nsclientV3.keys

import app.aaps.core.keys.interfaces.BooleanNonPreferenceKey

enum class NsclientBooleanKey(
    override val key: String,
    override val defaultValue: Boolean,
    override val exportable: Boolean = true
) : BooleanNonPreferenceKey {

    NsPaused("ns_client_paused", false)
}