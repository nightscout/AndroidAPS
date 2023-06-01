package info.nightscout.androidaps.plugins.pump.eopatch.ble.task;

import static info.nightscout.androidaps.plugins.pump.eopatch.core.api.StartBonding.OPTION_NUMERIC;

import android.bluetooth.BluetoothDevice;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

import info.nightscout.androidaps.plugins.pump.eopatch.core.api.StartBonding;
import info.nightscout.rx.logging.LTag;
import io.reactivex.rxjava3.core.Single;

@Singleton
public class StartBondTask extends TaskBase {
    private final StartBonding START_BOND;

    @Inject
    public StartBondTask() {
        super(TaskFunc.START_BOND);
        START_BOND = new StartBonding();
    }

    public Single<Boolean> start(String mac) {
        prefSetMacAddress(mac);
        patch.updateMacAddress(mac, false);

        return isReady()
                .concatMapSingle(v -> START_BOND.start(OPTION_NUMERIC))
                .doOnNext(this::checkResponse)
                .concatMap(response -> patch.observeBondState())
                .doOnNext(state -> {
                    if(state == BluetoothDevice.BOND_NONE) throw new Exception();
                })
                .filter(result -> result == BluetoothDevice.BOND_BONDED)
                .map(result -> true)
                .timeout(35, TimeUnit.SECONDS)
                .doOnNext(v -> prefSetMacAddress(mac))
                .doOnError(e -> {
                    prefSetMacAddress("");
                })
                .firstOrError();
    }

    private synchronized void prefSetMacAddress(String mac) {
        pm.getPatchConfig().setMacAddress(mac);
    }
}



