package app.aaps.pump.danars.di

import app.aaps.core.interfaces.di.FeatureMemberInjectors
import app.aaps.pump.danars.services.DanaRSService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.MembersInjector
import dev.zacsweers.metro.Provides

@ContributesTo(AppScope::class)
@BindingContainer
object DanaRSMemberInjectors {

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(DanaRSService::class)
    fun bindDanaRSService(injector: MembersInjector<DanaRSService>): MembersInjector<*> = injector
}
