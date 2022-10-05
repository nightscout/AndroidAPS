package info.nightscout.sdk.mapper

import info.nightscout.sdk.localmodel.treatment.Bolus
import info.nightscout.sdk.localmodel.treatment.Carbs
import info.nightscout.sdk.localmodel.treatment.EventType
import info.nightscout.sdk.localmodel.treatment.Treatment
import info.nightscout.sdk.remotemodel.RemoteEntry

@JvmSynthetic
internal fun RemoteEntry.toTreatment(): Treatment? =
    when {
        insulin != null && insulin > 0 ->
            Bolus(
                date = this.date,
                device = this.device,
                identifier = this.identifier,
                srvModified = this.srvModified,
                srvCreated = this.srvCreated,
                utcOffset = this.utcOffset ?: 0,
                subject = this.subject,
                isReadOnly = this.isReadOnly ?: false,
                isValid = this.isValid ?: true,
                eventType = this.eventType ?: EventType.NONE,
                notes = this.notes,
                pumpId = this.pumpId,
                pumpType = this.pumpType,
                pumpSerial = this.pumpSerial,
                insulin = this.insulin,
                type = Bolus.BolusType.fromString(this.type)
            )
        carbs != null && carbs > 0 ->
            Carbs(
                date = this.date,
                device = this.device,
                identifier = this.identifier,
                srvModified = this.srvModified,
                srvCreated = this.srvCreated,
                utcOffset = this.utcOffset ?: 0,
                subject = this.subject,
                isReadOnly = this.isReadOnly ?: false,
                isValid = this.isValid ?: true,
                eventType = this.eventType ?: EventType.NONE,
                notes = this.notes,
                pumpId = this.pumpId,
                pumpType = this.pumpType,
                pumpSerial = this.pumpSerial,
                carbs = this.carbs,
                duration = this.duration ?: 0L
            )

        else                           -> null
    }

