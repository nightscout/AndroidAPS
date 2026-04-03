package app.aaps.pump.eopatch.core.scan

import android.content.Context
import app.aaps.pump.eopatch.core.ble.PatchFunc
import app.aaps.pump.eopatch.core.noti.AlarmNotification
import app.aaps.pump.eopatch.core.noti.InfoNotification
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single

interface IBleDevice {

    fun init(context: Context)
    fun updateMacAddress(address: String, dryRun: Boolean)
    fun writeAndRead(data: ByteArray, func: PatchFunc): Single<ByteArray>
    fun observeConnectionState(): Observable<BleConnectionState>
    fun observeConnected(): Observable<Boolean>
    val connectionState: BleConnectionState
    fun observeBondState(): Observable<Int>
    fun observeInfoNotification(): Observable<InfoNotification>
    fun observeAlarmNotification(): Observable<AlarmNotification>
    fun setSeq(seq: Int)
    val isSeqReady: Boolean
    fun updateEncryptionParam(sharedKey: ByteArray)
}
