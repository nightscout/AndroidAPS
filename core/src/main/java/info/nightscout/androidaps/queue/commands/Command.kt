package info.nightscout.androidaps.queue.commands

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject

abstract class Command(
    val injector: HasAndroidInjector,
    val commandType: CommandType,
    val callback: Callback? = null
) {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var resourceHelper: ResourceHelper

    enum class CommandType {
        BOLUS,
        SMB_BOLUS,
        CARBS_ONLY_TREATMENT,
        TEMPBASAL,
        EXTENDEDBOLUS,
        BASAL_PROFILE,
        READSTATUS,
        LOAD_HISTORY,  // TDDs and so far only Dana specific
        LOAD_EVENTS,  // so far only Dana specific
        SET_USER_SETTINGS,  // so far only Dana specific,
        START_PUMP,
        STOP_PUMP,
        INSIGHT_SET_TBR_OVER_ALARM, // insight only
        CUSTOM_COMMAND
    }

    init {
        injector.androidInjector().inject(this)
    }

    abstract fun execute()
    abstract fun status(): String

    fun cancel() {
        val result = PumpEnactResult(injector)
        result.success = false
        result.comment = resourceHelper.gs(R.string.connectiontimedout)
        aapsLogger.debug(LTag.PUMPQUEUE, "Result cancel")
        callback?.result(result)?.run()
    }
}