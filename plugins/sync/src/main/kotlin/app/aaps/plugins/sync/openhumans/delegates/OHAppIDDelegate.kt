package app.aaps.plugins.sync.openhumans.delegates

import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.sync.openhumans.keys.OhStringKey
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KProperty

@Singleton
internal class OHAppIDDelegate @Inject internal constructor(
    private val preferences: Preferences
) {

    private var value: UUID? = null

    operator fun getValue(thisRef: Any?, property: KProperty<*>): UUID {
        if (value == null) {
            val saved = preferences.getIfExists(OhStringKey.AppId)
            if (saved.isNullOrBlank()) {
                val generated = UUID.randomUUID()
                value = generated
                preferences.put(OhStringKey.AppId, generated.toString())
            } else {
                value = UUID.fromString(saved)
            }
        }
        return value!!
    }
}