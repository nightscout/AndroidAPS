package app.aaps.plugins.constraints.objectives.keys

import app.aaps.core.keys.interfaces.BooleanComposedNonPreferenceKey

enum class ObjectivesBooleanComposedKey(
    override val key: String,
    override val format: String,
    override val defaultValue: Boolean,
    override val exportable: Boolean = true
) : BooleanComposedNonPreferenceKey {

    AnsweredExam("ExamTask_", "%s", false),
    AnsweredUi("UITask_", "%s", false),
}