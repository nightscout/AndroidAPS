package app.aaps.plugins.sync.openhumans.delegates

import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.sync.di.OpenHumansScope
import app.aaps.plugins.sync.openhumans.keys.OhStringKey
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.util.UUID
import kotlin.reflect.KProperty

@SingleIn(OpenHumansScope::class)
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