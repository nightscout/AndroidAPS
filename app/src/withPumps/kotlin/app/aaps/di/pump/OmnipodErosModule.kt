package app.aaps.di.pump

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.pump.omnipod.eros.driver.manager.ErosPodStateManager
import app.aaps.pump.omnipod.eros.driver.manager.OmnipodManager
import app.aaps.pump.omnipod.eros.rileylink.manager.OmnipodRileyLinkCommunicationManager
import app.aaps.pump.omnipod.eros.manager.AapsErosPodStateManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provided from `:app` so `:pump:omnipod:eros` needs no Hilt.
 *
 * The module keeps the plain Dagger compiler, and has to: two of its `@Inject constructor` classes
 * (`AapsOmnipodErosManager` and `OmnipodRileyLinkCommunicationManager`) are still **Java**, and Metro
 * is a Kotlin compiler plugin - it cannot generate a factory for a class it has no Kotlin IR for.
 * `AapsErosPodStateManager`, `AapsOmnipodUtil` and `OmnipodAlertUtil` were on this list and are Kotlin
 * now; Dagger still builds them because they are handed to Metro as leaves. Those two are what pins the last
 * Dagger processor outside `:app`; converting them to Kotlin is what would release it.
 */
@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
class OmnipodErosModule {

    @Provides
    fun erosPodStateManagerProvider(aapsErosPodStateManager: AapsErosPodStateManager): ErosPodStateManager = aapsErosPodStateManager

    /**
     * The pod command delegate, which `AapsOmnipodErosManager` used to build itself with `new`.
     *
     * Injecting it makes that class's command surface - bolus, temporary basal, cannula, status -
     * reachable from a test, which is what a safe Kotlin rewrite of those 1060 lines needs. It also
     * made the constructor *smaller*: `communicationService` and `aapsSchedulers` were parameters only
     * ever used to build this, so both are gone.
     *
     * **`@Singleton` is load bearing.** `AapsOmnipodErosManager` is a singleton and built exactly one of
     * these, so it holds pod communication state that must not be duplicated. Unscoped, every injection
     * point would get its own - the same split brain shape `SplitBrainTest` catches elsewhere.
     */
    @Provides
    @Singleton
    fun provideOmnipodManager(
        aapsLogger: AAPSLogger,
        aapsSchedulers: AapsSchedulers,
        communicationService: OmnipodRileyLinkCommunicationManager,
        podStateManager: ErosPodStateManager
    ): OmnipodManager = OmnipodManager(aapsLogger, aapsSchedulers, communicationService, podStateManager)
}
