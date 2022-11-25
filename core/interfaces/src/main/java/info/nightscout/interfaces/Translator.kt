package info.nightscout.interfaces

import info.nightscout.database.entities.OfflineEvent
import info.nightscout.database.entities.TemporaryTarget
import info.nightscout.database.entities.TherapyEvent
import info.nightscout.database.entities.UserEntry
import info.nightscout.database.entities.ValueWithUnit

interface Translator {

    fun translate(action: UserEntry.Action): String
    fun translate(units: ValueWithUnit?): String
    fun translate(meterType: TherapyEvent.MeterType?): String
    fun translate(type: TherapyEvent.Type?): String
    fun translate(reason: TemporaryTarget.Reason?): String
    fun translate(reason: OfflineEvent.Reason?): String
    fun translate(source: UserEntry.Sources): String
}