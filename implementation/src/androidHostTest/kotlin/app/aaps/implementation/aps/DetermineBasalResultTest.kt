package app.aaps.implementation.aps

import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.RT
import app.aaps.core.keys.IntKey
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

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
        val result = apsResultProvider()
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
        val result = apsResultProvider()
            .with(RT(runningDynamicIsf = true, algorithm = APSResult.Algorithm.AUTO_ISF, variable_sens = Double.POSITIVE_INFINITY))

        assertThrows<Exception> { result.json() }

        val captor = argumentCaptor<Throwable>()
        verify(fabricPrivacy).logException(captor.capture())
        assertThat(captor.firstValue.message).contains("variable_sens=Infinity")
        assertThat(captor.firstValue.message).contains("algorithm=AUTO_ISF")
    }

    @Test
    fun `json does not report or throw when all result fields are finite`() {
        val result = apsResultProvider()
            .with(RT(runningDynamicIsf = false, algorithm = APSResult.Algorithm.SMB, eventualBG = 120.0, insulinReq = 0.5, variable_sens = 45.0))

        result.json() // valid JSON — no crash

        verify(fabricPrivacy, never()).logException(any())
    }

    /**
     * What the document actually contains. The other tests only say whether `json()` throws, which left
     * the payload uploaded to Nightscout every loop cycle unasserted.
     */
    @Test
    fun `json carries the result fields`() {
        val result = apsResultProvider()
            .with(RT(runningDynamicIsf = false, algorithm = APSResult.Algorithm.SMB, eventualBG = 120.0, insulinReq = 0.5, rate = 1.5, duration = 30))

        val json = result.json()!!

        assertThat(json.getValue("eventualBG").jsonPrimitive.double).isEqualTo(120.0)
        assertThat(json.getValue("insulinReq").jsonPrimitive.double).isEqualTo(0.5)
        assertThat(json.getValue("rate").jsonPrimitive.double).isEqualTo(1.5)
        assertThat(json.getValue("duration").jsonPrimitive.int).isEqualTo(30)
    }

    /** A field the result did not set stays out of the document rather than going out as null. */
    @Test
    fun `json omits fields that were not set`() {
        val result = apsResultProvider()
            .with(RT(runningDynamicIsf = false, algorithm = APSResult.Algorithm.SMB, eventualBG = 120.0))

        assertThat(result.json()!!.containsKey("insulinReq")).isFalse()
    }

    /**
     * Whole numbers print as `1.0`, not `1`.
     *
     * This changed when `json()` stopped going through org.json on the way out: org.json trims a
     * trailing `.0` when it prints, kotlinx does not. Both are the same JSON number and Nightscout reads
     * them the same way, but the bytes stored in DeviceStatus differ, so it is stated here rather than
     * left to be noticed on a server. It also matches what `RT.serialize` has always produced.
     */
    @Test
    fun `a whole number keeps its decimal point`() {
        val result = apsResultProvider()
            .with(RT(runningDynamicIsf = false, algorithm = APSResult.Algorithm.SMB, rate = 1.0, duration = 30))

        assertThat(result.json().toString()).contains("\"rate\":1.0")
    }

    /** The return of the CGM data checks has no eventual BG. Such a result is not stored (#5413). */
    @Test
    fun `a result without eventual BG stopped early`() {
        val result = apsResultProvider().with(RT(runningDynamicIsf = true, algorithm = APSResult.Algorithm.SMB, rate = 1.0, duration = 30))

        assertThat(result.stoppedEarly).isTrue()
    }

    @Test
    fun `a result with eventual BG did not stop early`() {
        val result = apsResultProvider().with(RT(runningDynamicIsf = true, algorithm = APSResult.Algorithm.SMB, eventualBG = 120.0, variable_sens = 45.0))

        assertThat(result.stoppedEarly).isFalse()
    }

    /**
     * Open loop, no temp running, profile basal 1 U/h. With a 60 % minimum change, 140 % and 150 % are both
     * small changes and are not reported - unless the request is at the pump limit, which is always reported.
     */
    private suspend fun openLoopWithNoTempRunning() {
        whenever(constraintsChecker.isClosedLoopAllowed()).thenReturn(ConstraintObject(false, aapsLogger))
        whenever(processedTbrEbData.getTempBasalIncludingConvertedExtended(any())).thenReturn(null)
        whenever(profileFunction.getProfile()).thenReturn(effectiveProfile)
        whenever(preferences.get(IntKey.LoopOpenModeMinChange)).thenReturn(60)
    }

    private suspend fun percentRequest(percent: Int): Boolean =
        apsResultProvider().also {
            it.isTempBasalRequested = true
            it.usePercent = true
            it.percent = percent
        }.isChangeRequested()

    private suspend fun absoluteRequest(rate: Double): Boolean =
        apsResultProvider().also {
            it.isTempBasalRequested = true
            it.usePercent = false
            it.rate = rate
        }.isChangeRequested()

    /** The limit is the one in the description, not the one of the pump type behind it (#946). */
    @Test
    fun `open loop reports a percent temp at the max percent of the description`() = runBlocking<Unit> {
        openLoopWithNoTempRunning()
        testPumpPlugin.pumpDescription.tempBasalStyle = PumpDescription.PERCENT
        testPumpPlugin.pumpDescription.maxTempPercent = 150

        assertThat(percentRequest(140)).isFalse()
        assertThat(percentRequest(150)).isTrue()
    }

    /** The limit is the one in the description: a driver can change it (Medtronic sets the user's max basal). */
    @Test
    fun `open loop reports an absolute temp at the max rate of the description`() = runBlocking<Unit> {
        openLoopWithNoTempRunning()
        testPumpPlugin.pumpDescription.tempBasalStyle = PumpDescription.ABSOLUTE
        testPumpPlugin.pumpDescription.maxTempAbsolute = 1.5

        assertThat(absoluteRequest(1.4)).isFalse()
        assertThat(absoluteRequest(1.5)).isTrue()
    }
}
