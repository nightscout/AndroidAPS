package app.aaps.pump.ap550s.defs

import app.aaps.pump.ap550s.R

enum class ConnectStatus(var sourceId: Int) {
    NoBluetoothAdapter(R.string.ap550s_no_bt_adapter), //
    BluetoothDisabled(R.string.ap550s_bt_disabled), //
    Pairing(R.string.ap550s_pairing),
    PairSuccess(R.string.ap550s_pair_success),
    PairCodeError(R.string.ap550s_pair_code_error),
    CancelPair(R.string.ap550s_pair_cancel),
    Connected(R.string.ap550s_connected),
    ConnectFail(R.string.ap550s_connect_fail),
    Disconnected(R.string.ap550s_disconnected),
    RemoveByUser(R.string.ap550s_remove_by_user);
}