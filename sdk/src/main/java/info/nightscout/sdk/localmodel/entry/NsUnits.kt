package info.nightscout.sdk.localmodel.entry

enum class NsUnits(value: String) {
    MG_DL("mg/dl"),
    MMOL_L("mmol")
    ;

    companion object {

        fun fromString(name: String?) = values().firstOrNull { it.name == name } ?: MG_DL
    }
}
