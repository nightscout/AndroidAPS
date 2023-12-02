package app.aaps.core.data.model

import java.util.TimeZone

data class TDD(
    override var id: Long = 0,
    override var version: Int = 0,
    override var dateCreated: Long = -1,
    override var isValid: Boolean = true,
    override var referenceId: Long? = null,
    override var ids: IDs = IDs(),
    var timestamp: Long,
    var utcOffset: Long = TimeZone.getDefault().getOffset(timestamp).toLong(),
    var basalAmount: Double = 0.0,
    var bolusAmount: Double = 0.0,
    var totalAmount: Double = 0.0, // if zero it's calculated as basalAmount + bolusAmount
    var carbs: Double = 0.0
) : HasIDs {

    companion object
}