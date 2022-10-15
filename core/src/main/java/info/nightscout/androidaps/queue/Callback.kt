package info.nightscout.androidaps.queue

import info.nightscout.androidaps.data.PumpEnactResult

abstract class Callback : Runnable {

    lateinit var result: PumpEnactResult
    fun result(result: PumpEnactResult): Callback {
        this.result = result
        return this
    }
}