package app.aaps.core.interfaces.smsCommunicator

fun formatBolusCarbsCommand(insulin: Double, carbs: Int, time: String? = null, set_alarm: Boolean = false): String {
    var msg = "Boluscarbs " + "%.2f ".format(insulin) + carbs.toString()
    if (!time.isNullOrEmpty()) {
        if (time.all { it.isDigit() })
        // xx min offset
            msg += " +" + time
        else
        // either of -yy, +xx, 1:23, 2:34PM
            msg += " " + time

        if (set_alarm == true)
            msg += " alarm"
    }
    return msg
}
