package app.aaps.pump.eopatch.core.api

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.eopatch.core.ble.BaseAPI
import app.aaps.pump.eopatch.core.ble.PatchFunc
import app.aaps.pump.eopatch.core.response.KeyResponse
import app.aaps.pump.eopatch.core.scan.IBleDevice
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.reactivex.rxjava3.core.Single

@SingleIn(AppScope::class)
@Inject
class SequenceGet(patch: IBleDevice, aapsLogger: AAPSLogger) : BaseAPI<KeyResponse>(PatchFunc.GET_SEQ_NUM, patch, aapsLogger) {
    override fun parse(bytes: ByteArray): KeyResponse = KeyResponse.create(bytes[SEQ_INDEX], bytes[SEQ_INDEX + 1])
    fun get(): Single<KeyResponse> = writeAndRead(generate())

    companion object {
        private const val SEQ_INDEX = 37
    }
}
