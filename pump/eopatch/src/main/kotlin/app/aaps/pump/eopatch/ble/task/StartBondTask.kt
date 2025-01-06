package app.aaps.pump.eopatch.ble.task

import android.bluetooth.BluetoothDevice
import app.aaps.pump.eopatch.core.api.StartBonding
import app.aaps.pump.eopatch.core.response.BondingResponse
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.functions.Function
import io.reactivex.rxjava3.functions.Predicate
import java.lang.Exception
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("PrivatePropertyName")
@Singleton
class StartBondTask @Inject constructor() : TaskBase(TaskFunc.START_BOND) {

    private val START_BOND: StartBonding = StartBonding()

    fun start(mac: String): Single<Boolean> {
        prefSetMacAddress(mac)
        patch.updateMacAddress(mac, false)

        return isReady()
            .concatMapSingle<BondingResponse>(Function { START_BOND.start(StartBonding.OPTION_NUMERIC) })
            .doOnNext(Consumer { response: BondingResponse -> this.checkResponse(response) })
            .concatMap<Int>(Function { patch.observeBondState() })
            .doOnNext(Consumer { state: Int ->
                if (state == BluetoothDevice.BOND_NONE) throw Exception()
            })
            .filter(Predicate { result: Int -> result == BluetoothDevice.BOND_BONDED })
            .map<Boolean>(Function { true })
            .timeout(35, TimeUnit.SECONDS)
            .doOnNext(Consumer { prefSetMacAddress(mac) })
            .doOnError(Consumer { prefSetMacAddress("") })
            .firstOrError()
    }

    @Synchronized private fun prefSetMacAddress(mac: String) {
        patchConfig.macAddress = mac
    }
}



