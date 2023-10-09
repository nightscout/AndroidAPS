package app.aaps.core.main.extensions

import app.aaps.core.data.db.TE
import app.aaps.core.interfaces.utils.DateUtil

fun TE.isOlderThan(hours: Double, dateUtil: DateUtil): Boolean {
    return getHoursFromStart(dateUtil) > hours
}

fun TE.getHoursFromStart(dateUtil: DateUtil): Double {
    return (dateUtil.now() - timestamp) / (60 * 60 * 1000.0)
}