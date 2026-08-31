package app.aaps.pump.omnipod.eros.di

import app.aaps.pump.omnipod.eros.driver.manager.ErosPodStateManager
import app.aaps.pump.omnipod.eros.manager.AapsErosPodStateManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * A plain class, not an `abstract class` with a companion object.
 *
 * It held one `@Binds` for the plugin registration, which is why it was abstract; that moved onto
 * [app.aaps.pump.omnipod.eros.OmnipodErosPumpPlugin] as Metro's `@ContributesIntoMap`. It had to move:
 * the Dagger plugin buckets were deleted while this module was out of the build, so a `@Binds @IntoMap`
 * here would now feed a map nothing reads - and the driver would just be missing from the plugin list,
 * with nothing failing to say so.
 *
 * Left abstract with only a companion, this crashed Metro's code generator outright
 * (`companionObject(...) is null` while processing `AppRootGraph`).
 */
@Module(includes = [OmnipodErosHistoryModule::class])
@InstallIn(SingletonComponent::class)
@Suppress("unused")
class OmnipodErosModule {

    @Provides
    fun erosPodStateManagerProvider(aapsErosPodStateManager: AapsErosPodStateManager): ErosPodStateManager = aapsErosPodStateManager
}
