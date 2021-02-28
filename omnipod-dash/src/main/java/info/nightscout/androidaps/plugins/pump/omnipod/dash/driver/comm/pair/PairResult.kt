package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.pair

data class PairResult(val ltk: ByteArray) {
    init {
        require(ltk.size == 16)
    }
}
