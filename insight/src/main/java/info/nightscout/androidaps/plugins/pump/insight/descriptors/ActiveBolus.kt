package info.nightscout.androidaps.plugins.pump.insight.descriptors

class ActiveBolus {

    var bolusID = 0
    lateinit var bolusType: BolusType
    var initialAmount = 0.0
    var remainingAmount = 0.0
    var remainingDuration = 0
}