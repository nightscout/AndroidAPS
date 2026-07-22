package app.aaps.e2e

import org.junit.internal.AssumptionViolatedException
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Retries a failed test up to [attempts] total tries before giving up.
 *
 * The Dana E2E instrumentation tests are timing-sensitive and flake **intermittently** under CI load:
 * uiautomator element look-ups (`find`, `openManageAction`, …) time out when the emulator is briefly
 * starved by the concurrent unit suite and the other shard. The flakes are single, uncorrelated
 * failures (a different one test of ~18 per run), so a later attempt — often after the unit step has
 * finished and freed the box — succeeds. A genuine, deterministic failure still fails every attempt and
 * stays red, so this hides flakiness without masking real breakage.
 *
 * Wire it as the **outermost** rule so each attempt is a fully fresh test (Hilt setup/teardown included):
 * ```
 * val hiltRule = HiltAndroidRule(this)
 * @get:Rule val rules: RuleChain = RuleChain.outerRule(RetryRule()).around(hiltRule)
 * ```
 *
 * Assumption failures (skips) are never retried.
 */
class RetryRule(private val attempts: Int = 3) : TestRule {

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
                    } catch (t: Throwable) {
                        lastError = t
                        println("RetryRule: ${description.displayName} FAILED attempt $attempt/$attempts: ${t.javaClass.simpleName}: ${t.message}")
                    }
                }
                throw lastError ?: IllegalStateException("RetryRule: no attempts executed")
            }
        }
}
