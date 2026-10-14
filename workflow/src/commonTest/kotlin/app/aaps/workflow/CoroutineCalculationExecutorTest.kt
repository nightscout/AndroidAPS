package app.aaps.workflow

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.workflow.CalculationWakeLock
import app.aaps.core.interfaces.workflow.CalculationWorkflow.Companion.MAIN_CALCULATION
import app.aaps.core.objects.workflow.WorkOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The coroutine executor, which every platform runs the calculation chain on.
 *
 * In commonTest, so it runs on iOS too and not only on the JVM: the executor is shared code on the
 * loop's path. That is why the phases are plain fakes and the shared state uses Kotlin's own atomics.
 *
 * Most tests here are about one thing: that a replaced run knows it has been replaced. Cancelling a
 * coroutine is cooperative, so an outgoing run keeps going until it next asks whether it should stop.
 * That question used to be "is there a live job under my name?" - which the replacement had already
 * made true again. So the run that had just been cancelled was told it was still current and carried
 * on.
 *
 * Two things followed, and only the first was visible: both runs emitted progress into the same
 * signals, so during the first Nightscout sync the bar jumped between their two positions instead of
 * advancing; and the replaced run went on doing full IOB passes over data that was still arriving,
 * which is the worst possible moment to do the work twice.
 *
 * The rest came with Android moving onto this executor from WorkManager: starts must replace each
 * other in call order, a phase that throws must not end the app, a failed prepare phase must not let
 * the loop run, and each run must hold the wake lock until it ends.
 */
@OptIn(ExperimentalAtomicApi::class)
class CoroutineCalculationExecutorTest {

    /** A list that several threads add to: immutable copies behind one atomic reference. */
    private class Recorder<T> {

        private val items = AtomicReference(emptyList<T>())

        fun add(item: T) {
            while (true) {
                val old = items.load()
                if (items.compareAndSet(old, old + item)) return
            }
        }

        val all: List<T> get() = items.load()
        val size: Int get() = items.load().size
        operator fun get(index: Int): T = items.load()[index]
    }

    private class CountingWakeLock : CalculationWakeLock {

        val acquired = AtomicInt(0)
        val released = AtomicInt(0)

        override fun acquire(timeoutMs: Long): CalculationWakeLock.Held {
            acquired.addAndFetch(1)
            val done = AtomicInt(0)
            return object : CalculationWakeLock.Held {
                override fun release() {
                    if (done.compareAndSet(0, 1)) released.addAndFetch(1)
                }
            }
        }

        fun held(): Int = acquired.load() - released.load()
    }

    private object SilentLogger : AAPSLogger {

        override fun debug(message: String) {}
        override fun debug(enable: Boolean, tag: LTag, message: String) {}
        override fun debug(tag: LTag, message: String) {}
        override fun debug(tag: LTag, accessor: () -> String) {}
        override fun debug(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun warn(tag: LTag, message: String) {}
        override fun warn(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun info(tag: LTag, message: String) {}
        override fun info(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun error(tag: LTag, message: String) {}
        override fun error(tag: LTag, message: String, throwable: Throwable) {}
        override fun error(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun error(message: String) {}
        override fun error(message: String, throwable: Throwable) {}
        override fun error(format: String, vararg arguments: Any?) {}
        override fun debug(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
        override fun info(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
        override fun warn(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
        override fun error(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
    }

    /** The `isStopped` handed to each run, in start order. */
    private val checks = Recorder<() -> Boolean>()

    /** The same checks with the generation the run was started with. */
    private val checksByGeneration = Recorder<Pair<Long, () -> Boolean>>()

    /** The generation of each post phase that ran. */
    private val postGenerations = Recorder<Long>()

    /**
     * What the prepare phase does, given the generation of its run. Replaced by tests that need a run
     * which does not finish at once.
     */
    @Volatile private var prepareBody: suspend (generation: Long) -> Unit = {}

    /** What the prepare phase reports when [prepareBody] returns. */
    @Volatile private var prepareOutcome: WorkOutcome = WorkOutcome.Success

    /** What the post phase does. */
    @Volatile private var postBody: suspend () -> WorkOutcome = { WorkOutcome.Success }

    private val prepare = CalculationPhase { _, generation, isStopped ->
        checks.add(isStopped)
        checksByGeneration.add(generation to isStopped)
        prepareBody(generation)
        prepareOutcome
    }

    private val post = CalculationPhase { _, generation, _ ->
        postGenerations.add(generation)
        postBody()
    }

    /** A throw that got out of the executor. On Android it would have ended the app. */
    private val uncaught = Recorder<Throwable>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob() + CoroutineExceptionHandler { _, e -> uncaught.add(e) })

    private val wakeLock = CountingWakeLock()
    private val sut = CoroutineCalculationExecutor(scope, SilentLogger, prepare, post, wakeLock)

    /**
     * Real time, not the virtual time of runTest: the executor runs on Dispatchers.Default, so the
     * waits in a test have to really pass for its runs to get anywhere.
     */
    private fun realTime(block: suspend CoroutineScope.() -> Unit) = runTest(timeout = 60.seconds) {
        withContext(Dispatchers.Default) { block() }
    }

    /** The executor starts on its own scope, so a test has to wait for the run to reach the phase. */
    private suspend fun awaitRuns(count: Int) = withTimeout(5.seconds) {
        while (checks.size < count) delay(5)
    }

    /** The lock is given back from a completion handler, so a test waits for it rather than racing it. */
    private suspend fun awaitNoWakeLockHeld() = withTimeout(5.seconds) {
        while (wakeLock.held() != 0) delay(5)
    }

    /** Runs a test left behind - the replacement storm below starts 200 - must not go on into the next test. */
    @AfterTest
    fun cancelLeftoverRuns() {
        scope.cancel()
    }

    /**
     * The regression. Two starts under one name: once the second has taken the name, the first must
     * report stopped - otherwise both keep running and both keep emitting.
     */
    @Test
    fun `a replaced run reports itself stopped`() = realTime {
        sut.start(MAIN_CALCULATION, generation = 1, runPost = false)
        awaitRuns(1)
        val first = checks[0]
        assertFalse(first()) // still the current run at this point

        sut.start(MAIN_CALCULATION, generation = 2, runPost = false)
        awaitRuns(2)

        assertTrue(first())       // replaced, and it must know
        assertFalse(checks[1]())  // the replacement is the live one
    }

    /** Stopping a job must make its run report stopped. */
    @Test
    fun `a stopped run reports itself stopped`() = realTime {
        sut.start(MAIN_CALCULATION, generation = 1, runPost = false)
        awaitRuns(1)
        val check = checks[0]

        sut.stop(MAIN_CALCULATION, "test")

        assertTrue(check())
    }

    /** A different job name is a different run: starting one must not stop the other. */
    @Test
    fun `starting another job leaves the first one active`() = realTime {
        sut.start(MAIN_CALCULATION, generation = 1, runPost = false)
        awaitRuns(1)
        val main = checks[0]

        sut.start("OTHER", generation = 2, runPost = false)
        awaitRuns(2)

        assertFalse(main())
        assertFalse(checks[1]())
    }

    /** Three in a row: only the newest is live, and the two it replaced both know they are not. */
    @Test
    fun `only the newest run is active`() = realTime {
        repeat(3) { i ->
            sut.start(MAIN_CALCULATION, generation = (i + 1).toLong(), runPost = false)
            awaitRuns(i + 1)
        }

        assertTrue(checks[0]())
        assertTrue(checks[1]())
        assertFalse(checks[2]())
    }

    /**
     * `stop` has to take the prepare phase down too, and wait for it.
     *
     * The prepare half is a sibling on the executor's scope, not a child of the job in `runs`, so
     * cancelling and joining that one alone left prepare running and still reported "stopped". The
     * caller this matters to is `IobCobCalculatorPlugin.newHistoryData`, which uses `stop` as the
     * barrier before it invalidates the IOB and basal tables - it was invalidating them underneath a
     * pass that was still going.
     */
    @Test
    fun `stopping ends the prepare phase before it returns`() = realTime {
        val prepareEnded = CompletableDeferred<Unit>()
        prepareBody = {
            try {
                awaitCancellation()
            } finally {
                prepareEnded.complete(Unit)
            }
        }
        sut.start(MAIN_CALCULATION, generation = 1, runPost = false)
        awaitRuns(1)

        sut.stop(MAIN_CALCULATION, "test")

        assertTrue(prepareEnded.isCompleted)
    }

    /** Nothing was started, so a history change does not have to wait for anything. */
    @Test
    fun `awaitIdle is true at once when nothing runs`() = realTime {
        assertTrue(sut.awaitIdle(MAIN_CALCULATION, 1.seconds))
    }

    /**
     * A history change that waits for the running calculation must also wait for the post phase. That
     * is where the loop runs, and starting the next calculation then would cancel the loop in the
     * middle of a run.
     */
    @Test
    fun `awaitIdle waits for the post phase too`() = realTime {
        val postStarted = CompletableDeferred<Unit>()
        val postGate = CompletableDeferred<Unit>()
        postBody = {
            postStarted.complete(Unit)
            postGate.await()
            WorkOutcome.Success
        }
        sut.start(MAIN_CALCULATION, generation = 1, runPost = true)
        withTimeout(5.seconds) { postStarted.await() }

        val idle = async { sut.awaitIdle(MAIN_CALCULATION, 5.seconds) }
        delay(100)
        assertFalse(idle.isCompleted) // prepare is done, but the post phase still runs

        postGate.complete(Unit)
        assertTrue(idle.await())
    }

    /**
     * A run that is replaced ends too. Waiting must go on for the replacement, or the caller would
     * stop that one - the restart this wait is there to avoid.
     */
    @Test
    fun `awaitIdle goes on waiting when the run is replaced`() = realTime {
        val firstGate = CompletableDeferred<Unit>()
        val secondGate = CompletableDeferred<Unit>()
        prepareBody = { firstGate.await() }
        sut.start(MAIN_CALCULATION, generation = 1, runPost = false)
        awaitRuns(1)
        val idle = async { sut.awaitIdle(MAIN_CALCULATION, 5.seconds) }
        delay(100)

        prepareBody = { secondGate.await() }
        sut.start(MAIN_CALCULATION, generation = 2, runPost = false) // cancels the first run
        awaitRuns(2)
        delay(100)
        assertFalse(idle.isCompleted)

        secondGate.complete(Unit)
        assertTrue(idle.await())
    }

    /** A run that does not end within the limit gives false, so the caller can stop it after all. */
    @Test
    fun `awaitIdle gives up after the timeout`() = realTime {
        prepareBody = { awaitCancellation() }
        sut.start(MAIN_CALCULATION, generation = 1, runPost = false)
        awaitRuns(1)

        assertFalse(sut.awaitIdle(MAIN_CALCULATION, 100.milliseconds))
    }

    /** The same gap on the replace path: a new start must not leave the old prepare phase running. */
    @Test
    fun `starting a replacement cancels the previous prepare phase`() = realTime {
        val firstEnded = CompletableDeferred<Unit>()
        prepareBody = {
            try {
                awaitCancellation()
            } finally {
                firstEnded.complete(Unit)
            }
        }
        sut.start(MAIN_CALCULATION, generation = 1, runPost = false)
        awaitRuns(1)

        prepareBody = {}
        sut.start(MAIN_CALCULATION, generation = 2, runPost = false)
        awaitRuns(2)

        withTimeout(5.seconds) { firstEnded.await() }
    }

    /**
     * The check reads one `@Volatile` field on the run's own token, never the token map.
     *
     * The map is written under the mutex while the calculation coroutines run on other threads, so a
     * run that read it would have no happens-before with the write that replaced it - it could keep
     * seeing itself as current - and an unsynchronised read while another thread is putting into a
     * plain map is not safe in its own right. Hammering replacement from one thread while another
     * reads the check is what would show that up.
     */
    @Test
    fun `the stopped check is safe to read while runs are being replaced`() = realTime {
        sut.start(MAIN_CALCULATION, generation = 1, runPost = false)
        awaitRuns(1)
        val first = checks[0]

        val reader = async(Dispatchers.Default) {
            repeat(2_000) { first() }
        }
        repeat(200) { i ->
            sut.start(MAIN_CALCULATION, generation = (i + 2).toLong(), runPost = false)
        }
        reader.await()

        awaitRuns(2)
        // The run that started first is not the current one any more.
        assertTrue(first())
    }

    /**
     * Two starts close together must replace each other in the order they were called. When the
     * registration ran on a coroutine of its own, the older start could land last, replace the newer
     * run, find its own generation stale in WorkflowChainData, and that BG got no loop run.
     */
    @Test
    fun `starts close together keep their call order`() = realTime {
        // Run 1 would never end by itself, so the test only passes when run 2 replaced it.
        prepareBody = { generation -> if (generation == 1L) awaitCancellation() }
        sut.start(MAIN_CALCULATION, generation = 1, runPost = true)
        sut.start(MAIN_CALCULATION, generation = 2, runPost = true)
        assertTrue(sut.awaitIdle(MAIN_CALCULATION, 5.seconds))

        assertEquals(listOf(2L), postGenerations.all)
        // If run 1 started at all, it knows it was replaced.
        checksByGeneration.all.filter { it.first == 1L }.forEach { assertTrue(it.second()) }
    }

    /** A stop called right after a start must see that run, so stop is a real barrier. */
    @Test
    fun `stop right after start stops that run`() = realTime {
        prepareBody = { awaitCancellation() }
        sut.start(MAIN_CALCULATION, generation = 1, runPost = true)
        sut.stop(MAIN_CALCULATION, "test")

        assertTrue(sut.awaitIdle(MAIN_CALCULATION, 1.seconds))
        assertEquals(emptyList(), postGenerations.all)
        awaitNoWakeLockHeld()
    }

    /**
     * `waitForPrepare` is asked for by the loop when it runs outside the chain. When the prepare phase
     * it waits for is replaced, it must go on waiting for the replacement, whose data the loop will read.
     */
    @Test
    fun `waitForPrepare goes on waiting when the prepare phase is replaced`() = realTime {
        prepareBody = { awaitCancellation() }
        sut.start(MAIN_CALCULATION, generation = 1, runPost = false)
        awaitRuns(1)
        val waiting = async { sut.waitForPrepare(MAIN_CALCULATION, "test") }
        delay(100)

        val secondGate = CompletableDeferred<Unit>()
        prepareBody = { secondGate.await() }
        sut.start(MAIN_CALCULATION, generation = 2, runPost = false) // cancels the first prepare phase
        awaitRuns(2)
        delay(100)
        assertFalse(waiting.isCompleted)

        secondGate.complete(Unit)
        withTimeout(5.seconds) { waiting.await() }
    }

    /** The positive case for the two below: after a good prepare phase the post phase, and with it the loop, runs. */
    @Test
    fun `the post phase runs after a successful prepare phase`() = realTime {
        sut.start(MAIN_CALCULATION, generation = 1, runPost = true)
        awaitRuns(1)
        assertTrue(sut.awaitIdle(MAIN_CALCULATION, 5.seconds))

        assertEquals(listOf(1L), postGenerations.all)
    }

    /**
     * A failed prepare phase left no data the loop should act on. WorkManager did not start the post
     * worker after a failed prepare worker, and this keeps it that way.
     */
    @Test
    fun `the post phase does not run after a failed prepare phase`() = realTime {
        prepareOutcome = WorkOutcome.StaleInput
        sut.start(MAIN_CALCULATION, generation = 1, runPost = true)
        awaitRuns(1)
        assertTrue(sut.awaitIdle(MAIN_CALCULATION, 5.seconds))

        assertEquals(emptyList(), postGenerations.all)
    }

    /**
     * A throw in a phase is a failed phase. It must not reach the scope: on Android that has no
     * handler, and the throw would end the app and the pump connection with it. The next run must still
     * work.
     */
    @Test
    fun `a throwing prepare phase is a failed phase and no crash`() = realTime {
        prepareBody = { throw IllegalStateException("broken calculation") }
        sut.start(MAIN_CALCULATION, generation = 1, runPost = true)
        awaitRuns(1)
        assertTrue(sut.awaitIdle(MAIN_CALCULATION, 5.seconds))

        assertEquals(emptyList(), uncaught.all)
        assertEquals(emptyList(), postGenerations.all)

        prepareBody = {}
        sut.start(MAIN_CALCULATION, generation = 2, runPost = false)
        awaitRuns(2)
        assertTrue(sut.awaitIdle(MAIN_CALCULATION, 5.seconds))
        assertEquals(emptyList(), uncaught.all)
    }

    /** The same for the post phase, which is where the loop runs. */
    @Test
    fun `a throwing post phase is a failed phase and no crash`() = realTime {
        postBody = { throw IllegalStateException("broken loop") }
        sut.start(MAIN_CALCULATION, generation = 1, runPost = true)
        awaitRuns(1)
        assertTrue(sut.awaitIdle(MAIN_CALCULATION, 5.seconds))

        assertEquals(emptyList(), uncaught.all)
    }

    /** The system held a wake lock for a job automatically. A run has to hold its own until it ends. */
    @Test
    fun `a run holds the wake lock until it has ended`() = realTime {
        val gate = CompletableDeferred<Unit>()
        prepareBody = { gate.await() }
        sut.start(MAIN_CALCULATION, generation = 1, runPost = true)
        awaitRuns(1)
        assertEquals(1, wakeLock.held())

        gate.complete(Unit)
        assertTrue(sut.awaitIdle(MAIN_CALCULATION, 5.seconds))
        awaitNoWakeLockHeld()
        assertEquals(1, wakeLock.acquired.load())
    }

    /** A replaced run gives its lock back, so locks do not add up while BG values keep coming. */
    @Test
    fun `a replaced run gives its wake lock back`() = realTime {
        prepareBody = { awaitCancellation() }
        sut.start(MAIN_CALCULATION, generation = 1, runPost = true)
        awaitRuns(1)

        prepareBody = {}
        sut.start(MAIN_CALCULATION, generation = 2, runPost = true)
        awaitRuns(2)
        assertTrue(sut.awaitIdle(MAIN_CALCULATION, 5.seconds))

        awaitNoWakeLockHeld()
        assertEquals(2, wakeLock.acquired.load())
    }

    /** A stopped run gives its lock back too. */
    @Test
    fun `a stopped run gives its wake lock back`() = realTime {
        prepareBody = { awaitCancellation() }
        sut.start(MAIN_CALCULATION, generation = 1, runPost = true)
        awaitRuns(1)

        sut.stop(MAIN_CALCULATION, "test")

        awaitNoWakeLockHeld()
        assertEquals(1, wakeLock.acquired.load())
    }

    /** The post phase is where the loop runs and talks to the pump, so the lock must cover it too. */
    @Test
    fun `the wake lock is held while the post phase runs`() = realTime {
        val postStarted = CompletableDeferred<Unit>()
        val postGate = CompletableDeferred<Unit>()
        postBody = {
            postStarted.complete(Unit)
            postGate.await()
            WorkOutcome.Success
        }
        sut.start(MAIN_CALCULATION, generation = 1, runPost = true)
        withTimeout(5.seconds) { postStarted.await() }
        assertEquals(1, wakeLock.held())

        postGate.complete(Unit)
        assertTrue(sut.awaitIdle(MAIN_CALCULATION, 5.seconds))
        awaitNoWakeLockHeld()
    }

    /**
     * A stopped run ends its outer job at once, but its prepare half goes on until its next check, and
     * stop() waits for that. The lock must stay until the prepare half has really ended.
     */
    @Test
    fun `a stopped run keeps the wake lock until its prepare half has ended`() = realTime {
        val prepareGate = CompletableDeferred<Unit>()
        // Like a bucket that is still being calculated: the cancel is only seen when it is done.
        prepareBody = { withContext(NonCancellable) { prepareGate.await() } }
        sut.start(MAIN_CALCULATION, generation = 1, runPost = true)
        awaitRuns(1)

        val stopping = async { sut.stop(MAIN_CALCULATION, "test") }
        delay(100)
        assertEquals(1, wakeLock.held())

        prepareGate.complete(Unit)
        stopping.await()
        awaitNoWakeLockHeld()
    }

    /** The predictions update runs only the post phase. It holds the lock while it does. */
    @Test
    fun `a post only run holds the wake lock until it has ended`() = realTime {
        val postStarted = CompletableDeferred<Unit>()
        val postGate = CompletableDeferred<Unit>()
        postBody = {
            postStarted.complete(Unit)
            postGate.await()
            WorkOutcome.Success
        }
        sut.startPostOnly(MAIN_CALCULATION, generation = 1)
        withTimeout(5.seconds) { postStarted.await() }
        assertEquals(1, wakeLock.held())

        postGate.complete(Unit)
        assertTrue(sut.awaitIdle(MAIN_CALCULATION, 5.seconds))
        awaitNoWakeLockHeld()
    }

    /** A platform without a wake lock passes null, and runs work all the same. */
    @Test
    fun `runs work without a wake lock`() = realTime {
        val noLock = CoroutineCalculationExecutor(scope, SilentLogger, prepare, post, wakeLock = null)
        noLock.start(MAIN_CALCULATION, generation = 1, runPost = true)
        awaitRuns(1)
        assertTrue(noLock.awaitIdle(MAIN_CALCULATION, 5.seconds))

        assertEquals(listOf(1L), postGenerations.all)
        assertEquals(emptyList(), uncaught.all)
    }
}
