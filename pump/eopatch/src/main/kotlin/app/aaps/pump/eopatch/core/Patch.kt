package app.aaps.pump.eopatch.core

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.eopatch.core.ble.Cipher
import app.aaps.pump.eopatch.core.ble.ICipher
import app.aaps.pump.eopatch.core.ble.IPatchPacketConstant.Companion.ALARM_UUID
import app.aaps.pump.eopatch.core.ble.IPatchPacketConstant.Companion.BIG_CALL_UUID
import app.aaps.pump.eopatch.core.ble.IPatchPacketConstant.Companion.BIG_READ_UUID
import app.aaps.pump.eopatch.core.ble.IPatchPacketConstant.Companion.INFO_UUID
import app.aaps.pump.eopatch.core.ble.IPatchPacketConstant.Companion.MTU_SIZE
import app.aaps.pump.eopatch.core.ble.MacVerifier
import app.aaps.pump.eopatch.core.ble.PatchFunc
import app.aaps.pump.eopatch.core.noti.AlarmNotification
import app.aaps.pump.eopatch.core.noti.InfoNotification
import app.aaps.pump.eopatch.core.scan.BleConnectionState
import app.aaps.pump.eopatch.core.scan.IBleDevice
import com.jakewharton.rx3.ReplayingShare
import com.polidea.rxandroidble3.RxBleClient
import com.polidea.rxandroidble3.RxBleConnection
import com.polidea.rxandroidble3.RxBleDevice
import com.polidea.rxandroidble3.Timeout
import com.polidea.rxandroidble3.exceptions.BleException
import com.polidea.rxandroidble3.internal.RxBleLog
import io.reactivex.rxjava3.android.MainThreadDisposable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Observer
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.exceptions.OnErrorNotImplementedException
import io.reactivex.rxjava3.exceptions.UndeliverableException
import io.reactivex.rxjava3.internal.functions.Functions
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.PublishSubject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Patch @Inject constructor(
    private val aapsLogger: AAPSLogger
) : IBleDevice {

    private lateinit var rxBleClient: RxBleClient
    private lateinit var bondStateObservable: Observable<Intent>
    private lateinit var cipher: ICipher

    private var device: RxBleDevice? = null
    private val notificationDisposable = CompositeDisposable()
    private var autoConnection = false
    private var connectionObservable: Observable<RxBleConnection> = Observable.empty()
    private val disconnectTriggerSubject = PublishSubject.create<Boolean>()
    private val connStateSubject = BehaviorSubject.createDefault(BleConnectionState.DISCONNECTED)
    private val rxBleConnectionSubject = BehaviorSubject.createDefault<Observable<RxBleConnection>>(Observable.empty())
    private val alarmNotificationSubject = PublishSubject.create<AlarmNotification>()
    private val infoNotificationSubject = PublishSubject.create<InfoNotification>()
    private val bondStateSubject = BehaviorSubject.createDefault(BluetoothDevice.BOND_NONE)
    private val compositeDisposable = CompositeDisposable()
    private var keepConnect: Disposable? = null
    private var connStateDisposable: Disposable? = null
    private var initDone = false

    override fun init(context: Context) {
        if (!initDone) {
            initDone = true
            rxBleClient = RxBleClient.create(context)
            cipher = Cipher()
            bondStateObservable = BroadcastReceiverObservable(context, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
            compositeDisposable.add(
                bondStateObservable.subscribe(
                    { updateBondState(it) },
                    { e -> aapsLogger.error(LTag.PUMPCOMM, "Error monitoring bond state: ${e.message}") }
                )
            )
            setupRxAndroidBle()
        }
    }

    private fun setupRxAndroidBle() {
        @Suppress("DEPRECATION")
        RxBleClient.setLogLevel(RxBleLog.VERBOSE)

        RxJavaPlugins.setErrorHandler { throwable ->
            when {
                throwable is UndeliverableException && throwable.cause is BleException -> return@setErrorHandler
                throwable is UndeliverableException                                    -> {
                    aapsLogger.error(LTag.PUMPCOMM, "rx UndeliverableException Error Handler"); return@setErrorHandler
                }

                throwable is OnErrorNotImplementedException                            -> {
                    aapsLogger.error(LTag.PUMPCOMM, "rx exception Error Handler"); return@setErrorHandler
                }

                else                                                                   -> throw RuntimeException("Unexpected Throwable in RxJavaPlugins error handler", throwable)
            }
        }
    }

    @Synchronized override fun updateMacAddress(address: String, dryRun: Boolean) {
        val oldAddress = device?.macAddress ?: ""
        if (dryRun) return

        if (oldAddress != address) {
            disconnect()
            if (MacVerifier.isValid(address)) {
                device = rxBleClient.getBleDevice(address)
                device?.let { dev ->
                    connStateDisposable?.takeIf { !it.isDisposed }?.dispose()
                    connStateDisposable = dev.observeConnectionStateChanges()
                        .map { fromBleState(it) }
                        .takeUntil(disconnectTriggerSubject)
                        .onErrorReturnItem(BleConnectionState.DISCONNECTED)
                        .subscribe { updateConnectionState(it) }

                    bondStateSubject.onNext(BluetoothDevice.BOND_NONE)
                    setConnectionObservable(setupConnection(true, TIMEOUT_PATCH))
                    keepConnect = connectionObservable.subscribe(
                        Functions.emptyConsumer(),
                        { aapsLogger.error(LTag.PUMPCOMM, "Keep Connection Error") }
                    )
                } ?: setConnectionObservable(Observable.empty())
            } else {
                removeBond()
                device = null
                setConnectionObservable(Observable.empty())
            }
        }
    }

    @Synchronized override fun writeAndRead(data: ByteArray, func: PatchFunc): Single<ByteArray> =
        connectionObservable.concatMapSingle { conn ->
            cipher.encrypt(data, func)
                .flatMap { enc -> conn.writeCharacteristic(BIG_CALL_UUID, enc) }
                .doOnSuccess { written -> cipher.onPacketSent(written, func) }
                .flatMap { _ ->
                    conn.readCharacteristic(BIG_READ_UUID)
                        .flatMap { read -> cipher.decrypt(read, func) }
                }
                .flatMap { dec -> getSeqNumOnCryptoError(conn, dec) }
                .doOnError { e -> aapsLogger.error(LTag.PUMPCOMM, "Error: ${e.message}") }
        }.firstOrError()

    private fun updateBondState(intent: Intent) {
        @Suppress("DEPRECATION")
        val intentDevice = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)

        synchronized(this) {
            val dev = device
            if (dev != null && intentDevice != null) {
                if (dev.macAddress == intentDevice.address) {
                    val current = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                    bondStateSubject.onNext(current)
                }
            }
        }
    }

    override fun observeConnectionState(): Observable<BleConnectionState> = connStateSubject.distinctUntilChanged()

    override fun observeConnected(): Observable<Boolean> =
        observeConnectionState().map { it == BleConnectionState.CONNECTED }.distinctUntilChanged()

    override val connectionState: BleConnectionState
        get() = connStateSubject.value ?: BleConnectionState.DISCONNECTED

    @Synchronized private fun updateConnectionState(state: BleConnectionState) = connStateSubject.onNext(state)

    override fun observeBondState(): Observable<Int> = bondStateSubject
    override fun observeInfoNotification(): Observable<InfoNotification> = infoNotificationSubject
    override fun observeAlarmNotification(): Observable<AlarmNotification> = alarmNotificationSubject

    override fun setSeq(seq: Int) = cipher.setSeq(seq)
    override val isSeqReady: Boolean get() = cipher.getSequence() >= 0
    override fun updateEncryptionParam(sharedKey: ByteArray) = cipher.updateEncryptionParam(sharedKey)

    private fun removeBond() {
        val bluetoothDevice = device?.bluetoothDevice
        if (bluetoothDevice == null) {
            aapsLogger.error(LTag.PUMPCOMM, "Error bluetoothDevice is null")
            return
        }
        try {
            bluetoothDevice.javaClass.getMethod("removeBond").invoke(bluetoothDevice)
        } catch (e: Exception) {
            aapsLogger.error(LTag.PUMPCOMM, "removeBond error")
        }
    }

    @Synchronized fun setupConnection(auto: Boolean, timeout: Timeout): Observable<RxBleConnection> {
        autoConnection = auto
        val dev = device ?: return Observable.empty()

        return dev.establishConnection(auto, timeout)
            .concatMapSingle { setup(it) }
            .doOnNext { updateConnectionState(BleConnectionState.CONNECTED) }
            .takeUntil(disconnectTriggerSubject)
            .compose(ReplayingShare.instance())
            .retryWhen { it.delay(5, TimeUnit.SECONDS) }
            .doOnError { e -> aapsLogger.error(LTag.PUMPCOMM, "connection error: ${e.message}") }
    }

    @Synchronized private fun setConnectionObservable(observable: Observable<RxBleConnection>) {
        if (observable !== connectionObservable) {
            connectionObservable = observable
            rxBleConnectionSubject.onNext(connectionObservable)
        }
    }

    private fun fromBleState(bleState: RxBleConnection.RxBleConnectionState): BleConnectionState = when (bleState) {
        RxBleConnection.RxBleConnectionState.CONNECTED    -> BleConnectionState.CONNECTED_PREPARING
        RxBleConnection.RxBleConnectionState.CONNECTING   -> BleConnectionState.CONNECTING
        RxBleConnection.RxBleConnectionState.DISCONNECTED -> BleConnectionState.DISCONNECTED
        RxBleConnection.RxBleConnectionState.DISCONNECTING -> BleConnectionState.DISCONNECTING
    }

    @Synchronized fun disconnect() {
        bondStateSubject.onNext(BluetoothDevice.BOND_NONE)
        updateConnectionState(BleConnectionState.DISCONNECTED)
        if (device == null) return

        autoConnection = false
        keepConnect?.takeIf { !it.isDisposed }?.dispose()
        disconnectTriggerSubject.onNext(true)
    }

    private fun setup(conn: RxBleConnection): Single<RxBleConnection> = mtu(conn).doOnSuccess { setupNotification(it) }

    private fun mtu(conn: RxBleConnection): Single<RxBleConnection> {
        if (conn.mtu <= RxBleConnection.GATT_MTU_MINIMUM) {
            return Observable.timer(1000, TimeUnit.MILLISECONDS)
                .concatMapSingle {
                    if (conn.mtu <= RxBleConnection.GATT_MTU_MINIMUM)
                        conn.requestMtu(MTU_SIZE).map { conn }
                    else Single.just(conn)
                }
                .firstOrError()
        }
        return Single.just(conn)
    }

    @Synchronized private fun setupNotification(conn: RxBleConnection) {
        notificationDisposable.addAll(
            conn.setupNotification(ALARM_UUID).flatMap { it }
                .map { AlarmNotification(it, aapsLogger) }
                .subscribe { alarmNotificationSubject.onNext(it) },
            conn.setupNotification(INFO_UUID).flatMap { it }
                .map { InfoNotification(it, aapsLogger) }
                .subscribe { infoNotificationSubject.onNext(it) }
        )
    }

    @Synchronized private fun getSeqNumOnCryptoError(conn: RxBleConnection, dec: ByteArray): Single<ByteArray> {
        if ((dec[2].toInt() and 0xFF) == 0xE0 && dec[3].toInt() == 0x00) {
            val errCode = dec[4].toInt()
            aapsLogger.error(LTag.PUMPCOMM, "Crypto error received: $errCode")
            return when (errCode) {
                0x01 -> {
                    val sendPacket = byteArrayOf(0x00, 0x00, 0x20, 0xA2.toByte())
                    Single.just(sendPacket)
                        .flatMap { conn.writeCharacteristic(BIG_CALL_UUID, it) }
                        .flatMap { conn.readCharacteristic(BIG_READ_UUID) }
                        .doOnSuccess { read ->
                            if (read[0].toInt() == 0 && read[1].toInt() == 0 && read[2].toInt() == 0x20
                                && (read[3].toInt() and 0xFF) == 0xA2 && read[4].toInt() == 0
                            ) {
                                val seq15 = ((read[37].toInt() and 0xFF) shl 8) or (read[38].toInt() and 0xFF)
                                setSeq(seq15)
                            }
                        }
                        .doOnError { aapsLogger.error(LTag.PUMPCOMM, "getSeqNum Error") }
                        .flatMap { Single.just(dec) }
                }

                0x02 -> Single.error(Exception("Error in sending plaintext packets in encrypted communication"))
                0x03 -> Single.error(Exception("Error in sending ciphertext packets in unencrypted communication"))
                else -> Single.error(Exception("Unknown error"))
            }
        }
        return Single.just(dec)
    }

    private class BroadcastReceiverObservable(
        private val context: Context,
        private val intentFilter: IntentFilter
    ) : Observable<Intent>() {

        override fun subscribeActual(observer: Observer<in Intent>) {
            val listener = Listener(context, observer)
            observer.onSubscribe(listener)
            context.registerReceiver(listener.receiver, intentFilter)
        }

        private inner class Listener(
            private val context: Context,
            private val observer: Observer<in Intent>
        ) : MainThreadDisposable() {

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (!isDisposed) observer.onNext(intent)
                }
            }

            override fun onDispose() = context.unregisterReceiver(receiver)
        }
    }

    companion object {

        private val TIMEOUT_PATCH = Timeout(4, TimeUnit.DAYS)
    }
}
