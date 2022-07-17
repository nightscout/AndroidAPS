package info.nightscout.androidaps.plugins.pump.common.driver

import info.nightscout.androidaps.plugins.pump.common.driver.history.PumpHistoryDataProvider

interface PumpDriverConfiguration {

    fun getPumpBLESelector(): PumpBLESelector

    fun getPumpHistoryDataProvider(): PumpHistoryDataProvider

}