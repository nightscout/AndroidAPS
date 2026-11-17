package app.aaps.core.interfaces.di

import dev.zacsweers.metro.Qualifier

/**
 * Multibinding qualifier for the pump drivers, which only a build with pump support merges.
 * The map itself is `Map<Int, PluginBase>` - no pump type appears in it - so `AppRootGraph` can expose
 * the bucket even though it is compiled for follower builds, where no pump module exists. There the map
 * is simply empty, which is exactly what a follower should see.
 */
@Qualifier
annotation class PumpDriver
