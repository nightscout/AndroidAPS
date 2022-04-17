package info.nightscout.androidaps.dialogs

import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import dagger.android.support.DaggerDialogFragment
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.extensions.toVisibility
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.shared.sharedPreferences.SP
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

abstract class DialogFragmentWithDate : DaggerDialogFragment() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var sp: SP
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
        aapsLogger.debug(LTag.APS, "Dialog opened: ${this.javaClass.name}")
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

        eventDateView = view.findViewById(R.id.eventdate) as TextView?
        eventDateView?.text = dateUtil.dateString(eventTime)
        eventDateView?.setOnClickListener {
            val selection = dateUtil.timeStampToUtcDateMilis(eventTime)
            MaterialDatePicker.Builder.datePicker()
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

        eventTimeView = view.findViewById(R.id.eventtime) as TextView?
        eventTimeView?.text = dateUtil.timeString(eventTime)
        eventTimeView?.setOnClickListener {
            val clockFormat = if (DateFormat.is24HourFormat(context)) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H
            val cal = Calendar.getInstance().apply { timeInMillis = eventTime }
            val timePicker = MaterialTimePicker.Builder()
                .setTimeFormat(clockFormat)
                .setHour(cal.get(Calendar.HOUR_OF_DAY))
                .setMinute(cal.get(Calendar.MINUTE))
                .build()
            timePicker.addOnPositiveButtonClickListener {
                // Randomize seconds to prevent creating record of the same time, if user choose time manually
                eventTime = dateUtil.mergeHourMinuteToTimestamp(eventTime, timePicker.hour, timePicker.minute, true)
                eventTimeView?.text = dateUtil.timeString(eventTime)
                callValueChangedListener()
            }
            timePicker.show(parentFragmentManager, "event_time_time_picker")
        }

        (view.findViewById(R.id.notes_layout) as View?)?.visibility =
            sp.getBoolean(R.string.key_show_notes_entry_dialogs, false).toVisibility()

        (view.findViewById(R.id.ok) as Button?)?.setOnClickListener {
            synchronized(okClicked) {
                if (okClicked.get()) {
                    aapsLogger.warn(LTag.UI, "guarding: ok already clicked for dialog: ${this.javaClass.name}")
                } else {
                    okClicked.set(true)
                    if (submit()) {
                        aapsLogger.debug(LTag.APS, "Submit pressed for Dialog: ${this.javaClass.name}")
                        dismiss()
                    } else {
                        aapsLogger.debug(LTag.APS, "Submit returned false for Dialog: ${this.javaClass.name}")
                        okClicked.set(false)
                    }
                }
            }
        }
        (view.findViewById(R.id.cancel) as Button?)?.setOnClickListener {
            aapsLogger.debug(LTag.APS, "Cancel pressed for dialog: ${this.javaClass.name}")
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
