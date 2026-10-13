package app.aaps.pump.common.driver.refresh

interface PumpDataRefreshCapable {
    fun getRefreshTime(pumpDataRefreshType: PumpDataRefreshType): Int
    fun isInPreventConnectMode(): Boolean
}