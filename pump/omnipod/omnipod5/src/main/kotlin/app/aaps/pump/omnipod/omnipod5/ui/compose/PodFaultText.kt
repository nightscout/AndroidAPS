package app.aaps.pump.omnipod.omnipod5.ui.compose

import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.pump.omnipod.common.bledriver.pod.definition.PdmFaultCategory
import app.aaps.pump.omnipod.omnipod5.R

/** Omnipod 5 wording for the fault notification. Each template already contains its own Ref line. */
fun PdmFaultCategory.notificationText(rh: ResourceHelper, ref: String): String {
    val templateRes = when (this) {
        PdmFaultCategory.RESERVOIR_EMPTY -> R.string.omnipod_o5_pdm_notification_reservoir_empty
        PdmFaultCategory.AUTO_OFF        -> R.string.omnipod_o5_pdm_notification_auto_off
        PdmFaultCategory.POD_EXPIRED     -> R.string.omnipod_o5_pdm_notification_pod_expired
        PdmFaultCategory.OCCLUDED        -> R.string.omnipod_o5_pdm_notification_occluded
        PdmFaultCategory.POD_ERROR       -> R.string.omnipod_o5_pdm_notification_pod_error
    }
    return rh.gs(templateRes, ref)
}
