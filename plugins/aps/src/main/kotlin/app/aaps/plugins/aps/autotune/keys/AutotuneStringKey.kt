package app.aaps.plugins.aps.autotune.keys

import app.aaps.core.keys.interfaces.StringNonPreferenceKey

enum class AutotuneStringKey(
    override val key: String,
    override val defaultValue: String,
    override val exportable: Boolean = true
) : StringNonPreferenceKey {

    AutotuneLastRun("key_autotune_last_run", ""),
}
