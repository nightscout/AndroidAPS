package app.aaps.workflow

import app.aaps.core.interfaces.workflow.CalculationWorkflow.Companion.MAIN_CALCULATION
import app.aaps.core.objects.workflow.WorkOutcome
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import kotlin.time.Duration.Companion.seconds

/**
 * That a replaced run knows it has been replaced.
 *
 * Cancelling a coroutine is cooperative, so an outgoing run keeps going until it next asks whether it
 * should stop. That question used to be "is there a live job under my name?" - which the replacement
 * had already made true again. So the run that had just been cancelled was told it was still current
 * and carried on.
 *
 * Two things followed, and only the first was visible: both runs emitted progress into the same
 * signals, so during the first Nightscout sync the bar jumped between their two positions instead of
 * advancing; and the replaced run went on doing full IOB passes over data that was still arriving,
 * which is the worst possible moment to do the work twice.
 *
 * Only platforms using this executor - iOS and desktop - were affected. Android runs the WorkManager
 * one, which has its own replace semantics.
 */
class CoroutineCalculationExecutorTest : TestBase() {

    /** The `isStopped` handed to each run, in start order. */
    private val checks = mutableListOf<() -> Boolean>()

    /** What the prepare phase does. Replaced by tests that need a run which does not finish at once. */
    private var prepareBody: suspend () -> Unit = {}

    private val prepare = mock<PrepareGraphDataRunner> {
        onBlocking { run(anyOrNull(), any(), any()) } doSuspendableAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            checks.add(invocation.arguments[2] as () -> Boolean)
            prepareBody()
            WorkOutcome.Success
        }
    }
    private val post = mock<PostCalculationRunner>()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val sut = CoroutineCalculationExecutor(scope, aapsLogger, prepare, post)

    /** The executor starts on its own scope, so a test has to wait for the run to reach the runner. */
    private suspend fun awaitRuns(count: Int) = withTimeout(5.seconds) {
        while (checks.size < count) delay(5)
    }

    /**
     * The regression. Two starts under one name: once the second has taken the name, the first must
     * report stopped - otherwise both keep running and both keep emitting.
     */
    @Test
    fun `a replaced run reports itself stopped`() = runBlocking {
        sut.start(MAIN_CALCULATION, generation = 1, runPost = false)
        awaitRuns(1)
        val first = checks[0]
        assertThat(first()).isFalse() // still the current run at this point

        sut.start(MAIN_CALCULATION, generation = 2, runPost = false)
        awaitRuns(2)

        assertThat(first()).isTrue()      // replaced, and it must know
        assertThat(checks[1]()).isFalse() // the replacement is the live one
    }

    /** Stopping a job must make its run report stopped. */
    @Test
    fun `a stopped run reports itself stopped`() = runBlocking {
        sut.start(MAIN_CALCULATION, generation = 1, runPost = false)
        awaitRuns(1)
        val check = checks[0]

        sut.stop(MAIN_CALCULATION, "test")

        assertThat(check()).isTrue()
    }

    /** A different job name is a different run: starting one must not stop the other. */
    @Test
    fun `starting another job leaves the first one active`() = runBlocking {
        sut.start(MAIN_CALCULATION, generation = 1, runPost = false)
        awaitRuns(1)
        val main = checks[0]

        sut.start("OTHER", generation = 2, runPost = false)
        awaitRuns(2)

        assertThat(main()).isFalse()
        assertThat(checks[1]()).isFalse()
    }

    /** Three in a row: only the newest is live, and the two it replaced both know they are not. */
    @Test
    fun `only the newest run is active`() = runBlocking {
        repeat(3) { i ->
            sut.start(MAIN_CALCULATION, generation = (i + 1).toLong(), runPost = false)
            awaitRuns(i + 1)
        }

        assertThat(checks[0]()).isTrue()
        assertThat(checks[1]()).isTrue()
        assertThat(checks[2]()).isFalse()
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
    fun `stopping ends the prepare phase before it returns`() = runBlocking {
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

        assertThat(prepareEnded.isCompleted).isTrue()
    }

    /** The same gap on the replace path: a new start must not leave the old prepare phase running. */
    @Test
    fun `starting a replacement cancels the previous prepare phase`() = runBlocking {
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
    fun `the stopped check is safe to read while runs are being replaced`() = runBlocking {
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
        // Whatever order those landed in, the run that started first is not the current one any more.
        assertThat(first()).isTrue()
    }
}
