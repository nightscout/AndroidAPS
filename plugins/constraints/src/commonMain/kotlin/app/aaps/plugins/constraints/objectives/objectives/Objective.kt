package app.aaps.plugins.constraints.objectives.objectives

import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.constraints.ConstraintsStrings
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.constraints.objectives.keys.ObjectivesBooleanComposedKey
import app.aaps.plugins.constraints.objectives.keys.ObjectivesLongComposedKey
import kotlinx.coroutines.Runnable
import kotlin.math.floor
import kotlin.time.Clock

abstract class Objective(
    val preferences: Preferences,
    val rh: TextResolver,
    val dateUtil: DateUtil,
    /** Renders "3 d" and friends. A parameter because Android says it with plurals; see [DurationText]. */
    val durationText: DurationText,
    private val spName: String,
    val objective: TextRef,
    val gate: TextRef
) {

    var startedOn: Long = 0
        get() = preferences.get(ObjectivesLongComposedKey.Started, spName)
        set(value) {
            field = value
            preferences.put(ObjectivesLongComposedKey.Started, spName, value = value)
        }
    var accomplishedOn: Long = 0
        get() {
            var value = preferences.get(ObjectivesLongComposedKey.Accomplished, spName)
            if (value - dateUtil.now() > T.hours(3).msecs() || startedOn - dateUtil.now() > T.hours(3).msecs()) { // more than 3 hours in the future
                startedOn = 0
                accomplishedOn = 0
                value = 0
            }
            return value
        }
        set(value) {
            field = value
            preferences.put(ObjectivesLongComposedKey.Accomplished, spName, value = value)
        }

    var tasks: MutableList<Task> = ArrayList()

    suspend fun isCompleted(): Boolean {
        for (task in tasks) {
            if (!task.shouldBeIgnored() && !task.isCompleted()) return false
        }
        return true
    }

    suspend fun isCompleted(trueTime: Long): Boolean {
        for (task in tasks) {
            if (!task.shouldBeIgnored() && !task.isCompleted(trueTime)) return false
        }
        return true
    }

    val isAccomplished: Boolean
        get() = accomplishedOn != 0L && accomplishedOn < dateUtil.now()
    val isStarted: Boolean
        get() = startedOn != 0L

    abstract inner class Task(var objective: Objective, val task: TextRef) {

        var hints = ArrayList<Hint>()
        var learned = ArrayList<Learned>()

        abstract suspend fun isCompleted(): Boolean

        open suspend fun isCompleted(trueTime: Long): Boolean = isCompleted()

        open suspend fun progress(): String =
            rh.gs(if (isCompleted()) ConstraintsStrings.completed_well_done else ConstraintsStrings.not_completed_yet)

        fun hint(hint: Hint): Task {
            hints.add(hint)
            return this
        }

        fun learned(learned: Learned): Task {
            this.learned.add(learned)
            return this
        }

        open fun shouldBeIgnored(): Boolean = false
    }

    inner class MinimumDurationTask internal constructor(objective: Objective, private val minimumDuration: Long) : Task(objective, ConstraintsStrings.time_elapsed) {

        override suspend fun isCompleted(): Boolean =
            objective.isStarted && Clock.System.now().toEpochMilliseconds() - objective.startedOn >= minimumDuration

        override suspend fun isCompleted(trueTime: Long): Boolean {
            return objective.isStarted && trueTime - objective.startedOn >= minimumDuration
        }

        override suspend fun progress(): String =
            (getDurationText(Clock.System.now().toEpochMilliseconds() - objective.startedOn)
                + " / " + getDurationText(minimumDuration))

        private fun getDurationText(duration: Long): String = durationText.format(duration)
    }

    inner class UITask internal constructor(objective: Objective, task: TextRef, private val spIdentifier: String, val code: (task: UITask, callback: Runnable, showMessage: (String) -> Unit) -> Unit) : Task(objective, task) {

        var answered: Boolean = false
            set(value) {
                field = value
                preferences.put(ObjectivesBooleanComposedKey.AnsweredUi, spIdentifier, value = value)
            }

        init {
            answered = preferences.get(ObjectivesBooleanComposedKey.AnsweredUi, spIdentifier)
        }

        override suspend fun isCompleted(): Boolean = answered
    }

    inner class ExamTask internal constructor(objective: Objective, task: TextRef, val question: TextRef, private val spIdentifier: String) : Task(objective, task) {

        var options = ArrayList<Option>()
        var answered: Boolean = false
            set(value) {
                field = value
                preferences.put(ObjectivesBooleanComposedKey.AnsweredExam, spIdentifier, value = value)
            }
        var disabledTo: Long = 0
            set(value) {
                field = value
                preferences.put(ObjectivesLongComposedKey.DisabledTo, spIdentifier, value = value)
            }

        init {
            answered = preferences.get(ObjectivesBooleanComposedKey.AnsweredExam, spIdentifier)
            disabledTo = preferences.get(ObjectivesLongComposedKey.DisabledTo, spIdentifier)
        }

        override suspend fun isCompleted(): Boolean = answered

        fun isEnabledAnswer(): Boolean = disabledTo < dateUtil.now()

        fun option(option: Option): ExamTask {
            options.add(option)
            return this
        }
    }

    class Option internal constructor(var option: TextRef, var isCorrect: Boolean)

    class Hint internal constructor(var hint: TextRef)

    class Learned internal constructor(var learned: TextRef)
}