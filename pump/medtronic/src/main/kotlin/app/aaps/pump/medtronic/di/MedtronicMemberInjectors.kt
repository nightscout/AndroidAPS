package app.aaps.pump.medtronic.di

import app.aaps.core.interfaces.di.FeatureMemberInjectors
import app.aaps.pump.medtronic.service.RileyLinkMedtronicService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.MembersInjector
import dev.zacsweers.metro.Provides

@ContributesTo(AppScope::class)
@BindingContainer
object MedtronicMemberInjectors {

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(RileyLinkMedtronicService::class)
    fun bindRileyLinkMedtronicService(
        injector: MembersInjector<RileyLinkMedtronicService>
    ): MembersInjector<*> = injector
}
