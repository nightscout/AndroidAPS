package app.aaps.pump.common.di

import app.aaps.core.interfaces.di.FeatureMemberInjectors
import app.aaps.pump.common.hw.rileylink.service.RileyLinkBluetoothStateReceiver
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.MembersInjector
import dev.zacsweers.metro.Provides

/**
 * Member injector for the one entry point here that Metro can build.
 *
 * Its two fields are a logger and a util object. The other receiver and the service in this module both
 * reach types the graph builds itself, which trips a Metro codegen bug - see
 * https://github.com/ZacSweers/metro/issues/2731 - so they stay in [RileyLinkModule] for now.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object RileyLinkMemberInjectors {

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(RileyLinkBluetoothStateReceiver::class)
    fun bindRileyLinkBluetoothStateReceiver(
        injector: MembersInjector<RileyLinkBluetoothStateReceiver>
    ): MembersInjector<*> = injector
}
