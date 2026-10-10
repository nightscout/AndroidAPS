package app.aaps.pump.eopatch.core.ble

import io.reactivex.rxjava3.core.Single

interface ICipher {

    fun updateEncryptionParam(sharedKey: ByteArray)
    fun encrypt(bytes: ByteArray, function: PatchFunc): Single<ByteArray>
    fun decrypt(bytes: ByteArray, function: PatchFunc): Single<ByteArray>
    fun onPacketSent(bytes: ByteArray, function: PatchFunc)
    fun setSeq(seq: Int)
    fun getSequence(): Int

    companion object {

        const val ENC_START_INDEX = 1
        const val CRC_START_INDEX = 2
        const val ENC_END_INDEX = 226
        const val SEQ2525 = 0x2525
    }
}
