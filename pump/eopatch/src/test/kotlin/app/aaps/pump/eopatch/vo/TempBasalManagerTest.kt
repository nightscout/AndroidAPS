package app.aaps.pump.eopatch.vo

import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.eopatch.code.UnitOrPercent
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations

class TempBasalManagerTest {

    @Mock
    private lateinit var mockPreferences: Preferences

    private lateinit var manager: TempBasalManager

    @BeforeEach
    fun setup() {
        MockitoAnnotations.openMocks(this)
        manager = TempBasalManager()
    }

    @Test
    fun `init should have no started basal`() {
        assertThat(manager.startedBasal).isNull()
        assertThat(manager.unit).isEqualTo(UnitOrPercent.P)
    }

    @Test
    fun `updateBasalRunning should set started basal`() {
        val tempBasal = TempBasal.createAbsolute(120, 1.5f)

        manager.updateBasalRunning(tempBasal)

        assertThat(manager.startedBasal).isNotNull()
        assertThat(manager.startedBasal?.running).isTrue()
        assertThat(manager.startedBasal?.durationMinutes).isEqualTo(120)
        assertThat(manager.startedBasal?.doseUnitPerHour).isWithin(0.001f).of(1.5f)
    }

    /** The patch screen showed a running temp basal as "@01:00 120/120'" and the suspend dialog as "00:00" left. */
    @Test
    fun `updateBasalRunning sets the start of the running temp basal`() {
        val before = System.currentTimeMillis()

        manager.updateBasalRunning(TempBasal.createAbsolute(120, 0.0f))

        val started = manager.startedBasal!!
        assertThat(started.startTimestamp).isAtLeast(before)
        assertThat(started.startTimestamp).isAtMost(System.currentTimeMillis())
        assertThat(started.endTimestamp).isEqualTo(started.startTimestamp + 120 * 60_000L)
        assertThat(started.remainTimeText).isNotEqualTo("00:00")
    }

    @Test
    fun `updateBasalRunning should clone temp basal`() {
        val tempBasal = TempBasal.createAbsolute(120, 1.5f)

        manager.updateBasalRunning(tempBasal)

        // Modify original
        tempBasal.durationMinutes = 90

        // Manager's copy should be unchanged
        assertThat(manager.startedBasal?.durationMinutes).isEqualTo(120)
    }

    @Test
    fun `updateBasalStopped should set running to false`() {
        val tempBasal = TempBasal.createAbsolute(120, 1.5f)
        manager.updateBasalRunning(tempBasal)

        manager.updateBasalStopped()

        assertThat(manager.startedBasal?.running).isFalse()
        assertThat(manager.startedBasal?.startTimestamp).isEqualTo(0)
    }

    @Test
    fun `clear should reset all values`() {
        val tempBasal = TempBasal.createAbsolute(120, 1.5f)
        manager.updateBasalRunning(tempBasal)

        manager.clear()

        assertThat(manager.startedBasal).isNull()
    }

    @Test
    fun `update should copy values from other manager`() {
        val other = TempBasalManager()
        val tempBasal = TempBasal.createAbsolute(60, 2.0f)
        other.updateBasalRunning(tempBasal)
        other.unit = UnitOrPercent.U

        manager.update(other)

        assertThat(manager.startedBasal).isNotNull()
        assertThat(manager.startedBasal?.durationMinutes).isEqualTo(60)
        assertThat(manager.unit).isEqualTo(UnitOrPercent.U)
    }

    @Test
    fun `toString should contain key information`() {
        val tempBasal = TempBasal.createAbsolute(120, 1.5f)
        manager.updateBasalRunning(tempBasal)

        val stringRep = manager.toString()

        assertThat(stringRep).contains("TempBasalManager")
        assertThat(stringRep).contains("startedBasal=")
    }

    @Test
    fun `unit should be mutable`() {
        assertThat(manager.unit).isEqualTo(UnitOrPercent.P)

        manager.unit = UnitOrPercent.U

        assertThat(manager.unit).isEqualTo(UnitOrPercent.U)
    }
}
