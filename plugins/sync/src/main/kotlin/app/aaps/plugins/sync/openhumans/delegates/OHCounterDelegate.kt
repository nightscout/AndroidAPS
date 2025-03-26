package app.aaps.plugins.sync.openhumans.delegates

import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.sync.openhumans.keys.OhLongKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KProperty

@Singleton
internal class OHCounterDelegate @Inject internal constructor(
    private val preferences: Preferences
) {

    private var value = preferences.get(OhLongKey.Counter)

    operator fun getValue(thisRef: Any?, property: KProperty<*>): Long = value

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Long) {
        this.value = value
        preferences.put(OhLongKey.Counter, value)
    }
}