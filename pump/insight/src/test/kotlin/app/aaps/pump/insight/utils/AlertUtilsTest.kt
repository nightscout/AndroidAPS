package app.aaps.pump.insight.utils

import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.pump.insight.descriptors.Alert
import app.aaps.pump.insight.descriptors.AlertCategory
import app.aaps.pump.insight.descriptors.AlertType
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** Covers [AlertUtils]: the code/title/description/icon lookups across every AlertType / AlertCategory. */
class AlertUtilsTest {

    private val rh: ResourceHelper = mock<ResourceHelper>().apply {
        whenever(gs(anyInt())).thenReturn("s")
        whenever(gs(anyInt(), anyVararg())).thenReturn("s")
    }
    private val sut = AlertUtils(rh)

    @Test
    fun codeAndTitle_resolveForEveryAlertType() {
        AlertType.entries.forEach {
            assertThat(sut.getAlertCode(it)).isEqualTo("s")
            assertThat(sut.getAlertTitle(it)).isEqualTo("s")
        }
    }

    @Test
    fun description_handlesNullAndNonNullBranchesForEveryAlertType() {
        // types whose description is intentionally null
        val nullDescriptions = setOf(
            AlertType.REMINDER_01, AlertType.REMINDER_02, AlertType.REMINDER_03,
            AlertType.REMINDER_04, AlertType.WARNING_39
        )
        AlertType.entries.forEach { type ->
            val alert = Alert().apply {
                alertType = type
                tBRAmount = 50
                tBRDuration = 90
                cartridgeAmount = 12.5
                programmedBolusAmount = 2.0
                deliveredBolusAmount = 1.0
            }
            val desc = sut.getAlertDescription(alert)
            if (type in nullDescriptions) assertThat(desc).isNull()
            else assertThat(desc).isEqualTo("s")
        }
    }

    @Test
    fun icon_resolvesForEveryCategory() {
        AlertCategory.entries.forEach { assertThat(sut.getAlertIcon(it)).isNotNull() }
    }
}
