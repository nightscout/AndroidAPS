package app.aaps.pump.common.driver

import app.aaps.pump.common.driver.ble.PumpBLESelector
import app.aaps.pump.common.driver.history.PumpHistoryDataProvider

interface PumpDriverConfiguration {

    fun getPumpBLESelector(): PumpBLESelector

    fun getPumpHistoryDataProvider(): PumpHistoryDataProvider

}