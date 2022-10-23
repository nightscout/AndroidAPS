package info.nightscout.sdk.mapper

import info.nightscout.sdk.localmodel.entry.NsUnits
import info.nightscout.sdk.localmodel.treatment.EventType
import info.nightscout.sdk.localmodel.treatment.NSBolus
import info.nightscout.sdk.localmodel.treatment.NSBolusWizard
import info.nightscout.sdk.localmodel.treatment.NSCarbs
import info.nightscout.sdk.localmodel.treatment.NSEffectiveProfileSwitch
import info.nightscout.sdk.localmodel.treatment.NSProfileSwitch
import info.nightscout.sdk.localmodel.treatment.NSTemporaryBasal
import info.nightscout.sdk.localmodel.treatment.NSTemporaryTarget
import info.nightscout.sdk.localmodel.treatment.NSTreatment
import info.nightscout.sdk.remotemodel.RemoteTreatment
import org.json.JSONObject
import java.util.concurrent.TimeUnit

@JvmSynthetic
internal fun RemoteTreatment.toTreatment(): NSTreatment? {
    val treatmentTimestamp = timestamp()
    when {
        insulin != null && insulin > 0                                  ->
            return NSBolus(
                date = treatmentTimestamp,
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
                type = NSBolus.BolusType.fromString(this.type),
            )

        carbs != null && carbs > 0                                      ->
            return NSCarbs(
                date = treatmentTimestamp,
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

        eventType == EventType.TEMPORARY_TARGET                         -> {
            if (treatmentTimestamp == 0L) return null

            this.duration ?: return null
            this.targetBottom ?: return null
            this.targetTop ?: return null

            return NSTemporaryTarget(
                date = treatmentTimestamp,
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
                reason = NSTemporaryTarget.Reason.fromString(this.reason)
            )
        }

        eventType == EventType.TEMPORARY_BASAL                          -> {
            if (treatmentTimestamp == 0L) return null

            this.absolute ?: this.percent ?: return null
            this.duration ?: return null
            if (this.duration == 0L && this.durationInMilliseconds == null) return null

            return NSTemporaryBasal(
                date = treatmentTimestamp,
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
                isAbsolute = this.absolute != null,
                rate = this.absolute ?: (this.percent?.plus(100.0)) ?: 0.0,
                type = NSTemporaryBasal.Type.fromString(this.type)
            )
        }

        eventType == EventType.NOTE && this.originalProfileName != null -> {
            if (treatmentTimestamp == 0L) return null
            this.profileJson ?: return null
            this.originalCustomizedName ?: return null
            this.originalTimeshift ?: return null
            this.originalPercentage ?: return null
            this.originalDuration ?: return null
            this.originalEnd ?: return null

            return NSEffectiveProfileSwitch(
                date = treatmentTimestamp,
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
                profileJson = JSONObject(this.profileJson),
                originalProfileName = this.originalProfileName,
                originalCustomizedName = this.originalCustomizedName,
                originalTimeshift = this.originalTimeshift,
                originalPercentage = this.originalPercentage,
                originalDuration = this.originalDuration,
                originalEnd = this.originalEnd
            )
        }

        eventType == EventType.PROFILE_SWITCH                           -> {
            if (treatmentTimestamp == 0L) return null
            this.profile ?: return null

            return NSProfileSwitch(
                date = treatmentTimestamp,
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
                profileJson = this.profileJson?.let { JSONObject(this.profileJson) },
                profileName = this.profile,
                originalProfileName = this.originalProfileName,
                originalDuration = this.originalDuration,
                duration = this.duration,
                timeShift = this.timeshift,
                percentage = this.percentage,
            )
        }

        eventType == EventType.BOLUS_WIZARD                             -> {
            if (treatmentTimestamp == 0L) return null
            this.bolusCalculatorResult ?: return null

            return NSBolusWizard(
                date = treatmentTimestamp,
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
                bolusCalculatorResult = this.bolusCalculatorResult,
                glucose = this.glucose
            )
        }
    }

    return null
}
