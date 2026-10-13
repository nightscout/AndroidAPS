package app.aaps.plugins.constraints.objectives.objectives

import android.content.Context
import android.text.util.Linkify
import android.widget.CheckBox
import android.widget.TextView
import androidx.annotation.StringRes
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.constraints.R
import app.aaps.plugins.constraints.objectives.keys.ObjectivesBooleanComposedKey
import app.aaps.plugins.constraints.objectives.keys.ObjectivesLongComposedKey
import kotlinx.coroutines.Runnable
import kotlin.math.floor

abstract class Objective(
    val preferences: Preferences,
    val rh: ResourceHelper,
    val dateUtil: DateUtil,
    private val spName: String,
    @StringRes val objective: Int,
    @StringRes val gate: Int
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

    val isCompleted: Boolean
        get() {
            for (task in tasks) {
                if (!task.shouldBeIgnored() && !task.isCompleted()) return false
            }
            return true
        }

    fun isCompleted(trueTime: Long): Boolean {
        for (task in tasks) {
            if (!task.shouldBeIgnored() && !task.isCompleted(trueTime)) return false
        }
        return true
    }

    val isAccomplished: Boolean
        get() = accomplishedOn != 0L && accomplishedOn < dateUtil.now()
    val isStarted: Boolean
        get() = startedOn != 0L

    abstract inner class Task(var objective: Objective, @StringRes val task: Int) {

        var hints = ArrayList<Hint>()
        var learned = ArrayList<Learned>()

        abstract fun isCompleted(): Boolean

        open fun isCompleted(trueTime: Long): Boolean = isCompleted()

        open val progress: String
            get() = rh.gs(if (isCompleted()) R.string.completed_well_done else R.string.not_completed_yet)

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

    inner class MinimumDurationTask internal constructor(objective: Objective, private val minimumDuration: Long) : Task(objective, R.string.time_elapsed) {

        override fun isCompleted(): Boolean =
            objective.isStarted && System.currentTimeMillis() - objective.startedOn >= minimumDuration

        override fun isCompleted(trueTime: Long): Boolean {
            return objective.isStarted && trueTime - objective.startedOn >= minimumDuration
        }

        override val progress: String
            get() = (getDurationText(System.currentTimeMillis() - objective.startedOn)
                + " / " + getDurationText(minimumDuration))

        private fun getDurationText(duration: Long): String {
            val days = floor(duration.toDouble() / T.days(1).msecs()).toInt()
            val hours = floor(duration.toDouble() / T.hours(1).msecs()).toInt()
            val minutes = floor(duration.toDouble() / T.mins(1).msecs()).toInt()
            return when {
                days > 0  -> rh.gq(app.aaps.core.ui.R.plurals.days, days, days)
                hours > 0 -> rh.gq(app.aaps.core.ui.R.plurals.hours, hours, hours)
                else      -> rh.gq(app.aaps.core.ui.R.plurals.minutes, minutes, minutes)
            }
        }
    }

    inner class UITask internal constructor(objective: Objective, @StringRes task: Int, private val spIdentifier: String, val code: (context: Context, task: UITask, callback: Runnable) -> Unit) : Task(objective, task) {

        var answered: Boolean = false
            set(value) {
                field = value
                preferences.put(ObjectivesBooleanComposedKey.AnsweredUi, spIdentifier, value = value)
            }

        init {
            answered = preferences.get(ObjectivesBooleanComposedKey.AnsweredUi, spIdentifier)
        }

        override fun isCompleted(): Boolean = answered
    }

    inner class ExamTask internal constructor(objective: Objective, @StringRes task: Int, @StringRes val question: Int, private val spIdentifier: String) : Task(objective, task) {

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

        override fun isCompleted(): Boolean = answered

        fun isEnabledAnswer(): Boolean = disabledTo < dateUtil.now()

        fun option(option: Option): ExamTask {
            options.add(option)
            return this
        }
    }

    inner class Option internal constructor(@StringRes var option: Int, var isCorrect: Boolean) {

        private var cb: CheckBox? = null // TODO: change it, this will block releasing memory

        fun generate(context: Context): CheckBox {
            cb = CheckBox(context)
            cb?.setText(option)
            return cb!!
        }

        fun evaluate(): Boolean {
            val selection = cb!!.isChecked
            return if (selection && isCorrect) true else !selection && !isCorrect
        }
    }

    inner class Hint internal constructor(@StringRes var hint: Int) {

        fun generate(context: Context): TextView {
            val textView = TextView(context)
            textView.setText(hint)
            textView.autoLinkMask = Linkify.WEB_URLS
            textView.linksClickable = true
            textView.setLinkTextColor(rh.gac(context, com.google.android.material.R.attr.colorSecondary))
            Linkify.addLinks(textView, Linkify.WEB_URLS)
            return textView
        }
    }

    inner class Learned internal constructor(@StringRes var learned: Int)
}