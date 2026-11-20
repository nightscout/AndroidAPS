package app.aaps.plugins.libre3.gatt

/**
 * Bluetooth identifiers of a Libre 3 or Libre 3 Plus sensor.
 *
 * All of them share the same shape, `0898xxxx-EF89-11E9-81B4-2A2AE2DBCCE4`.
 * Ported from LibreCRKit `BLE/LibreSensorGATT.swift` at pin `a86b92f`.
 *
 * They are kept as text, not as a platform type, because each platform spells that type
 * differently: Android builds a `java.util.UUID`. The text is the protocol, so it lives here and
 * the platform turns it into its own type once - see `Libre3GattUuids` on Android.
 * The text is upper case, which is what `Libre3GattUuids.textOf` gives back, so a value that came
 * from the radio can be compared with one of these with `==`.
 *
 * There is no Libre 2 identifier here and there must never be one. Libre 2 is out of scope.
 */
object Libre3BluetoothUuids {

    private fun sensorUuid(short: String): String = "$short-EF89-11E9-81B4-2A2AE2DBCCE4"

    /** Service that carries the glucose data and the status. Also used to find the sensor. */
    val SERVICE: String = sensorUuid("089810CC")

    /** Service that carries the pairing messages. */
    val SECURITY_SERVICE: String = sensorUuid("0898203A")

    val GLUCOSE_DATA: String = sensorUuid("0898177A")
    val PATCH_STATUS: String = sensorUuid("08981482")
    val PATCH_CONTROL: String = sensorUuid("08981338")
    val HISTORIC_DATA: String = sensorUuid("0898195A")
    val EVENT_LOG: String = sensorUuid("08981BEE")
    val CLINICAL_DATA: String = sensorUuid("08981AB8")
    val FACTORY_DATA: String = sensorUuid("08981D24")

    val SEC_CERT_DATA: String = sensorUuid("089823FA")
    val SEC_CHALLENGE_DATA: String = sensorUuid("089822CE")
    val SEC_COMMAND_RESPONSE: String = sensorUuid("08982198")

    /**
     * The three channels the pairing needs. They must be listening **before** the handshake
     * starts, otherwise the answers of the sensor are lost.
     */
    val HANDSHAKE_CHANNELS: List<String> = listOf(SEC_CERT_DATA, SEC_CHALLENGE_DATA, SEC_COMMAND_RESPONSE)

    /**
     * The seven channels that must be turned off and on again after the handshake, in this order.
     *
     * The sensor looks at all of them before it starts sending. If some are left off, the link
     * stays open but no glucose ever arrives. The last two are not read in this version, but they
     * are still armed, because a sensor with them off refuses other commands as well.
     *
     * Order and set come from LibreLoop `LibreLoopSensorMonitor.refreshPostAuthNotifications`
     * together with LibreCRKit `LibreSensorGATT.Char.dataPlaneNotifying`, not from the five
     * channel list alone.
     */
    val DATA_PLANE_CHANNELS: List<String> = listOf(
        PATCH_CONTROL,
        EVENT_LOG,
        FACTORY_DATA,
        GLUCOSE_DATA,
        PATCH_STATUS,
        HISTORIC_DATA,
        CLINICAL_DATA,
    )
}
