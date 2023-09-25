package app.aaps.core.ui.elements

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.Checkable
import androidx.appcompat.widget.AppCompatCheckedTextView
import androidx.constraintlayout.widget.ConstraintLayout
import app.aaps.core.ui.databinding.WeekdayPickerBinding
import java.util.Calendar

class WeekdayPicker(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : ConstraintLayout(context, attrs, defStyleAttr) {

    private var changeListener: ((Int, Boolean) -> Unit)? = null

    private var binding: WeekdayPickerBinding

    init {
        val inflater = LayoutInflater.from(context)
        binding = WeekdayPickerBinding.inflate(inflater, this, true)
        determineBeginOfWeek()
        setupClickListeners()
    }

    fun Boolean.toVisibility() = if (this) View.VISIBLE else View.GONE

    private fun determineBeginOfWeek() {
        (Calendar.getInstance().firstDayOfWeek == Calendar.SUNDAY).let {
            binding.weekdayPickerSundayStart.visibility = it.toVisibility()
            binding.weekdayPickerSundayEnd.visibility = it.not().toVisibility()
        }
    }

    fun setSelectedDays(list: List<Int>) = with(binding) {
        weekdayPickerSundayStart.isChecked = list.contains(Calendar.SUNDAY)
        weekdayPickerSundayEnd.isChecked = list.contains(Calendar.SUNDAY)
        weekdayPickerMonday.isChecked = list.contains(Calendar.MONDAY)
        weekdayPickerTuesday.isChecked = list.contains(Calendar.TUESDAY)
        weekdayPickerWednesday.isChecked = list.contains(Calendar.WEDNESDAY)
        weekdayPickerThursday.isChecked = list.contains(Calendar.THURSDAY)
        weekdayPickerFriday.isChecked = list.contains(Calendar.FRIDAY)
        weekdayPickerSaturday.isChecked = list.contains(Calendar.SATURDAY)
    }

    private fun setupClickListeners() = with(binding) {
        weekdayPickerSundayStart.setupCallbackFor(Calendar.SUNDAY)
        weekdayPickerSundayEnd.setupCallbackFor(Calendar.SUNDAY)
        weekdayPickerMonday.setupCallbackFor(Calendar.MONDAY)
        weekdayPickerTuesday.setupCallbackFor(Calendar.TUESDAY)
        weekdayPickerWednesday.setupCallbackFor(Calendar.WEDNESDAY)
        weekdayPickerThursday.setupCallbackFor(Calendar.THURSDAY)
        weekdayPickerFriday.setupCallbackFor(Calendar.FRIDAY)
        weekdayPickerSaturday.setupCallbackFor(Calendar.SATURDAY)
    }

    fun setOnWeekdaysChangeListener(changeListener: (Int, Boolean) -> Unit) {
        this.changeListener = changeListener
    }

    private fun AppCompatCheckedTextView.setupCallbackFor(day: Int) = setOnClickListener {
        val checkable = it as Checkable
        val checked = checkable.isChecked
        checkable.isChecked = !checked
        changeListener?.invoke(day, !checked)
    }

}