package info.nightscout.aaps.pump.common.driver

import info.nightscout.aaps.pump.common.driver.ble.PumpBLESelector
import info.nightscout.aaps.pump.common.driver.history.PumpHistoryDataProvider

interface PumpDriverConfiguration {

    fun getPumpBLESelector(): PumpBLESelector?

    fun getPumpHistoryDataProvider(): PumpHistoryDataProvider?

}