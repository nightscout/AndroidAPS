package info.nightscout.androidaps.plugins.pump.carelevo.ble.data

import java.util.UUID

data class BleParams(
    val cccd : UUID,
    val serviceUuid : UUID,
    val txUuid : UUID,
    val rxUUID: UUID
)

data class ConfigParams(
    val isForeground : Boolean = true
)