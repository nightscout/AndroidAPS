package app.aaps.implementation.pump

import app.aaps.core.data.model.RM
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.utils.Translator
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

/**
 * Pins the Nightscout device-status payload built by [PumpStatusProviderImpl].
 *
 * There was no coverage here at all, which is thin for something uploaded to Nightscout on every
 * loop: a field silently appearing, vanishing or changing shape is invisible locally and only shows
 * up as wrong data on the server.
 *
 * Two behaviours matter most and are easy to break by accident. Empty values are **omitted** rather
 * than sent as zero - `putIfThereIsValue` skips nulls *and* zeros, which is what keeps idle temp-basal
 * and extended-bolus fields out of the payload. And the active pump gets to contribute its own
 * entries to `extended`, which is the seam the driver hook hangs on.
 */
class PumpStatusProviderImplTest : TestBaseWithProfile() {

    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var translator: Translator

    private lateinit var sut: PumpStatusProviderImpl

    @BeforeEach
    fun prepare() = runTest {
        sut = PumpStatusProviderImpl(
            activePlugin, pumpSync, profileFunction, persistenceLayer,
            rh, dateUtil, decimalFormatter, translator, config
        )
        whenever(activePlugin.activePump).thenReturn(testPumpPlugin)
        whenever(profileFunction.getProfile()).thenReturn(effectiveProfile)
        whenever(profileFunction.getProfileName()).thenReturn("SomeProfile")
        whenever(pumpSync.expectedPumpState()).thenReturn(PumpSync.PumpState(null, null, null, null, "1"))
        whenever(persistenceLayer.getRunningModeActiveAt(any())).thenReturn(RM(timestamp = 0, mode = RM.Mode.OPEN_LOOP, duration = 0L))
        whenever(translator.translate(any<RM.Mode>())).thenReturn("Open Loop")
        // Real clock, not dateUtil.now(): the base stubs now() to a fixed value while isOlderThan is a
        // spy that still reads the system clock, so a stubbed timestamp always looks an hour stale.
        testPumpPlugin.lastData = System.currentTimeMillis()
    }

    /**
     * The assertions below were written against the previous `org.json` implementation and are kept
     * word for word across the move to kotlinx. Re-parsing the produced document with `org.json` is
     * what makes that possible, and it is also the stronger statement: not just "the new code has the
     * same fields", but "the bytes we upload still parse into the same document".
     */
    private suspend fun status(): JSONObject = JSONObject(sut.generatePumpJsonStatus().toString())

    @Test
    fun `carries the top level shape Nightscout expects`() = runTest {
        val json = status()

        assertThat(json.has("reservoir")).isTrue()
        assertThat(json.has("clock")).isTrue()
        assertThat(json.has("status")).isTrue()
        assertThat(json.has("extended")).isTrue()
        assertThat(json.getJSONObject("status").getString("status")).isEqualTo("Open Loop")
        assertThat(json.getJSONObject("extended").has("Version")).isTrue()
        assertThat(json.getJSONObject("extended").getString("ActiveProfile")).isEqualTo("SomeProfile")
    }

    /**
     * Stale pump data must not be uploaded at all - an hour-old reservoir reading presented as current
     * is worse than no reading.
     */
    @Test
    fun `data older than an hour yields an empty document`() = runTest {
        // Relative to dateUtil.now(), which the base stubs to a fixed value. isOlderThan reads that
        // same now() since DateUtilImpl moved to commonMain - it used to go to the system clock
        // directly, so this line had to use the real clock to look stale.
        testPumpPlugin.lastData = dateUtil.now() - 61 * 60 * 1000L

        assertThat(status().length()).isEqualTo(0)
    }

    @Test
    fun `no running profile yields an empty document`() = runTest {
        whenever(profileFunction.getProfile()).thenReturn(null)

        assertThat(status().length()).isEqualTo(0)
    }

    /**
     * The zero-skipping in `putIfThereIsValue`. With no temp basal or extended bolus running, those
     * keys must be absent rather than present-and-zero, or Nightscout shows a 0 U/h temp basal.
     */
    @Test
    fun `absent values are omitted rather than sent as zero`() = runTest {
        val extended = status().getJSONObject("extended")

        assertThat(extended.has("TempBasalAbsoluteRate")).isFalse()
        assertThat(extended.has("TempBasalRemaining")).isFalse()
        assertThat(extended.has("ExtendedBolusAbsoluteRate")).isFalse()
        assertThat(extended.has("ExtendedBolusRemaining")).isFalse()
        assertThat(extended.has("LastBolusAmount")).isFalse()
    }

    /** A battery level of null must not appear as 0%. */
    @Test
    fun `a missing battery level is omitted`() = runTest {
        assertThat(status().getJSONObject("battery").has("percent")).isFalse()
    }

    /**
     * The driver extension seam: whatever the active pump contributes has to reach `extended`. This is
     * the only thing [app.aaps.core.interfaces.pump.Pump.extendedStatus] exists for, and Combo V2 uses
     * it to report alert codes.
     */
    @Test
    fun `entries contributed by the pump reach the extended section`() = runTest {
        testPumpPlugin.extendedStatusExtras = buildJsonObject { put("WarningCode", "W73") }

        assertThat(status().getJSONObject("extended").getString("WarningCode")).isEqualTo("W73")
    }

    /**
     * Combo V2 contributes an `Int` alert code, so the value has to stay a JSON number. Flattening
     * driver entries to strings would silently change what Nightscout receives.
     */
    @Test
    fun `a numeric entry from the pump stays a number`() = runTest {
        testPumpPlugin.extendedStatusExtras = buildJsonObject { put("WarningCode", 73) }

        assertThat(status().getJSONObject("extended").get("WarningCode")).isEqualTo(73)
    }
}
