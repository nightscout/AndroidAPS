package app.aaps.pump.virtual.di

import app.aaps.core.interfaces.pump.VirtualPump
import app.aaps.pump.virtual.VirtualPumpFragment
import app.aaps.pump.virtual.VirtualPumpPlugin
import dagger.Binds
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module(
    includes = [
        VirtualPumpModule.Bindings::class
    ]
)
@Suppress("unused")
abstract class VirtualPumpModule {

    @ContributesAndroidInjector abstract fun contributesVirtualPumpFragment(): VirtualPumpFragment

    @Module
    interface Bindings {

        @Binds fun bindVirtualPump(virtualPumpPlugin: VirtualPumpPlugin): VirtualPump
    }

}