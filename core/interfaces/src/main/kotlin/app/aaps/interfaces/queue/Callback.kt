package app.aaps.interfaces.queue

import app.aaps.interfaces.pump.PumpEnactResult

abstract class Callback : Runnable {

    lateinit var result: PumpEnactResult
    fun result(result: PumpEnactResult): Callback {
        this.result = result
        return this
    }
}