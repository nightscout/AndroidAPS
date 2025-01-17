package app.aaps.core.data.model

@Suppress("SpellCheckingInspection")
enum class GlucoseUnit(val asText: String) {

    // This is Nightscout's representation
    MGDL("mg/dl"),
    MMOL("mmol");

    companion object {

        const val MMOLL_TO_MGDL = 18.0 // 18.0182;
        const val MGDL_TO_MMOLL = 1 / MMOLL_TO_MGDL

        fun fromText(name: String) = entries.firstOrNull { it.asText == name } ?: MGDL
    }
}