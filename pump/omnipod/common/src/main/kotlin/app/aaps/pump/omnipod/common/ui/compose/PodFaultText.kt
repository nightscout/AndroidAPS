package app.aaps.pump.omnipod.common.ui.compose

import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.pump.omnipod.common.R
import app.aaps.pump.omnipod.common.bledriver.pod.definition.PdmFaultCategory

/** Uses the Dash PDM wording and reference format for both Dash and O5 faults. */
fun PdmFaultCategory.notificationText(rh: ResourceHelper, ref: String): String {
    val (headerRes, textRes) = when (this) {
        PdmFaultCategory.RESERVOIR_EMPTY -> R.string.omnipod_common_pdm_notification_header_reservoir_empty to
            R.string.omnipod_common_pdm_notification_text_reservoir_empty
        PdmFaultCategory.AUTO_OFF        -> R.string.omnipod_common_pdm_notification_header_auto_off to
            R.string.omnipod_common_pdm_notification_text_auto_off
        PdmFaultCategory.POD_EXPIRED     -> R.string.omnipod_common_pdm_notification_header_pod_expired to
            R.string.omnipod_common_pdm_notification_text_pod_expired
        PdmFaultCategory.OCCLUDED        -> R.string.omnipod_common_pdm_notification_header_occluded to
            R.string.omnipod_common_pdm_notification_text_occluded
        PdmFaultCategory.POD_ERROR       -> R.string.omnipod_common_pdm_notification_header_pod_error to
            R.string.omnipod_common_pdm_notification_text_pod_error
    }
    return listOf(rh.gs(headerRes), rh.gs(textRes), "", rh.gs(R.string.omnipod_common_pdm_ref, ref)).joinToString("\n")
}
