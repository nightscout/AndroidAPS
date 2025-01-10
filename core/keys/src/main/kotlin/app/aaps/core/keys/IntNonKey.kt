package app.aaps.core.keys

enum class IntNonKey(
    override val key: String,
    override val defaultValue: Int
) : IntNonPreferenceKey {

    ObjectivesManualEnacts("ObjectivesmanualEnacts", 0),
}