package app.aaps.pump.omnipod.eros.rileylink.service

import app.aaps.core.interfaces.di.FeatureMemberInjectors
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.MembersInjector
import dev.zacsweers.metro.Provides

/**
 * Member injector for the eros RileyLink service. Same shape as `MedtronicMemberInjectors`.
 *
 * This was a hand written injector that assigned all twelve fields itself, because the service was
 * Java and asking Metro to generate one for a Java class crashed its code generator. The service is
 * Kotlin now, so Metro generates it - and a field added to the service or to `RileyLinkService` can no
 * longer be silently left null, which the hand written version had no way to catch.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object RileyLinkOmnipodServiceInjector {

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(RileyLinkOmnipodService::class)
    fun bindRileyLinkOmnipodService(
        injector: MembersInjector<RileyLinkOmnipodService>
    ): MembersInjector<*> = injector
}
