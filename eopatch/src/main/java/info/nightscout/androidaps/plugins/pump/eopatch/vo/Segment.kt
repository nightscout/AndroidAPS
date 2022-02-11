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

internal class JoinedSegments {
    var activeBasal: String
    var mList: Array<JoinedSegment>

    init {
        activeBasal = ""
        mList = Array(DEF_COUNT, { i ->  JoinedSegment(i)})
    }


    fun apply(segments: SegmentsEntity<out SegmentEntity<*>>) {
        var i = 0
        var no = 0

        for (item in segments.list) {
            while (i < DEF_COUNT && item.includes(mList[i])) { // i<mList.length 이 앞에 있어야 함.
                val segment = mList[i++]
                segment.no = no

                item.apply(segment)
            }
            no++
        }
    }

    companion object {
        private val DEF_COUNT = 1440 / SegmentEntity.TIME_BASE
    }
}