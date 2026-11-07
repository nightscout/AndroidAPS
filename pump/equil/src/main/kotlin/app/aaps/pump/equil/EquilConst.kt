package app.aaps.pump.equil

object EquilConst {

    const val EQUIL_CMD_TIME_OUT: Long = 300000
    const val EQUIL_BLE_WRITE_TIME_OUT: Long = 20
    const val EQUIL_BLE_NEXT_CMD: Long = 500

    /**
     * Standard bolus duration: 40 s per unit = 1.5 U/min. The user can choose a faster one
     * (EquilIntPreferenceKey.EquilBolusSpeed). Measured on a real pump: 12 U in 477 s.
     */
    const val EQUIL_BOLUS_SECONDS_PER_UNIT_STANDARD = 40

    /** How long the scan for the pump's advertisement may run when the pump address is known. */
    const val EQUIL_ADVERT_SCAN_TIME_OUT: Long = 30000
    const val EQUIL_SUPPORT_LEVEL = 5.3f
    const val EQUIL_BOLUS_THRESHOLD_STEP = 1600
    const val EQUIL_BASAL_THRESHOLD_STEP = 240
    const val EQUIL_STEP_MAX = 32000
    const val EQUIL_STEP_FILL = 160
    const val EQUIL_STEP_AIR = 120
}
