package app.aaps.core.interfaces.insulin

import androidx.annotation.StringRes
import app.aaps.core.data.model.ICfg
import app.aaps.core.interfaces.R
import app.aaps.core.interfaces.insulin.InsulinType.OREF_RAPID_ACTING
import app.aaps.core.interfaces.resources.ResourceHelper

enum class ConcentrationType(val value: Double, @StringRes val label: Int) {
    UNKNOWN(-1.0, R.string.unknown),
    U10(0.1, R.string.u10),
    U40(0.4, R.string.u40),
    U50(0.5, R.string.u50),
    U100(1.0, R.string.u100),
    U200(2.0, R.string.u200),
    U300(3.0, R.string.u300),
    U500(5.0, R.string.u500);

    companion object {

        fun fromDouble(type: Double) = values().firstOrNull {it.value == type} ?:UNKNOWN
        fun fromInt(type: Int) = values().firstOrNull {it.value * 100 == type.toDouble()} ?:UNKNOWN
    }
}