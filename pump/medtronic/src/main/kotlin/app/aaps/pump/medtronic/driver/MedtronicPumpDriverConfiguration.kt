package app.aaps.pump.medtronic.driver

import app.aaps.core.data.pump.defs.PumpType
import app.aaps.pump.common.driver.PumpDriverConfiguration
import app.aaps.pump.common.driver.ble.PumpBLESelector
import app.aaps.pump.common.driver.db.PumpDriverDatabaseOperation
import app.aaps.pump.common.driver.history.PumpHistoryDataProvider

// At the moment this class is not used, but since Medtronic is using PumpPluginAbstract, it needs it
// and changes could be made to use this, which would deduplicate History and BLE Activity screens
class MedtronicPumpDriverConfiguration: PumpDriverConfiguration {

    override fun getPumpBLESelector(): PumpBLESelector? {
        //TODO("Not yet implemented")
        return null
    }

    override fun getPumpHistoryDataProvider(): PumpHistoryDataProvider? {
        //TODO("Not yet implemented")
        return null
    }

    override fun getPumpDriverDatabaseOperation(): PumpDriverDatabaseOperation {
        TODO("Not yet implemented")
    }

    override fun getPumpType(): PumpType {
        return PumpType.MEDTRONIC_523_723_REVEL
    }

    override var logPrefix: String = "MedtronicPumpPlugin::"
    override var canHandleDST: Boolean = false
    override var hasService: Boolean = true

}