package app.aaps.core.data.db

import java.util.TimeZone

data class TDD(
    var id: Long = 0,
    var version: Int = 0,
    var dateCreated: Long = -1,
    var isValid: Boolean = true,
    var referenceId: Long? = null,
    var ids: IDs = IDs(),
    var timestamp: Long,
    var utcOffset: Long = TimeZone.getDefault().getOffset(timestamp).toLong(),
    var basalAmount: Double = 0.0,
    var bolusAmount: Double = 0.0,
    var totalAmount: Double = 0.0, // if zero it's calculated as basalAmount + bolusAmount
    var carbs: Double = 0.0
) {

    companion object
}