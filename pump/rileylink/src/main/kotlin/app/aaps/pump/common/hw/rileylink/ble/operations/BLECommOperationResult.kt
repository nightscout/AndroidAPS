package app.aaps.pump.common.hw.rileylink.ble.operations

/**
 * Created by geoff on 5/26/16.
 */
class BLECommOperationResult {

    var value: ByteArray? = null
    var resultCode: Int = 0

    companion object {

        const val RESULT_NONE: Int = 0
        const val RESULT_SUCCESS: Int = 1
        const val RESULT_TIMEOUT: Int = 2
        const val RESULT_BUSY: Int = 3
        const val RESULT_INTERRUPTED: Int = 4
        const val RESULT_NOT_CONFIGURED: Int = 5
    }
}
