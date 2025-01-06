package app.aaps.pump.insight.satl

import app.aaps.pump.insight.utils.ByteBuf
import java.util.Calendar

class KeyRequest : SatlMessage() {

    private lateinit var randomBytes: ByteArray
    private lateinit var preMasterKey: ByteArray
    override val data: ByteBuf
        get() {
            val byteBuf = ByteBuf(288)
            byteBuf.putBytes(randomBytes)
            byteBuf.putUInt32LE(translateDate().toLong())
            byteBuf.putBytes(preMasterKey)
            return byteBuf
        }

    fun setRandomBytes(randomBytes: ByteArray) {
        this.randomBytes = randomBytes
    }

    fun setPreMasterKey(preMasterKey: ByteArray) {
        this.preMasterKey = preMasterKey
    }

    companion object {

        private fun translateDate(): Int {
            val calendar = Calendar.getInstance()
            val second = calendar[Calendar.SECOND]
            val minute = calendar[Calendar.MINUTE]
            val hour = calendar[Calendar.HOUR_OF_DAY]
            val day = calendar[Calendar.DAY_OF_MONTH]
            val month = calendar[Calendar.MONTH]
            val year = calendar[Calendar.YEAR]
            return year % 100 and 0x3f shl 26 or (month and 0x0f shl 22) or (day and 0x1f shl 17) or (hour and 0x1f shl 12) or (minute and 0x3f shl 6) or (second and 0x3f)
        }
    }
}