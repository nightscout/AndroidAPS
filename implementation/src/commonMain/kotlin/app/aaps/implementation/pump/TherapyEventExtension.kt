package app.aaps.implementation.pump

import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.utils.DateUtil

fun TE.isOlderThan(hours: Int, dateUtil: DateUtil): Boolean {
    return getHoursFromStart(dateUtil) > hours
}

private fun TE.getHoursFromStart(dateUtil: DateUtil): Double {
    return (dateUtil.now() - timestamp) / (60 * 60 * 1000.0)
}
