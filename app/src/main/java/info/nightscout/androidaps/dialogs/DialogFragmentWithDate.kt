package info.nightscout.androidaps.dialogs

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.res.Resources
import android.os.Bundle
import android.text.format.DateFormat
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.ms_square.etsyblur.BlurConfig
import com.ms_square.etsyblur.BlurDialogFragment
import com.ms_square.etsyblur.SmartAsyncPolicy
import dagger.android.support.DaggerDialogFragment
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.general.themeselector.util.ThemeUtil
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.sharedPreferences.SP
import info.nightscout.androidaps.utils.extensions.toVisibility
import kotlinx.android.synthetic.main.datetime.*
import kotlinx.android.synthetic.main.notes.*
import kotlinx.android.synthetic.main.okcancel.*
import java.util.*
import javax.inject.Inject

abstract class DialogFragmentWithDate : DaggerDialogFragment()  {
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var sp: SP
    @Inject lateinit var dateUtil: DateUtil

    class Nested : BlurDialogFragment() {

    }

    private lateinit var blurDialogFragment: Nested

    var eventTime = DateUtil.now()
    var eventTimeChanged = false

    //one shot guards
    private var okClicked: Boolean = false

    companion object {
        private var seconds: Int = (Math.random() * 59.0).toInt()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putLong("eventTime", eventTime)
        savedInstanceState.putBoolean("eventTimeChanged", eventTimeChanged)
    }

    fun onCreateViewGeneral() {

        dialog?.window?.requestFeature(Window.FEATURE_NO_TITLE)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        isCancelable = true
        dialog?.setCanceledOnTouchOutside(false)

        val blurConfig = context?.let { SmartAsyncPolicy(it) }?.let {
            BlurConfig.Builder()
                .overlayColor(ContextCompat.getColor(requireContext(), R.color.white_alpha_40))  // semi-transparent white color
                .debug(true)
                .asyncPolicy(it)
                .build()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        eventTime = savedInstanceState?.getLong("eventTime") ?: DateUtil.now()
        eventTimeChanged = savedInstanceState?.getBoolean("eventTimeChanged") ?: false
        overview_eventdate?.text = DateUtil.dateString(eventTime)
        overview_eventtime?.text = dateUtil.timeString(eventTime)

        var themeToSet = sp.getInt("theme", ThemeUtil.THEME_DARKSIDE)
        try {
            val theme: Resources.Theme? = context?.theme
            // https://stackoverflow.com/questions/11562051/change-activitys-theme-programmatically
            if (theme != null) {
                theme.applyStyle(ThemeUtil.getThemeId(themeToSet), true)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // create an OnDateSetListener
        val dateSetListener = DatePickerDialog.OnDateSetListener { _, year, monthOfYear, dayOfMonth ->
            val cal = Calendar.getInstance()
            cal.timeInMillis = eventTime
            cal.set(Calendar.YEAR, year)
            cal.set(Calendar.MONTH, monthOfYear)
            cal.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            eventTime = cal.timeInMillis
            eventTimeChanged = true
            overview_eventdate?.text = DateUtil.dateString(eventTime)
        }

        overview_eventdate?.setOnClickListener {
            context?.let {
                val cal = Calendar.getInstance()
                cal.timeInMillis = eventTime
                DatePickerDialog(it, dateSetListener,
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
                ).show()
            }
        }

        // create an OnTimeSetListener
        val timeSetListener = TimePickerDialog.OnTimeSetListener { _, hour, minute ->
            val cal = Calendar.getInstance()
            cal.timeInMillis = eventTime
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
            cal.set(Calendar.SECOND, seconds++) // randomize seconds to prevent creating record of the same time, if user choose time manually
            eventTime = cal.timeInMillis
            eventTimeChanged = true
            overview_eventtime?.text = dateUtil.timeString(eventTime)
        }

        overview_eventtime?.setOnClickListener {
            context?.let {
                val cal = Calendar.getInstance()
                cal.timeInMillis = eventTime
                TimePickerDialog(it, timeSetListener,
                    cal.get(Calendar.HOUR_OF_DAY),
                    cal.get(Calendar.MINUTE),
                    DateFormat.is24HourFormat(context)
                ).show()
            }
        }

        notes_layout?.visibility = sp.getBoolean(R.string.key_show_notes_entry_dialogs, false).toVisibility()

        ok.setOnClickListener {
            synchronized(okClicked) {
                if (okClicked) {
                    aapsLogger.warn(LTag.UI, "guarding: ok already clicked")
                } else {
                    okClicked = true
                    if (submit()) dismiss()
                    else okClicked = false
                }
            }
        }
        cancel.setOnClickListener { dismiss() }
    }

    abstract fun submit(): Boolean
}