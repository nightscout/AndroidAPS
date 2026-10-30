package app.aaps.plugins.libre3.session

/**
 * What the driver may send to the sensor when a session ends.
 *
 * The answer is always the same: nothing. Ending a session is a link level action only.
 *
 * The sensor has a command channel that can tell it to stop for good. This version never writes
 * to that channel, for any reason. A stopped sensor cannot be started again, so a bug, a crash or
 * a user simply switching the plugin off must never be able to end a sensor that still had days
 * of life in it.
 */
object Libre3DisconnectPolicy {

    /** Why a session is ending. Kept so the log tells the reasons apart. */
    enum class Reason {
        USER_STOPPED_PLUGIN,
        USER_DISCONNECTED,
        LINK_LOST,
        DRIVER_SWITCHED,
        PROCESS_ENDING,
        SENSOR_EXPIRED,
    }

    /**
     * @return true when the driver may write a command to the sensor before it drops the link.
     *   It is false for every reason, and there is no plan to add a reason where it is true.
     */
    fun mayWriteSensorCommand(reason: Reason): Boolean = false

    /** @return true when the driver may drop the link, which is the only allowed action. */
    fun mayDropLink(reason: Reason): Boolean = true
}
