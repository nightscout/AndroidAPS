package app.aaps.plugins.sync.nsclientV3.extensions

import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.utils.T
import app.aaps.core.main.extensions.toTemporaryBasal
import app.aaps.core.nssdk.localmodel.treatment.EventType
import app.aaps.core.nssdk.localmodel.treatment.NSExtendedBolus
import app.aaps.core.nssdk.localmodel.treatment.NSTreatment
import app.aaps.database.entities.ExtendedBolus
import app.aaps.database.entities.embedments.InterfaceIDs
import java.security.InvalidParameterException

fun NSExtendedBolus.toExtendedBolus(): ExtendedBolus =
    ExtendedBolus(
        isValid = isValid,
        timestamp = date ?: throw InvalidParameterException(),
        utcOffset = T.mins(utcOffset ?: 0L).msecs(),
        amount = enteredinsulin,
        duration = duration,
        isEmulatingTempBasal = isEmulatingTempBasal ?: false,
        interfaceIDs_backing = InterfaceIDs(nightscoutId = identifier, pumpId = pumpId, pumpType = InterfaceIDs.PumpType.fromString(pumpType), pumpSerial = pumpSerial, endId = endId)
    )

fun ExtendedBolus.toNSExtendedBolus(profile: Profile, convertToTemporaryBasal: Boolean = true): NSTreatment =
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
            identifier = interfaceIDs.nightscoutId,
            pumpId = interfaceIDs.pumpId,
            pumpType = interfaceIDs.pumpType?.name,
            pumpSerial = interfaceIDs.pumpSerial,
            endId = interfaceIDs.endId
        )
