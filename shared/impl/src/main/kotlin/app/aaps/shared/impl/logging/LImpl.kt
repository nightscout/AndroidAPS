package app.aaps.shared.impl.logging

import app.aaps.core.interfaces.logging.L
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.LogElement
import app.aaps.core.keys.BooleanComposedKey
import app.aaps.core.keys.interfaces.Preferences

// Built explicitly by both DI frameworks - SharedImplModule for :wear, SharedImplBindings for the
// phone - so it carries no scope of its own. A plain factory, not dagger.Lazy: the phone side is Metro
// now and that type is Dagger vocabulary. Still deferred, because Preferences reads log settings back.
class LImpl(
    private val preferences: () -> Preferences
) : L {

    private var _logElements: List<LogElement>? = null

    override fun logElements(): List<LogElement> {
        if (_logElements == null) {
            _logElements = LTag.entries.map { LogElementImpl(it, preferences()) }
        }
        return _logElements!!
    }

    override fun findByName(name: String): LogElement =
        logElements().find { it.name == name } ?: LogElementImpl(false, preferences())

    override fun resetToDefaults() {
        logElements().forEach { it.resetToDefault() }
    }

    class LogElementImpl : LogElement {

        var preferences: Preferences
        override var name: String
        override var defaultValue: Boolean
        override var enabled: Boolean
        private var requiresRestart = false

        internal constructor(tag: LTag, preferences: Preferences) {
            this.preferences = preferences
            this.name = tag.tag
            this.defaultValue = tag.defaultValue
            this.requiresRestart = tag.requiresRestart
            enabled = preferences.get(BooleanComposedKey.Log, name, defaultValue = defaultValue)
        }

        internal constructor(defaultValue: Boolean, preferences: Preferences) {
            this.preferences = preferences
            name = "NONEXISTENT"
            this.defaultValue = defaultValue
            enabled = defaultValue
        }

        override fun enable(enabled: Boolean) {
            this.enabled = enabled
            preferences.put(BooleanComposedKey.Log, name, value = enabled)
        }

        override fun resetToDefault() {
            enable(defaultValue)
        }
    }
}