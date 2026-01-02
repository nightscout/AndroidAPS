package app.aaps.plugins.main.general.overview.keys

import app.aaps.core.keys.interfaces.StringNonPreferenceKey

@Suppress("SpellCheckingInspection")
enum class OverviewStringKey(
    override val key: String,
    override val defaultValue: String,
    override val exportable: Boolean = true
) : StringNonPreferenceKey {

    GraphConfig("graphconfig", "")
}