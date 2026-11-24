package app.aaps.implementation.utils

import app.aaps.core.interfaces.alerts.Alert
import app.aaps.core.interfaces.alerts.AlertLifetime
import app.aaps.core.interfaces.alerts.AlertSeverity
import app.aaps.core.interfaces.alerts.Alerts
import app.aaps.core.interfaces.alerts.CoreAlert
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class HardLimitsImplTest : TestBase() {

    @Mock lateinit var alerts: Alerts
    @Mock lateinit var preferences: Preferences
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var dateUtil: DateUtil

    private lateinit var hardLimits: HardLimitsImpl

    @BeforeEach
    fun setup() {
        hardLimits = HardLimitsImpl(aapsLogger, { alerts }, preferences, rh, dateUtil, rxBus)
        whenever(dateUtil.now()).thenReturn(1000L)
        whenever(rh.gs(any<Int>())).thenReturn("")
        whenever(rh.gs(any<Int>(), any())).thenReturn("")
    }

    @Test
    fun `maxBolus returns correct value for child`() {
        whenever(preferences.get(StringKey.SafetyAge)).thenReturn("child")
        assertThat(hardLimits.maxBolus()).isEqualTo(5.0)
    }

    @Test
    fun `maxBolus returns correct value for teenage`() {
        whenever(preferences.get(StringKey.SafetyAge)).thenReturn("teenage")
        assertThat(hardLimits.maxBolus()).isEqualTo(10.0)
    }

    @Test
    fun `maxBolus returns correct value for adult`() {
        whenever(preferences.get(StringKey.SafetyAge)).thenReturn("adult")
        assertThat(hardLimits.maxBolus()).isEqualTo(17.0)
    }

    @Test
    fun `maxBolus returns correct value for resistant adult`() {
        whenever(preferences.get(StringKey.SafetyAge)).thenReturn("resistantadult")
        assertThat(hardLimits.maxBolus()).isEqualTo(25.0)
    }

    @Test
    fun `maxBolus returns correct value for pregnant`() {
        whenever(preferences.get(StringKey.SafetyAge)).thenReturn("pregnant")
        assertThat(hardLimits.maxBolus()).isEqualTo(60.0)
    }

    @Test
    fun `maxBolus defaults to adult for unknown age`() {
        whenever(preferences.get(StringKey.SafetyAge)).thenReturn("unknown")
        assertThat(hardLimits.maxBolus()).isEqualTo(17.0)
    }

    @Test
    fun `maxIobAMA returns correct values for all ages`() {
        whenever(preferences.get(StringKey.SafetyAge)).thenReturn("child")
        assertThat(hardLimits.maxIobAMA()).isEqualTo(3.0)

        whenever(preferences.get(StringKey.SafetyAge)).thenReturn("teenage")
        assertThat(hardLimits.maxIobAMA()).isEqualTo(5.0)

        whenever(preferences.get(StringKey.SafetyAge)).thenReturn("adult")
        assertThat(hardLimits.maxIobAMA()).isEqualTo(7.0)

        whenever(preferences.get(StringKey.SafetyAge)).thenReturn("resistantadult")
        assertThat(hardLimits.maxIobAMA()).isEqualTo(12.0)

        whenever(preferences.get(StringKey.SafetyAge)).thenReturn("pregnant")
        assertThat(hardLimits.maxIobAMA()).isEqualTo(25.0)
    }

    @Test
    fun `maxIobSMB returns correct values for all ages`() {
        whenever(preferences.get(StringKey.SafetyAge)).thenReturn("child")
        assertThat(hardLimits.maxIobSMB()).isEqualTo(7.0)

        whenever(preferences.get(StringKey.SafetyAge)).thenReturn("teenage")
        assertThat(hardLimits.maxIobSMB()).isEqualTo(13.0)

        whenever(preferences.get(StringKey.SafetyAge)).thenReturn("adult")
        assertThat(hardLimits.maxIobSMB()).isEqualTo(22.0)

        whenever(preferences.get(StringKey.SafetyAge)).thenReturn("resistantadult")
        assertThat(hardLimits.maxIobSMB()).isEqualTo(30.0)

        whenever(preferences.get(StringKey.SafetyAge)).thenReturn("pregnant")
        assertThat(hardLimits.maxIobSMB()).isEqualTo(70.0)
    }

    @Test
    fun `maxBasal returns correct values for all ages`() {
        whenever(preferences.get(StringKey.SafetyAge)).thenReturn("child")
        assertThat(hardLimits.maxBasal()).isEqualTo(2.0)

        whenever(preferences.get(StringKey.SafetyAge)).thenReturn("teenage")
        assertThat(hardLimits.maxBasal()).isEqualTo(5.0)

        whenever(preferences.get(StringKey.SafetyAge)).thenReturn("adult")
        assertThat(hardLimits.maxBasal()).isEqualTo(10.0)

        whenever(preferences.get(StringKey.SafetyAge)).thenReturn("resistantadult")
        assertThat(hardLimits.maxBasal()).isEqualTo(12.0)

        whenever(preferences.get(StringKey.SafetyAge)).thenReturn("pregnant")
        assertThat(hardLimits.maxBasal()).isEqualTo(25.0)
    }

    @Test
    fun `diaRange returns correct values for all ages`() {
        whenever(preferences.get(StringKey.SafetyAge)).thenReturn("child")
        assertThat(hardLimits.diaRange()).isEqualTo(5.0..9.0)

        whenever(preferences.get(StringKey.SafetyAge)).thenReturn("pregnant")
        assertThat(hardLimits.diaRange()).isEqualTo(5.0..10.0)
    }

    @Test
    fun `icRange returns correct values for all ages`() {
        whenever(preferences.get(StringKey.SafetyAge)).thenReturn("child")
        assertThat(hardLimits.icRange()).isEqualTo(2.0..100.0)

        whenever(preferences.get(StringKey.SafetyAge)).thenReturn("pregnant")
        assertThat(hardLimits.icRange()).isEqualTo(0.3..100.0)
    }

    @Test
    fun `an unknown age falls back to adult`() {
        whenever(preferences.get(StringKey.SafetyAge)).thenReturn("nonsense")
        assertThat(hardLimits.maxBolus()).isEqualTo(HardLimits.MAX_BOLUS.getValue(HardLimits.AgeType.ADULT))
        assertThat(hardLimits.diaRange()).isEqualTo(HardLimits.LIMIT_DIA.getValue(HardLimits.AgeType.ADULT))
    }

    @Test
    fun `verifyHardLimits with a range clamps the same way as with two bounds`() {
        assertThat(hardLimits.verifyHardLimits(5.0, app.aaps.core.interfaces.R.string.bolus, 0.0..10.0)).isEqualTo(5.0)
        assertThat(hardLimits.verifyHardLimits(-5.0, app.aaps.core.interfaces.R.string.bolus, 0.0..10.0)).isEqualTo(0.0)
        assertThat(hardLimits.verifyHardLimits(15.0, app.aaps.core.interfaces.R.string.bolus, 0.0..10.0)).isEqualTo(10.0)
    }

    @Test
    fun `checkHardLimits returns true when value is within limits`() {
        assertThat(hardLimits.checkHardLimits(5.0, app.aaps.core.interfaces.R.string.bolus, 0.0, 10.0)).isTrue()
    }

    @Test
    fun `checkHardLimits returns false when value is below limit`() {
        assertThat(hardLimits.checkHardLimits(-1.0, app.aaps.core.interfaces.R.string.bolus, 0.0, 10.0)).isFalse()
    }

    @Test
    fun `checkHardLimits returns false when value is above limit`() {
        assertThat(hardLimits.checkHardLimits(11.0, app.aaps.core.interfaces.R.string.bolus, 0.0, 10.0)).isFalse()
    }

    @Test
    fun `verifyHardLimits returns original value when within limits`() {
        val result = hardLimits.verifyHardLimits(5.0, app.aaps.core.interfaces.R.string.bolus, 0.0, 10.0)
        assertThat(result).isEqualTo(5.0)
    }

    @Test
    fun `verifyHardLimits clamps value to low limit when below`() {
        val result = hardLimits.verifyHardLimits(-5.0, app.aaps.core.interfaces.R.string.bolus, 0.0, 10.0)
        assertThat(result).isEqualTo(0.0)
    }

    @Test
    fun `verifyHardLimits clamps value to high limit when above`() {
        val result = hardLimits.verifyHardLimits(15.0, app.aaps.core.interfaces.R.string.bolus, 0.0, 10.0)
        assertThat(result).isEqualTo(10.0)
    }

    @Test
    fun `a value out of range raises the hard limit warning`() {
        hardLimits.verifyHardLimits(15.0, app.aaps.core.interfaces.R.string.bolus, 0.0, 10.0)

        // One chime and a card, ended by the user. Its announcement is the alert core's.
        val captor = argumentCaptor<Alert>()
        verify(alerts).raise(captor.capture())
        assertThat(captor.firstValue.kind).isEqualTo(CoreAlert.HARD_LIMIT)
        assertThat(captor.firstValue.severity).isEqualTo(AlertSeverity.WARNING)
        assertThat(captor.firstValue.lifetime).isEqualTo(AlertLifetime.EVENT)
    }

    @Test
    fun `ageEntries returns correct number of entries`() {
        whenever(rh.gs(app.aaps.core.ui.R.string.child)).thenReturn("Child")
        whenever(rh.gs(app.aaps.core.ui.R.string.teenage)).thenReturn("Teenage")
        whenever(rh.gs(app.aaps.core.ui.R.string.adult)).thenReturn("Adult")
        whenever(rh.gs(app.aaps.core.ui.R.string.resistant_adult)).thenReturn("Resistant adult")
        whenever(rh.gs(app.aaps.core.ui.R.string.pregnant)).thenReturn("Pregnant")

        val entries = hardLimits.ageEntries()
        assertThat(entries).hasLength(5)
    }

    @Test
    fun `ageEntryValues returns correct values`() {
        val values = hardLimits.ageEntryValues()
        assertThat(values).hasLength(5)
        assertThat(values[HardLimits.AgeType.CHILD.ordinal]).isEqualTo("child")
        assertThat(values[HardLimits.AgeType.TEENAGE.ordinal]).isEqualTo("teenage")
        assertThat(values[HardLimits.AgeType.ADULT.ordinal]).isEqualTo("adult")
        assertThat(values[HardLimits.AgeType.RESISTANT_ADULT.ordinal]).isEqualTo("resistantadult")
        assertThat(values[HardLimits.AgeType.PREGNANT.ordinal]).isEqualTo("pregnant")
    }

    @Test
    fun `verifyHardLimits handles edge case at exact low limit`() {
        val result = hardLimits.verifyHardLimits(0.0, app.aaps.core.interfaces.R.string.bolus, 0.0, 10.0)
        assertThat(result).isEqualTo(0.0)
    }

    @Test
    fun `verifyHardLimits handles edge case at exact high limit`() {
        val result = hardLimits.verifyHardLimits(10.0, app.aaps.core.interfaces.R.string.bolus, 0.0, 10.0)
        assertThat(result).isEqualTo(10.0)
    }

    @Test
    fun `verifyHardLimits handles negative limits correctly`() {
        val result = hardLimits.verifyHardLimits(-15.0, app.aaps.core.interfaces.R.string.bolus, -10.0, 10.0)
        assertThat(result).isEqualTo(-10.0)
    }

    @Test
    fun `verifyHardLimits handles very large values`() {
        val result = hardLimits.verifyHardLimits(1000000.0, app.aaps.core.interfaces.R.string.bolus, 0.0, 10.0)
        assertThat(result).isEqualTo(10.0)
    }

    @Test
    fun `verifyHardLimits handles very small values`() {
        val result = hardLimits.verifyHardLimits(-1000000.0, app.aaps.core.interfaces.R.string.bolus, 0.0, 10.0)
        assertThat(result).isEqualTo(0.0)
    }
}
