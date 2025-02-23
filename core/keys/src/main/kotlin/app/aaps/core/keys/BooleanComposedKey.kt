package app.aaps.core.keys

import app.aaps.core.keys.interfaces.BooleanComposedNonPreferenceKey

enum class BooleanComposedKey(
    override val key: String,
    override val format: String,
    override val defaultValue: Boolean,
    override val exportable: Boolean = true
) : BooleanComposedNonPreferenceKey {

    Log("log_", "%s", false),
    WidgetUseBlack("appwidget_use_black_", "%d", false),
}