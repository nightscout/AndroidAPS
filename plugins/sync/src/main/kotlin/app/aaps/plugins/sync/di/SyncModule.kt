package app.aaps.plugins.sync.di

import android.content.Context
import androidx.work.WorkManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * What is left of this module's own wiring, now on Metro.
 *
 * The three `@Binds` that used to live here are gone: `SmsCommunicatorPlugin`, `NSClientV3Plugin` and
 * `ClientControlRoundTrip` carry their own contributions now, so Metro binds the interfaces directly
 * and `:app` reads them back through `CoreObjectsModule` delegates rather than the other way round.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object SyncModule {

    /**
     * Was `@Reusable` under Dagger, then `@Singleton`. `WorkManager.getInstance` already returns one
     * instance per process, so scoping it here only avoids repeating that lookup.
     */
    @Provides
    @SingleIn(AppScope::class)
    fun providesWorkManager(context: Context): WorkManager = WorkManager.getInstance(context)
}
