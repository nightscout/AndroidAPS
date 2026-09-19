package app.aaps.core.interfaces.insulin

import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.keys.interfaces.TextRef

enum class ConcentrationType(val value: Double, val label: TextRef) {
    UNKNOWN(-1.0, InterfacesStrings.unknown),
    U10(0.1, InterfacesStrings.u10),
    U40(0.4, InterfacesStrings.u40),
    U50(0.5, InterfacesStrings.u50),
    U100(1.0, InterfacesStrings.u100),
    U200(2.0, InterfacesStrings.u200),
    U300(3.0, InterfacesStrings.u300),
    U500(5.0, InterfacesStrings.u500);

    companion object {

        fun fromDouble(type: Double) = entries.firstOrNull { it.value == type } ?: UNKNOWN
        fun fromInt(type: Int) = entries.firstOrNull { it.value * 100 == type.toDouble() } ?: UNKNOWN
    }
}
