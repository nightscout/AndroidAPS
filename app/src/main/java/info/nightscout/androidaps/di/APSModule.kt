package info.nightscout.androidaps.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.plugins.aps.logger.LoggerCallback
import info.nightscout.androidaps.plugins.aps.openAPSAMA.DetermineBasalAdapterAMAJS
import info.nightscout.androidaps.plugins.aps.openAPSAMA.DetermineBasalResultAMA
import info.nightscout.androidaps.plugins.aps.openAPSSMB.DetermineBasalAdapterSMBJS
import info.nightscout.androidaps.plugins.aps.openAPSSMB.DetermineBasalResultSMB
import info.nightscout.androidaps.plugins.aps.openAPSSMBDynamicISF.DetermineBasalAdapterSMBDynamicISFJS

@Module
@Suppress("unused")
abstract class APSModule {

    @ContributesAndroidInjector abstract fun loggerCallbackInjector(): LoggerCallback
    @ContributesAndroidInjector abstract fun determineBasalResultSMBInjector(): DetermineBasalResultSMB
    @ContributesAndroidInjector abstract fun determineBasalResultAMAInjector(): DetermineBasalResultAMA
    @ContributesAndroidInjector abstract fun determineBasalAdapterAMAJSInjector(): DetermineBasalAdapterAMAJS
    @ContributesAndroidInjector abstract fun determineBasalAdapterSMBJSInjector(): DetermineBasalAdapterSMBJS
    @ContributesAndroidInjector abstract fun determineBasalAdapterSMBAutoISFJSInjector(): DetermineBasalAdapterSMBDynamicISFJS
}