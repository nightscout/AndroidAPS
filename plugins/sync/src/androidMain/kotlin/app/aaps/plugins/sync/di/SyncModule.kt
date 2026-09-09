package app.aaps.plugins.sync.di

import android.content.Context
import androidx.work.WorkManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * This module's own wiring.
 *
 * `SmsCommunicatorPlugin`, `NSClientV3Plugin` and `ClientControlRoundTrip` carry their own
 * contributions, so Metro binds those interfaces directly and `:app` reads them back off the graph.
 * Only what cannot be contributed from the class itself is left here.
 */
@ContributesTo(AppScope::class)
@BindingContainer
object SyncModule {

    @Provides
    @SingleIn(AppScope::class)
    fun providesWorkManager(context: Context): WorkManager = WorkManager.getInstance(context)
}
