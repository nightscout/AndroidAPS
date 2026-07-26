package app.aaps.di

import app.aaps.plugins.aps.logger.LoggerCallback
import app.aaps.plugins.aps.openAPSAMA.DetermineBasalAdapterAMAJS
import app.aaps.plugins.aps.openAPSAMA.DetermineBasalResultAMAFromJS
import app.aaps.plugins.aps.openAPSSMB.DetermineBasalAdapterSMBJS
import app.aaps.plugins.aps.openAPSSMB.DetermineBasalResultSMBFromJS
import app.aaps.plugins.aps.openAPSSMBAutoISF.DetermineBasalAdapterAutoISFJS
import app.aaps.plugins.aps.openAPSSMBDynamicISF.DetermineBasalAdapterSMBDynamicISFJS
import dagger.Module
import dagger.android.ContributesAndroidInjector
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// Contributes android-injectors for the JS reference algorithm adapters that live in app/src/androidTest
// (test-only — not present in the production graph). Installed only in the test Hilt graph.
@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class AlgModule {

    @ContributesAndroidInjector abstract fun loggerCallbackInjector(): LoggerCallback
    @ContributesAndroidInjector abstract fun determineBasalResultSMBInjector(): DetermineBasalResultSMBFromJS
    @ContributesAndroidInjector abstract fun determineBasalResultAMAInjector(): DetermineBasalResultAMAFromJS
    @ContributesAndroidInjector abstract fun determineBasalAdapterAMAJSInjector(): DetermineBasalAdapterAMAJS
    @ContributesAndroidInjector abstract fun determineBasalAdapterSMBJSInjector(): DetermineBasalAdapterSMBJS
    @ContributesAndroidInjector abstract fun determineBasalAdapterSMBDynamicISFJSInjector(): DetermineBasalAdapterSMBDynamicISFJS
    @ContributesAndroidInjector abstract fun determineBasalAdapterSMBAutoISFJSInjector(): DetermineBasalAdapterAutoISFJS
}