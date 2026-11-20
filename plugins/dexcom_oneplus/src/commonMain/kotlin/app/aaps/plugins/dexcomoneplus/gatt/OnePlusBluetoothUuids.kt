package app.aaps.plugins.dexcomoneplus.gatt

/**
 * Dex family GATT UUIDs used by xDrip Direct / Ob1 path.
 *
 * They are kept as text, not as a platform UUID type. The text is the protocol, so it lives here
 * and the platform turns it into its own type once - see `OnePlusGattUuids` on Android.
 *
 * Provenance: NightscoutFoundation/xDrip `BluetoothServices.java`
 * at pin `1e86d9a2a52577ed2c30fbf7b69d75fd56e6918f` (GPL-3.0).
 * See `plugins/dexcom_oneplus/NOTICE`.
 *
 * Historical file comment upstream: "Created by joeginley on 3/16/16."
 */
object OnePlusBluetoothUuids {

    const val DeviceInfo: String = "0000180A-0000-1000-8000-00805F9B34FB"
    const val Advertisement: String = "0000FEBC-0000-1000-8000-00805F9B34FB"
    const val CgmService: String = "F8083532-849E-531C-C594-30F1F86A4EA5"
    const val ServiceB: String = "F8084532-849E-531C-C594-30F1F86A4EA5"

    const val ManufacturerNameString: String = "00002A29-0000-1000-8000-00805F9B34FB"

    const val Communication: String = "F8083533-849E-531C-C594-30F1F86A4EA5"
    const val Control: String = "F8083534-849E-531C-C594-30F1F86A4EA5"
    const val Authentication: String = "F8083535-849E-531C-C594-30F1F86A4EA5"
    const val ProbablyBackfill: String = "F8083536-849E-531C-C594-30F1F86A4EA5"
    const val ExtraData: String = "F8083538-849E-531C-C594-30F1F86A4EA5"

    const val CharacteristicE: String = "F8084533-849E-531C-C594-30F1F86A4EA5"
    const val CharacteristicF: String = "F8084534-849E-531C-C594-30F1F86A4EA5"

    const val CharacteristicUpdateNotification: String = "00002902-0000-1000-8000-00805F9B34FB"
}
