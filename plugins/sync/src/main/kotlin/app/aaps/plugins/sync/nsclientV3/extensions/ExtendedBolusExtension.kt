package app.aaps.plugins.sync.nsclientV3.extensions

import app.aaps.core.data.model.EB
import app.aaps.core.data.model.IDs
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.nssdk.localmodel.treatment.EventType
import app.aaps.core.nssdk.localmodel.treatment.NSExtendedBolus
import app.aaps.core.nssdk.localmodel.treatment.NSTreatment
import app.aaps.core.objects.extensions.toTemporaryBasal
import java.security.InvalidParameterException

fun NSExtendedBolus.toExtendedBolus(): EB =
    EB(
        isValid = isValid,
        timestamp = date ?: throw InvalidParameterException(),
        utcOffset = T.mins(utcOffset ?: 0L).msecs(),
        amount = enteredinsulin,
        duration = duration,
        isEmulatingTempBasal = isEmulatingTempBasal == true,
        ids = IDs(nightscoutId = identifier, pumpId = pumpId, pumpType = PumpType.fromString(pumpType), pumpSerial = pumpSerial, endId = endId)
    )

fun EB.toNSExtendedBolus(profile: Profile, convertToTemporaryBasal: Boolean = true): NSTreatment =
    if (isEmulatingTempBasal && convertToTemporaryBasal)
        this.toTemporaryBasal(profile).toNSTemporaryBasal(profile).also {
            it.extendedEmulated = toNSExtendedBolus(profile, convertToTemporaryBasal = false) as NSExtendedBolus
        }
    else
        NSExtendedBolus(
            eventType = EventType.COMBO_BOLUS,
            isValid = isValid,
            date = timestamp,
            utcOffset = T.msecs(utcOffset).mins(),
            enteredinsulin = amount,
            duration = duration,
            isEmulatingTempBasal = isEmulatingTempBasal,
            rate = rate,
            identifier = ids.nightscoutId,
            pumpId = ids.pumpId,
            pumpType = ids.pumpType?.name,
            pumpSerial = ids.pumpSerial,
            endId = ids.endId
        )
