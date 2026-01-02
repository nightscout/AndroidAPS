package app.aaps.core.interfaces.pump

/**
 * Store data about ongoing bolus. Singleton is used to persist app swipe out.
 * Except `delivered` It's purely UI related because we want to be able restore progress
 */
object BolusProgressData {

    /**
     * Call this before popping up BolusProgressDialog
     * @param insulin Initial bolus amount
     * @param isSMB true for SMB bolus
     * @param id ID from DetailedBolusInfo for bolus identification. Progress updates with different ID are ignored
     */
    fun set(insulin: Double, isSMB: Boolean, id: Long) {
        this.insulin = insulin
        this.isSMB = isSMB
        this.id = id
        delivered = 0.0
        bolusEnded = false
        stopPressed = false
        status = ""
        percent = 0
    }

    /**
     * Initial bolus amount
     */
    var insulin: Double = 0.0

    /**
     * Actually delivered bolus amount.
     * (May not be used by all pumps)
     */
    var delivered: Double = 0.0

    /**
     * SMB flag
     */
    var isSMB: Boolean = false

    /**
     * ID from DetailedBolusInfo
     */
    var id: Long = -1

    /**
     * Last received status update
     */
    var status = ""
    var percent = 0

    var bolusEnded = false

    /**
     * set to true if user press STOP button
     */
    var stopPressed = false
}