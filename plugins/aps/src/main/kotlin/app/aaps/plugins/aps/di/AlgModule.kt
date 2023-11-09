package app.aaps.plugins.aps.di

import app.aaps.core.interfaces.logging.ScriptLogger
import app.aaps.plugins.aps.logger.LoggerCallback
import app.aaps.plugins.aps.openAPSAMA.DetermineBasalAdapterAMAJS
import app.aaps.plugins.aps.openAPSAMA.DetermineBasalResultAMA
import app.aaps.plugins.aps.openAPSSMB.DetermineBasalAdapterSMBJS
import app.aaps.plugins.aps.openAPSSMB.DetermineBasalResultSMB
import app.aaps.plugins.aps.openAPSSMBDynamicISF.DetermineBasalAdapterSMBDynamicISFJS
import dagger.Binds
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module(
    includes = [
        AlgModule.Bindings::class,
    ]
)

@Suppress("unused")
abstract class AlgModule {

    @Module
    interface Bindings {

        @Binds fun bindLoggerCallback(loggerCallback: LoggerCallback): ScriptLogger
    }

    @ContributesAndroidInjector abstract fun loggerCallbackInjector(): LoggerCallback
    @ContributesAndroidInjector abstract fun determineBasalResultSMBInjector(): DetermineBasalResultSMB
    @ContributesAndroidInjector abstract fun determineBasalResultAMAInjector(): DetermineBasalResultAMA
    @ContributesAndroidInjector abstract fun determineBasalAdapterAMAJSInjector(): DetermineBasalAdapterAMAJS
    @ContributesAndroidInjector abstract fun determineBasalAdapterSMBJSInjector(): DetermineBasalAdapterSMBJS
    @ContributesAndroidInjector abstract fun determineBasalAdapterSMBAutoISFJSInjector(): DetermineBasalAdapterSMBDynamicISFJS
}