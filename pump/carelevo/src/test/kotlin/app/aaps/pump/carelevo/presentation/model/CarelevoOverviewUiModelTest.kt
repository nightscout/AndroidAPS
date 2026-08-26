package app.aaps.pump.carelevo.presentation.model

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

internal class CarelevoOverviewUiModelTest {

    private fun model(
        serialNumber: String = "SN-123",
        lotNumber: String = "LOT-9",
        bootDateTimeUi: String = "2026-07-16 10:00",
        expirationTime: String = "72h",
        infusionStatus: Int? = 1,
        insulinRemainText: String = "120 U",
        totalBasal: Double = 12.5,
        totalBolus: Double = 3.0,
        isPumpStopped: Boolean = false,
        runningRemainMinutes: Int = 4320
    ) = CarelevoOverviewUiModel(
        serialNumber = serialNumber,
        lotNumber = lotNumber,
        bootDateTimeUi = bootDateTimeUi,
        expirationTime = expirationTime,
        infusionStatus = infusionStatus,
        insulinRemainText = insulinRemainText,
        totalBasal = totalBasal,
        totalBolus = totalBolus,
        isPumpStopped = isPumpStopped,
        runningRemainMinutes = runningRemainMinutes
    )

    @Test
    fun `constructor stores every field verbatim`() {
        val m = model()
        assertThat(m.serialNumber).isEqualTo("SN-123")
        assertThat(m.lotNumber).isEqualTo("LOT-9")
        assertThat(m.bootDateTimeUi).isEqualTo("2026-07-16 10:00")
        assertThat(m.expirationTime).isEqualTo("72h")
        assertThat(m.infusionStatus).isEqualTo(1)
        assertThat(m.insulinRemainText).isEqualTo("120 U")
        assertThat(m.totalBasal).isEqualTo(12.5)
        assertThat(m.totalBolus).isEqualTo(3.0)
        assertThat(m.isPumpStopped).isFalse()
        assertThat(m.runningRemainMinutes).isEqualTo(4320)
    }

    @Test
    fun `infusionStatus may be null`() {
        assertThat(model(infusionStatus = null).infusionStatus).isNull()
    }

    @Test
    fun `isPumpStopped true is preserved`() {
        assertThat(model(isPumpStopped = true).isPumpStopped).isTrue()
    }

    @Test
    fun `copy changes only the requested field`() {
        val original = model()
        val copy = original.copy(insulinRemainText = "10 U")
        assertThat(copy.insulinRemainText).isEqualTo("10 U")
        assertThat(copy.serialNumber).isEqualTo(original.serialNumber)
        assertThat(copy.runningRemainMinutes).isEqualTo(original.runningRemainMinutes)
        assertThat(copy).isNotEqualTo(original)
    }

    @Test
    fun `equals and hashCode agree for identical models`() {
        val a = model()
        val b = model()
        assertThat(a).isEqualTo(b)
        assertThat(a.hashCode()).isEqualTo(b.hashCode())
    }

    @Test
    fun `models differing by one field are not equal`() {
        assertThat(model()).isNotEqualTo(model(totalBolus = 99.0))
        assertThat(model()).isNotEqualTo(model(infusionStatus = null))
        assertThat(model()).isNotEqualTo(model(isPumpStopped = true))
    }

    @Test
    fun `destructuring returns components in declaration order`() {
        val (sn, lot, boot, exp, status, remain, basal, bolus, stopped, mins) = model()
        assertThat(sn).isEqualTo("SN-123")
        assertThat(lot).isEqualTo("LOT-9")
        assertThat(boot).isEqualTo("2026-07-16 10:00")
        assertThat(exp).isEqualTo("72h")
        assertThat(status).isEqualTo(1)
        assertThat(remain).isEqualTo("120 U")
        assertThat(basal).isEqualTo(12.5)
        assertThat(bolus).isEqualTo(3.0)
        assertThat(stopped).isFalse()
        assertThat(mins).isEqualTo(4320)
    }

    @Test
    fun `toString exposes the field values`() {
        val text = model().toString()
        assertThat(text).contains("SN-123")
        assertThat(text).contains("CarelevoOverviewUiModel")
    }
}
