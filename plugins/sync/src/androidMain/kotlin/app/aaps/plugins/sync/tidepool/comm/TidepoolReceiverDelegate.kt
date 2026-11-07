package app.aaps.plugins.sync.tidepool.comm

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.sync.nsclientV3.ConnectivityGate
import app.aaps.plugins.sync.nsclientV3.ConnectivitySettings
import app.aaps.plugins.sync.nsclientV3.NightscoutConnectivitySettings
import app.aaps.plugins.sync.tidepool.keys.TidepoolBooleanKey
import app.aaps.plugins.sync.tidepool.keys.TidepoolStringKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/** The [ConnectivityGate] for Tidepool, with [TidepoolConnectivitySettings]. */
@SingleIn(AppScope::class)
@Inject
class TidepoolReceiverDelegate(
    aapsLogger: AAPSLogger,
    rh: TextResolver,
    preferences: Preferences,
    receiverStatusStore: ReceiverStatusStore
) : ConnectivityGate(aapsLogger, rh, receiverStatusStore, TidepoolConnectivitySettings(preferences), LTag.TIDEPOOL)

/**
 * The Tidepool connection settings: the Nightscout ones while [TidepoolBooleanKey.UseNsConnectionSettings]
 * is on (the default, and the only choice before 4.0), else Tidepool's own keys (#2993).
 */
class TidepoolConnectivitySettings(private val preferences: Preferences) : ConnectivitySettings {

    private val nightscout = NightscoutConnectivitySettings(preferences)
    private val followNightscout: Boolean get() = preferences.get(TidepoolBooleanKey.UseNsConnectionSettings)

    override val useCellular: Boolean get() = if (followNightscout) nightscout.useCellular else preferences.get(TidepoolBooleanKey.UseCellular)
    override val useRoaming: Boolean get() = if (followNightscout) nightscout.useRoaming else preferences.get(TidepoolBooleanKey.UseRoaming)
    override val useWifi: Boolean get() = if (followNightscout) nightscout.useWifi else preferences.get(TidepoolBooleanKey.UseWifi)
    override val wifiSsids: String get() = if (followNightscout) nightscout.wifiSsids else preferences.get(TidepoolStringKey.WifiSsids)
    override val useOnBattery: Boolean get() = if (followNightscout) nightscout.useOnBattery else preferences.get(TidepoolBooleanKey.UseOnBattery)
    override val useOnCharging: Boolean get() = if (followNightscout) nightscout.useOnCharging else preferences.get(TidepoolBooleanKey.UseOnCharging)

    // Both sets, and the switch between them, can change the result
    override val networkSettingChanges: Flow<Unit> = merge(
        nightscout.networkSettingChanges,
        preferences.observe(TidepoolBooleanKey.UseNsConnectionSettings).drop(1).map { },
        preferences.observe(TidepoolBooleanKey.UseCellular).drop(1).map { },
        preferences.observe(TidepoolBooleanKey.UseRoaming).drop(1).map { },
        preferences.observe(TidepoolBooleanKey.UseWifi).drop(1).map { },
        preferences.observe(TidepoolStringKey.WifiSsids).drop(1).map { }
    )

    override val chargingSettingChanges: Flow<Unit> = merge(
        nightscout.chargingSettingChanges,
        preferences.observe(TidepoolBooleanKey.UseNsConnectionSettings).drop(1).map { },
        preferences.observe(TidepoolBooleanKey.UseOnBattery).drop(1).map { },
        preferences.observe(TidepoolBooleanKey.UseOnCharging).drop(1).map { }
    )
}
