package app.aaps.core.interfaces.queue

import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.keys.interfaces.TextRef
import kotlinx.coroutines.CompletableDeferred

/**
 * A command waiting in the queue, or running.
 *
 * An abstract class and not an interface, so [completion] lives here once and every command
 * gets its own instance without having to create it.
 */
abstract class Command {

    abstract val commandType: CommandType

    /**
     * Makes a fresh [PumpEnactResult].
     */
    abstract val pumpEnactResultProvider: () -> PumpEnactResult

    /**
     * The result of this command. The caller that put the command in the queue awaits it.
     *
     * It is completed once: by [executeAndComplete] when the command runs, or by [cancel] when the
     * queue drops it. A later completion is ignored.
     */
    val completion = CompletableDeferred<PumpEnactResult>()

    enum class CommandType {
        BOLUS,
        SMB_BOLUS,
        TEMPBASAL,
        EXTENDEDBOLUS,
        BASAL_PROFILE,
        READSTATUS,
        LOAD_HISTORY,  // TDDs and so far only Dana specific
        LOAD_EVENTS,
        LOAD_TDD,
        SET_USER_SETTINGS,  // so far only Dana specific,
        START_PUMP,
        STOP_PUMP,
        CLEAR_ALARMS, // so far only Medtrum specific
        DEACTIVATE, // so far only Medtrum specific
        UPDATE_TIME, // so far only Medtrum specific
        INSIGHT_SET_TBR_OVER_ALARM, // insight only
        CUSTOM_COMMAND
    }

    abstract suspend fun execute(): PumpEnactResult

    suspend fun executeAndComplete() {
        completion.complete(execute())
    }

    abstract fun status(): String
    abstract fun log(): String

    /**
     * Invoked when the queue drops this command without executing it (queue cleared,
     * superseded by a newer same-type command, etc.). Resumes any caller waiting on
     * [completion] with a result carrying [comment] as the reason.
     * Override to add side-effects (e.g. clearing progress UI).
     * Return success = true to avoid command failed dialog.
     *
     * [cancelled] says the drop was on purpose, so nothing alarms about it - see
     * [PumpEnactResult.cancelled]. It defaults to false because most drops are not: a connection
     * timeout is a real delivery failure and must still ring.
     */
    open fun cancel(comment: TextRef, success: Boolean = true, cancelled: Boolean = false) {
        completion.complete(pumpEnactResultProvider().success(success).cancelled(cancelled).comment(comment))
    }
}
