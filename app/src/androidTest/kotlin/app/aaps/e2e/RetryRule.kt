package app.aaps.e2e

import org.junit.internal.AssumptionViolatedException
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Runs a failed test once more when the failure can come from a loaded emulator rather than from the code: a screen or
 * a state that did not show in time, or a screen object that changed under the test. On CI the emulators share the
 * machine, and a uiautomator look-up or a wait then times out now and then.
 *
 * A wrong result is never retried: an [AssertionError] (what Truth and JUnit checks throw) fails the test at once.
 * Retried, a real bug only passed now and then and cost three runs of a slow test, and it stayed hidden.
 * Assumption failures (skips) are never retried either.
 *
 * Every retry is logged ("RetryRule: ... FAILED attempt", "... PASSED on attempt") and CI prints these lines after
 * each shard, so a test that passes only on its second try stays visible.
 */
class RetryRule(private val attempts: Int = 2) : TestRule {

    override fun apply(base: Statement, description: Description): Statement =
        object : Statement() {
            override fun evaluate() {
                var lastError: Throwable? = null
                for (attempt in 1..attempts) {
                    try {
                        base.evaluate()
                        if (attempt > 1) println("RetryRule: ${description.displayName} PASSED on attempt $attempt/$attempts")
                        return
                    } catch (ave: AssumptionViolatedException) {
                        throw ave // a skip, not a flake - never retry
                    } catch (wrong: AssertionError) {
                        println("RetryRule: ${description.displayName} FAILED, not retried (a wrong result): ${wrong.javaClass.simpleName}: ${wrong.message}")
                        throw wrong
                    } catch (t: Throwable) {
                        lastError = t
                        println("RetryRule: ${description.displayName} FAILED attempt $attempt/$attempts: ${t.javaClass.simpleName}: ${t.message}")
                    }
                }
                throw lastError ?: IllegalStateException("RetryRule: no attempts executed")
            }
        }
}
