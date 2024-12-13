package app.aaps.pump.medtronic.comm.message

import java.util.*

/**
 * Created by geoff on 5/29/16.
 * refactored into enum
 */
enum class PacketType(value: Int) {

    Invalid(0x00),  //
    MySentry(0xa2),  //
    Meter(0xa5),  //
    Carelink(0xa7),  //
    Sensor(0xa8 //
    );

    companion object {

        var mapByValue: MutableMap<Byte, PacketType> = HashMap()

        fun getByValue(value: Short): PacketType? {
            return if (mapByValue.containsKey(value.toByte())) mapByValue[value.toByte()] else Invalid
        }

        init {
            for (packetType in PacketType.entries) {
                mapByValue[packetType.value] = packetType
            }
        }
    }

    val value: Byte = value.toByte()
}