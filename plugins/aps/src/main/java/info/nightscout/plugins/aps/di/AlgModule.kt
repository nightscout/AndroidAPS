package info.nightscout.plugins.aps.di

import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
@Suppress("unused")
abstract class AlgModule {

    @ContributesAndroidInjector abstract fun loggerCallbackInjector(): info.nightscout.plugins.aps.logger.LoggerCallback
    @ContributesAndroidInjector abstract fun determineBasalResultSMBInjector(): info.nightscout.plugins.aps.openAPSSMB.DetermineBasalResultSMB
    @ContributesAndroidInjector abstract fun determineBasalResultAMAInjector(): info.nightscout.plugins.aps.openAPSAMA.DetermineBasalResultAMA
    @ContributesAndroidInjector abstract fun determineBasalAdapterAMAJSInjector(): info.nightscout.plugins.aps.openAPSAMA.DetermineBasalAdapterAMAJS
    @ContributesAndroidInjector abstract fun determineBasalAdapterSMBJSInjector(): info.nightscout.plugins.aps.openAPSSMB.DetermineBasalAdapterSMBJS
    @ContributesAndroidInjector abstract fun determineBasalAdapterSMBAutoISFJSInjector(): info.nightscout.plugins.aps.openAPSSMBDynamicISF.DetermineBasalAdapterSMBDynamicISFJS
}