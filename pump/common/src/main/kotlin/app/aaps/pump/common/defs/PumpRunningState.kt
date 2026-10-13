package app.aaps.pump.common.defs

// TODO there are 3 classes now, that do similar things, sort of, need to define exact rules: PumpDeviceState, PumpDriverState, PumpStatusState

enum class PumpRunningState(val status: String) {

    Running("normal"),
    Suspended("suspended");
}