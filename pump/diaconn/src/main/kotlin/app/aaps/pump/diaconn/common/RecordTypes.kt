package app.aaps.pump.diaconn.common

object RecordTypes {
    const val RECORD_TYPE_BOLUS = 0x01.toByte()
    const val RECORD_TYPE_DAILY = 0x02.toByte()
    const val RECORD_TYPE_ALARM = 0x03.toByte()
    const val RECORD_TYPE_REFILL = 0x04.toByte()
    const val RECORD_TYPE_SUSPEND = 0x05.toByte()
    const val RECORD_TYPE_BASALHOUR = 0x06.toByte()
    const val RECORD_TYPE_TB = 0x07.toByte()
}