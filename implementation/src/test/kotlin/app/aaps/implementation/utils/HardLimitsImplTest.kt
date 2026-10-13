package app.aaps.implementation.utils

import android.content.Context
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import io.reactivex.rxjava3.core.Single
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class HardLimitsImplTest : TestBase() {

    @Mock lateinit var uiInteraction: UiInteraction
    @Mock lateinit var preferences: Preferences
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var context: Context
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var dateUtil: DateUtil

    private lateinit var hardLimits: HardLimitsImpl

    @BeforeEach
    fun setup() {
        hardLimits = HardLimitsImpl(aapsLogger, uiInteraction, preferences, rh, context, persistenceLayer, dateUtil)
        whenever(dateUtil.now()).thenReturn(1000L)
        whenever(persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(any(), any(), any(), any(), any(), any())).thenReturn(Single.just(PersistenceLayer.TransactionResult()))
        whenever(rh.gs(any())).thenReturn("")
        whenever(rh.gs(any(), any())).thenReturn("")
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
    fun `minDia returns correct values for all ages`() {
        whenever(preferences.get(StringKey.SafetyAge)).thenReturn("child")
        assertThat(hardLimits.minDia()).isEqualTo(5.0)

        whenever(preferences.get(StringKey.SafetyAge)).thenReturn("pregnant")
        assertThat(hardLimits.minDia()).isEqualTo(5.0)
    }

    @Test
    fun `maxDia returns correct values for all ages`() {
        whenever(preferences.get(StringKey.SafetyAge)).thenReturn("child")
        assertThat(hardLimits.maxDia()).isEqualTo(9.0)

        whenever(preferences.get(StringKey.SafetyAge)).thenReturn("pregnant")
        assertThat(hardLimits.maxDia()).isEqualTo(10.0)
    }

    @Test
    fun `minIC returns correct values for all ages`() {
        whenever(preferences.get(StringKey.SafetyAge)).thenReturn("child")
        assertThat(hardLimits.minIC()).isEqualTo(2.0)

        whenever(preferences.get(StringKey.SafetyAge)).thenReturn("pregnant")
        assertThat(hardLimits.minIC()).isEqualTo(0.3)
    }

    @Test
    fun `maxIC returns correct values for all ages`() {
        whenever(preferences.get(StringKey.SafetyAge)).thenReturn("child")
        assertThat(hardLimits.maxIC()).isEqualTo(100.0)

        whenever(preferences.get(StringKey.SafetyAge)).thenReturn("pregnant")
        assertThat(hardLimits.maxIC()).isEqualTo(100.0)
    }

    @Test
    fun `isInRange returns true when value is within range`() {
        assertThat(hardLimits.isInRange(5.0, 0.0, 10.0)).isTrue()
        assertThat(hardLimits.isInRange(0.0, 0.0, 10.0)).isTrue()
        assertThat(hardLimits.isInRange(10.0, 0.0, 10.0)).isTrue()
    }

    @Test
    fun `isInRange returns false when value is outside range`() {
        assertThat(hardLimits.isInRange(-0.1, 0.0, 10.0)).isFalse()
        assertThat(hardLimits.isInRange(10.1, 0.0, 10.0)).isFalse()
    }

    @Test
    fun `checkHardLimits returns true when value is within limits`() {
        assertThat(hardLimits.checkHardLimits(5.0, app.aaps.core.ui.R.string.bolus, 0.0, 10.0)).isTrue()
    }

    @Test
    fun `checkHardLimits returns false when value is below limit`() {
        assertThat(hardLimits.checkHardLimits(-1.0, app.aaps.core.ui.R.string.bolus, 0.0, 10.0)).isFalse()
    }

    @Test
    fun `checkHardLimits returns false when value is above limit`() {
        assertThat(hardLimits.checkHardLimits(11.0, app.aaps.core.ui.R.string.bolus, 0.0, 10.0)).isFalse()
    }

    @Test
    fun `verifyHardLimits returns original value when within limits`() {
        val result = hardLimits.verifyHardLimits(5.0, app.aaps.core.ui.R.string.bolus, 0.0, 10.0)
        assertThat(result).isEqualTo(5.0)
    }

    @Test
    fun `verifyHardLimits clamps value to low limit when below`() {
        val result = hardLimits.verifyHardLimits(-5.0, app.aaps.core.ui.R.string.bolus, 0.0, 10.0)
        assertThat(result).isEqualTo(0.0)
    }

    @Test
    fun `verifyHardLimits clamps value to high limit when above`() {
        val result = hardLimits.verifyHardLimits(15.0, app.aaps.core.ui.R.string.bolus, 0.0, 10.0)
        assertThat(result).isEqualTo(10.0)
    }

    @Test
    fun `verifyHardLimits logs error and shows notification when value is out of range`() {
        hardLimits.verifyHardLimits(15.0, app.aaps.core.ui.R.string.bolus, 0.0, 10.0)

        verify(uiInteraction).showToastAndNotification(any(), any(), any())
        verify(persistenceLayer).insertPumpTherapyEventIfNewByTimestamp(any(), any(), any(), any(), any(), any())
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
        val result = hardLimits.verifyHardLimits(0.0, app.aaps.core.ui.R.string.bolus, 0.0, 10.0)
        assertThat(result).isEqualTo(0.0)
    }

    @Test
    fun `verifyHardLimits handles edge case at exact high limit`() {
        val result = hardLimits.verifyHardLimits(10.0, app.aaps.core.ui.R.string.bolus, 0.0, 10.0)
        assertThat(result).isEqualTo(10.0)
    }

    @Test
    fun `verifyHardLimits handles negative limits correctly`() {
        val result = hardLimits.verifyHardLimits(-15.0, app.aaps.core.ui.R.string.bolus, -10.0, 10.0)
        assertThat(result).isEqualTo(-10.0)
    }

    @Test
    fun `verifyHardLimits handles very large values`() {
        val result = hardLimits.verifyHardLimits(1000000.0, app.aaps.core.ui.R.string.bolus, 0.0, 10.0)
        assertThat(result).isEqualTo(10.0)
    }

    @Test
    fun `verifyHardLimits handles very small values`() {
        val result = hardLimits.verifyHardLimits(-1000000.0, app.aaps.core.ui.R.string.bolus, 0.0, 10.0)
        assertThat(result).isEqualTo(0.0)
    }
}
