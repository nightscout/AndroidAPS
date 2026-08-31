package app.aaps.plugins.sync.nsclientV3.ws

import app.aaps.core.objects.workflow.WorkOutcome

/**
 * Whether a load round carries on after a step.
 *
 * Split out of [CoroutineNsLoadExecutor] because the nine runners are concrete classes with their
 * own dependencies and cannot be stood in for, while this - the only decision the round actually
 * makes - can be tested on its own.
 *
 * The rule matches the Android chain: a step that fails stops the round, because a later step
 * usually reads what an earlier one wrote. A step that was **skipped** does not, because skipped is
 * a success that happened to have nothing to do, and stopping there would abandon the rest of the
 * round for no reason.
 */
internal object NsLoadChain {

    fun shouldContinue(outcome: WorkOutcome): Boolean = when (outcome) {
        is WorkOutcome.Success -> true
        is WorkOutcome.Skipped -> true
        is WorkOutcome.Failure -> false
        // No NS runner asks to be retried today. Treated like a failure rather than a skip because the
        // step did not do its work, so a later step would read state that is not there. This round
        // stops; the next scheduled round runs the step again, which is what a retry asks for.
        is WorkOutcome.Retry   -> false
    }
}
