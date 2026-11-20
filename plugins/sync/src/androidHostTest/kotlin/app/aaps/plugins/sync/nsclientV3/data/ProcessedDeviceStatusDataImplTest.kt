package app.aaps.plugins.sync.nsclientV3.data

import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.plugins.sync.nsclientV3.data.ProcessedDeviceStatusDataImpl.Companion.UPLOADER_SILENT_AFTER_MS
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

/**
 * #5547: the client showed the battery of every phone that ever uploaded, and the lowest of them as the
 * uploader battery - also for phones that were switched off long ago.
 */
internal class ProcessedDeviceStatusDataImplTest {

    private val sut = ProcessedDeviceStatusDataImpl { mock() }

    private fun report(device: String, clock: Long, battery: Int) {
        sut.uploaderMap[device] = ProcessedDeviceStatusData.Uploader().also {
            it.clock = clock
            it.battery = battery
        }
    }

    @Test
    fun aPhoneThatStoppedReportingIsLeftOut() {
        report(PHONE_IN_USE, NOW, 80)
        report(OLD_PHONE, NOW - UPLOADER_SILENT_AFTER_MS - 1, 5)

        assertThat(sut.activeUploaders.keys).containsExactly(PHONE_IN_USE)
        // The old phone's 5 % used to stand for the phone in use
        assertThat(sut.uploaderStatus).isEqualTo("80%")
        // Nothing is removed from the map itself, so a reader walking through it cannot fail
        assertThat(sut.uploaderMap.keys).containsExactly(PHONE_IN_USE, OLD_PHONE)
    }

    @Test
    fun twoPhonesThatBothReportAreBothShown() {
        report(PHONE_IN_USE, NOW, 80)
        report(OLD_PHONE, NOW - 5 * 60_000L, 40)

        assertThat(sut.activeUploaders.keys).containsExactly(PHONE_IN_USE, OLD_PHONE)
        assertThat(sut.uploaderStatus).isEqualTo("40%")
    }

    @Test
    fun aPhoneJustInsideTheLimitIsStillShown() {
        report(PHONE_IN_USE, NOW, 80)
        report(OLD_PHONE, NOW - UPLOADER_SILENT_AFTER_MS, 40)

        assertThat(sut.activeUploaders.keys).containsExactly(PHONE_IN_USE, OLD_PHONE)
    }

    /** The limit is counted from the newest report, not from now: the last known level stays. */
    @Test
    fun whenEveryPhoneIsSilentTheNewestReportStays() {
        report(PHONE_IN_USE, NOW - 10 * UPLOADER_SILENT_AFTER_MS, 30)
        report(OLD_PHONE, NOW - 20 * UPLOADER_SILENT_AFTER_MS, 5)

        assertThat(sut.activeUploaders.keys).containsExactly(PHONE_IN_USE)
        assertThat(sut.uploaderStatus).isEqualTo("30%")
    }

    @Test
    fun noUploaderYet() {
        assertThat(sut.activeUploaders).isEmpty()
        assertThat(sut.uploaderStatus).isEqualTo("100%")
    }

    private companion object {

        const val NOW = 1_800_000_000_000L
        const val PHONE_IN_USE = "openaps://samsung SM-S911B"
        const val OLD_PHONE = "openaps://Pixel 6"
    }
}
