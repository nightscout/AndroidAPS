package app.aaps.pump.equil.di

import app.aaps.pump.equil.EquilPumpPlugin
import app.aaps.pump.equil.ble.EquilBleTransport
import app.aaps.pump.equil.database.EquilHistoryPumpDao
import app.aaps.pump.equil.database.EquilHistoryRecordDao
import app.aaps.pump.equil.manager.EquilManager
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo

/**
 * What an instrumented test needs from the Equil driver.
 *
 * Read from the app's own graph rather than built by the test, because a test that builds its own
 * `EquilManager` is watching an object the pump never writes to. Declared in this module rather than in
 * `:app`, so that removing `:pump:equil` from `settings.gradle` takes the accessors with it instead of
 * failing the app's main compilation - `DanaAccessors` in `:pump:dana` carries the longer version of
 * both arguments, and of what contributing them costs.
 */
@ContributesTo(AppScope::class)
interface EquilAccessors {

    val equilPumpPlugin: EquilPumpPlugin
    val equilManager: EquilManager
    val equilBleTransport: EquilBleTransport
    val equilHistoryRecordDao: EquilHistoryRecordDao
    val equilHistoryPumpDao: EquilHistoryPumpDao
}
