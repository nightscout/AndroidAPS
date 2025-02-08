package app.aaps.core.keys

import app.aaps.core.keys.interfaces.IntComposedNonPreferenceKey

enum class IntComposedKey(
    override val key: String,
    override val format: String,
    override val defaultValue: Int
) : IntComposedNonPreferenceKey {

    WidgetOpacity("appwidget_", "%d", 25)
}