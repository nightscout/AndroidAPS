package app.aaps.pump.omnipod.common.bledriver.comm.interfaces.io

import java.math.BigInteger
import java.util.UUID

enum class CharacteristicType(val value: String) {
    CMD("1a7e2441-e3ed-4464-8b7e-751e03d0dc5f"),
    DATA("1a7e2442-e3ed-4464-8b7e-751e03d0dc5f"),

    /**
     * Omnipod 5's data characteristic. O5 shares Dash's CMD characteristic (and GATT
     * service) but uses a different DATA characteristic - see
     * [app.aaps.pump.omnipod.common.bledriver.pod.util.BluetoothServiceUuids] for the
     * canonical source of these UUIDs (this enum predates that file and is left as the
     * existing GATT-wiring mechanism rather than migrated wholesale, to avoid touching
     * more of the working Dash connection path than necessary).
     */
    DATA_O5("1a7e2443-e3ed-4464-8b7e-751e03d0dc5f");

    val uuid: UUID
        get() = UUID(
            BigInteger(value.replace("-", "").substring(0, 16), 16).toLong(),
            BigInteger(value.replace("-", "").substring(16), 16).toLong()
        )

    companion object {

        fun byValue(value: String): CharacteristicType =
            CharacteristicType.entries.firstOrNull { it.value == value }
                ?: throw IllegalArgumentException("Unknown Characteristic Type: $value")
    }
}
