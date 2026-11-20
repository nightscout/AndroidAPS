package app.aaps.pump.ap550s.database.model

import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.pump.ap550s.defs.ConnectStatus
import app.aaps.pump.ap550s.defs.PumpDeviceState
import app.aaps.pump.ap550s.defs.TargetDevice
import org.joda.time.LocalDateTime

class ConnectHistoryItem {
    var dateTime: LocalDateTime? = null
    protected var connectStatus: ConnectStatus? = null
    protected var pumpDeviceState: PumpDeviceState? = null
    protected var targetDevice: TargetDevice? = null
    protected var connectCommand: String? = null

    var source: ConnectHistoryItemSource? = null

    constructor(connectStatus: ConnectStatus, pumpDeviceState: PumpDeviceState){
        this.dateTime = LocalDateTime.now()
        this.connectStatus = connectStatus
        this.pumpDeviceState = pumpDeviceState
    }

    constructor(connectStatus: ConnectStatus){
        this.dateTime = LocalDateTime.now()
        this.connectStatus = connectStatus
        this.targetDevice = TargetDevice.Ap550s
        this.source = ConnectHistoryItemSource.AP550S
    }

    constructor(connectCommand: String){
        this.dateTime = LocalDateTime.now()
        this.source = ConnectHistoryItemSource.AP550SCommand
        this.connectCommand = connectCommand
    }

    fun getDescription(rh: ResourceHelper): String {
        when(this.source){
            ConnectHistoryItemSource.AP550S -> {
                return "${connectStatus?.sourceId?.let { rh.gs(it) }}"
            }
            ConnectHistoryItemSource.AP550SCommand -> {
                return ("$connectCommand")
            }
            else -> {
                return "Unknown Description"
            }
        }
    }


    enum class ConnectHistoryItemSource(val desc: String) {
        AP550S("ap550s"),
        AP550SCommand("command");
    }


    class Comparator : java.util.Comparator<ConnectHistoryItem?> {
        override fun compare(p0: ConnectHistoryItem?, p1: ConnectHistoryItem?): Int {
            return p1?.dateTime?.compareTo(p0?.dateTime) ?: 0
        }
    }

}