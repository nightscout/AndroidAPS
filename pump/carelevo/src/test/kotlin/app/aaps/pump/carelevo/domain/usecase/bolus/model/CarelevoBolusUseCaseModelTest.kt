package app.aaps.pump.carelevo.domain.usecase.bolus.model

import app.aaps.pump.carelevo.domain.usecase.CarelevoUseCaseRequest
import app.aaps.pump.carelevo.domain.usecase.CarelevoUseCaseResponse
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Value-semantics coverage for the bolus use-case request/response DTOs. These are passed across the
 * coordinator boundary and compared/copied there, so the generated data-class contract (equals /
 * hashCode / copy / destructuring) is what callers actually rely on.
 */
internal class CarelevoBolusUseCaseModelTest {

    // ---------- StartImmeBolusInfusionRequestModel ----------

    @Test
    fun `StartImmeBolusInfusionRequestModel exposes its action seq and volume`() {
        val model = StartImmeBolusInfusionRequestModel(actionSeq = 7, volume = 2.5)

        assertThat(model.actionSeq).isEqualTo(7)
        assertThat(model.volume).isEqualTo(2.5)
        assertThat(model).isInstanceOf(CarelevoUseCaseRequest::class.java)
    }

    @Test
    fun `StartImmeBolusInfusionRequestModel has value equality and a matching hashCode`() {
        val a = StartImmeBolusInfusionRequestModel(actionSeq = 7, volume = 2.5)
        val b = StartImmeBolusInfusionRequestModel(actionSeq = 7, volume = 2.5)

        assertThat(a).isEqualTo(b)
        assertThat(a.hashCode()).isEqualTo(b.hashCode())
    }

    @Test
    fun `StartImmeBolusInfusionRequestModel differs when any field differs`() {
        val base = StartImmeBolusInfusionRequestModel(actionSeq = 7, volume = 2.5)

        assertThat(base).isNotEqualTo(base.copy(actionSeq = 8))
        assertThat(base).isNotEqualTo(base.copy(volume = 2.6))
    }

    @Test
    fun `StartImmeBolusInfusionRequestModel copy keeps untouched fields and destructures in order`() {
        val bumped = StartImmeBolusInfusionRequestModel(actionSeq = 7, volume = 2.5).copy(actionSeq = 8)

        val (actionSeq, volume) = bumped
        assertThat(actionSeq).isEqualTo(8)
        assertThat(volume).isEqualTo(2.5)
        assertThat(bumped.toString()).contains("actionSeq=8")
    }

    // ---------- StartExtendBolusInfusionRequestModel ----------

    @Test
    fun `StartExtendBolusInfusionRequestModel exposes its volume and minutes`() {
        val model = StartExtendBolusInfusionRequestModel(volume = 3.0, minutes = 90)

        assertThat(model.volume).isEqualTo(3.0)
        assertThat(model.minutes).isEqualTo(90)
        assertThat(model).isInstanceOf(CarelevoUseCaseRequest::class.java)
    }

    @Test
    fun `StartExtendBolusInfusionRequestModel has value equality and a matching hashCode`() {
        val a = StartExtendBolusInfusionRequestModel(volume = 3.0, minutes = 90)
        val b = StartExtendBolusInfusionRequestModel(volume = 3.0, minutes = 90)

        assertThat(a).isEqualTo(b)
        assertThat(a.hashCode()).isEqualTo(b.hashCode())
    }

    @Test
    fun `StartExtendBolusInfusionRequestModel differs when any field differs`() {
        val base = StartExtendBolusInfusionRequestModel(volume = 3.0, minutes = 90)

        assertThat(base).isNotEqualTo(base.copy(volume = 3.5))
        assertThat(base).isNotEqualTo(base.copy(minutes = 120))
    }

    @Test
    fun `StartExtendBolusInfusionRequestModel copy keeps untouched fields and destructures in order`() {
        val extended = StartExtendBolusInfusionRequestModel(volume = 3.0, minutes = 90).copy(minutes = 120)

        val (volume, minutes) = extended
        assertThat(volume).isEqualTo(3.0)
        assertThat(minutes).isEqualTo(120)
        assertThat(extended.toString()).contains("minutes=120")
    }

    @Test
    fun `the two request models are never equal to each other despite both carrying a volume`() {
        val imme = StartImmeBolusInfusionRequestModel(actionSeq = 90, volume = 3.0)
        val extend = StartExtendBolusInfusionRequestModel(volume = 3.0, minutes = 90)

        assertThat(imme).isNotEqualTo(extend)
    }

    // ---------- CancelBolusInfusionResponseModel ----------

    @Test
    fun `CancelBolusInfusionResponseModel exposes the pump reported infused amount`() {
        val model = CancelBolusInfusionResponseModel(infusedAmount = 1.35)

        assertThat(model.infusedAmount).isEqualTo(1.35)
        assertThat(model).isInstanceOf(CarelevoUseCaseResponse::class.java)
    }

    @Test
    fun `CancelBolusInfusionResponseModel has value equality and a matching hashCode`() {
        val a = CancelBolusInfusionResponseModel(infusedAmount = 1.35)
        val b = CancelBolusInfusionResponseModel(infusedAmount = 1.35)

        assertThat(a).isEqualTo(b)
        assertThat(a.hashCode()).isEqualTo(b.hashCode())
        assertThat(a).isNotEqualTo(b.copy(infusedAmount = 1.4))
    }

    @Test
    fun `CancelBolusInfusionResponseModel carries a zero infused amount for a bolus cancelled before delivery`() {
        val model = CancelBolusInfusionResponseModel(infusedAmount = 0.0)

        val (infusedAmount) = model
        assertThat(infusedAmount).isEqualTo(0.0)
        assertThat(model.toString()).contains("infusedAmount=0.0")
    }

    // ---------- StartImmeBolusInfusionResponseModel ----------

    @Test
    fun `StartImmeBolusInfusionResponseModel exposes the expected completion seconds`() {
        val model = StartImmeBolusInfusionResponseModel(expectSec = 42)

        assertThat(model.expectSec).isEqualTo(42)
        assertThat(model).isInstanceOf(CarelevoUseCaseResponse::class.java)
    }

    @Test
    fun `StartImmeBolusInfusionResponseModel has value equality and a matching hashCode`() {
        val a = StartImmeBolusInfusionResponseModel(expectSec = 42)
        val b = StartImmeBolusInfusionResponseModel(expectSec = 42)

        assertThat(a).isEqualTo(b)
        assertThat(a.hashCode()).isEqualTo(b.hashCode())
        assertThat(a).isNotEqualTo(b.copy(expectSec = 43))
    }

    @Test
    fun `StartImmeBolusInfusionResponseModel copy and destructuring round trip the expected seconds`() {
        val model = StartImmeBolusInfusionResponseModel(expectSec = 42).copy(expectSec = 0)

        val (expectSec) = model
        assertThat(expectSec).isEqualTo(0)
        assertThat(model.toString()).contains("expectSec=0")
    }
}
