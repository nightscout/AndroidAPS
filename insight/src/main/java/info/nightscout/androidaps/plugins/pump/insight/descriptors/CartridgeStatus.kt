package info.nightscout.androidaps.plugins.pump.insight.descriptors

class CartridgeStatus {

    var isInserted = false
    lateinit var cartridgeType: CartridgeType
    lateinit var symbolStatus: SymbolStatus
    var remainingAmount = 0.0
}