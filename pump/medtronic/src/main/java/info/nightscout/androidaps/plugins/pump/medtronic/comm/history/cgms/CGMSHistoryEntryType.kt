package info.nightscout.androidaps.plugins.pump.medtronic.comm.history.cgms

/**
 * This file was taken from GGC - GNU Gluco Control (ggc.sourceforge.net), application for diabetes
 * management and modified/extended for AAPS.
 *
 * Author: Andy {andy.rozman@gmail.com}
 */
enum class CGMSHistoryEntryType(val code: Int, val description: String, val headLength: Int, val dateLength: Int, val bodyLength: Int, dateType: DateType) {

    None(0, "None", 1, 0, 0, DateType.None),  //
    DataEnd(0x01, "DataEnd", 1, 0, 0, DateType.PreviousTimeStamp),  //
    SensorWeakSignal(0x02, "SensorWeakSignal", 1, 0, 0, DateType.PreviousTimeStamp),  //
    SensorCal(0x03, "SensorCal", 1, 0, 1, DateType.PreviousTimeStamp),  //
    SensorPacket(0x04, "SensorPacket", 1, 0, 1, DateType.PreviousTimeStamp),
    SensorError(0x05, "SensorError", 1, 0, 1, DateType.PreviousTimeStamp),
    SensorDataLow(0x06, "SensorDataLow", 1, 0, 1, DateType.PreviousTimeStamp),
    SensorDataHigh(0x07, "SensorDataHigh", 1, 0, 1, DateType.PreviousTimeStamp),
    SensorTimestamp(0x08, "SensorTimestamp", 1, 4, 0, DateType.MinuteSpecific),  //
    BatteryChange(0x0a, "BatteryChange", 1, 4, 0, DateType.MinuteSpecific),  //
    SensorStatus(0x0b, "SensorStatus", 1, 4, 0, DateType.MinuteSpecific),  //
    DateTimeChange(0x0c, "DateTimeChange", 1, 4, 0, DateType.SecondSpecific),  //
    SensorSync(0x0d, "SensorSync',packet_size=4", 1, 4, 0, DateType.MinuteSpecific),  //
    CalBGForGH(0x0e, "CalBGForGH',packet_size=5", 1, 4, 1, DateType.MinuteSpecific),  //
    SensorCalFactor(0x0f, "SensorCalFactor", 1, 4, 2, DateType.MinuteSpecific),  //
    Something10(0x10, "10-Something", 1, 4, 0, DateType.MinuteSpecific),  //
    Something19(0x13, "19-Something", 1, 0, 0, DateType.PreviousTimeStamp),
    GlucoseSensorData(0xFF, "GlucoseSensorData", 1, 0, 0, DateType.PreviousTimeStamp),
    UnknownOpCode(0xFF, "Unknown", 0, 0, 0, DateType.None);

    companion object {

        private val opCodeMap: MutableMap<Int, CGMSHistoryEntryType> = mutableMapOf()

        fun getByCode(opCode: Int): CGMSHistoryEntryType =
            opCodeMap[opCode] ?: UnknownOpCode

        init {
            for (type in values()) {
                opCodeMap[type.code] = type
            }
        }
    }

    var schemaSet: Boolean = true
    val totalLength: Int = headLength + dateLength + bodyLength
    val dateType: DateType

    fun hasDate(): Boolean {
        return dateType == DateType.MinuteSpecific || dateType == DateType.SecondSpecific
    }

    enum class DateType {
        None,  //
        MinuteSpecific,  //
        SecondSpecific,  //
        PreviousTimeStamp //
    }

    init {
        this.dateType = dateType
    }
}