package app.aaps.pump.common.driver

import app.aaps.core.data.pump.defs.PumpType
import app.aaps.pump.common.driver.ble.PumpBLESelector
import app.aaps.pump.common.driver.db.PumpDriverDatabaseOperation
import app.aaps.pump.common.driver.history.PumpHistoryDataProvider

interface PumpDriverConfiguration {

    fun getPumpBLESelector(): PumpBLESelector?

    fun getPumpHistoryDataProvider(): PumpHistoryDataProvider?

    fun getPumpDriverDatabaseOperation(): PumpDriverDatabaseOperation

    fun getPumpType(): PumpType

    var logPrefix : String

    var canHandleDST : Boolean

    var hasService: Boolean


}
