package app.aaps.core.nssdk.localmodel.entry

enum class NsUnits(val value: String) {
    MG_DL("mg/dl"),
    MMOL_L("mmol")
    ;

    companion object {

        fun fromString(name: String?) = NsUnits.entries.firstOrNull { it.value == name } ?: MG_DL
    }
}
