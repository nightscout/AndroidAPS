package app.aaps.di.pump

import app.aaps.pump.omnipod.eros.driver.manager.ErosPodStateManager
import app.aaps.pump.omnipod.eros.manager.AapsErosPodStateManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Provided from `:app` so `:pump:omnipod:eros` needs no Hilt.
 *
 * The module keeps the plain Dagger compiler, and has to: five of its `@Inject constructor` classes
 * (`AapsErosPodStateManager`, `AapsOmnipodErosManager`, `AapsOmnipodUtil`, `OmnipodAlertUtil`,
 * `OmnipodRileyLinkCommunicationManager`) are **Java**, and Metro is a Kotlin compiler plugin - it
 * cannot generate a factory for a class it has no Kotlin IR for. Those five are what pins the last
 * Dagger processor outside `:app`; converting them to Kotlin is what would release it.
 */
@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
class OmnipodErosModule {

    @Provides
    fun erosPodStateManagerProvider(aapsErosPodStateManager: AapsErosPodStateManager): ErosPodStateManager = aapsErosPodStateManager
}
