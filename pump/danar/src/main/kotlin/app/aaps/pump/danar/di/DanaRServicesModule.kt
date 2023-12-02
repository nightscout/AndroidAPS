package app.aaps.pump.danar.di

import app.aaps.pump.danar.services.AbstractDanaRExecutionService
import app.aaps.pump.danar.services.DanaRExecutionService
import app.aaps.pump.danarkorean.services.DanaRKoreanExecutionService
import app.aaps.pump.danarv2.services.DanaRv2ExecutionService
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
@Suppress("unused")
abstract class DanaRServicesModule {

    @ContributesAndroidInjector abstract fun contributesAbstractDanaRExecutionService(): AbstractDanaRExecutionService
    @ContributesAndroidInjector abstract fun contributesDanaRv2ExecutionService(): DanaRv2ExecutionService
    @ContributesAndroidInjector abstract fun contributesDanaRExecutionService(): DanaRExecutionService
    @ContributesAndroidInjector abstract fun contributesDanaRKoreanExecutionService(): DanaRKoreanExecutionService
}