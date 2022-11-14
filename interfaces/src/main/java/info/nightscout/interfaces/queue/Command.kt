package info.nightscout.interfaces.queue

import dagger.android.HasAndroidInjector
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.shared.interfaces.ResourceHelper
import javax.inject.Inject

abstract class Command(
    val injector: HasAndroidInjector,
    val commandType: CommandType,
    val callback: Callback? = null
) {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rh: ResourceHelper

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
        LOAD_TDD,
        SET_USER_SETTINGS,  // so far only Dana specific,
        START_PUMP,
        STOP_PUMP,
        INSIGHT_SET_TBR_OVER_ALARM, // insight only
        CUSTOM_COMMAND
    }

    init {
        @Suppress("LeakingThis")
        injector.androidInjector().inject(this)
    }

    abstract fun execute()
    abstract fun status(): String
    abstract fun log(): String
    abstract fun cancel()
}