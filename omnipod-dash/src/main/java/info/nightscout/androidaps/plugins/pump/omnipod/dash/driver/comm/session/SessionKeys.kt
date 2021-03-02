package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.session

data class SessionKeys(val ck: ByteArray, val noncePrefix: ByteArray, val sqn: ByteArray) {
    init {
        require(ck.size == 16) { "CK has to be 16 bytes long" }
        require(noncePrefix.size == 8) { "noncePrefix has to be 8 bytes long" }
        require(sqn.size == 6) { "SQN has to be 6 bytes long" }
    }
}
