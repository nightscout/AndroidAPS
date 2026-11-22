package app.aaps.plugins.libre3.gatt

import java.util.UUID

/**
 * The shared [Libre3BluetoothUuids] text, as the `java.util.UUID` values the Android BLE stack
 * takes. Built once, so a `when` branch can compare them the way it always did.
 */
object Libre3GattUuids {

    /** Service that carries the glucose data and the status. Also used to find the sensor. */
    val SERVICE: UUID = UUID.fromString(Libre3BluetoothUuids.SERVICE)

    /** Service that carries the pairing messages. */
    val SECURITY_SERVICE: UUID = UUID.fromString(Libre3BluetoothUuids.SECURITY_SERVICE)

    /** The descriptor every characteristic uses to turn its messages on and off. */
    val CLIENT_CHARACTERISTIC_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    /**
     * The shared text of a UUID that came from the radio.
     *
     * `UUID.toString` is lower case and the shared table is upper case, so the case is put back
     * here. Without that a channel the sensor sent on would not match the constant it is, and the
     * driver would drop a glucose message as if it came from a channel it does not know.
     */
    fun textOf(uuid: UUID): String = uuid.toString().uppercase()

    /** The platform value of one of the shared texts. */
    fun uuidOf(text: String): UUID = UUID.fromString(text)
}
