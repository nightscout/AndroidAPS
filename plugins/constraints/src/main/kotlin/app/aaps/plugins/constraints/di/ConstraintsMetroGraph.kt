package app.aaps.plugins.constraints.di

import app.aaps.core.ui.compose.MetroViewModelCreator
import app.aaps.plugins.constraints.objectives.compose.ObjectivesViewModel
import dev.zacsweers.metro.ClassKey
import dev.zacsweers.metro.GraphExtension
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.Provides
import kotlin.reflect.KClass

/**
 * Scope marker for this module's view models.
 *
 * The graph used to be a second root on `AppScope`, which was unsafe: two graphs declaring one scope
 * each get their own copy of anything scoped there, silently. Now it is an extension with a scope of
 * its own, so `AppScope` means exactly one graph.
 */
abstract class ConstraintsScope private constructor()

/**
 * What is left of this module's wiring: one view model.
 *
 * The seven plugins used to be listed here, each with a `@Provides @IntoMap @IntKey(n)` and a build
 * bucket qualifier. They carry all of that on the class now, with `@ContributesIntoMap`, so they no
 * longer appear in any graph - and neither do the ten objectives, which had to move with
 * `ObjectivesPlugin` because it takes `List<Objective>` and is now built in the root graph.
 *
 * The buckets did not change meaning. A plugin with no qualifier goes into every build, `@APS` only
 * into a build that runs the loop, `@NotNSClient` only into a build that is not a follower. Putting one
 * in the wrong bucket is still silent - it either vanishes from the app or turns up where it has never
 * been shown - so `ContributedPluginsTest` checks the split.
 *
 * When the view model follows, this file goes too.
 */
@GraphExtension(ConstraintsScope::class)
interface ConstraintsMetroGraph {

    /** Builds [ObjectivesViewModel] - the `@HiltViewModel` replacement for this module. */
    val viewModelCreators: Map<KClass<*>, MetroViewModelCreator>

    @Provides
    @IntoMap
    @ClassKey(ObjectivesViewModel::class)
    fun bindObjectivesViewModel(provider: Provider<ObjectivesViewModel>): MetroViewModelCreator =
        MetroViewModelCreator { provider() }
}
