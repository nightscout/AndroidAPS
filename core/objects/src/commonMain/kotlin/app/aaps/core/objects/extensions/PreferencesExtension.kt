package app.aaps.core.objects.extensions

import app.aaps.core.keys.interfaces.BooleanNonPreferenceKey
import app.aaps.core.keys.interfaces.DoubleNonPreferenceKey
import app.aaps.core.keys.interfaces.IntNonPreferenceKey
import app.aaps.core.keys.interfaces.LongNonPreferenceKey
import app.aaps.core.keys.interfaces.NonPreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.StringNonPreferenceKey
import app.aaps.core.keys.interfaces.UnitDoublePreferenceKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map

/**
 * Observe changes of a [NonPreferenceKey] and emit [Unit] each time the value changes.
 * Skips the initial replay from the underlying [kotlinx.coroutines.flow.StateFlow] so subscribers
 * only react to actual changes.
 */
fun Preferences.observeChange(key: NonPreferenceKey): Flow<Unit> {
    val source: Flow<Any?> = when (key) {
        is BooleanNonPreferenceKey -> observe(key)
        is StringNonPreferenceKey  -> observe(key)
        is IntNonPreferenceKey     -> observe(key)
        is LongNonPreferenceKey    -> observe(key)
        is DoubleNonPreferenceKey  -> observe(key)
        is UnitDoublePreferenceKey -> observe(key)
        else                       -> error("Unsupported key type: ${key::class.simpleName}")
    }
    return source.drop(1).map { }
}
