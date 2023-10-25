package info.nightscout.androidaps.plugins.pump.eopatch.dagger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.android.ContributesAndroidInjector
import dagger.multibindings.IntoMap
import info.nightscout.androidaps.plugins.pump.eopatch.OsAlarmReceiver
import info.nightscout.androidaps.plugins.pump.eopatch.alarm.AlarmManager
import info.nightscout.androidaps.plugins.pump.eopatch.alarm.AlarmRegistry
import info.nightscout.androidaps.plugins.pump.eopatch.alarm.IAlarmManager
import info.nightscout.androidaps.plugins.pump.eopatch.alarm.IAlarmRegistry
import info.nightscout.androidaps.plugins.pump.eopatch.ble.IPatchManager
import info.nightscout.androidaps.plugins.pump.eopatch.ble.IPreferenceManager
import info.nightscout.androidaps.plugins.pump.eopatch.ble.PatchManager
import info.nightscout.androidaps.plugins.pump.eopatch.ble.PreferenceManager
import info.nightscout.androidaps.plugins.pump.eopatch.ui.*
import info.nightscout.androidaps.plugins.pump.eopatch.ui.dialogs.AlarmDialog
import info.nightscout.androidaps.plugins.pump.eopatch.ui.dialogs.ActivationNotCompleteDialog
import info.nightscout.androidaps.plugins.pump.eopatch.ui.dialogs.CommonDialog
import info.nightscout.androidaps.plugins.pump.eopatch.ui.viewmodel.EopatchOverviewViewModel
import info.nightscout.androidaps.plugins.pump.eopatch.ui.viewmodel.EopatchViewModel
import info.nightscout.androidaps.plugins.pump.eopatch.ui.viewmodel.ViewModelFactory
import info.nightscout.androidaps.plugins.pump.eopatch.ui.viewmodel.ViewModelKey
import javax.inject.Provider
import javax.inject.Singleton

@Module(includes = [EopatchPrefModule::class])
@Suppress("unused")
abstract class EopatchModule {
    companion object {
        @Provides
        @EopatchPluginQualifier
        fun providesViewModelFactory(@EopatchPluginQualifier viewModels: MutableMap<Class<out ViewModel>, @JvmSuppressWildcards Provider<ViewModel>>): ViewModelProvider.Factory {
            return ViewModelFactory(viewModels)
        }

    }

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
    abstract fun bindPreferenceManager(preferenceManager: PreferenceManager): IPreferenceManager

    // #### VIEW MODELS ############################################################################
    @Binds
    @IntoMap
    @EopatchPluginQualifier
    @ViewModelKey(EopatchOverviewViewModel::class)
    internal abstract fun bindsEopatchOverviewViewmodel(viewModel: EopatchOverviewViewModel): ViewModel

    @Binds
    @IntoMap
    @EopatchPluginQualifier
    @ViewModelKey(EopatchViewModel::class)
    internal abstract fun bindsEopatchViewModel(viewModel: EopatchViewModel): ViewModel

    // #### FRAGMENTS ##############################################################################
    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesEopatchOverviewFragment(): EopatchOverviewFragment

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesEopatchSafeDeactivationFragment(): EopatchSafeDeactivationFragment

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesEopatchTurningOffAlarmFragment(): EopatchTurningOffAlarmFragment

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesEopatchRemoveFragment(): EopatchRemoveFragment

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesEopatchWakeUpFragment(): EopatchWakeUpFragment

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesEopatchConnectNewFragment(): EopatchConnectNewFragment

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesEopatchRemoveNeedleCapFragment(): EopatchRemoveNeedleCapFragment

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesEopatchRemoveProtectionTapeFragment(): EopatchRemoveProtectionTapeFragment

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesEopatchSafetyCheckFragment(): EopatchSafetyCheckFragment

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesEopatchRotateKnobFragment(): EopatchRotateKnobFragment

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesEopatchBasalScheduleFragment(): EopatchBasalScheduleFragment

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesAlarmDialog(): AlarmDialog

    @FragmentScope
    @ContributesAndroidInjector
    internal abstract fun contributesCommonDialog(): ActivationNotCompleteDialog

    // Activities
    @ContributesAndroidInjector
    abstract fun contributesEopatchActivity(): EopatchActivity

    @ContributesAndroidInjector
    abstract fun contributesAlarmHelperActivity(): AlarmHelperActivity

    @ContributesAndroidInjector
    abstract fun contributesDialogHelperActivity(): DialogHelperActivity

    @ContributesAndroidInjector
    abstract fun contributesEoDialog(): CommonDialog

    @ContributesAndroidInjector
    abstract fun contributesOsAlarmReceiver(): OsAlarmReceiver
}
