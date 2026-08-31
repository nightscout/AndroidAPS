package app.aaps.di.pump

import app.aaps.pump.eopatch.alarm.AlarmManager
import app.aaps.pump.eopatch.alarm.AlarmRegistry
import app.aaps.pump.eopatch.alarm.IAlarmManager
import app.aaps.pump.eopatch.alarm.IAlarmRegistry
import app.aaps.pump.eopatch.ble.IPatchManager
import app.aaps.pump.eopatch.ble.PatchManager
import app.aaps.pump.eopatch.ble.PreferenceManager
import app.aaps.pump.eopatch.ble.PreferenceManagerImpl
import app.aaps.pump.eopatch.core.Patch
import app.aaps.pump.eopatch.core.scan.IBleDevice
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Provided from `:app` so `:pump:eopatch` needs no Dagger processor.
 *
 * The `@Binds` carry no scope: Metro rejects a scoped `@Binds`, and each implementation is already
 * `@Singleton` itself, so the behaviour is the same.
 */
@Module(includes = [EopatchPrefModule::class])
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class EopatchModule {

    @Binds abstract fun bindBleDevice(patch: Patch): IBleDevice

    @Binds abstract fun bindPatchManager(patchManager: PatchManager): IPatchManager

    @Binds abstract fun bindAlarmManager(alarmManager: AlarmManager): IAlarmManager

    @Binds abstract fun bindAlarmRegistry(alarmRegistry: AlarmRegistry): IAlarmRegistry

    @Binds abstract fun bindPreferenceManager(preferenceManager: PreferenceManagerImpl): PreferenceManager
}
