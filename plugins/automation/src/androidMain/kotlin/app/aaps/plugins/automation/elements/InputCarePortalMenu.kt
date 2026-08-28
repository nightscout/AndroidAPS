package app.aaps.plugins.automation.elements

import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.resources.ResourceHelper

class InputCarePortalMenu() {

    enum class EventType(val therapyEventType: TE.Type) {
        NOTE(TE.Type.NOTE),
        EXERCISE(TE.Type.EXERCISE),
        QUESTION(TE.Type.QUESTION),
        ANNOUNCEMENT(TE.Type.ANNOUNCEMENT);

        val stringResWithValue: TextRef
            get() = when (this) {
                NOTE         -> CoreUiStrings.careportal_note_message
                EXERCISE     -> CoreUiStrings.careportal_exercise_message
                QUESTION     -> CoreUiStrings.careportal_question_message
                ANNOUNCEMENT -> CoreUiStrings.careportal_announcement_message
            }

        val stringRes: TextRef
            get() = when (this) {
                NOTE         -> CoreUiStrings.careportal_note
                EXERCISE     -> CoreUiStrings.careportal_exercise
                QUESTION     -> CoreUiStrings.careportal_question
                ANNOUNCEMENT -> CoreUiStrings.careportal_announcement
            }

        companion object {

            fun labels(rh: ResourceHelper): List<String> {
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
