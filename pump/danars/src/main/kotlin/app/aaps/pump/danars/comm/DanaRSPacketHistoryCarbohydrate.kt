package app.aaps.pump.danars.comm

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.dana.database.DanaHistoryRecordDao
import app.aaps.pump.danars.encryption.BleEncryption
import javax.inject.Inject

class DanaRSPacketHistoryCarbohydrate @Inject constructor(
    aapsLogger: AAPSLogger,
    dateUtil: DateUtil,
    rxBus: RxBus,
    danaHistoryRecordDao: DanaHistoryRecordDao,
    pumpSync: PumpSync,
    danaPump: DanaPump
) : DanaRSPacketHistory(aapsLogger, dateUtil, rxBus, danaHistoryRecordDao, pumpSync, danaPump) {

    init {
        opCode = BleEncryption.DANAR_PACKET__OPCODE_REVIEW__CARBOHYDRATE
        aapsLogger.debug(LTag.PUMPCOMM, "New message")
    }

    override val friendlyName: String = "REVIEW__CARBOHYDRATE"
}