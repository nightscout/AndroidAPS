package info.nightscout.pump.medtrum.di

import dagger.Binds
import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.pump.medtrum.ui.MedtrumPumpFragment

@Module
@Suppress("unused")
abstract class MedtrumPumpModule {

    @ContributesAndroidInjector abstract fun contributesMedtrumPumpFragment(): MedtrumPumpFragment

}