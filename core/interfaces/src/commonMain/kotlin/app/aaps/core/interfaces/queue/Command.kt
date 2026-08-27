package app.aaps.core.interfaces.queue

import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.keys.interfaces.TextRef

interface Command {

    val commandType: CommandType
    val callback: Callback?

    /**
     * Makes a fresh [PumpEnactResult].
     *
     * A plain factory function rather than a `javax.inject.Provider`: that type is Dagger vocabulary,
     * and it appeared in the public API of this interface, which pinned every implementer and this
     * whole file to a JVM-only DI framework. Android still injects a `Provider` and passes its `::get`.
     */
    val pumpEnactResultProvider: () -> PumpEnactResult

    enum class CommandType {
        BOLUS,
        SMB_BOLUS,
        CARBS_ONLY_TREATMENT,
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

    suspend fun execute(): PumpEnactResult = error("Not implemented")
    suspend fun executeWithCallback() {
        callback?.result(execute())?.run()
    }

    fun status(): String
    fun log(): String

    /**
     * Invoked when the queue drops this command without executing it (queue cleared,
     * superseded by a newer same-type command, etc.). Resumes any caller waiting on the
     * command's [callback] with a failure result carrying [comment] as the reason.
     * Override to add side-effects (e.g. clearing progress UI).
     * Return success = true to avoid command failed dialog
     */
    fun cancel(comment: TextRef, success: Boolean = true) {
        callback?.result(pumpEnactResultProvider().success(success).comment(comment))?.run()
    }
}