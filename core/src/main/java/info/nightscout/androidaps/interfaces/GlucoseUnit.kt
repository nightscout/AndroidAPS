package info.nightscout.androidaps.interfaces

import info.nightscout.androidaps.Constants

enum class GlucoseUnit(val asText: String) {
    MGDL(Constants.MGDL),
    MMOL(Constants.MMOL);

    companion object {

        fun fromText(name: String) = values().firstOrNull { it.asText == name } ?: MGDL
    }

}