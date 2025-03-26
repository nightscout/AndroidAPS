package app.aaps.pump.common.hw.rileylink.ble

import android.os.SystemClock
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.utils.pump.ByteUtil
import app.aaps.core.utils.pump.ThreadUtil
import app.aaps.pump.common.hw.rileylink.ble.data.GattAttributes
import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkEncodingType
import app.aaps.pump.common.hw.rileylink.ble.operations.BLECommOperationResult
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * Created by geoff on 5/26/16.
 */
class RFSpyReader internal constructor(private val aapsLogger: AAPSLogger, private val rileyLinkBle: RileyLinkBLE) {

    private var executor = Executors.newSingleThreadExecutor()
    private val waitForRadioData = Semaphore(0, true)
    private val mDataQueue = LinkedBlockingQueue<ByteArray>()
    private var acquireCount = 0
    private var releaseCount = 0
    private var stopAtNull = true
    fun setRileyLinkEncodingType(encodingType: RileyLinkEncodingType) {
        aapsLogger.debug("setRileyLinkEncodingType: $encodingType")
        stopAtNull = !(encodingType == RileyLinkEncodingType.Manchester || encodingType == RileyLinkEncodingType.FourByteSixByteRileyLink)
    }

    // This timeout must be coordinated with the length of the RFSpy radio operation or Bad Things Happen.
    fun poll(timeoutMs: Int): ByteArray? {
        aapsLogger.debug(LTag.PUMPBTCOMM, "${ThreadUtil.sig()}Entering poll at t==${SystemClock.uptimeMillis()}, timeout is $timeoutMs mDataQueue size is ${mDataQueue.size}")
        if (mDataQueue.isEmpty() || timeoutMs == 0) { //0 timeout is used for drain queue in RFSpy.writeToDataRaw before sending new command
            try {
                // block until timeout or data available.
                // returns null if timeout.
                val dataFromQueue = mDataQueue.poll(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
                if (dataFromQueue != null)
                    aapsLogger.debug(LTag.PUMPBTCOMM, "Got data [${ByteUtil.shortHexString(dataFromQueue)}] at t==${SystemClock.uptimeMillis()}")
                else
                    aapsLogger.debug(LTag.PUMPBTCOMM, "Got data [null] at t==" + SystemClock.uptimeMillis())
                return dataFromQueue
            } catch (_: InterruptedException) {
                aapsLogger.error(LTag.PUMPBTCOMM, "poll: Interrupted waiting for data")
            }
        }
        return null
    }

    // Call this from the "response count" notification handler.
    fun newDataIsAvailable() {
        releaseCount++
        aapsLogger.debug(LTag.PUMPBTCOMM, "${ThreadUtil.sig()}waitForRadioData released(count=$releaseCount) at t=${SystemClock.uptimeMillis()}")
        waitForRadioData.release()
    }

    fun start() {
        executor.execute {
            val serviceUUID = UUID.fromString(GattAttributes.SERVICE_RADIO)
            val radioDataUUID = UUID.fromString(GattAttributes.CHARA_RADIO_DATA)
            while (true) {
                try {
                    acquireCount++
                    waitForRadioData.acquire()
                    aapsLogger.debug(LTag.PUMPBTCOMM, "${ThreadUtil.sig()}waitForRadioData acquired (count=$acquireCount) at t=${SystemClock.uptimeMillis()}")
                    SystemClock.sleep(100)
                    var result = rileyLinkBle.readCharacteristicBlocking(serviceUUID, radioDataUUID)
                    SystemClock.sleep(100)
                    if (result.resultCode == BLECommOperationResult.RESULT_SUCCESS) {
                        if (stopAtNull) {
                            // only data up to the first null is valid
                            result.value?.let { resultValue ->
                                for (i in resultValue.indices) {
                                    if (resultValue[i].toInt() == 0) {
                                        result.value = ByteUtil.substring(resultValue, 0, i)
                                        break
                                    }
                                }
                            }
                        }
                        mDataQueue.add(result.value)
                    } else if (result.resultCode == BLECommOperationResult.RESULT_INTERRUPTED)
                        aapsLogger.error(LTag.PUMPBTCOMM, "Read operation was interrupted")
                    else if (result.resultCode == BLECommOperationResult.RESULT_TIMEOUT)
                        aapsLogger.error(LTag.PUMPBTCOMM, "Read operation on Radio Data timed out")
                    else if (result.resultCode == BLECommOperationResult.RESULT_BUSY)
                        aapsLogger.error(LTag.PUMPBTCOMM, "FAIL: RileyLinkBLE reports operation already in progress")
                    else if (result.resultCode == BLECommOperationResult.RESULT_NONE)
                        aapsLogger.error(LTag.PUMPBTCOMM, "FAIL: got invalid result code: ${result.resultCode}")
                } catch (_: InterruptedException) {
                    aapsLogger.error(LTag.PUMPBTCOMM, "Interrupted while waiting for data")
                }
            }
        }
    }
}