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
import app.aaps.pump.equil.database.EquilHistoryPumpDao
import app.aaps.pump.equil.database.EquilHistoryRecordDao
import app.aaps.pump.equil.manager.EquilManager

/**
 * The Metro→Dagger direction for pump types, which [PumpLeaves] does not cover.
 *
 * [PumpLeaves] runs Dagger→Metro: Dagger builds a type and Metro borrows it. This is the mirror, for
 * types Metro now owns that something on the Dagger side still needs. `MetroGraphs` cannot carry them,
 * because it lives in `src/main` where no pump module is on the classpath - so the accessors go in a
 * variant-specific interface, exactly as [PumpLeaves] does, and `MetroGraphs` names only the interface.
 * Both flavours must declare it or `src/main` will not compile.
 *
 * `AppRootGraph` extends this interface directly (not `@ContributesTo`, which would only reach the
 * generated implementation), so every accessor resolves against the one real graph. Nothing here
 * builds anything.
 *
 * ## Why this exists: the instrumented tests were silently getting second copies
 *
 * The only consumers are the pump-emulator tests in `androidTest`, which used to `@Inject` these types
 * from Hilt. Once the drivers became `@SingleIn(AppScope::class)`, that was wrong in two different ways
 * and only one of them was loud:
 *
 *  - **Loud:** six types moved into Metro binding containers, so Hilt had no binding at all and the
 *    graph failed with `[Dagger/MissingBinding]`. Note `assembleFullDebug` does **not** compile the
 *    instrumented Hilt component - only `assembleFullDebugAndroidTest` does, so this reached CI.
 *  - **Silent, and worse:** the plugins themselves (`DanaRv2Plugin` and friends) still have an
 *    `@Inject` constructor, so Dagger happily built a **second** one. The test then drove a plugin
 *    object that the running app had never heard of, while the real one sat in the plugin list. Nothing
 *    failed to compile and no assertion could have noticed.
 *
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

    val danaHistoryRecordDao: DanaHistoryRecordDao
    val equilHistoryRecordDao: EquilHistoryRecordDao
    val equilHistoryPumpDao: EquilHistoryPumpDao
}
