package app.aaps.plugins.sync.openhumans.delegates

import app.aaps.core.keys.Preferences
import app.aaps.plugins.sync.openhumans.OpenHumansUploaderPlugin
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KProperty

@Singleton
internal class OHCounterDelegate @Inject internal constructor(
    private val preferences: Preferences
) {

    private var value = preferences.get(OpenHumansUploaderPlugin.OhLongKey.Counter)

    operator fun getValue(thisRef: Any?, property: KProperty<*>): Long = value

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Long) {
        this.value = value
        preferences.put(OpenHumansUploaderPlugin.OhLongKey.Counter, value)
    }
}