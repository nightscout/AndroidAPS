package info.nightscout.androidaps.plugins.pump.omnipod.dash.history.data

sealed class Record

data class BolusRecord(val amout: Double, val bolusType: BolusType) : Record()

data class TempBasalRecord(val duration: Long, val rate: Double) : Record()

enum class BolusType {
    DEFAULT, SMB
}
