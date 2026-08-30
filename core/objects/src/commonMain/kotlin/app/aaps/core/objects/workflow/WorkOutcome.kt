package app.aaps.core.objects.workflow

/**
 * What a piece of background work reports back.
 *
 * This is the shared half of `ListenableWorker.Result`. A worker body written against it is plain
 * Kotlin and can live in commonMain; the Android worker maps it to a `Result` and is the only thing
 * that needs to know WorkManager exists.
 *
 * See `RunnerWorker` for the Android side, and `PrepareGraphDataRunner` in `:workflow` for the first
 * body written this way.
 */
sealed interface WorkOutcome {

    data object Success : WorkOutcome

    /**
     * Succeeded without doing the work, because there was nothing to do.
     *
     * Still a success - it must not be retried and must not fail a chain - but it carries why, so a
     * run that did nothing can be told apart from one that ran. That distinction is only visible in
     * the work's output data; nothing in the app reads it.
     */
    data class Skipped(val reason: String) : WorkOutcome

    /** [reason] is only logged and reported, never shown to the user. */
    data class Failure(val reason: String) : WorkOutcome

    companion object {

        /** The work was cancelled or replaced part way through. */
        val Stopped = Failure("stopped")

        /** The input a chain left for this step is gone, or a newer run has replaced it. */
        val StaleInput = Failure("missing or stale input data")
    }
}
