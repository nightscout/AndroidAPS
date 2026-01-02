package app.aaps.core.interfaces.utils

import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TT
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit

interface Translator {

    fun translate(action: Action): String
    fun translate(units: ValueWithUnit?): String
    fun translate(meterType: TE.MeterType?): String
    fun translate(type: TE.Type?): String
    fun translate(location: TE.Location?): String
    fun translate(arrow: TE.Arrow?): String
    fun translate(reason: TT.Reason?): String
    fun translate(mode: RM.Mode?): String
    fun translate(source: Sources): String
}