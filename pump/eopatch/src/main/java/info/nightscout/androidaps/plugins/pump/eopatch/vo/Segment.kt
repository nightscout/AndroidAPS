package info.nightscout.androidaps.plugins.pump.eopatch.vo

internal class JoinedSegment(var index: Int) {

    var no = 0

    var startMinute = 0
    var endMinute = 0


    //BasalSegment
    var doseUnitPerHour = 0f

    init {
        startMinute = index * SegmentEntity.TIME_BASE
        endMinute = startMinute + SegmentEntity.TIME_BASE
    }
}
