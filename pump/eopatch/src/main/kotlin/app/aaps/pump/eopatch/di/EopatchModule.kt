package app.aaps.pump.eopatch.di

import app.aaps.core.interfaces.di.PumpDriver
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.pump.eopatch.EopatchPumpPlugin
import app.aaps.pump.eopatch.OsAlarmReceiver
import app.aaps.pump.eopatch.alarm.AlarmManager
import app.aaps.pump.eopatch.alarm.AlarmRegistry
import app.aaps.pump.eopatch.alarm.IAlarmManager
import app.aaps.pump.eopatch.alarm.IAlarmRegistry
import app.aaps.pump.eopatch.ble.IPatchManager
import app.aaps.pump.eopatch.ble.PatchManager
import app.aaps.pump.eopatch.ble.PreferenceManager
import app.aaps.pump.eopatch.ble.PreferenceManagerImpl
import app.aaps.pump.eopatch.ui.AlarmHelperActivity
import app.aaps.pump.eopatch.ui.DialogHelperActivity
import app.aaps.pump.eopatch.ui.dialogs.ActivationNotCompleteDialog
import app.aaps.pump.eopatch.ui.dialogs.AlarmDialog
import app.aaps.pump.eopatch.ui.dialogs.CommonDialog
import dagger.Binds
import dagger.Module
import dagger.android.ContributesAndroidInjector
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntKey
import dagger.multibindings.IntoMap
import javax.inject.Singleton

@Module(includes = [EopatchPrefModule::class])
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class EopatchModule {

    @Binds
    @Singleton
    abstract fun bindPatchManager(patchManager: PatchManager): IPatchManager

    @Binds
    @Singleton
    abstract fun bindAlarmManager(alarmManager: AlarmManager): IAlarmManager

    @Binds
    @Singleton
    abstract fun bindAlarmRegistry(alarmRegistry: AlarmRegistry): IAlarmRegistry

    @Binds
    @Singleton
    abstract fun bindPreferenceManager(preferenceManager: PreferenceManagerImpl): PreferenceManager

    // #### DIALOGS (still needed for alarm/dialog helper activities) ################################
    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesAlarmDialog(): AlarmDialog

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesActivationNotCompleteDialog(): ActivationNotCompleteDialog

    // #### ACTIVITIES (alarm-driven entry points + helpers) ###########################################
    // EopatchActivity is now @AndroidEntryPoint (Hilt-injected)

    @ContributesAndroidInjector
    abstract fun contributesAlarmHelperActivity(): AlarmHelperActivity

    @ContributesAndroidInjector
    abstract fun contributesDialogHelperActivity(): DialogHelperActivity

    @ContributesAndroidInjector
    abstract fun contributesEoDialog(): CommonDialog

    @ContributesAndroidInjector
    abstract fun contributesOsAlarmReceiver(): OsAlarmReceiver

    // Pump plugin registration — @IntKey range 1000–1200, see PluginsListModule for overview
    @Binds
    @PumpDriver
    @IntoMap
    @IntKey(1110)
    abstract fun bindEopatchPumpPlugin(plugin: EopatchPumpPlugin): PluginBase
}
