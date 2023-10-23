package app.aaps.core.interfaces.utils

import app.aaps.database.entities.OfflineEvent
import app.aaps.database.entities.TemporaryTarget
import app.aaps.database.entities.TherapyEvent
import app.aaps.database.entities.UserEntry
import app.aaps.database.entities.ValueWithUnit

interface Translator {

    fun translate(action: UserEntry.Action): String
    fun translate(units: ValueWithUnit?): String
    fun translate(meterType: TherapyEvent.MeterType?): String
    fun translate(type: TherapyEvent.Type?): String
    fun translate(reason: TemporaryTarget.Reason?): String
    fun translate(reason: OfflineEvent.Reason?): String
    fun translate(source: UserEntry.Sources): String
}