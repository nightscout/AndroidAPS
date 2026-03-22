package app.aaps.pump.eopatch.core.ble

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.eopatch.core.ble.IPatchPacketConstant.Companion.BIG_API
import app.aaps.pump.eopatch.core.ble.IPatchPacketConstant.Companion.CIPHER
import app.aaps.pump.eopatch.core.ble.IPatchPacketConstant.Companion.LEGACY
import app.aaps.pump.eopatch.core.define.IPatchConstant
import app.aaps.pump.eopatch.core.response.BaseResponse
import app.aaps.pump.eopatch.core.scan.IBleDevice
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import java.nio.ByteBuffer

abstract class BaseAPI<T : BaseResponse>(
    protected var func: PatchFunc,
    private val patch: IBleDevice,
    private val aapsLogger: AAPSLogger
) : IPatchConstant {

    private val responseSubject: PublishSubject<T> = PublishSubject.create()
    @Volatile private var retryCount = API_RETRY
    protected var queue: IAPIQueue = APIQueue()

    fun observeResponse(): Observable<T> = responseSubject.hide()

    protected fun writeAndRead(write: ByteArray): Single<T> =
        queue.getTurn(func)
            .doOnNext { retryCount = API_RETRY }
            .concatMapSingle { _ ->
                patch.writeAndRead(write, func)
                    .map { bytes -> read(bytes) }
                    .retryWhen { errors -> handleAPINotMatchException(errors) }
            }
            .retry(API_RETRY.toLong())
            .firstOrError()
            .observeOn(Schedulers.single())
            .doOnSuccess { onResponse(it) }

    private fun handleAPINotMatchException(errors: Flowable<Throwable>): Flowable<Int> =
        errors.concatMap { error ->
            if (error is APINotMatchException && retryCount > 0) {
                retryCount--
                Flowable.just(0)
            } else {
                Flowable.error(error)
            }
        }

    private fun read(bytes: ByteArray): T {
        if (!match(bytes)) {
            throw APINotMatchException("API does not match w:${bytes[FUNC0]} : r:${bytes[FUNC0 + 1]}")
        }
        return parse(bytes)
    }

    private fun onResponse(response: T) = responseSubject.onNext(response)

    private fun match(input: ByteArray): Boolean = try {
        val bytes = getBytes(func)
        bytes.indices.all { i -> bytes[i] == input[i + 2] }
    } catch (e: Exception) {
        aapsLogger.error(LTag.PUMPCOMM, "API does not match")
        false
    }

    protected open fun generate(): ByteArray = allocate().build()

    protected fun allocate(size: Int = DEFAULT_BLE_MTU_SIZE) = PacketBuilder(size)

    private fun getBytes(func: PatchFunc): ByteArray {
        val (b0, b1) = when (func) {
            PatchFunc.REQUEST_BONDING           -> LEGACY to 0x0B.toByte()
            PatchFunc.SET_KEY                   -> LEGACY to 0x11.toByte()
            PatchFunc.SET_GLOBAL_TIME           -> LEGACY to 0x20.toByte()
            PatchFunc.GET_GLOBAL_TIME           -> LEGACY to 0x21.toByte()
            PatchFunc.UPDATE_CONNECTION         -> LEGACY to 0x22.toByte()
            PatchFunc.GET_PUMP_DURATION         -> LEGACY to 0x34.toByte()
            PatchFunc.SET_LOW_RESERVOIR         -> LEGACY to 0x3A.toByte()
            PatchFunc.GET_VOLTAGE_B4_PRIMING    -> LEGACY to 0x53.toByte()
            PatchFunc.STOP_BUZZER               -> LEGACY to 0x60.toByte()
            PatchFunc.SET_INFO_REMINDER         -> LEGACY to 0x63.toByte()
            PatchFunc.GET_TEMPERATURE           -> LEGACY to 0x71.toByte()
            PatchFunc.SET_BASAL_SCHEDULE        -> BIG_API to 0x82.toByte()
            PatchFunc.RESUME_NORMAL_BASAL       -> LEGACY to 0x84.toByte()
            PatchFunc.PAUSE_BASAL               -> LEGACY to 0x85.toByte()
            PatchFunc.STOP_BASAL                -> LEGACY to 0x86.toByte()
            PatchFunc.GET_BASAL_HISTORY_EX      -> BIG_API to 0x8A.toByte()
            PatchFunc.GET_BASAL_HISTORY_INDEX   -> LEGACY to 0x8B.toByte()
            PatchFunc.START_TEMP_BASAL          -> LEGACY to 0x91.toByte()
            PatchFunc.STOP_TEMP_BASAL           -> LEGACY to 0x94.toByte()
            PatchFunc.GET_TEMP_BASAL_FINISH_TIME -> LEGACY to 0x97.toByte()
            PatchFunc.GET_TEMP_BASAL_HISTORY_EX -> BIG_API to 0x9A.toByte()
            PatchFunc.START_NOW_BOLUS           -> LEGACY to 0xB0.toByte()
            PatchFunc.STOP_BOLUS                -> LEGACY to 0xB3.toByte()
            PatchFunc.START_EXT_BOLUS           -> LEGACY to 0xB6.toByte()
            PatchFunc.GET_BOLUS_FINISH_TIME     -> LEGACY to 0xBD.toByte()
            PatchFunc.STOP_COMBO_BOLUS          -> LEGACY to 0xBE.toByte()
            PatchFunc.START_COMBO_BOLUS         -> LEGACY to 0xBF.toByte()
            PatchFunc.DEACTIVATE                -> LEGACY to 0xC0.toByte()
            PatchFunc.START_PRIMING             -> LEGACY to 0xC5.toByte()
            PatchFunc.GET_INTERNAL_SUSPENDED_TIME -> LEGACY to 0xC7.toByte()
            PatchFunc.START_NEEDLE_CHECK        -> LEGACY to 0xC8.toByte()
            PatchFunc.GET_FIRMWARE_VERSION      -> LEGACY to 0xE5.toByte()
            PatchFunc.GET_WAKE_UP_TIME          -> LEGACY to 0xE6.toByte()
            PatchFunc.GET_SERIAL_NUMBER         -> LEGACY to 0xE9.toByte()
            PatchFunc.GET_LOT_NUMBER            -> LEGACY to 0xEA.toByte()
            PatchFunc.GET_MODEL_NAME            -> LEGACY to 0xD7.toByte()
            PatchFunc.GET_AE_CODES              -> LEGACY to 0xEC.toByte()
            PatchFunc.STOP_AE_BEEP              -> LEGACY to 0xED.toByte()
            PatchFunc.SET_PUBLIC_KEY            -> CIPHER to 0x10.toByte()
            PatchFunc.GET_PUBLIC_KEY            -> CIPHER to 0x11.toByte()
            PatchFunc.GET_SEQ_NUM               -> CIPHER to 0xA2.toByte()
        }
        return byteArrayOf(b0, b1)
    }

    protected abstract fun parse(bytes: ByteArray): T

    protected inner class PacketBuilder(size: Int) {

        private val buffer: ByteBuffer = ByteBuffer.allocate(size)
            .put(0.toByte())
            .put(0.toByte())
            .put(getBytes(func))

        fun putInt(i: Int) = apply { buffer.putInt(i) }
        fun putShort(s: Short) = apply { buffer.putShort(s) }
        fun putShort(s: Int) = apply { buffer.putShort(s.toShort()) }
        fun putByte(b: Byte) = apply { buffer.put(b) }
        fun putByte(b: Int) = apply { buffer.put(b.toByte()) }
        fun putBytes(bytes: ByteArray) = apply { buffer.put(bytes) }
        fun putBoolean(flag: Boolean) = apply { buffer.put(if (flag) 1.toByte() else 0.toByte()) }
        fun reserved() = apply { buffer.put(0.toByte()) }
        fun build(): ByteArray = buffer.array()
    }

    companion object {

        const val NOOP0 = 0
        const val NOOP1 = 1
        const val FUNC0 = 2
        const val FUNC1 = 3
        const val DATA0 = 4
        const val DATA1 = 5
        const val DATA2 = 6
        const val DATA3 = 7
        const val DATA4 = 8
        const val DATA5 = 9
        const val DATA6 = 10
        const val DATA7 = 11
        const val DATA8 = 12
        const val DATA9 = 13

        const val RESERVED: Byte = 0x00

        private const val API_RETRY = 2
        private const val DEFAULT_BLE_MTU_SIZE = 21
    }
}
