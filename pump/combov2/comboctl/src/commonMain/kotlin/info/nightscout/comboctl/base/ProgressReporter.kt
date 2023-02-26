package info.nightscout.comboctl.base

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.reflect.KClassifier

private val logger = Logger.get("Pump")

/**
 * Base class for specifying a stage for a [ProgressReporter] instance.
 *
 * @property id ID string, useful for serialization and logging.
 */
open class ProgressStage(val id: String)

/**
 * Progress stages for basic operations.
 */
object BasicProgressStage {
    // Fundamental stages, used for starting / ending a progress sequence.
    // The Aborted stage base class is useful to be able to catch all possible
    // abort reasons and also differentiate between them.
    object Idle : ProgressStage("idle")
    open class Aborted(id: String) : ProgressStage(id)
    object Cancelled : Aborted("cancelled")
    object Timeout : Aborted("timeout")
    class Error(val cause: Throwable) : Aborted("error")
    object Finished : ProgressStage("finished")

    // Connection related stages.
    object ScanningForPumpStage : ProgressStage("scanningForPump")
    /**
     * Bluetooth connection establishing stage.
     *
     * The connection setup may require several attempts on some platforms.
     * If the number of attempts so far exceeds the total number, the
     * connection attempt fails. If no total number is set (that is,
     * (totalNumAttempts is set to null), then there is no defined limit.
     * This is typically used when the caller manually aborts connection
     * attempts after a while.
     *
     * @property currentAttemptNr Current attempt number, starting at 1.
     * @property totalNumAttempts Total number of attempts that will be done,
     *   or null if no total number is defined.
     */
    data class EstablishingBtConnection(val currentAttemptNr: Int, val totalNumAttempts: Int?) :
        ProgressStage("establishingBtConnection")
    object PerformingConnectionHandshake : ProgressStage("performingConnectionHandshake")

    // Pairing related stages.
    object ComboPairingKeyAndPinRequested : ProgressStage("comboPairingKeyAndPinRequested")
    object ComboPairingFinishing : ProgressStage("comboPairingFinishing")
}

/**
 * Report with updated progress information.
 *
 * @property stageNumber Current progress stage number, starting at 0.
 *   If stageNumber == numStages, then the stage is always
 *   [BasicProgressStage.Finished] or a subclass of [BasicProgressStage.Aborted].
 * @property numStages Total number of stages in the progress sequence.
 * @property stage Information about the current stage.
 * @property overallProgress Numerical indicator for the overall progress.
 *   Valid range is 0.0 - 1.0, with 0.0 being the beginning of
 *   the progress, and 1.0 specifying the end.
 */
data class ProgressReport(val stageNumber: Int, val numStages: Int, val stage: ProgressStage, val overallProgress: Double)

/**
 * Class for reporting progress updates.
 *
 * "Progress" is defined here as a planned sequence of [ProgressStage] instances.
 * These stages describe information about the current progress. Stage instances
 * can contain varying information, such as the index of the factor that is
 * currently being set in a basal profile, or the IUs of a bolus that were
 * administered so far.
 *
 * A sequence always begins with [BasicProgressStage.Idle] and ends with either
 * [BasicProgressStage.Finished] or a subclass of [BasicProgressStage.Aborted]. These are
 * special in that they are never explicitly specified in the sequence. [BasicProgressStage.Idle]
 * is always set as the current flow value when the reporter is created and when
 * [reset] is called. The other two are passed to [setCurrentProgressStage], which
 * then immediately forwards them in a [ProgressReport] instance, with that instance's
 * stage number set to [numStages] (since both of these stages define the end of a sequence).
 *
 * In code that reports progress, the [setCurrentProgressStage] function is called
 * to deliver updates to the reporter, which then forwards that update to subscribers
 * of the [progressFlow]. The reporter takes care of checking where in the sequence
 * that stage is. Stages are indexed by stage numbers, which start at 0. The size
 * of the sequence equals [numStages].
 *
 * Updates to the flow are communicated as [ProgressReport] instances. They provide
 * subscribers with the necessary information to show details about the current
 * stage and to compute a progress percentage (useful for GUI progress bar elements).
 *
 * Example of how to use this class:
 *
 * First, the reporter is instantiated, like this:
 *
 * ```
 * val reporter = ProgressReporter(listOf(
 *     BasicProgressStage.StartingConnectionSetup::class,
 *     BasicProgressStage.EstablishingBtConnection::class,
 *     BasicProgressStage.PerformingConnectionHandshake::class
 * ))
 * ```
 *
 * Code can then report an update like this:
 *
 * ```
 * reporter.setCurrentProgressStage(BasicProgressStage.EstablishingBtConnection(1, 4))
 * ```
 *
 * This will cause the reporter to publish a [ProgressReport] instance through its
 * [progressFlow], looking like this:
 *
 * ```
 * ProgressReport(stageNumber = 1, numStages = 3, stage = BasicProgressStage.EstablishingBtConnection(1, 4))
 * ```
 *
 * This allows code to report progress without having to know what its current
 * stage number is (so it does not have to concern itself about providing a correct
 * progress percentage). Also, that way, code that reports progress can be combined.
 * For example, if function A contains setCurrentProgressStage calls, then the
 * function that called A can continue to report progress. And, the setCurrentProgressStage
 * calls from A can also be used to report progress in an entirely different function.
 * One actual example is the progress reported when a Bluetooth connection is being
 * established. This is used both during pairing and when setting up a regular
 * connection, without having to write separate progress report code for both.
 *
 * @param plannedSequence The planned progress sequence, as a list of ProgressStage
 *        classes. This never contains [BasicProgressStage.Idle],
 *        [BasicProgressStage.Finished], or a [BasicProgressStage.Aborted] subclass.
 * @param context User defined contxt to pass to computeOverallProgressCallback.
 *        This can be updated via [reset] calls.
 * @param computeOverallProgressCallback Callback for computing an overall progress
 *        indication out of the current stage. Valid range for the return value
 *        is 0.0 to 1.0. See [ProgressReport] for an explanation of the arguments.
 *        Default callback calculates (stageNumber / numStages) and uses the result.
 */
class ProgressReporter<Context>(
    private val plannedSequence: List<KClassifier>,
    private var context: Context,
    private val computeOverallProgressCallback: (stageNumber: Int, numStages: Int, stage: ProgressStage, context: Context) -> Double =
        { stageNumber: Int, numStages: Int, _: ProgressStage, _: Context -> stageNumber.toDouble() / numStages.toDouble() }
) {
    private var currentStageNumber = 0
    private val mutableProgressFlow =
        MutableStateFlow(ProgressReport(0, plannedSequence.size, BasicProgressStage.Idle, 0.0))

    /**
     * Flow for getting progress reports.
     */
    val progressFlow = mutableProgressFlow.asStateFlow()

    /**
     * Total number of stages in the sequence.
     */
    val numStages = plannedSequence.size

    /**
     * Resets the reporter to its initial state.
     *
     * The flow's state will be set to a report whose stage is [BasicProgressStage.Idle].
     *
     * @param context User defined contxt to pass to computeOverallProgressCallback.
     *        Replaces the context passed to the constructor.
     */
    fun reset(context: Context) {
        this.context = context
        reset()
    }

    /**
     * Resets the reporter to its initial state.
     *
     * The flow's state will be set to a report whose stage is [BasicProgressStage.Idle].
     *
     * This overload works just like the other one, except that it keeps the context intact.
     */
    fun reset() {
        currentStageNumber = 0
        mutableProgressFlow.value = ProgressReport(0, numStages, BasicProgressStage.Idle, 0.0)
    }

    /**
     * Sets the current stage and triggers an update via a [ProgressReport] instance through the [progressFlow].
     *
     * If the process that is being tracked by this reported was cancelled
     * or aborted due to an error, pass a subclass of [BasicProgressStage.Aborted]
     * as the stage argument. This will trigger a report with the stage number
     * set to the total number of stages (to signify that the work is over)
     * and the stage set to the [BasicProgressStage.Aborted] subclass.
     *
     * If the process finished successfully, do the same as written above,
     * except using [BasicProgressStage.Finished] as the stage instead.
     *
     * @param stage Stage of the progress to report.
     */
    fun setCurrentProgressStage(stage: ProgressStage) {
        when (stage) {
            is BasicProgressStage.Finished,
            is BasicProgressStage.Aborted -> {
                currentStageNumber = numStages
                mutableProgressFlow.value = ProgressReport(currentStageNumber, numStages, stage, 1.0)
                return
            }
            else -> Unit
        }

        if (stage::class != plannedSequence[currentStageNumber]) {
            // Search forward first. Typically, this succeeds, since stages
            // are reported in the order specified in the sequence.
            var succeedingStageNumber = plannedSequence.subList(currentStageNumber + 1, numStages).indexOfFirst {
                stage::class == it
            }

            currentStageNumber = if (succeedingStageNumber == -1) {
                // Unusual case: An _earlier_ stage was reported. This is essentially
                // a backwards progress (= a regress?). It is not unthinkable that
                // this can happen, but it should be rare. In that case, we have
                // to search backwards in the sequence.
                val precedingStageNumber = plannedSequence.subList(0, currentStageNumber).indexOfFirst {
                    stage::class == it
                }

                // If the progress info was not found in the sequence, log this and exit.
                // Do not throw; a missing progress info ID in the sequence is not
                // a fatal error, so do not break the application because of it.
                if (precedingStageNumber == -1) {
                    logger(LogLevel.WARN) { "Progress stage \"$stage\" not found in stage sequence; not passing it to flow" }
                    return
                }

                precedingStageNumber
            } else {
                // Need to add (currentStageNumber + 1) as an offset, since the indexOfFirst
                // call returns indices that are based on the sub list, not the entire list.
                succeedingStageNumber + (currentStageNumber + 1)
            }
        }

        mutableProgressFlow.value = ProgressReport(
            currentStageNumber, numStages, stage,
            computeOverallProgressCallback(currentStageNumber, numStages, stage, context)
        )
    }
}
