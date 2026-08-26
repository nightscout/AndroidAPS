package app.aaps.pump.eopatch.di

import app.aaps.core.interfaces.di.PumpDriver
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.pump.eopatch.EopatchPumpPlugin
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
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap

// The @Binds below carry no scope: Metro rejects a scoped @Binds, and each implementation class is
// already @Singleton itself, so the behaviour is the same.
@Module(includes = [EopatchPrefModule::class])
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class EopatchModule {

    @Binds
    abstract fun bindBleDevice(patch: Patch): IBleDevice

    @Binds
    abstract fun bindPatchManager(patchManager: PatchManager): IPatchManager

    @Binds
    abstract fun bindAlarmManager(alarmManager: AlarmManager): IAlarmManager

    @Binds
    abstract fun bindAlarmRegistry(alarmRegistry: AlarmRegistry): IAlarmRegistry

    @Binds
    abstract fun bindPreferenceManager(preferenceManager: PreferenceManagerImpl): PreferenceManager

    // Pump plugin registration — @IntKey range 1000–1200, see PluginsListModule for overview
    @Binds
    @PumpDriver
    @IntoMap
    @IntKey(1110)
    abstract fun bindEopatchPumpPlugin(plugin: EopatchPumpPlugin): PluginBase
}
