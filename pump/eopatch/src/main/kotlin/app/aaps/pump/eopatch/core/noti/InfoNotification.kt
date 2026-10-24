package app.aaps.pump.eopatch.core.noti

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.eopatch.core.code.BolusType

class InfoNotification(bytes: ByteArray, aapsLogger: AAPSLogger) : BaseNotification(bytes, aapsLogger) {

    val act = ByteArray(IDX_COUNT)
    val `in` = IntArray(IDX_COUNT)
    val target = IntArray(IDX_COUNT)

    init {
        buffer.get() // skip
        buffer.get() // skip
        for (i in 0 until IDX_COUNT) {
            act[i] = buffer.get()
            `in`[i] = buffer.short.toInt() and 0xFFFF
            target[i] = buffer.short.toInt() and 0xFFFF
        }
    }

    fun getInjected(type: BolusType): Int = when (type) {
        BolusType.NOW  -> `in`[NOW_IDX]
        BolusType.EXT  -> `in`[EXT_IDX]
        BolusType.COMBO -> `in`[NOW_IDX] + `in`[EXT_IDX]
    }

    fun getRemain(type: BolusType): Int = when (type) {
        BolusType.NOW  -> target[NOW_IDX] - `in`[NOW_IDX]
        BolusType.EXT  -> target[EXT_IDX] - `in`[EXT_IDX]
        BolusType.COMBO -> (target[NOW_IDX] + target[EXT_IDX]) - (`in`[NOW_IDX] + `in`[EXT_IDX])
    }

    val isBolusRegAct: Boolean get() = isBolusRegAct(BolusType.COMBO)

    fun isBolusRegAct(type: BolusType): Boolean = when (type) {
        BolusType.NOW  -> act[NOW_IDX] > ACT_EMPTY
        BolusType.EXT  -> act[EXT_IDX] > ACT_EMPTY
        BolusType.COMBO -> act[NOW_IDX] + act[EXT_IDX] > ACT_EMPTY
    }

    fun isBolusDone(type: BolusType): Boolean = when (type) {
        BolusType.NOW  -> act[NOW_IDX] == ACT_FINISHED
        BolusType.EXT  -> act[EXT_IDX] == ACT_FINISHED
        BolusType.COMBO -> act[NOW_IDX] == ACT_FINISHED || act[EXT_IDX] == ACT_FINISHED
    }

    fun isBolusDone(): Boolean = act[NOW_IDX] == ACT_FINISHED || act[EXT_IDX] == ACT_FINISHED

    companion object {
        private const val ACT_EMPTY: Byte = 0
        private const val ACT_FINISHED: Byte = 2
        private const val NOW_IDX = 0
        private const val EXT_IDX = 1
        private const val IDX_COUNT = 2
    }
}
