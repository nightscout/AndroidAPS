package app.aaps.plugins.automation.elements

import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings

class InputCarePortalMenu() {

    // Stored by name in the automation JSON, so new entries go at the end.
    enum class EventType(val therapyEventType: TE.Type) {
        NOTE(TE.Type.NOTE),
        EXERCISE(TE.Type.EXERCISE),
        QUESTION(TE.Type.QUESTION),
        ANNOUNCEMENT(TE.Type.ANNOUNCEMENT),

        // Lets a rule record the site change, for example when the insulin age was just reset by a
        // reservoir change the pump driver recorded by itself (#4548).
        CANNULA_CHANGE(TE.Type.CANNULA_CHANGE);

        val stringResWithValue: TextRef
            get() = when (this) {
                NOTE           -> CoreUiStrings.careportal_note_message
                EXERCISE       -> CoreUiStrings.careportal_exercise_message
                QUESTION       -> CoreUiStrings.careportal_question_message
                ANNOUNCEMENT   -> CoreUiStrings.careportal_announcement_message
                CANNULA_CHANGE -> CoreUiStrings.careportal_pump_site_change_message
            }

        val stringRes: TextRef
            get() = when (this) {
                NOTE           -> CoreUiStrings.careportal_note
                EXERCISE       -> CoreUiStrings.careportal_exercise
                QUESTION       -> CoreUiStrings.careportal_question
                ANNOUNCEMENT   -> CoreUiStrings.careportal_announcement
                CANNULA_CHANGE -> CoreUiStrings.careportal_pump_site_change
            }

        companion object {

            fun labels(rh: TextResolver): List<String> {
                val list: MutableList<String> = ArrayList()
                for (e in entries) {
                    list.add(rh.gs(e.stringRes))
                }
                return list
            }
        }
    }

    constructor(value: EventType) : this() {
        this.value = value
    }

    var value = EventType.NOTE

    fun setValue(eventType: EventType): InputCarePortalMenu {
        value = eventType
        return this
    }
}
