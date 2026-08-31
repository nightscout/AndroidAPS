package app.aaps.core.interfaces.di

import dev.zacsweers.metro.Qualifier

/**
 * Multibinding qualifier for plugins that are present in every build config except the NSClient
 * (AAPSClient) builds. Kept in :core:interfaces (like [PumpDriver]) so feature modules can
 * self-register their [app.aaps.core.interfaces.plugin.PluginBase] into the map without depending on :app.
 */
// Carries both qualifier annotations on purpose. Dagger reads the javax one; Metro reads its own.
// Metro would also read the javax one, but only in a module with Dagger interop switched on - and the
// module that has to read it is `:app`, where interop cannot be enabled because it breaks
// `@BindingContainer` in Metro 1.4.2. Without both, the three plugin buckets collapse into one map and
// every plugin appears in every build, silently. See ConstraintsBucketsTest.
@Qualifier
annotation class NotNSClient
