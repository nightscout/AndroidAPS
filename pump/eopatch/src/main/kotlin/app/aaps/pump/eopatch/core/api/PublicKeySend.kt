package app.aaps.pump.eopatch.core.api

import app.aaps.pump.eopatch.core.ble.BaseAPI
import app.aaps.pump.eopatch.core.ble.PatchFunc
import app.aaps.pump.eopatch.core.response.KeyResponse
import io.reactivex.rxjava3.core.Single

class PublicKeySend : BaseAPI<KeyResponse>(PatchFunc.SET_PUBLIC_KEY) {
    override fun parse(bytes: ByteArray): KeyResponse {
        val dest = ByteArray(KEY_SIZE)
        System.arraycopy(bytes, DATA1, dest, 0, KEY_SIZE)
        return KeyResponse.create(dest, bytes[DATA0].toInt())
    }

    fun send(key: ByteArray): Single<KeyResponse> = writeAndRead(allocate(70).putBytes(key).build())

    companion object {
        private const val KEY_SIZE = 64
    }
}
