package info.nightscout.androidaps.danar.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.androidaps.danaRKorean.services.DanaRKoreanExecutionService
import info.nightscout.androidaps.danaRv2.services.DanaRv2ExecutionService
import info.nightscout.androidaps.danar.services.AbstractDanaRExecutionService
import info.nightscout.androidaps.danar.services.DanaRExecutionService

@Module
@Suppress("unused")
abstract class DanaRServicesModule {

    @ContributesAndroidInjector abstract fun contributesAbstractDanaRExecutionService(): AbstractDanaRExecutionService
    @ContributesAndroidInjector abstract fun contributesDanaRv2ExecutionService(): DanaRv2ExecutionService
    @ContributesAndroidInjector abstract fun contributesDanaRExecutionService(): DanaRExecutionService
    @ContributesAndroidInjector abstract fun contributesDanaRKoreanExecutionService(): DanaRKoreanExecutionService
}