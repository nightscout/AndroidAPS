package app.aaps.di.metro

import app.aaps.core.interfaces.pump.ble.BleTransport
import app.aaps.core.interfaces.pump.rfcomm.RfcommTransport
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.dana.database.DanaHistoryRecordDao
import app.aaps.pump.danar.DanaRPlugin
import app.aaps.pump.danarkorean.DanaRKoreanPlugin
import app.aaps.pump.danars.DanaRSPlugin
import app.aaps.pump.danarv2.DanaRv2Plugin
import app.aaps.pump.equil.EquilPumpPlugin
import app.aaps.pump.equil.ble.EquilBleTransport
import app.aaps.pump.equil.database.EquilHistoryPumpDao
import app.aaps.pump.equil.database.EquilHistoryRecordDao
import app.aaps.pump.equil.manager.EquilManager

/**
 * `AppRootGraph` extends this interface directly (not `@ContributesTo`, which would only reach the
 * generated implementation), so every accessor resolves against the one real graph. Nothing here
 * builds anything.
 * ## Why this exists: the instrumented tests were silently getting second copies
 * Going through the graph is what makes both impossible: there is one instance and it is the app's.
 */
interface PumpAccessors {

    val danaRPlugin: DanaRPlugin
    val danaRKoreanPlugin: DanaRKoreanPlugin
    val danaRv2Plugin: DanaRv2Plugin
    val danaRSPlugin: DanaRSPlugin
    val equilPumpPlugin: EquilPumpPlugin

    val danaPump: DanaPump
    val equilManager: EquilManager

    val rfcommTransport: RfcommTransport
    val bleTransport: BleTransport
    val equilBleTransport: EquilBleTransport

    val danaHistoryRecordDao: DanaHistoryRecordDao
    val equilHistoryRecordDao: EquilHistoryRecordDao
    val equilHistoryPumpDao: EquilHistoryPumpDao
}
