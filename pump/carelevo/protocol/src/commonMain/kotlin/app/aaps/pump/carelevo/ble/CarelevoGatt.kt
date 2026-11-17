package app.aaps.pump.carelevo.ble

import app.aaps.core.interfaces.pump.ble.BleUuid
import app.aaps.core.interfaces.pump.ble.CharacteristicId

/**
 * The Bluetooth GATT of a CareLevo patch: one service, one characteristic the app writes its commands to and one the
 * patch sends its answers and reports on (notifications, not indications). Here, in the protocol, because the driver
 * and the emulator both need it and neither depends on the other.
 */
object CarelevoGatt {

    val SERVICE: BleUuid = BleUuid.of("e1b40001-ffc4-4daa-a49b-1c92f99072ab")

    /** The app writes here; the vendor code calls it "rx", seen from the patch. */
    val WRITE = CharacteristicId(SERVICE, BleUuid.of("e1b40002-ffc4-4daa-a49b-1c92f99072ab"))

    /** The patch sends here; the vendor code calls it "tx". */
    val NOTIFY = CharacteristicId(SERVICE, BleUuid.of("e1b40003-ffc4-4daa-a49b-1c92f99072ab"))
}
