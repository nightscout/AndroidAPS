package info.nightscout.interfaces.queue

import info.nightscout.interfaces.pump.PumpEnactResult

abstract class Callback : Runnable {

    lateinit var result: PumpEnactResult
    fun result(result: PumpEnactResult): Callback {
        this.result = result
        return this
    }
}