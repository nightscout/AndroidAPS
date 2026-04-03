package app.aaps.pump.eopatch.core.ble

import android.os.ParcelUuid
import java.util.UUID

interface IPatchPacketConstant {

    companion object {

        val BIG_CALL_UUID: UUID = UUID.fromString("00002503-0000-1000-8000-00805f9b34fb")
        val BIG_READ_UUID: UUID = UUID.fromString("00002504-0000-1000-8000-00805f9b34fb")
        val INFO_UUID: UUID = UUID.fromString("00002506-0000-1000-8000-00805f9b34fb")
        val ALARM_UUID: UUID = UUID.fromString("00002507-0000-1000-8000-00805f9b34fb")
        val SERVICE_UUID: ParcelUuid = ParcelUuid.fromString("00005201-0000-1000-8000-00805f9b34fb")

        const val MTU_SIZE = 256

        const val BIG_API: Byte = 0x40
        const val LEGACY: Byte = 0x50
        const val CIPHER: Byte = 0x20

        const val KEY0 = 0
        const val KEY1 = 1
        const val FUNC0 = 2
        const val FUNC1 = 3
        const val DATA0 = 4
        const val DATA1 = 5
        const val DATA2 = 6
        const val DATA3 = 7
        const val DATA4 = 8
        const val DATA5 = 9
        const val DATA6 = 10
        const val DATA7 = 11
        const val DATA8 = 12
        const val DATA9 = 13
        const val DATA10 = 14
    }
}
