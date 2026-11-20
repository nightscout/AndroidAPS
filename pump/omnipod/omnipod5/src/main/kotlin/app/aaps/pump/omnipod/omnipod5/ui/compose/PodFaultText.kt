package app.aaps.pump.omnipod.omnipod5.ui.compose

import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.pump.omnipod.common.bledriver.pod.definition.PdmFaultCategory
import app.aaps.pump.omnipod.omnipod5.R
import app.aaps.pump.omnipod.common.R as CommonR

/** Omnipod 5 wording for the fault notification. */
fun PdmFaultCategory.notificationText(rh: ResourceHelper, ref: String): String {
    val templateRes = when (this) {
        PdmFaultCategory.RESERVOIR_EMPTY -> R.string.omnipod_o5_pdm_notification_reservoir_empty
        PdmFaultCategory.AUTO_OFF        -> R.string.omnipod_o5_pdm_notification_auto_off
        PdmFaultCategory.POD_EXPIRED     -> R.string.omnipod_o5_pdm_notification_pod_expired
        PdmFaultCategory.OCCLUDED        -> R.string.omnipod_o5_pdm_notification_occluded
        PdmFaultCategory.POD_ERROR       -> R.string.omnipod_o5_pdm_notification_pod_error
    }
    val message = rh.gs(templateRes)
    return when (this) {
        PdmFaultCategory.POD_EXPIRED, PdmFaultCategory.POD_ERROR ->
            message + "\n\n" + rh.gs(CommonR.string.omnipod_common_pdm_ref, ref)
        else -> message
    }
}
