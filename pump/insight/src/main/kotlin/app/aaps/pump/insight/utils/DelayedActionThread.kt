package app.aaps.pump.insight.utils

class DelayedActionThread private constructor(name: String, private val duration: Long, private val runnable: Runnable) : Thread() {

    override fun run() {
        try {
            sleep(duration)
            runnable.run()
        } catch (e: InterruptedException) {
        }
    }

    companion object {

        fun runDelayed(name: String, duration: Long, runnable: Runnable): DelayedActionThread {
            val delayedActionThread = DelayedActionThread(name, duration, runnable)
            delayedActionThread.start()
            return delayedActionThread
        }
    }

    init {
        setName(name)
    }
}