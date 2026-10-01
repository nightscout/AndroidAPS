package app.aaps.wear.di

import android.content.Context
import app.aaps.core.interfaces.di.FeatureMemberInjectors
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.wear.WearApp
import app.aaps.wear.sharedPreferences.PreferencesImpl
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.MembersInjector
import dev.zacsweers.metro.Multibinds
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlin.reflect.KClass
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The wear app's object graph, replacing the hand-written `WearComponent`.
 *
 * Wear is its own application with its own graph - it shares source with the phone through
 * `:shared:impl` and the `:core` modules, but never shares a graph with it.
 *
 * Everything the old `WearModule` bound by hand is either contributed by the class itself now
 * (`@ContributesBinding` / `@SingleIn`) or provided below. The one thing that cannot be contributed is
 * [WearApp] itself, because Android constructs it - it arrives through the factory.
 */
@SingleIn(AppScope::class)
@DependencyGraph(AppScope::class)
interface WearGraph {

    /**
     * Keyed by the class whose fields are filled. [WearApp] dispatches on the runtime type.
     *
     * `allowEmpty` is not needed - `WearMemberInjectors` always contributes - but the declaration has
     * to be here for the map to exist as a binding at all.
     */
    @Multibinds
    @FeatureMemberInjectors
    val memberInjectors: Map<KClass<*>, MembersInjector<*>>

    @Provides fun preferences(impl: PreferencesImpl): Preferences = impl

    /** The application, under the type most consumers ask for. */
    @Provides fun context(app: WearApp): Context = app

    @OptIn(ExperimentalTime::class)
    @Provides fun clock(): Clock = Clock.System

    @DependencyGraph.Factory
    fun interface Factory {

        fun create(@Provides application: WearApp): WearGraph
    }
}
