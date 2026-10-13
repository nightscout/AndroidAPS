package app.aaps.core.nssdk.mapper

import app.aaps.core.nssdk.localmodel.entry.NsUnits
import app.aaps.core.nssdk.localmodel.treatment.EventType
import app.aaps.core.nssdk.localmodel.treatment.NSBolus
import app.aaps.core.nssdk.localmodel.treatment.NSBolusWizard
import app.aaps.core.nssdk.localmodel.treatment.NSCarbs
import app.aaps.core.nssdk.localmodel.treatment.NSEffectiveProfileSwitch
import app.aaps.core.nssdk.localmodel.treatment.NSExtendedBolus
import app.aaps.core.nssdk.localmodel.treatment.NSOfflineEvent
import app.aaps.core.nssdk.localmodel.treatment.NSProfileSwitch
import app.aaps.core.nssdk.localmodel.treatment.NSTemporaryBasal
import app.aaps.core.nssdk.localmodel.treatment.NSTemporaryTarget
import app.aaps.core.nssdk.localmodel.treatment.NSTherapyEvent
import app.aaps.core.nssdk.localmodel.treatment.NSTreatment
import app.aaps.core.nssdk.remotemodel.RemoteTreatment
import com.google.gson.Gson
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Convert to [RemoteTreatment] and back to [NSTreatment]
 * testing purpose only
 *
 * @return treatment after double conversion
 */
fun NSTreatment.convertToRemoteAndBack(): NSTreatment? =
    toRemoteTreatment()?.toTreatment()

fun String.toNSTreatment(): NSTreatment? =
    Gson().fromJson(this, RemoteTreatment::class.java).toTreatment()

internal fun RemoteTreatment.toTreatment(): NSTreatment? {
    val treatmentTimestamp = timestamp()
    when {
        insulin != null && insulin > 0                                     ->
            return NSBolus(
                date = treatmentTimestamp,
                device = this.device,
                identifier = this.identifier,
                units = NsUnits.fromString(this.units),
                srvModified = this.srvModified,
                srvCreated = this.srvCreated,
                utcOffset = this.utcOffset ?: 0,
                subject = this.subject,
                isReadOnly = this.isReadOnly == true,
                isValid = this.isValid != false,
                eventType = this.eventType ?: EventType.MEAL_BOLUS,
                notes = this.notes,
                pumpId = this.pumpId,
                endId = this.endId,
                pumpType = this.pumpType,
                pumpSerial = this.pumpSerial,
                insulin = this.insulin,
                type = NSBolus.BolusType.fromString(this.type),
                isBasalInsulin = isBasalInsulin == true
            )

        carbs != null && carbs != 0.0                                      -> {
            val durationInMilliseconds = this.durationInMilliseconds ?: this.duration?.let { TimeUnit.MINUTES.toMillis(this.duration) } ?: 0L
            return NSCarbs(
                date = treatmentTimestamp,
                device = this.device,
                identifier = this.identifier,
                units = NsUnits.fromString(this.units),
                srvModified = this.srvModified,
                srvCreated = this.srvCreated,
                utcOffset = this.utcOffset ?: 0,
                subject = this.subject,
                isReadOnly = this.isReadOnly == true,
                isValid = this.isValid != false,
                eventType = this.eventType ?: EventType.CARBS_CORRECTION,
                notes = this.notes,
                pumpId = this.pumpId,
                endId = this.endId,
                pumpType = this.pumpType,
                pumpSerial = this.pumpSerial,
                carbs = this.carbs,
                duration = durationInMilliseconds
            )
        }

        eventType == EventType.TEMPORARY_TARGET                            -> {
            if (treatmentTimestamp == 0L) return null

            val durationInMilliseconds = this.durationInMilliseconds ?: this.duration?.let { TimeUnit.MINUTES.toMillis(this.duration) } ?: return null

            if (durationInMilliseconds == 0L)
                return NSTemporaryTarget(
                    date = treatmentTimestamp,
                    device = this.device,
                    identifier = this.identifier,
                    units = NsUnits.fromString(this.units),
                    srvModified = this.srvModified,
                    srvCreated = this.srvCreated,
                    utcOffset = this.utcOffset ?: 0,
                    subject = this.subject,
                    isReadOnly = this.isReadOnly == true,
                    isValid = this.isValid != false,
                    eventType = this.eventType,
                    notes = this.notes,
                    pumpId = this.pumpId,
                    endId = this.endId,
                    pumpType = this.pumpType,
                    pumpSerial = this.pumpSerial,
                    duration = 0,
                    targetBottom = 0.0,
                    targetTop = 0.0,
                    reason = NSTemporaryTarget.Reason.CUSTOM
                )

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
                isReadOnly = this.isReadOnly == true,
                isValid = this.isValid != false,
                eventType = this.eventType,
                notes = this.notes,
                pumpId = this.pumpId,
                endId = this.endId,
                pumpType = this.pumpType,
                pumpSerial = this.pumpSerial,
                duration = durationInMilliseconds,
                targetBottom = this.targetBottom,
                targetTop = this.targetTop,
                reason = NSTemporaryTarget.Reason.fromString(this.reason)
            )
        }

        // Convert back emulated TBR -> EB
        eventType == EventType.TEMPORARY_BASAL && extendedEmulated != null -> {

            return NSExtendedBolus(
                date = treatmentTimestamp,
                device = device,
                identifier = identifier,
                units = NsUnits.fromString(extendedEmulated?.units),
                srvModified = srvModified,
                srvCreated = srvCreated,
                utcOffset = utcOffset ?: 0,
                subject = subject,
                isReadOnly = extendedEmulated?.isReadOnly == true,
                isValid = extendedEmulated?.isValid != false,
                eventType = EventType.COMBO_BOLUS,
                notes = extendedEmulated?.notes,
                pumpId = extendedEmulated?.pumpId,
                endId = extendedEmulated?.endId,
                pumpType = extendedEmulated?.pumpType,
                pumpSerial = extendedEmulated?.pumpSerial,
                enteredinsulin = extendedEmulated?.enteredinsulin ?: 0.0,
                duration = extendedEmulated?.durationInMilliseconds ?: TimeUnit.MINUTES.toMillis(extendedEmulated?.duration ?: 0L),
                isEmulatingTempBasal = extendedEmulated?.isEmulatingTempBasal,
                rate = rate
            )
        }

        eventType == EventType.TEMPORARY_BASAL                             -> {
            if (treatmentTimestamp == 0L) return null

            this.absolute ?: this.percent ?: return null
            val durationInMilliseconds = this.durationInMilliseconds ?: this.duration?.let { TimeUnit.MINUTES.toMillis(this.duration) } ?: return null
            if (this.durationInMilliseconds == 0L) return null

            return NSTemporaryBasal(
                date = treatmentTimestamp,
                device = this.device,
                identifier = this.identifier,
                units = NsUnits.fromString(this.units),
                srvModified = this.srvModified,
                srvCreated = this.srvCreated,
                utcOffset = this.utcOffset ?: 0,
                subject = this.subject,
                isReadOnly = this.isReadOnly == true,
                isValid = this.isValid != false,
                eventType = this.eventType,
                notes = this.notes,
                pumpId = this.pumpId,
                endId = this.endId,
                pumpType = this.pumpType,
                pumpSerial = this.pumpSerial,
                duration = durationInMilliseconds,
                isAbsolute = this.absolute != null,
                rate = this.absolute ?: (this.percent?.plus(100.0)) ?: 0.0,
                type = NSTemporaryBasal.Type.fromString(this.type)
            )
        }

        eventType == EventType.NOTE && this.originalProfileName != null    -> {
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
                isReadOnly = this.isReadOnly == true,
                isValid = this.isValid != false,
                eventType = this.eventType,
                notes = this.notes,
                pumpId = this.pumpId,
                endId = this.endId,
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

        eventType == EventType.PROFILE_SWITCH                              -> {
            if (treatmentTimestamp == 0L) return null
            this.profile ?: return null
            val durationInMilliseconds = this.durationInMilliseconds ?: this.duration?.let { TimeUnit.MINUTES.toMillis(this.duration) } ?: 0L

            return NSProfileSwitch(
                date = treatmentTimestamp,
                device = this.device,
                identifier = this.identifier,
                units = NsUnits.fromString(this.units),
                srvModified = this.srvModified,
                srvCreated = this.srvCreated,
                utcOffset = this.utcOffset ?: 0,
                subject = this.subject,
                isReadOnly = this.isReadOnly == true,
                isValid = this.isValid != false,
                eventType = this.eventType,
                notes = this.notes,
                pumpId = this.pumpId,
                endId = this.endId,
                pumpType = this.pumpType,
                pumpSerial = this.pumpSerial,
                profileJson = this.profileJson?.let { JSONObject(this.profileJson) },
                profile = this.profile,
                originalProfileName = this.originalProfileName,
                originalDuration = this.originalDuration,
                duration = durationInMilliseconds,
                timeShift = this.timeshift,
                percentage = this.percentage,
            )
        }

        eventType == EventType.BOLUS_WIZARD                                -> {
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
                isReadOnly = this.isReadOnly == true,
                isValid = this.isValid != false,
                eventType = this.eventType,
                notes = this.notes,
                pumpId = this.pumpId,
                endId = this.endId,
                pumpType = this.pumpType,
                pumpSerial = this.pumpSerial,
                bolusCalculatorResult = this.bolusCalculatorResult,
                glucose = this.glucose
            )
        }

        eventType == EventType.CANNULA_CHANGE ||
            eventType == EventType.INSULIN_CHANGE ||
            eventType == EventType.SENSOR_CHANGE ||
            eventType == EventType.FINGER_STICK_BG_VALUE ||
            eventType == EventType.NONE ||
            eventType == EventType.ANNOUNCEMENT ||
            eventType == EventType.QUESTION ||
            eventType == EventType.EXERCISE ||
            eventType == EventType.NOTE ||
            eventType == EventType.PUMP_BATTERY_CHANGE                     -> {
            if (treatmentTimestamp == 0L) return null
            val durationInMilliseconds = this.durationInMilliseconds ?: this.duration?.let { TimeUnit.MINUTES.toMillis(this.duration) } ?: 0L

            return NSTherapyEvent(
                date = treatmentTimestamp,
                device = this.device,
                identifier = this.identifier,
                units = NsUnits.fromString(this.units),
                srvModified = this.srvModified,
                srvCreated = this.srvCreated,
                utcOffset = this.utcOffset ?: 0,
                subject = this.subject,
                isReadOnly = this.isReadOnly == true,
                isValid = this.isValid != false,
                eventType = this.eventType,
                location = this.location,
                arrow = this.arrow,
                notes = this.notes,
                pumpId = this.pumpId,
                endId = this.endId,
                pumpType = this.pumpType,
                pumpSerial = this.pumpSerial,
                duration = durationInMilliseconds,
                glucose = this.glucose,
                enteredBy = this.enteredBy,
                glucoseType = NSTherapyEvent.MeterType.fromString(this.glucoseType)
            )
        }

        eventType == EventType.APS_OFFLINE                                 -> {
            if (treatmentTimestamp == 0L) return null
            val durationInMilliseconds = this.durationInMilliseconds ?: this.duration?.let { TimeUnit.MINUTES.toMillis(this.duration) } ?: 0L

            return NSOfflineEvent(
                date = treatmentTimestamp,
                device = this.device,
                identifier = this.identifier,
                units = NsUnits.fromString(this.units),
                srvModified = this.srvModified,
                srvCreated = this.srvCreated,
                utcOffset = this.utcOffset ?: 0,
                subject = this.subject,
                isReadOnly = this.isReadOnly == true,
                isValid = this.isValid != false,
                eventType = this.eventType,
                notes = this.notes,
                pumpId = this.pumpId,
                endId = this.endId,
                pumpType = this.pumpType,
                pumpSerial = this.pumpSerial,
                duration = durationInMilliseconds,
                reason = NSOfflineEvent.Reason.fromString(this.reason),
                // RunningMode
                originalDuration = this.originalDuration,
                mode = NSOfflineEvent.Mode.fromString(this.mode),
                autoForced = this.autoForced == true,
                reasons = this.reasons ?: ""
            )
        }

        eventType == EventType.COMBO_BOLUS                                 -> {
            if (treatmentTimestamp == 0L) return null
            this.enteredinsulin ?: return null
            val durationInMilliseconds = this.durationInMilliseconds ?: this.duration?.let { TimeUnit.MINUTES.toMillis(this.duration) } ?: 0L

            return NSExtendedBolus(
                date = treatmentTimestamp,
                device = this.device,
                identifier = this.identifier,
                units = NsUnits.fromString(this.units),
                srvModified = this.srvModified,
                srvCreated = this.srvCreated,
                utcOffset = this.utcOffset ?: 0,
                subject = this.subject,
                isReadOnly = this.isReadOnly == true,
                isValid = this.isValid != false,
                eventType = this.eventType,
                notes = this.notes,
                pumpId = this.pumpId,
                endId = this.endId,
                pumpType = this.pumpType,
                pumpSerial = this.pumpSerial,
                enteredinsulin = this.enteredinsulin,
                duration = durationInMilliseconds,
                isEmulatingTempBasal = this.isEmulatingTempBasal,
                rate = rate
            )
        }
    }

    return null
}

internal fun NSTreatment.toRemoteTreatment(): RemoteTreatment? =
    when (this) {
        is NSBolus                  -> RemoteTreatment(
            date = date,
            device = device,
            identifier = identifier,
            units = units?.value,
            utcOffset = utcOffset,
            subject = subject,
            isReadOnly = isReadOnly,
            isValid = isValid,
            eventType = eventType,
            notes = notes,
            pumpId = pumpId,
            endId = endId,
            pumpType = pumpType,
            pumpSerial = pumpSerial,
            insulin = insulin,
            type = type.name,
            isBasalInsulin = isBasalInsulin
        )

        is NSCarbs                  -> RemoteTreatment(
            date = date,
            device = device,
            identifier = identifier,
            units = units?.value,
            utcOffset = utcOffset,
            subject = subject,
            isReadOnly = isReadOnly,
            isValid = isValid,
            eventType = eventType,
            notes = notes,
            pumpId = pumpId,
            endId = endId,
            pumpType = pumpType,
            pumpSerial = pumpSerial,
            carbs = carbs,
            duration = duration?.let { TimeUnit.MILLISECONDS.toMinutes(it) },
            durationInMilliseconds = duration
        )

        is NSTemporaryTarget        -> RemoteTreatment(
            date = date,
            device = device,
            identifier = identifier,
            units = units?.value,
            srvModified = srvModified,
            srvCreated = srvCreated,
            utcOffset = utcOffset,
            subject = subject,
            isReadOnly = isReadOnly,
            isValid = isValid,
            eventType = eventType,
            notes = notes,
            pumpId = pumpId,
            endId = endId,
            pumpType = pumpType,
            pumpSerial = pumpSerial,
            duration = TimeUnit.MILLISECONDS.toMinutes(duration),
            durationInMilliseconds = duration,
            targetBottom = targetBottom,
            targetTop = targetTop,
            reason = reason.text
        )

        is NSTemporaryBasal         -> RemoteTreatment(
            date = date,
            device = device,
            identifier = identifier,
            units = units?.value,
            srvModified = srvModified,
            srvCreated = srvCreated,
            utcOffset = utcOffset,
            subject = subject,
            isReadOnly = isReadOnly,
            isValid = isValid,
            eventType = eventType,
            notes = notes,
            pumpId = pumpId,
            endId = endId,
            pumpType = pumpType,
            pumpSerial = pumpSerial,
            duration = TimeUnit.MILLISECONDS.toMinutes(duration),
            durationInMilliseconds = duration,
            absolute = absolute,
            percent = percent,
            rate = rate,
            type = type.name,
            extendedEmulated = extendedEmulated?.toRemoteTreatment()
        )

        is NSEffectiveProfileSwitch -> RemoteTreatment(
            date = date,
            device = device,
            identifier = identifier,
            units = units?.value,
            srvModified = srvModified,
            srvCreated = srvCreated,
            utcOffset = utcOffset,
            subject = subject,
            isReadOnly = isReadOnly,
            isValid = isValid,
            eventType = eventType,
            notes = notes,
            pumpId = pumpId,
            endId = endId,
            pumpType = pumpType,
            pumpSerial = pumpSerial,
            profileJson = profileJson.toString(),
            originalProfileName = originalProfileName,
            originalCustomizedName = originalCustomizedName,
            originalTimeshift = originalTimeshift,
            originalPercentage = originalPercentage,
            originalDuration = originalDuration,
            originalEnd = originalEnd
        )

        is NSProfileSwitch          -> RemoteTreatment(
            date = date,
            device = device,
            identifier = identifier,
            units = units?.value,
            srvModified = srvModified,
            srvCreated = srvCreated,
            utcOffset = utcOffset,
            subject = subject,
            isReadOnly = isReadOnly,
            isValid = isValid,
            eventType = eventType,
            notes = notes,
            pumpId = pumpId,
            endId = endId,
            pumpType = pumpType,
            pumpSerial = pumpSerial,
            profileJson = profileJson.toString(), // must be de-customized
            profile = profile,
            originalProfileName = originalProfileName,
            originalDuration = originalDuration,
            duration = duration?.let { TimeUnit.MILLISECONDS.toMinutes(it) },
            durationInMilliseconds = duration,
            timeshift = timeShift,
            percentage = percentage,
        )

        is NSBolusWizard            -> RemoteTreatment(
            date = date,
            device = device,
            identifier = identifier,
            units = units?.value,
            srvModified = srvModified,
            srvCreated = srvCreated,
            utcOffset = utcOffset,
            subject = subject,
            isReadOnly = isReadOnly,
            isValid = isValid,
            eventType = eventType,
            notes = notes,
            pumpId = pumpId,
            endId = endId,
            pumpType = pumpType,
            pumpSerial = pumpSerial,
            bolusCalculatorResult = bolusCalculatorResult,
            glucose = glucose
        )

        is NSTherapyEvent           -> RemoteTreatment(
            date = date,
            device = device,
            identifier = identifier,
            units = units?.value,
            srvModified = srvModified,
            srvCreated = srvCreated,
            utcOffset = utcOffset,
            subject = subject,
            isReadOnly = isReadOnly,
            isValid = isValid,
            eventType = eventType,
            location = location,
            arrow = arrow,
            notes = notes,
            pumpId = pumpId,
            endId = endId,
            pumpType = pumpType,
            pumpSerial = pumpSerial,
            duration = TimeUnit.MILLISECONDS.toMinutes(duration),
            durationInMilliseconds = duration,
            glucose = glucose,
            enteredBy = enteredBy,
            glucoseType = glucoseType?.text
        )

        is NSOfflineEvent           -> RemoteTreatment(
            date = date,
            device = device,
            identifier = identifier,
            units = units?.value,
            srvModified = srvModified,
            srvCreated = srvCreated,
            utcOffset = utcOffset,
            subject = subject,
            isReadOnly = isReadOnly,
            isValid = isValid,
            eventType = eventType,
            notes = notes,
            pumpId = pumpId,
            endId = endId,
            pumpType = pumpType,
            pumpSerial = pumpSerial,
            duration = TimeUnit.MILLISECONDS.toMinutes(duration),
            durationInMilliseconds = duration,
            reason = reason.name,
            // RunningMode
            originalDuration = originalDuration,
            mode = mode.name,
            autoForced = autoForced,
            reasons = reasons
        )

        is NSExtendedBolus          ->
            RemoteTreatment(
                date = date,
                device = device,
                identifier = identifier,
                units = units?.value,
                srvModified = srvModified,
                srvCreated = srvCreated,
                utcOffset = utcOffset,
                subject = subject,
                isReadOnly = isReadOnly,
                isValid = isValid,
                eventType = eventType,
                duration = TimeUnit.MILLISECONDS.toMinutes(duration),
                durationInMilliseconds = duration,
                notes = notes,
                splitNow = 0,
                splitExt = 100,
                enteredinsulin = enteredinsulin,
                relative = rate,
                isEmulatingTempBasal = isEmulatingTempBasal,
                pumpId = pumpId,
                endId = endId,
                pumpType = pumpType,
                pumpSerial = pumpSerial
            )

        else                        -> null
    }
