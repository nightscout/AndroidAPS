package info.nightscout.androidaps.utils.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.Checkable
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatCheckedTextView
import androidx.constraintlayout.widget.ConstraintLayout
import info.nightscout.androidaps.core.databinding.WeekdayPickerBinding
import info.nightscout.androidaps.utils.extensions.toVisibility
import java.util.*

class WeekdayPicker @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private var changeListener: ((Int, Boolean) -> Unit)? = null

    private var binding: WeekdayPickerBinding

    init {
        val inflater = LayoutInflater.from(context)
        binding = WeekdayPickerBinding.inflate(inflater, this, true)
        determineBeginOfWeek()
        setupClickListeners()
    }

    private fun determineBeginOfWeek() {
        (Calendar.getInstance().firstDayOfWeek == Calendar.SUNDAY).let {
            binding.weekdayPickerSundayStart.visibility = it.toVisibility()
            binding.weekdayPickerSundayEnd.visibility = it.not().toVisibility()
        }
    }

    fun setSelectedDays(list: List<Int>) {
        binding.weekdayPickerSundayStart.isChecked = list.contains(Calendar.SUNDAY)
        binding.weekdayPickerSundayEnd.isChecked = list.contains(Calendar.SUNDAY)
        binding.weekdayPickerMonday.isChecked = list.contains(Calendar.MONDAY)
        binding.weekdayPickerTuesday.isChecked = list.contains(Calendar.TUESDAY)
        binding.weekdayPickerWednesday.isChecked = list.contains(Calendar.WEDNESDAY)
        binding.weekdayPickerThursday.isChecked = list.contains(Calendar.THURSDAY)
        binding.weekdayPickerFriday.isChecked = list.contains(Calendar.FRIDAY)
        binding.weekdayPickerSaturday.isChecked = list.contains(Calendar.SATURDAY)
    }

   private fun setupClickListeners() {
        binding.weekdayPickerSundayStart.setupCallbackFor(Calendar.SUNDAY)
        binding.weekdayPickerSundayEnd.setupCallbackFor(Calendar.SUNDAY)
        binding.weekdayPickerMonday.setupCallbackFor(Calendar.MONDAY)
        binding.weekdayPickerTuesday.setupCallbackFor(Calendar.TUESDAY)
        binding.weekdayPickerWednesday.setupCallbackFor(Calendar.WEDNESDAY)
        binding.weekdayPickerThursday.setupCallbackFor(Calendar.THURSDAY)
        binding.weekdayPickerFriday.setupCallbackFor(Calendar.FRIDAY)
        binding.weekdayPickerSaturday.setupCallbackFor(Calendar.SATURDAY)
    }

    fun setOnWeekdaysChangeListener(changeListener: (Int, Boolean) -> Unit) {
        this.changeListener = changeListener
    }

    private fun AppCompatCheckedTextView.setupCallbackFor(day: Int) = setOnClickListener{
        val checkable = it as Checkable
        val checked = checkable.isChecked
        checkable.isChecked = !checked
        changeListener?.invoke(day, !checked)
    }

}