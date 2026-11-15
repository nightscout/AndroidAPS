package app.aaps.core.keys

import app.aaps.core.keys.interfaces.BooleanComposedNonPreferenceKey

enum class BooleanComposedKey(
    override val key: String,
    override val format: String,
    override val defaultValue: Boolean,
) : BooleanComposedNonPreferenceKey {

    Log("log_", "%s", false),
    /**
     * Stored as `widget_use_black_<widget id>`, NOT under `appwidget_`.
     *
     * It used to be `appwidget_use_black_`, which sits inside `IntComposedKey.WidgetOpacity`'s
     * `appwidget_` prefix - so the stored key matched both, and a prefix lookup answered with
     * whichever came first out of the registration order. That reads a Boolean as an Int.
     * `ComposedKeyPrefixTest` now fails if any prefix swallows another; `MainApp.doMigrations` moves
     * the old values across.
     */
    WidgetUseBlack("widget_use_black_", "%d", false),
    ConfigBuilderEnabled("ConfigBuilder_Enabled_", "%s", false),
}