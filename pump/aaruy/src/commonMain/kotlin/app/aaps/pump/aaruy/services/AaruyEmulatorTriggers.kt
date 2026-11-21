package app.aaps.pump.aaruy.services

import app.aaps.pump.aaruy.emulator.AaruyEmulatorPeripheral

/**
 * Lets a person testing on a phone drive the emulated pump from outside the app, as `adb shell am broadcast` does on
 * Android. Only with the emulator: a real pump is never touched by it. A platform with no such way does nothing.
 */
interface AaruyEmulatorTriggers {

    fun start(emulator: AaruyEmulatorPeripheral)
}
