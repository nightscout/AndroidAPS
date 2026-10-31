package app.aaps.pump.dana.comm

object RecordTypes {

    const val RECORD_TYPE_BOLUS = 0x01.toByte()
    const val RECORD_TYPE_DAILY = 0x02.toByte()
    const val RECORD_TYPE_PRIME = 0x03.toByte()
    const val RECORD_TYPE_ERROR = 0x04.toByte()
    const val RECORD_TYPE_ALARM = 0x05.toByte()
    const val RECORD_TYPE_GLUCOSE = 0x06.toByte()
    const val RECORD_TYPE_CARBO = 0x08.toByte()
    const val RECORD_TYPE_REFILL = 0x09.toByte()
    const val RECORD_TYPE_SUSPEND = 0x0B.toByte()
    const val RECORD_TYPE_BASALHOUR = 0x0C.toByte()
    const val RECORD_TYPE_TB = 0x0D.toByte()
    const val RECORD_TYPE_TEMP_BASAL = 0x14.toByte()
}