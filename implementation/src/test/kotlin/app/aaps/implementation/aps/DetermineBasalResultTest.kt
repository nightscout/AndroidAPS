package app.aaps.implementation.aps

import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.RT
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

/**
 * Covers the non-finite-field diagnostic in [DetermineBasalResult.json] (the single serialization
 * choke point shared by SMB / AutoISF / AMA). kotlinx.serialization's default Json rejects NaN/±Infinity,
 * which crashed device-status upload every loop cycle with an opaque `RT.write$Self` stack. The diagnostic
 * reports WHICH field went non-finite (and the ISF inputs) to Crashlytics before the (intentionally
 * unchanged) serialize still throws — nothing is swallowed.
 */
class DetermineBasalResultTest : TestBaseWithProfile() {

    @Test
    fun `json reports the offending non-finite field to Crashlytics and still throws`() {
        val result = apsResultProvider.get()
            .with(RT(runningDynamicIsf = false, algorithm = APSResult.Algorithm.SMB, insulinReq = Double.NaN))

        // Not swallowed: the default Json still rejects the NaN and json() propagates the crash as before.
        assertThrows<Exception> { result.json() }

        // …but the diagnostic fired first, naming the field + algorithm so the next Crashlytics event is actionable.
        val captor = argumentCaptor<Throwable>()
        verify(fabricPrivacy).logException(captor.capture())
        assertThat(captor.firstValue.message).contains("insulinReq=NaN")
        assertThat(captor.firstValue.message).contains("algorithm=SMB")
    }

    @Test
    fun `json reports infinity too`() {
        val result = apsResultProvider.get()
            .with(RT(runningDynamicIsf = true, algorithm = APSResult.Algorithm.AUTO_ISF, variable_sens = Double.POSITIVE_INFINITY))

        assertThrows<Exception> { result.json() }

        val captor = argumentCaptor<Throwable>()
        verify(fabricPrivacy).logException(captor.capture())
        assertThat(captor.firstValue.message).contains("variable_sens=Infinity")
        assertThat(captor.firstValue.message).contains("algorithm=AUTO_ISF")
    }

    @Test
    fun `json does not report or throw when all result fields are finite`() {
        val result = apsResultProvider.get()
            .with(RT(runningDynamicIsf = false, algorithm = APSResult.Algorithm.SMB, eventualBG = 120.0, insulinReq = 0.5, variable_sens = 45.0))

        result.json() // valid JSON — no crash

        verify(fabricPrivacy, never()).logException(any())
    }
}
