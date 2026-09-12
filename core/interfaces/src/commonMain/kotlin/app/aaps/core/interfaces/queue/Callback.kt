package app.aaps.core.interfaces.queue

import app.aaps.core.interfaces.pump.PumpEnactResult

/**
 * Result handler for a queued [Command].
 *
 * Declares its own [run] instead of implementing `java.lang.Runnable`. Nothing ever handed a
 * Callback to something that wanted a Runnable, and Runnable is JVM only, so the supertype only
 * tied this file to one platform.
 */
abstract class Callback {

    lateinit var result: PumpEnactResult
    fun result(result: PumpEnactResult): Callback {
        this.result = result
        return this
    }

    abstract fun run()
}
