package info.nightscout.plugins.sync.nsclientV3.extensions

import info.nightscout.core.extensions.toTemporaryBasal
import info.nightscout.database.entities.ExtendedBolus
import info.nightscout.database.entities.embedments.InterfaceIDs
import info.nightscout.interfaces.profile.Profile
import info.nightscout.sdk.localmodel.treatment.EventType
import info.nightscout.sdk.localmodel.treatment.NSExtendedBolus
import info.nightscout.sdk.localmodel.treatment.NSTreatment
import info.nightscout.shared.utils.T
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
