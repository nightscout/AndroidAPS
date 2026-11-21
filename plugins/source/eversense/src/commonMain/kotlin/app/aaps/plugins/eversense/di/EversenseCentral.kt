package app.aaps.plugins.eversense.di

import dev.zacsweers.metro.Qualifier

/**
 * The Bluetooth central Eversense talks through: the phone's, or an emulated one that holds an emulated transmitter
 * (the `emulate_eversense` option file). Only Eversense gets the emulated one; the pumps keep the phone's.
 */
@Qualifier
annotation class EversenseCentral
