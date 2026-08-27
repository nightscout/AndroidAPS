package app.aaps.pump.omnipod.eros.di

import app.aaps.core.interfaces.di.FeatureMemberInjectors
import app.aaps.pump.omnipod.eros.rileylink.service.RileyLinkOmnipodService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.MembersInjector
import dev.zacsweers.metro.Provides

/**
 * Member injector for the eros RileyLink service - the `@ContributesAndroidInjector` replacement.
 *
 * Same shape as `MedtronicMemberInjectors`. The service extends `RileyLinkService`, whose `onCreate`
 * already calls `injectMetroMembers(this)`, so this entry is all that was missing.
 *
 * This was the last real `@ContributesAndroidInjector` on the phone side; every other mention of it in
 * the tree is a comment in a file that has already been converted.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object OmnipodErosMemberInjectors {

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(RileyLinkOmnipodService::class)
    fun bindRileyLinkOmnipodService(
        injector: MembersInjector<RileyLinkOmnipodService>
    ): MembersInjector<*> = injector
}
