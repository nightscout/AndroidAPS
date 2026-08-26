package app.aaps.pump.common.di

import app.aaps.pump.common.hw.rileylink.service.RileyLinkBluetoothStateReceiver
import app.aaps.pump.common.hw.rileylink.service.RileyLinkBroadcastReceiver
import app.aaps.pump.common.hw.rileylink.service.RileyLinkService
import dagger.Module
import dagger.android.ContributesAndroidInjector
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * The Android entry points here are still on `dagger.android`.
 *
 * Member injecting them trips a Metro codegen bug - see https://github.com/ZacSweers/metro/issues/2731.
 * `RileyLinkBroadcastReceiver` is the clearest case: it holds three `Provider<Task>` fields, the same
 * shape that breaks `DanaRSService`.
 *
 * This module used to have ten more entries, for the tasks, `RFSpy`, `RileyLinkBLE` and `OrangeLinkImpl`.
 * Nothing ever called an injector on any of them - they are all built by their constructors - so those
 * were dead and have been removed.
 */
@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
abstract class RileyLinkModule {

    @ContributesAndroidInjector abstract fun contributesRileyLinkService(): RileyLinkService
    @ContributesAndroidInjector abstract fun contributesRileyLinkBroadcastReceiver(): RileyLinkBroadcastReceiver
    @ContributesAndroidInjector abstract fun contributesRileyLinkBluetoothStateReceiver(): RileyLinkBluetoothStateReceiver
}
