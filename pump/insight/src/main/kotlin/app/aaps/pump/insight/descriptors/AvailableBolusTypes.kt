package app.aaps.pump.insight.descriptors

class AvailableBolusTypes {

    var isStandardAvailable = false
    var isExtendedAvailable = false
    var isMultiwaveAvailable = false
    fun isBolusTypeAvailable(bolusType: BolusType?): Boolean {
        return when (bolusType) {
            BolusType.STANDARD  -> isStandardAvailable
            BolusType.EXTENDED  -> isExtendedAvailable
            BolusType.MULTIWAVE -> isMultiwaveAvailable
            else                -> false
        }
    }
}