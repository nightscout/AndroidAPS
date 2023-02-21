package info.nightscout.pump.medtrum.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.pump.medtrum.ui.MedtrumPumpFragment

@Module
@Suppress("unused")
abstract class MedtrumActivitiesModule {

    @ContributesAndroidInjector abstract fun contributesMedtrumPumpFragment(): MedtrumPumpFragment

}
