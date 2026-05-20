package app.aaps.core.interfaces.queue

import app.aaps.core.interfaces.pump.PumpEnactResult

interface Command {

    val commandType: CommandType
    val callback: Callback?

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
    suspend fun executeWithCallback(): Unit = error("Not implemented")
    fun status(): String
    fun log(): String
    fun cancel()
}