package app.aaps.plugins.eversense.connection

import java.util.concurrent.CancellationException

/** Owns connection intent, one retry, the attempt deadline and the data watchdog. */
internal class EversenseConnectionController(
    private val scheduler: Scheduler,
    private val now: () -> Long,
    private val transport: Transport
) {
    interface Scheduler {
        fun after(delayMs: Long, action: () -> Unit): Task
    }
    fun interface Task { fun cancel() }
    interface Transport {
        fun open(session: Long): Boolean
        fun reuse(session: Long): Boolean
        fun close()
    }
    enum class State { STOPPED, PAUSED, DISCONNECTED, CONNECTING, READY }

    private val lock = Any()
    private var running = false
    private var wanted = false
    private var cloudGeneration = 0L
    private var session = 0L
    private var attempts = 0
    private var lastData = 0L
    private var retry: Task? = null
    private var deadline: Task? = null
    private var watchdog: Task? = null
    @Volatile var state = State.STOPPED
        private set

    fun start(autoConnect: Boolean = true) = synchronized(lock) {
        if (running) return@synchronized
        running = true
        wanted = autoConnect
        if (wanted) attempt(false) else state = State.PAUSED
    }

    fun stop() = synchronized(lock) {
        running = false
        wanted = false
        invalidate(State.STOPPED)
    }

    fun disconnect() = synchronized(lock) {
        wanted = false
        invalidate(if (running) State.PAUSED else State.STOPPED)
    }

    fun connect(force: Boolean = false): Boolean = synchronized(lock) {
        if (!running) return@synchronized false
        wanted = true
        if (!force && state == State.READY) return@synchronized true
        if (!force && state == State.CONNECTING) return@synchronized false
        attempt(false)
    }

    fun bluetoothAvailable() = synchronized(lock) {
        if (running && wanted) attempt(false)
    }

    fun owns(token: Long): Boolean = synchronized(lock) { running && wanted && token == session }
    fun active(token: Long): Boolean = synchronized(lock) { owns(token) && (state == State.CONNECTING || state == State.READY) }

    fun deliver(token: Long, action: () -> Unit) = synchronized(lock) {
        if (active(token)) action()
    }

    fun whileOwned(token: Long, action: () -> Unit) = synchronized(lock) {
        if (owns(token)) action()
    }

    fun cloudStore(delegate: app.aaps.plugins.eversense.storage.EversenseStore): app.aaps.plugins.eversense.storage.EversenseStore? = synchronized(lock) {
        if (!running || !wanted) null else SessionStore(this, cloudGeneration, delegate, cloud = true)
    }

    fun <T> withCloudSession(token: Long, action: () -> T): T = synchronized(lock) {
        if (!running || !wanted || token != cloudGeneration) throw CancellationException("Eversense cloud session has ended")
        action()
    }

    fun <T> withSession(token: Long, action: () -> T): T = synchronized(lock) {
        if (!active(token)) throw CancellationException("Eversense session has ended")
        action()
    }

    fun ready(token: Long) = synchronized(lock) {
        if (!active(token)) return@synchronized
        state = State.READY
        attempts = 0
        lastData = now()
        deadline?.cancel(); deadline = null
        scheduleWatchdog(token)
    }

    fun dataReceived(token: Long) = synchronized(lock) {
        if (active(token)) lastData = now()
    }

    fun disconnected(token: Long, status: Int, reuse: Boolean = false) = synchronized(lock) {
        if (!active(token)) return@synchronized
        state = State.DISCONNECTED
        deadline?.cancel(); deadline = null
        watchdog?.cancel(); watchdog = null
        if (!reuse) transport.close()
        scheduleRetry(if (status == 19 && !reuse) 30_000L else backoff(), reuse)
    }

    private fun attempt(reuse: Boolean): Boolean {
        retry?.cancel(); retry = null
        deadline?.cancel(); deadline = null
        watchdog?.cancel(); watchdog = null
        if (!reuse) {
            session++ // Invalidate callbacks and queued work before closing the old client.
            transport.close()
        }
        state = State.CONNECTING
        val token = session
        val accepted = try {
            if (reuse) transport.reuse(token) else transport.open(token)
        } catch (_: SecurityException) {
            false
        }
        if (!accepted) {
            state = State.DISCONNECTED
            transport.close()
            scheduleRetry(60_000L, false)
        } else if (state == State.CONNECTING && active(token)) {
            deadline = scheduler.after(180_000L) {
                synchronized(lock) {
                    if (active(token) && state == State.CONNECTING) disconnected(token, -1)
                }
            }
        }
        return accepted
    }

    private fun backoff(): Long = minOf(5_000L * (1L shl minOf(attempts++, 4)), 60_000L)

    private fun scheduleRetry(delay: Long, reuse: Boolean) {
        retry?.cancel()
        val token = session
        retry = scheduler.after(delay) {
            synchronized(lock) {
                if (owns(token) && state == State.DISCONNECTED) attempt(reuse)
            }
        }
    }

    private fun scheduleWatchdog(token: Long) {
        watchdog?.cancel()
        watchdog = scheduler.after(60_000L) {
            synchronized(lock) {
                if (!active(token) || state != State.READY) return@synchronized
                if (now() - lastData > 7 * 60_000L) attempt(false)
                else scheduleWatchdog(token)
            }
        }
    }

    private fun invalidate(next: State) {
        cloudGeneration++
        session++
        state = next
        retry?.cancel(); retry = null
        deadline?.cancel(); deadline = null
        watchdog?.cancel(); watchdog = null
        attempts = 0
        transport.close()
    }
}
