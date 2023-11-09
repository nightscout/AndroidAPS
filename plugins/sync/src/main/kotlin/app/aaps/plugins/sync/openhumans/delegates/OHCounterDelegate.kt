package app.aaps.plugins.sync.openhumans.delegates

import app.aaps.core.interfaces.sharedPreferences.SP
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KProperty

@Singleton
internal class OHCounterDelegate @Inject internal constructor(
    private val sp: SP
) {

    private var value = sp.getLong("openhumans_counter", 1)

    operator fun getValue(thisRef: Any?, property: KProperty<*>): Long = value

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Long) {
        this.value = value
        sp.putLong("openhumans_counter", value)
    }
}