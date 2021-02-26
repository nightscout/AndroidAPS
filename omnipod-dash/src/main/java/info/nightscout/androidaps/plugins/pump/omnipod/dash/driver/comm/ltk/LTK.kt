package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.ltk

data class LTK(val ltk: ByteArray, val noncePrefix: ByteArray) {
    init {
        require(ltk.size == 16)
        require(noncePrefix.size == 16)
    }
}
