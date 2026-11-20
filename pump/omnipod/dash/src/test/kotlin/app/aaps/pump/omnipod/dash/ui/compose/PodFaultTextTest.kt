package app.aaps.pump.omnipod.dash.ui.compose

import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.pump.omnipod.common.bledriver.pod.definition.PdmFaultCategory
import app.aaps.pump.omnipod.dash.R
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import app.aaps.pump.omnipod.common.R as CommonR

internal class PodFaultTextTest {

    @Test
    fun eachFaultCategoryShowsItsMessageAndReference() {
        val rh = mock<ResourceHelper>()
        val ref = "ref"
        val resources = mapOf(
            PdmFaultCategory.RESERVOIR_EMPTY to R.string.omnipod_dash_pdm_notification_reservoir_empty,
            PdmFaultCategory.AUTO_OFF to R.string.omnipod_dash_pdm_notification_auto_off,
            PdmFaultCategory.POD_EXPIRED to R.string.omnipod_dash_pdm_notification_pod_expired,
            PdmFaultCategory.OCCLUDED to R.string.omnipod_dash_pdm_notification_occluded,
            PdmFaultCategory.POD_ERROR to R.string.omnipod_dash_pdm_notification_pod_error
        )
        resources.values.forEach { resource ->
            whenever(rh.gs(resource)).thenReturn("Fault $resource")
        }
        whenever(rh.gs(CommonR.string.omnipod_common_pdm_ref, ref)).thenReturn("Ref: $ref")

        resources.forEach { (category, resource) ->
            assertThat(category.notificationText(rh, ref)).isEqualTo("Fault $resource\n\nRef: $ref")
        }
    }
}
