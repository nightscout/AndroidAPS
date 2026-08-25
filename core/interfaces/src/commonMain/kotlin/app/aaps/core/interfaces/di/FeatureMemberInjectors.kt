package app.aaps.core.interfaces.di

import dev.zacsweers.metro.Qualifier

/**
 * Marks the member-injector map that feature modules contribute to directly, at the root.
 *
 * Without a qualifier this map is the same multibinding as the `memberInjectors` map each graph
 * extension declares - they are all `Map<KClass<*>, MembersInjector<*>>`. A contribution made at the
 * root is then visible through every extension's accessor as well, which is how 73 Diaconn packets
 * turned up in the source graph's map and failed `SourceGraphTest`.
 *
 * Metro's qualifier only: nothing on the Dagger side reads this map, so there is no javax counterpart to
 * keep in step, and it can live in commonMain.
 */
@Qualifier
annotation class FeatureMemberInjectors
