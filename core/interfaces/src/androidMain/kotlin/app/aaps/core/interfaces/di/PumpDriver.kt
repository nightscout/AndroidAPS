package app.aaps.core.interfaces.di

import dev.zacsweers.metro.Qualifier

/**
 * Multibinding qualifier for the pump drivers, which only a build with pump support merges.
 *
 * Carries both qualifier annotations, for the same reason as [AllConfigs] and [APS]: Dagger reads the
 * javax one, Metro reads its own. Metro would read the javax one too, but only in a module with Dagger
 * interop switched on, and the module that has to read it is `:app`, where interop cannot be enabled.
 *
 * The map itself is `Map<Int, PluginBase>` - no pump type appears in it - so `AppRootGraph` can expose
 * the bucket even though it is compiled for follower builds, where no pump module exists. There the map
 * is simply empty, which is exactly what a follower should see.
 */
@Qualifier
annotation class PumpDriver
