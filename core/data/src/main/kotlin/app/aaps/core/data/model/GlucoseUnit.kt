package app.aaps.core.data.model

@Suppress("SpellCheckingInspection")
enum class GlucoseUnit(val asText: String) {

    // This is Nightscout's representation
    MGDL("mg/dl"),
    MMOL("mmol");

    val displayLabel: String get() = when (this) {
        MGDL -> "mg/dL"
        MMOL -> "mmol/L"
    }

    companion object {

        fun fromText(name: String) = entries.firstOrNull { it.asText == name } ?: MGDL
    }
}