package app.aaps.core.interfaces.nsclient

/**
 *
 * {"mgdl":105,"mills":1455136282375,"device":"xDrip-BluetoothWixel","direction":"Flat","filtered":98272,"unfiltered":98272,"noise":1,"rssi":100}
 */
@Suppress("SpellCheckingInspection")
interface NSSgv {

    val mgdl: Int?
    val filtered: Int?
    val unfiltered: Int?
    val noise: Int?
    val rssi: Int?
    val mills: Long?
    val device: String?
    val direction: String?
    val id: String?
}