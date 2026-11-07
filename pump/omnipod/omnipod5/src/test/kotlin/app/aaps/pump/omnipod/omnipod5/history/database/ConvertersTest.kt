package app.aaps.pump.omnipod.omnipod5.history.database

import app.aaps.pump.omnipod.common.bledriver.pod.definition.BolusType
import app.aaps.pump.omnipod.common.history.data.InitialResult
import app.aaps.pump.omnipod.common.history.data.ResolvedResult
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun `old basal drift history reads as a shared basal correction`() {
        assertThat(converters.toBolusType("BASAL_DRIFT_COMPENSATION")).isEqualTo(BolusType.BASAL_CORRECTION)
        assertThat(converters.toBolusType("BASAL_CORRECTION")).isEqualTo(BolusType.BASAL_CORRECTION)
    }

    @Test
    fun `basal corrections keep the old stored name`() {
        assertThat(converters.fromBolusType(BolusType.BASAL_CORRECTION)).isEqualTo("BASAL_DRIFT_COMPENSATION")
    }

    @Test
    fun `every shared bolus type round trips`() {
        BolusType.entries.forEach { type ->
            assertThat(converters.toBolusType(converters.fromBolusType(type))).isEqualTo(type)
            if (type != BolusType.BASAL_CORRECTION) {
                assertThat(converters.fromBolusType(type)).isEqualTo(type.name)
            }
        }
    }

    @Test
    fun `null bolus types stay null`() {
        assertThat(converters.toBolusType(null)).isNull()
        assertThat(converters.fromBolusType(null)).isNull()
    }

    @Test
    fun `unknown bolus types are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { converters.toBolusType("UNKNOWN") }
    }

    @Test
    fun `shared result states keep their stored names`() {
        InitialResult.entries.forEach { result ->
            assertThat(converters.fromInitialResult(result)).isEqualTo(result.name)
            assertThat(converters.toInitialResult(result.name)).isEqualTo(result)
        }
        ResolvedResult.entries.forEach { result ->
            assertThat(converters.fromResolvedResult(result)).isEqualTo(result.name)
            assertThat(converters.toResolvedResult(result.name)).isEqualTo(result)
        }
        assertThat(converters.toResolvedResult(null)).isNull()
        assertThat(converters.fromResolvedResult(null)).isNull()
    }
}
