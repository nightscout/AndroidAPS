package app.aaps.plugins.sync.di

import app.aaps.core.interfaces.di.FeatureMemberInjectors
import app.aaps.plugins.sync.tidepool.auth.AuthFlowIn
import app.aaps.plugins.sync.wear.receivers.WearDataReceiver
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.MembersInjector
import dev.zacsweers.metro.Provides

/**
 * Member injectors for the Android entry points in this module.
 *
 * Separate from [OpenHumansMetroGraph], which is still a root graph of its own for the reason written up
 * there. This one contributes straight into the app root, so it needs no mention in `:app` at all.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object SyncMemberInjectors {

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(AuthFlowIn::class)
    fun bindAuthFlowIn(injector: MembersInjector<AuthFlowIn>): MembersInjector<*> = injector

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(WearDataReceiver::class)
    fun bindWearDataReceiver(injector: MembersInjector<WearDataReceiver>): MembersInjector<*> = injector
}
