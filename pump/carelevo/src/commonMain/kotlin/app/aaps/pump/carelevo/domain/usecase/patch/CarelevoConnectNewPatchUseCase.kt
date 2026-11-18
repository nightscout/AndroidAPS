package app.aaps.pump.carelevo.domain.usecase.patch

import app.aaps.pump.carelevo.domain.model.patch.CarelevoPatchInfoDomainModel
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import app.aaps.pump.carelevo.domain.usecase.patch.model.CarelevoConnectNewPatchRequestModel
import app.aaps.pump.carelevo.ext.BOOT_DATE_TIME_FORMAT
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

// App-scoped: a view model injects this, and ViewModelOwnershipTest requires every concrete class
// a view model injects to have one owner. It is a stateless view over the app-scoped DAOs, so one
// instance is also the only sensible number.
@SingleIn(AppScope::class)
class CarelevoConnectNewPatchUseCase @Inject constructor(
    private val patchInfoRepository: CarelevoPatchInfoRepository,
) {

    /**
     * Persist the freshly paired patch — used by the pairing session (`CarelevoBleSession.runPairing`).
     * bootDateTime is fabricated from the phone clock — it is never on the wire; the persistence layer
     * stamps it as `yyMMddHHmm`. Returns false if the DB write fails.
     */
    fun persistNewPatch(
        address: String,
        serialNumber: String,
        firmwareVersion: String,
        modelName: String,
        request: CarelevoConnectNewPatchRequestModel
    ): Boolean {
        val zone = TimeZone.currentSystemDefault()
        val now = Clock.System.now().toLocalDateTime(zone)
        // To the minute, as the stored text has it, so the text and the millis name the same time.
        val bootMinute = LocalDateTime(now.date, LocalTime(now.hour, now.minute))
        return patchInfoRepository.updatePatchInfo(
            CarelevoPatchInfoDomainModel(
                address = address,
                manufactureNumber = serialNumber,
                firmwareVersion = firmwareVersion,
                bootDateTime = bootMinute.format(BOOT_DATE_TIME_FORMAT),
                bootDateTimeUtcMillis = bootMinute.toInstant(zone).toEpochMilliseconds(),
                modelName = modelName,
                insulinAmount = request.volume,
                insulinRemain = request.volume.toDouble(),
                thresholdInsulinRemain = request.remains,
                thresholdExpiry = request.expiry,
                thresholdMaxBasalSpeed = request.maxBasalSpeed,
                thresholdMaxBolusDose = request.maxVolume,
                // False, not null: null means a patch from an older build, and those count once the needle is in.
                activationComplete = false
            )
        )
    }
}
