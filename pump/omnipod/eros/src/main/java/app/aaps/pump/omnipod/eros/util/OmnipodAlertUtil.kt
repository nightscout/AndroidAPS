package app.aaps.pump.omnipod.eros.util

import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.omnipod.common.keys.OmnipodBooleanPreferenceKey
import app.aaps.pump.omnipod.common.keys.OmnipodIntPreferenceKey
import org.joda.time.Duration
import javax.inject.Inject
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.SingleIn

/**
 * Was Java. Kept as functions rather than turned into properties, so the existing `getX()` call sites -
 * Kotlin and Java alike - keep compiling unchanged.
 *
 * javax `@Singleton`, not Metro's `@SingleIn`, on purpose: Dagger still owns this and hands it to Metro
 * through `PumpLeaves`. `@SingleIn` here would have Metro build a second copy, which is the split brain
 * `SplitBrainTest` exists to catch.
 */
@SingleIn(AppScope::class)
class OmnipodAlertUtil @Inject constructor(
    private val preferences: Preferences
) {

    fun getExpirationReminderTimeBeforeShutdown(): Duration? =
        if (preferences.get(OmnipodBooleanPreferenceKey.ExpirationReminder))
            Duration.standardHours(preferences.get(OmnipodIntPreferenceKey.ExpirationReminderHours).toLong())
        else null

    fun getLowReservoirAlertUnits(): Int? =
        if (preferences.get(OmnipodBooleanPreferenceKey.LowReservoirAlert))
            preferences.get(OmnipodIntPreferenceKey.LowReservoirAlertUnits)
        else null
}
