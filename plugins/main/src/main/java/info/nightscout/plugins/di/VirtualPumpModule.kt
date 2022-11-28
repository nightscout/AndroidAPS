package info.nightscout.plugins.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import info.nightscout.plugins.pump.virtual.VirtualPumpFragment

@Module
@Suppress("unused")
abstract class VirtualPumpModule {

    @ContributesAndroidInjector abstract fun contributesVirtualPumpFragment(): VirtualPumpFragment
}