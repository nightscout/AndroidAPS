package app.aaps.core.interfaces.configuration

/**
 * Ends the running app, which is the one part of [ConfigBuilder.exitApp] that is not shared.
 *
 * Everything around it - announcing the exit on the bus, logging it, and writing the user entry - is
 * the same on every platform and stays in `ConfigBuilderImpl`. Only the last step differs, and it
 * differs completely: Android schedules a relaunch through `AlarmManager` and then kills the
 * process, a desktop simply exits, and iOS is not allowed to do either.
 *
 * ## This is not a formality on Apple
 *
 * An iOS app may not terminate itself. Apple treats a self-terminating app as a crash, and an
 * implementation that quietly called `exitProcess` would be shipping a crash on purpose. So an
 * implementation is allowed to **refuse**, and callers must not assume the process is gone when this
 * returns - on the platforms that do exit, it never returns at all.
 */
interface AppExit {

    /**
     * Ends the process.
     *
     * @param launchAgain start the app again afterwards, for a restart rather than a shutdown. A
     *   platform that cannot relaunch itself should still exit, and say in the log that it could not
     *   honour this - stopping is what the caller asked for, and the restart is the extra.
     */
    fun exit(launchAgain: Boolean)
}
