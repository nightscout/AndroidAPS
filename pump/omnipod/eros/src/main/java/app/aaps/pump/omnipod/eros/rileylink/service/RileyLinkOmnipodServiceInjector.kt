package app.aaps.pump.omnipod.eros.rileylink.service

import app.aaps.core.interfaces.di.FeatureMemberInjectors
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.common.hw.rileylink.RileyLinkUtil
import app.aaps.pump.common.hw.rileylink.ble.RFSpy
import app.aaps.pump.common.hw.rileylink.ble.RileyLinkBLE
import app.aaps.pump.common.hw.rileylink.service.RileyLinkServiceData
import app.aaps.pump.omnipod.eros.OmnipodErosPumpPlugin
import app.aaps.pump.omnipod.eros.rileylink.manager.OmnipodRileyLinkCommunicationManager
import app.aaps.pump.omnipod.eros.util.AapsOmnipodUtil
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.MembersInjector
import dev.zacsweers.metro.Provides

/**
 * Fills [RileyLinkOmnipodService]'s injected fields by hand, instead of letting Metro generate the
 * injector.
 *
 * **Why this one is different.** Every other member injector in the tree is a one line
 * `MembersInjector<TheClass>` that Metro generates. That does not work here: the service is **Java**,
 * and asking Metro to generate an injector for a Java class crashes its code generator outright -
 * `IrGenerationExtensionException: ... companionObject(...) is null` while processing `AppRootGraph`,
 * the same class of defect as https://github.com/ZacSweers/metro/issues/2731. Metro is a Kotlin
 * compiler plugin and a Java class has no Kotlin IR for it to work from. `RileyLinkMedtronicService`
 * is the identical shape and injects fine, because it is Kotlin.
 *
 * The generated version was written while this module was **out of the build**, so it had never been
 * compiled and nobody had found out. That is what dropping a module from `settings.gradle` while
 * still editing it costs.
 *
 * **Why it lives in this package.** The service's own three fields are Java package-private. Kotlin in
 * the same package can reach them, so nothing has to be widened to `public` just to be injected.
 *
 * The nine fields from [app.aaps.pump.common.hw.rileylink.service.RileyLinkService] have to be set
 * here too: `injectMetroMembers` is called with the concrete subclass, so this one entry is
 * responsible for the whole object, base class included. **If a field is added to either class and
 * not added here, it stays null** - there is no compiler check on this list. That is the price of the
 * hand-written injector, and the reason to delete this file the moment the service becomes Kotlin.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object RileyLinkOmnipodServiceInjector {

    @Provides
    @FeatureMemberInjectors
    @IntoMap
    @ClassKey(RileyLinkOmnipodService::class)
    fun bindRileyLinkOmnipodService(
        // RileyLinkService, the base class
        aapsLogger: AAPSLogger,
        preferences: Preferences,
        rxBus: RxBus,
        rileyLinkUtil: RileyLinkUtil,
        rh: ResourceHelper,
        rileyLinkServiceData: RileyLinkServiceData,
        activePlugin: ActivePlugin,
        rileyLinkBLE: RileyLinkBLE,
        rfSpy: RFSpy,
        // RileyLinkOmnipodService, the subclass
        omnipodErosPumpPlugin: OmnipodErosPumpPlugin,
        aapsOmnipodUtil: AapsOmnipodUtil,
        omnipodRileyLinkCommunicationManager: OmnipodRileyLinkCommunicationManager
    ): MembersInjector<*> = MembersInjector<RileyLinkOmnipodService> { target ->
        target.aapsLogger = aapsLogger
        target.preferences = preferences
        target.rxBus = rxBus
        target.rileyLinkUtil = rileyLinkUtil
        target.rh = rh
        target.rileyLinkServiceData = rileyLinkServiceData
        target.activePlugin = activePlugin
        target.rileyLinkBLE = rileyLinkBLE
        target.rfSpy = rfSpy
        target.omnipodErosPumpPlugin = omnipodErosPumpPlugin
        target.aapsOmnipodUtil = aapsOmnipodUtil
        target.omnipodRileyLinkCommunicationManager = omnipodRileyLinkCommunicationManager
    }
}
