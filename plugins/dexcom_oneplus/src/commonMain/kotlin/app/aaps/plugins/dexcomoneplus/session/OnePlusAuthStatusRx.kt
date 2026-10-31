package app.aaps.plugins.dexcomoneplus.session

/**
 * AuthStatus (opcode 0x05) fields from Dexcom G7/ONE+ Authentication characteristic.
 *
 * Semantics aligned with Juggluco [DexGattCallback.authenticate] ChallengeReply and
 * xDrip libkeks [AuthStatusRxMessage]: only [authenticated] == 1 is crypto success.
 */
internal data class OnePlusAuthStatusRx(
    val authenticated: Int,
    val bonded: Int,
) {
    val isAuthenticated: Boolean get() = authenticated == AUTHENTICATED_OK
    val sensorReportsBonded: Boolean get() = bonded == SENSOR_BONDED
    val needsKeyRefresh: Boolean get() = bonded == SENSOR_NEEDS_REFRESH

    companion object {
        const val OPCODE: Int = 0x05
        const val AUTHENTICATED_OK: Int = 1
        const val SENSOR_BONDED: Int = 1
        /** Juggluco accepts OS-bonded + this after full cert exchange. */
        const val SENSOR_BOND_ALT: Int = 2
        const val SENSOR_NEEDS_REFRESH: Int = 3

        fun parse(payload: ByteArray): OnePlusAuthStatusRx? {
            if (payload.size < 3) return null
            if ((payload[0].toInt() and 0xff) != OPCODE) return null
            return OnePlusAuthStatusRx(
                authenticated = payload[1].toInt() and 0xff,
                bonded = payload[2].toInt() and 0xff,
            )
        }
    }
}
