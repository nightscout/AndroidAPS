package app.aaps.pump.carelevo.domain.usecase.patch

import app.aaps.pump.carelevo.domain.model.patch.CarelevoPatchInfoDomainModel
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import app.aaps.pump.carelevo.domain.usecase.patch.model.CarelevoConnectNewPatchRequestModel
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal class CarelevoConnectNewPatchUseCaseTest {

    private val patchInfoRepository: CarelevoPatchInfoRepository = mock()

    private val request = CarelevoConnectNewPatchRequestModel(
        volume = 300,
        expiry = 120,
        remains = 30,
        maxBasalSpeed = 15.0,
        maxVolume = 25.0,
        isBuzzOn = true
    )

    private val sut = CarelevoConnectNewPatchUseCase(
        patchInfoRepository = patchInfoRepository
    )

    @Test
    fun persistNewPatch_writes_pairing_identity_and_fabricated_boot_time() {
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)

        val persisted = sut.persistNewPatch(
            address = "94:b2:16:1d:2f:6d",
            serialNumber = "EO12507099001",
            firmwareVersion = "T168",
            modelName = "6776514848",
            request = request
        )

        assertThat(persisted).isTrue()
        val captor = argumentCaptor<CarelevoPatchInfoDomainModel>()
        verify(patchInfoRepository).updatePatchInfo(captor.capture())
        with(captor.firstValue) {
            assertThat(address).isEqualTo("94:b2:16:1d:2f:6d")
            assertThat(manufactureNumber).isEqualTo("EO12507099001")
            assertThat(firmwareVersion).isEqualTo("T168")
            assertThat(modelName).isEqualTo("6776514848")
            assertThat(insulinAmount).isEqualTo(300)
            assertThat(insulinRemain).isEqualTo(300.0)
            assertThat(thresholdInsulinRemain).isEqualTo(30)
            assertThat(thresholdExpiry).isEqualTo(120)
            assertThat(thresholdMaxBasalSpeed).isEqualTo(15.0)
            assertThat(thresholdMaxBolusDose).isEqualTo(25.0)
            // Fabricated from the phone clock as yyMMddHHmm and parseable back.
            assertThat(bootDateTime).hasLength(10)
            assertThat(bootDateTimeUtcMillis).isNotNull()
        }
    }

    /** A zone with a 45 minute offset, so a wrong zone or a lost offset shows in the minutes too. */
    @Test
    fun persistNewPatch_stamps_the_minute_of_pairing_in_local_time_and_as_utc_millis() {
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(true)
        val kathmandu = TimeZone.getTimeZone("Asia/Kathmandu")
        val saved = TimeZone.getDefault()
        TimeZone.setDefault(kathmandu)
        try {
            val before = System.currentTimeMillis()
            sut.persistNewPatch(
                address = "94:b2:16:1d:2f:6d",
                serialNumber = "EO12507099001",
                firmwareVersion = "T168",
                modelName = "6776514848",
                request = request
            )
            val after = System.currentTimeMillis()

            val captor = argumentCaptor<CarelevoPatchInfoDomainModel>()
            verify(patchInfoRepository).updatePatchInfo(captor.capture())
            val millis = captor.firstValue.bootDateTimeUtcMillis!!
            // The start of the minute the patch was paired in.
            assertThat(millis % 60_000L).isEqualTo(0L)
            assertThat(millis).isAtLeast(before - before % 60_000L)
            assertThat(millis).isAtMost(after)
            // The text is that same minute, as the clock in Kathmandu shows it.
            val text = SimpleDateFormat("yyMMddHHmm", Locale.US).apply { timeZone = kathmandu }.format(Date(millis))
            assertThat(captor.firstValue.bootDateTime).isEqualTo(text)
        } finally {
            TimeZone.setDefault(saved)
        }
    }

    @Test
    fun persistNewPatch_returns_false_when_repository_write_fails() {
        whenever(patchInfoRepository.updatePatchInfo(any())).thenReturn(false)

        val persisted = sut.persistNewPatch(
            address = "94:b2:16:1d:2f:6d",
            serialNumber = "EO12507099001",
            firmwareVersion = "T168",
            modelName = "6776514848",
            request = request
        )

        assertThat(persisted).isFalse()
    }
}
