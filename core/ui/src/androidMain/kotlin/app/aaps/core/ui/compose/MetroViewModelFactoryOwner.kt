package app.aaps.core.ui.compose

import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory

/**
 * How a screen reaches the app's view model factory.
 *
 * The `Application` implements this, the same way it implements `MetroMemberInjector`: Android builds
 * activities, so a screen has to reach out and ask rather than be handed anything.
 *
 * The factory itself is [MetroViewModelFactory] from `metrox-viewmodel` now, not a hand-written one.
 * A view model registers itself with `@ContributesIntoMap` and `@ViewModelKey` on the class, so adding
 * one touches no graph, no factory and no map - which is the point, with eighty still to convert.
 */
interface MetroViewModelFactoryOwner {

    val metroViewModelFactory: MetroViewModelFactory
}
