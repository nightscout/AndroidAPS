package app.aaps.pump.diaconn.packet

import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.diaconn.DiaconnG8Pump
import app.aaps.pump.diaconn.keys.DiaconnStringNonKey
import dagger.android.HasAndroidInjector
import javax.inject.Inject

/**
 * SerialNumInquireResponsePacket
 */
class SerialNumInquireResponsePacket(injector: HasAndroidInjector) : DiaconnG8Packet(injector) {

    @Inject lateinit var diaconnG8Pump: DiaconnG8Pump
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var rh: ResourceHelper

    init {
        msgType = 0xAE.toByte()
        aapsLogger.debug(LTag.PUMPCOMM, "SerialNumInquireResponsePacket init")
    }

    override fun handleMessage(data: ByteArray) {
        val result = defect(data)
        if (result != 0) {
            aapsLogger.debug(LTag.PUMPCOMM, "SerialNumInquireResponsePacket Got some Error")
            failed = true
            return
        } else failed = false

        val bufferData = prefixDecode(data)
        val result2 = getByteToInt(bufferData)
        if (!isSuccInquireResponseResult(result2)) {
            failed = true
            return
        }

        diaconnG8Pump.country = getByteToInt(bufferData).toChar().toString().toInt() // ASCII
        diaconnG8Pump.productType = getByteToInt(bufferData).toChar().toString().toInt() // ASCII
        diaconnG8Pump.makeYear = getByteToInt(bufferData)
        diaconnG8Pump.makeMonth = getByteToInt(bufferData)
        diaconnG8Pump.makeDay = getByteToInt(bufferData)
        diaconnG8Pump.lotNo = getByteToInt(bufferData)// LOT NO
        diaconnG8Pump.serialNo = getShortToInt(bufferData)
        diaconnG8Pump.majorVersion = getByteToInt(bufferData)
        diaconnG8Pump.minorVersion = getByteToInt(bufferData)


        aapsLogger.debug(LTag.PUMPCOMM, "Result       --> ${diaconnG8Pump.result}")
        aapsLogger.debug(LTag.PUMPCOMM, "country      --> ${diaconnG8Pump.country}")
        aapsLogger.debug(LTag.PUMPCOMM, "productType  --> ${diaconnG8Pump.productType}")
        aapsLogger.debug(LTag.PUMPCOMM, "makeYear     --> ${diaconnG8Pump.makeYear}")
        aapsLogger.debug(LTag.PUMPCOMM, "makeMonth    --> ${diaconnG8Pump.makeMonth}")
        aapsLogger.debug(LTag.PUMPCOMM, "makeDay      --> ${diaconnG8Pump.makeDay}")
        aapsLogger.debug(LTag.PUMPCOMM, "lotNo        --> ${diaconnG8Pump.lotNo}")
        aapsLogger.debug(LTag.PUMPCOMM, "serialNo     --> ${diaconnG8Pump.serialNo}")
        aapsLogger.debug(LTag.PUMPCOMM, "majorVersion --> ${diaconnG8Pump.majorVersion}")
        aapsLogger.debug(LTag.PUMPCOMM, "minorVersion --> ${diaconnG8Pump.minorVersion}")

        preferences.put(DiaconnStringNonKey.PumpVersion, diaconnG8Pump.majorVersion.toString() + "." + diaconnG8Pump.minorVersion.toString())
    }

    override val friendlyName = "PUMP_SERIAL_NUM_INQUIRE_RESPONSE"
}