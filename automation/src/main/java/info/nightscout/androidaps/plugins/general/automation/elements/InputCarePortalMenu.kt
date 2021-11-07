package info.nightscout.androidaps.plugins.general.automation.elements

import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import info.nightscout.androidaps.automation.R
import info.nightscout.androidaps.database.entities.TherapyEvent
import info.nightscout.androidaps.utils.resources.ResourceHelper

class InputCarePortalMenu(private val rh: ResourceHelper) : Element() {

    enum class EventType(val therapyEventType: TherapyEvent.Type) {
        NOTE(TherapyEvent.Type.NOTE),
        EXERCISE(TherapyEvent.Type.EXERCISE),
        QUESTION(TherapyEvent.Type.QUESTION),
        ANNOUNCEMENT(TherapyEvent.Type.ANNOUNCEMENT);

        @get:StringRes val stringResWithValue: Int
            get() = when (this) {
                NOTE         -> R.string.careportal_note_message
                EXERCISE     -> R.string.careportal_exercise_message
                QUESTION     -> R.string.careportal_question_message
                ANNOUNCEMENT -> R.string.careportal_announcement_message
            }

        @get:StringRes val stringRes: Int
            get() = when (this) {
                NOTE         -> R.string.careportal_note
                EXERCISE     -> R.string.careportal_exercise
                QUESTION     -> R.string.careportal_question
                ANNOUNCEMENT -> R.string.careportal_announcement
            }
        @get:DrawableRes val drawableRes: Int
            get() = when (this) {
                NOTE         -> R.drawable.ic_cp_note
                EXERCISE     -> R.drawable.ic_cp_exercise
                QUESTION     -> R.drawable.ic_cp_question
                ANNOUNCEMENT -> R.drawable.ic_cp_announcement
            }

        companion object {

            fun labels(rh: ResourceHelper): List<String> {
                val list: MutableList<String> = ArrayList()
                for (e in values()) {
                    list.add(rh.gs(e.stringRes))
                }
                return list
            }
        }
    }

    constructor(rh: ResourceHelper, value: EventType) : this(rh) {
        this.value = value
    }

    var value = EventType.NOTE

    override fun addToLayout(root: LinearLayout) {
        root.addView(
            Spinner(root.context).apply {
                adapter = ArrayAdapter(root.context, R.layout.spinner_centered, EventType.labels(rh)).apply {
                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                }
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, rh.dpToPx(4), 0, rh.dpToPx(4))
                }

                onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        value = EventType.values()[position]
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }
                setSelection(value.ordinal)
                gravity = Gravity.CENTER_HORIZONTAL
            })
    }

    fun setValue(eventType: EventType): InputCarePortalMenu {
        value = eventType
        return this
    }
}