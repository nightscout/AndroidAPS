package app.aaps.core.interfaces.queue

import app.aaps.core.interfaces.pump.PumpEnactResult

abstract class Callback : Runnable {

    lateinit var result: PumpEnactResult
    fun result(result: PumpEnactResult): Callback {
        this.result = result
        return this
    }
}