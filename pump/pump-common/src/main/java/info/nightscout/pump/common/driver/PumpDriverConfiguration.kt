package info.nightscout.pump.common.driver

import info.nightscout.pump.common.driver.ble.PumpBLESelector
import info.nightscout.pump.common.driver.history.PumpHistoryDataProvider

interface PumpDriverConfiguration {

    fun getPumpBLESelector(): PumpBLESelector

    fun getPumpHistoryDataProvider(): PumpHistoryDataProvider

}