package app.aaps.pump.eopatch.core.noti

import app.aaps.core.interfaces.logging.AAPSLogger

class AlarmNotification(bytes: ByteArray, aapsLogger: AAPSLogger) : BaseNotification(bytes, aapsLogger) {

    val curIndex: Int
    val lastFinishedIndex: Int
    val patchState: ByteArray = ByteArray(SIZE)

    init {
        System.arraycopy(bytes, 0, patchState, 0, SIZE)
        buffer.position(INDEX_POSITION)
        curIndex = buffer.short.toInt()
        lastFinishedIndex = if (curIndex > 0 && curIndex != 0xFFFF) curIndex - 1 else -1
    }

    companion object {
        private const val SIZE = 20
        private const val INDEX_POSITION = 100
    }
}
