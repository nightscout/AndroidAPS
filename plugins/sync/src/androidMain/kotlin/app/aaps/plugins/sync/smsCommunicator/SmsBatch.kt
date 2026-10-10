package app.aaps.plugins.sync.smsCommunicator

import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.bolus.BatchAction
import app.aaps.core.interfaces.bolus.WizardBolusExecutor
import kotlinx.coroutines.CompletableDeferred

/**
 * How a confirmed SMS command ended, after it went through [WizardBolusExecutor].
 */
sealed interface SmsBatchResult {

    /** Applied. [preview] holds the values after the executor's caps. */
    data class Done(val preview: WizardBolusExecutor.PrepareResult.Preview) : SmsBatchResult

    /** Not applied. */
    sealed interface NotDone : SmsBatchResult {

        /**
         * The reply for the sender. [headline] is the command's own "failed" sentence. [status] gives the short pump
         * status for a pump command; it is only asked for when it is used.
         */
        fun reply(headline: String, status: (() -> String)? = null): String
    }

    /** The executor refused the command before doing anything. [reason] is its own short message, if it gave one. */
    data class Refused(val reason: String?) : NotDone {

        override fun reply(headline: String, status: (() -> String)?): String = reason ?: headline
    }

    /** The command was sent but did not work, for example the pump did not answer. [reason] is the executor's message. */
    data class Failed(val reason: String?) : NotDone {

        override fun reply(headline: String, status: (() -> String)?): String = listOfNotNull(headline, reason, status?.invoke()).joinToString("\n")
    }
}

/**
 * Runs one confirmed SMS command through the executor: the same path the phone, the watch and a paired client use,
 * so every remote command is checked once and in one place.
 *
 * Prepare and confirm run back to back, after the pass code arrived. The executor holds one prepared batch at a
 * time, and an SMS confirmation is valid for 5 minutes, so preparing at parse time would let a bolus from the phone
 * or the watch clear it in between.
 *
 * A bolus or carbs entry is delivered in the background, so with [waitForDose] this waits for the pump to finish
 * before it returns. Every other step is awaited inside `confirm` already.
 */
suspend fun WizardBolusExecutor.runSmsBatch(actions: List<BatchAction>, waitForDose: Boolean = false): SmsBatchResult {
    val prepared = prepareBatch(actions)
    if (prepared !is WizardBolusExecutor.PrepareResult.Preview)
        return SmsBatchResult.Refused((prepared as? WizardBolusExecutor.PrepareResult.Error)?.message)
    var failure: WizardBolusExecutor.Failure? = null
    val doseOutcome = CompletableDeferred<WizardBolusExecutor.Failure?>()
    val result = confirm(
        bolusId = prepared.bolusId,
        source = Sources.SMS,
        onError = {
            failure = failure ?: it
            doseOutcome.complete(it)
        },
        onSuccess = { doseOutcome.complete(null) }
    )
    if (result != WizardBolusExecutor.ConfirmResult.Delivered) return SmsBatchResult.Failed(failure?.comment)
    val doseFailure = if (waitForDose) doseOutcome.await() else failure
    return if (doseFailure == null) SmsBatchResult.Done(prepared) else SmsBatchResult.Failed(doseFailure.comment)
}
