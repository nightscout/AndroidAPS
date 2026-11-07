package app.aaps.pump.aaruy.comm

import no.nordicsemi.android.support.v18.scanner.ScanResult

enum class BleStatus {
    CONNECTED,
    DISCONNECTED,
    CONNECTING
}

/** What the pump reports back to the driver. */
interface AaruyBleListener {

    fun onBleStatusChanged(status: BleStatus)
    fun onReceiveBasicParamSetting(basicParam: BasicParam)
    fun onReceiveBasalSetting(basalInfo: BasalInfo)
    fun onReceiveCurrentStatus(statusInfo: StatusInfo)
    fun onReceiveHistoryLog(logInfo: LogInfo)
    fun onReceiveHistoryDaily(dailyInfo: DailyInfo)
    fun onReceiveRealData(battery: Int, signal: Byte)
    fun onReceiveErrorData(errorType: Int, error: Int)
    fun onReceiveConfirmWarn(confirm: Int)
    fun onRequestResult(success: Boolean, paramId: Int)
    fun onBolusValueTooHigh(value: Double, maxValue: Double)
    fun onInjectTimeError(time: Int)
    fun onRunBasalFirst(currentState: Int)
    fun onRunReplaceFirst(currentState: Int)
    fun onRunTempBasalFirst(currentState: Int)
    fun onBolusIsRunning(currentState: Int)
    fun onLiquidShow()
    fun onCurrentNoSuspend(currentState: Int)
    fun onUnbindClearInfo()
    fun onPumpClosedMode(currentMode: Int)
}

fun interface ScanResultListener {

    fun onScanResult(results: List<ScanResult>)
}
