package com.nightscout.eversense.packets.e365

class Eversense365Packets {
    companion object {

        const val AuthenticateCommandId = 0x09.toByte()
        const val AuthenticateResponseId = 0x0B.toByte()

        const val ReadCommandId = 0x02.toByte()
        const val ReadResponseId = 0x42.toByte()

        const val WriteCommandId = 0x03.toByte()
        const val WriteResponseId = 0x43.toByte()

        const val NotificationResponseId = 0x44.toByte()

        const val AuthenticateWhoAmI = 0x01.toByte()
        const val AuthenticateIdentity = 0x02.toByte()
        const val AuthenticateStart = 0x03.toByte()

        const val ReadPing = 0x01.toByte()
        const val ReadLogRangeOld = 0x09.toByte()
        const val ReadSignalStrength = 0x1B.toByte()
        const val ReadCalibrationInfo = 0x1D.toByte()
        const val ReadGlucoseData = 0x1F.toByte()
        const val ReadSensorInformation = 0x20.toByte()
        const val ReadPatientInformation = 0x21.toByte()
        const val ReadActiveAlerts = 0x22.toByte()
        const val ReadLogRange = 0x38.toByte()
        const val ReadLogValue = 0x3A.toByte()

        const val WriteCurrentDateTime = 0x01.toByte()

        const val NotificationKeepAlive = 0x02.toByte()


        fun isNotificationPacket(value: Byte): Boolean {
            return value == NotificationResponseId
        }

        fun isKeepAlivePacket(value1: Byte, value2: Byte): Boolean {
            return value1 == NotificationResponseId && value2 == NotificationKeepAlive
        }
    }
}