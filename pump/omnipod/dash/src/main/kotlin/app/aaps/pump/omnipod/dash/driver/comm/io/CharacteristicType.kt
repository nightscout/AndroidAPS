package app.aaps.pump.omnipod.dash.driver.comm.io

import java.math.BigInteger
import java.util.*

enum class CharacteristicType(val value: String) {
    CMD("1a7e2441-e3ed-4464-8b7e-751e03d0dc5f"), DATA("1a7e2442-e3ed-4464-8b7e-751e03d0dc5f");

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
