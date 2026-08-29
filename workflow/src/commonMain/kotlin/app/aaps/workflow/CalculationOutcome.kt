package app.aaps.workflow

/**
 * What a calculation phase reports back.
 *
 * The phases used to return `ListenableWorker.Result` directly, which put WorkManager's vocabulary
 * in the middle of the maths. The Android worker maps this to a `Result`; nothing else needs to know
 * WorkManager exists.
 */
sealed interface CalculationOutcome {

    data object Success : CalculationOutcome

    /** [reason] is only logged and reported, never shown to the user. */
    data class Failure(val reason: String) : CalculationOutcome

    companion object {

        /** The chain was cancelled or replaced part way through. */
        val Stopped = Failure("stopped")

        /** The slot is empty or a newer chain has replaced this one - see [WorkflowChainData]. */
        val StaleInput = Failure("missing or stale input data")
    }
}
