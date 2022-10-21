package info.nightscout.sdk.mapper

import info.nightscout.sdk.localmodel.entry.NsUnits
import info.nightscout.sdk.localmodel.treatment.Bolus
import info.nightscout.sdk.localmodel.treatment.Carbs
import info.nightscout.sdk.localmodel.treatment.EventType
import info.nightscout.sdk.localmodel.treatment.TemporaryTarget
import info.nightscout.sdk.localmodel.treatment.Treatment
import info.nightscout.sdk.remotemodel.RemoteTreatment
import java.util.concurrent.TimeUnit

@JvmSynthetic
internal fun RemoteTreatment.toTreatment(): Treatment? {
    when {
        insulin != null && insulin > 0          ->
            return Bolus(
                date = this.date,
                device = this.device,
                identifier = this.identifier,
                units = NsUnits.fromString(this.units),
                srvModified = this.srvModified,
                srvCreated = this.srvCreated,
                utcOffset = this.utcOffset ?: 0,
                subject = this.subject,
                isReadOnly = this.isReadOnly ?: false,
                isValid = this.isValid ?: true,
                eventType = this.eventType,
                notes = this.notes,
                pumpId = this.pumpId,
                pumpType = this.pumpType,
                pumpSerial = this.pumpSerial,
                insulin = this.insulin,
                type = Bolus.BolusType.fromString(this.type)
            )

        carbs != null && carbs > 0              ->
            return Carbs(
                date = this.date,
                device = this.device,
                identifier = this.identifier,
                units = NsUnits.fromString(this.units),
                srvModified = this.srvModified,
                srvCreated = this.srvCreated,
                utcOffset = this.utcOffset ?: 0,
                subject = this.subject,
                isReadOnly = this.isReadOnly ?: false,
                isValid = this.isValid ?: true,
                eventType = this.eventType,
                notes = this.notes,
                pumpId = this.pumpId,
                pumpType = this.pumpType,
                pumpSerial = this.pumpSerial,
                carbs = this.carbs,
                duration = this.duration ?: 0L
            )

        eventType == EventType.TEMPORARY_TARGET -> {
            if (this.date == 0L) return null

            this.duration ?: return null
            this.targetBottom ?: return null
            this.targetTop ?: return null

            return TemporaryTarget(
                date = this.date,
                device = this.device,
                identifier = this.identifier,
                units = NsUnits.fromString(this.units),
                srvModified = this.srvModified,
                srvCreated = this.srvCreated,
                utcOffset = this.utcOffset ?: 0,
                subject = this.subject,
                isReadOnly = this.isReadOnly ?: false,
                isValid = this.isValid ?: true,
                eventType = this.eventType,
                notes = this.notes,
                pumpId = this.pumpId,
                pumpType = this.pumpType,
                pumpSerial = this.pumpSerial,
                duration = this.durationInMilliseconds ?: TimeUnit.MINUTES.toMillis(this.duration),
                targetBottom = this.targetBottom,
                targetTop = this.targetTop,
                reason = TemporaryTarget.Reason.fromString(this.reason)
            )
        }
    }

    return null
}
