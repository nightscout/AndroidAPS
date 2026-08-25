package app.aaps.core.interfaces.di

import javax.inject.Qualifier

/**
 * Multibinding qualifier for plugins that are present only in APS (loop-enabled) builds.
 * Kept in :core:interfaces (like [PumpDriver]) so feature modules can self-register
 * their [app.aaps.core.interfaces.plugin.PluginBase] into the map without depending on :app.
 */
@Qualifier
annotation class APS
