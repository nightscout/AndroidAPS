package app.aaps.ui.dialogs

import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.extensions.toVisibility
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import dagger.android.support.DaggerDialogFragment
import java.util.Calendar
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

abstract class DialogFragmentWithDate : DaggerDialogFragment() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var dateUtil: DateUtil

    fun interface OnValueChangedListener {

        fun onValueChanged(value: Long)
    }

    var eventTime: Long = 0
    var eventTimeOriginal: Long = 0
    val eventTimeChanged: Boolean
        get() = eventTime != eventTimeOriginal

    private var eventDateView: TextView? = null
    private var eventTimeView: TextView? = null
    private var mOnValueChangedListener: OnValueChangedListener? = null

    //one shot guards
    private var okClicked: AtomicBoolean = AtomicBoolean(false)

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        aapsLogger.debug(LTag.UI, "Dialog opened: ${this.javaClass.simpleName}")
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putLong("eventTime", eventTime)
        savedInstanceState.putLong("eventTimeOriginal", eventTimeOriginal)
    }

    fun onCreateViewGeneral() {
        dialog?.window?.requestFeature(Window.FEATURE_NO_TITLE)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        isCancelable = true
        dialog?.setCanceledOnTouchOutside(false)
    }

    fun updateDateTime(timeMs: Long) {
        eventTime = timeMs
        eventDateView?.text = dateUtil.dateString(eventTime)
        eventTimeView?.text = dateUtil.timeString(eventTime)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        eventTimeOriginal = savedInstanceState?.getLong("eventTimeOriginal") ?: dateUtil.nowWithoutMilliseconds()
        eventTime = savedInstanceState?.getLong("eventTime") ?: eventTimeOriginal

        eventDateView = view.findViewById<TextView>(app.aaps.core.ui.R.id.eventdate)
        eventDateView?.text = dateUtil.dateString(eventTime)
        eventDateView?.setOnClickListener {
            val selection = dateUtil.timeStampToUtcDateMillis(eventTime)
            MaterialDatePicker.Builder.datePicker()
                .setTheme(app.aaps.core.ui.R.style.DatePicker)
                .setSelection(selection)
                .build()
                .apply {
                    addOnPositiveButtonClickListener { selection ->
                        eventTime = dateUtil.mergeUtcDateToTimestamp(eventTime, selection)
                        eventDateView?.text = dateUtil.dateString(eventTime)
                        callValueChangedListener()

                    }
                }
                .show(parentFragmentManager, "event_time_date_picker")
        }

        eventTimeView = view.findViewById<TextView>(app.aaps.core.ui.R.id.eventtime)
        eventTimeView?.text = dateUtil.timeString(eventTime)
        eventTimeView?.setOnClickListener {
            val clockFormat = if (DateFormat.is24HourFormat(context)) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H
            val cal = Calendar.getInstance().apply { timeInMillis = eventTime }
            val timePicker = MaterialTimePicker.Builder()
                .setTimeFormat(clockFormat)
                .setHour(cal.get(Calendar.HOUR_OF_DAY))
                .setMinute(cal.get(Calendar.MINUTE))
                .setTheme(app.aaps.core.ui.R.style.TimePicker)
                .build()
            timePicker.addOnPositiveButtonClickListener {
                // Randomize seconds to prevent creating record of the same time, if user choose time manually
                eventTime = dateUtil.mergeHourMinuteToTimestamp(eventTime, timePicker.hour, timePicker.minute, true)
                eventTimeView?.text = dateUtil.timeString(eventTime)
                callValueChangedListener()
            }
            timePicker.show(parentFragmentManager, "event_time_time_picker")
        }

        (view.findViewById<View>(app.aaps.core.ui.R.id.notes_layout))?.visibility =
            preferences.get(BooleanKey.OverviewShowNotesInDialogs).toVisibility()

        (view.findViewById<Button>(app.aaps.core.ui.R.id.ok))?.setOnClickListener {
            synchronized(okClicked) {
                if (okClicked.get()) {
                    aapsLogger.warn(LTag.UI, "guarding: ok already clicked for dialog: ${this.javaClass.simpleName}")
                } else {
                    okClicked.set(true)
                    if (submit()) {
                        aapsLogger.debug(LTag.UI, "Submit pressed for Dialog: ${this.javaClass.simpleName}")
                        dismiss()
                    } else {
                        aapsLogger.debug(LTag.UI, "Submit returned false for Dialog: ${this.javaClass.simpleName}")
                        okClicked.set(false)
                    }
                }
            }
        }
        (view.findViewById<Button>(app.aaps.core.ui.R.id.cancel))?.setOnClickListener {
            aapsLogger.debug(LTag.APS, "Cancel pressed for dialog: ${this.javaClass.simpleName}")
            dismiss()
        }

    }

    private fun callValueChangedListener() {
        mOnValueChangedListener?.onValueChanged(eventTime)
    }

    fun setOnValueChangedListener(onValueChangedListener: OnValueChangedListener?) {
        mOnValueChangedListener = onValueChangedListener
    }

    override fun show(manager: FragmentManager, tag: String?) {
        try {
            manager.beginTransaction().let {
                it.add(this, tag)
                it.commitAllowingStateLoss()
            }
        } catch (e: IllegalStateException) {
            aapsLogger.debug(e.localizedMessage ?: "")
        }
    }

    abstract fun submit(): Boolean
}
