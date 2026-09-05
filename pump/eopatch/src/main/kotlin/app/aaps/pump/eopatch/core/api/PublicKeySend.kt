package app.aaps.pump.eopatch.core.api

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.eopatch.core.ble.BaseAPI
import app.aaps.pump.eopatch.core.ble.PatchFunc
import app.aaps.pump.eopatch.core.response.KeyResponse
import app.aaps.pump.eopatch.core.scan.IBleDevice
import io.reactivex.rxjava3.core.Single
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.SingleIn

@SingleIn(AppScope::class)
class PublicKeySend @Inject constructor(patch: IBleDevice, aapsLogger: AAPSLogger) : BaseAPI<KeyResponse>(PatchFunc.SET_PUBLIC_KEY, patch, aapsLogger) {
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
