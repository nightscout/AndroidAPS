package app.aaps.core.interfaces.utils

import app.aaps.core.data.db.OE
import app.aaps.core.data.db.TE
import app.aaps.core.data.db.TT
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit

interface Translator {

    fun translate(action: Action): String
    fun translate(units: ValueWithUnit?): String
    fun translate(meterType: TE.MeterType?): String
    fun translate(type: TE.Type?): String
    fun translate(reason: TT.Reason?): String
    fun translate(reason: OE.Reason?): String
    fun translate(source: Sources): String
}