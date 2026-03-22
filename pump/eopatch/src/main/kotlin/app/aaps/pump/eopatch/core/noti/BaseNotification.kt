package app.aaps.pump.eopatch.core.noti

import android.util.Log
import java.nio.ByteBuffer

abstract class BaseNotification(bytes: ByteArray) {

    protected val buffer: ByteBuffer = ByteBuffer.wrap(bytes)

    val sB_CNT: Int
    val eB_CNT: Int
    val basal_CNT: Int

    init {
        buffer.position(SB_CNT_POSITION)
        sB_CNT = buffer.short.toInt() and 0xFFFF
        eB_CNT = buffer.short.toInt() and 0xFFFF
        basal_CNT = buffer.short.toInt() and 0xFFFF
        Log.i(
            "EOPATCH_CORE",
            "updateInjected $totalInjected (SB:$sB_CNT, EB:$eB_CNT, Basal:$basal_CNT clazz:${javaClass.simpleName})"
        )
        buffer.rewind()
    }

    val totalInjected: Int get() = basal_CNT + sB_CNT + eB_CNT

    companion object {
        private const val SB_CNT_POSITION = 26
    }
}
