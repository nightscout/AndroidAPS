package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message

data class Address(val address: ByteArray) {
    init {
        require(address.size == 4)
    }
}
