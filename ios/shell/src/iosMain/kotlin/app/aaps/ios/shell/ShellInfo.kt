package app.aaps.ios.shell

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * A small entry point the iOS side can call to prove the framework loaded and runs.
 *
 * The link itself is what this module is for, so this stays deliberately small. It gives an Xcode
 * project something to call, so that "the framework builds", "an app can reach it" and "Kotlin code
 * actually runs" stay three separate questions with three separate answers.
 */
object ShellInfo {

    /** Name of the framework, so a caller can print something it did not hard code itself. */
    const val NAME: String = "AapsShared"

    /** How many migrated modules this framework links. Kept in step with `ios/shell/build.gradle.kts`. */
    const val LINKED_MODULES: Int = 14

    /**
     * The current local time, formatted by Kotlin.
     *
     * Swift could read a clock by itself, so the value is not the point. Running this proves the
     * Kotlin runtime started and that a multiplatform dependency, here kotlinx-datetime, works
     * inside a real app rather than only linking.
     */
    fun localTime(): String = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).toString()
}
