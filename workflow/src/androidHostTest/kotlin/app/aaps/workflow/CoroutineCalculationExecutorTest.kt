package app.aaps.workflow

import app.aaps.core.interfaces.workflow.CalculationWorkflow.Companion.MAIN_CALCULATION
import app.aaps.core.objects.workflow.WorkOutcome
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
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

    private val prepare = mock<PrepareGraphDataRunner> {
        onBlocking { run(anyOrNull(), any(), any()) } doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            checks.add(invocation.arguments[2] as () -> Boolean)
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
}
