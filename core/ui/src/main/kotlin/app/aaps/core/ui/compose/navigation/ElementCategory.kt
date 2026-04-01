package app.aaps.core.ui.compose.navigation

import app.aaps.core.ui.R

enum class ElementCategory(val labelResId: Int = 0) {

    TREATMENT(R.string.overview_treatment_label),
    CGM(R.string.cgm),
    MANAGEMENT(R.string.manage),
    CAREPORTAL(R.string.careportal),
    DEVICE(R.string.device_maintenance),
    BASAL(R.string.basal),
    SYSTEM(0),
    NAVIGATION(0),
    INTERNAL(0)
}
