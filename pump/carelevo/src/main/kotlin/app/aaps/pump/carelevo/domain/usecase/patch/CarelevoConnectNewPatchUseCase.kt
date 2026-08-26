package app.aaps.pump.carelevo.domain.usecase.patch

import app.aaps.pump.carelevo.domain.model.patch.CarelevoPatchInfoDomainModel
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import app.aaps.pump.carelevo.domain.usecase.patch.model.CarelevoConnectNewPatchRequestModel
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class CarelevoConnectNewPatchUseCase @Inject constructor(
    private val patchInfoRepository: CarelevoPatchInfoRepository,
) {

    companion object {

        private val BOOT_DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyMMddHHmm")
    }

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
        val bootDateTime = LocalDateTime.now().format(BOOT_DATE_TIME_FORMATTER)
        return patchInfoRepository.updatePatchInfo(
            CarelevoPatchInfoDomainModel(
                address = address,
                manufactureNumber = serialNumber,
                firmwareVersion = firmwareVersion,
                bootDateTime = bootDateTime,
                bootDateTimeUtcMillis = parseBootDateTimeUtcMillis(bootDateTime),
                modelName = modelName,
                insulinAmount = request.volume,
                insulinRemain = request.volume.toDouble(),
                thresholdInsulinRemain = request.remains,
                thresholdExpiry = request.expiry,
                thresholdMaxBasalSpeed = request.maxBasalSpeed,
                thresholdMaxBolusDose = request.maxVolume
            )
        )
    }

    private fun parseBootDateTimeUtcMillis(raw: String?): Long? {
        if (raw.isNullOrBlank()) {
            return null
        }

        return runCatching {
            LocalDateTime.parse(raw, BOOT_DATE_TIME_FORMATTER)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
    }
}
