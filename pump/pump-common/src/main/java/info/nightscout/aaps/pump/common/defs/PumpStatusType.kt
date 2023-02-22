package info.nightscout.aaps.pump.common.defs

enum class PumpStatusType(val status: String) {

    Running("normal"),
    Suspended("suspended");
}