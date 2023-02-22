package info.nightscout.aaps.pump.common.defs

enum class PumpDriverMode {
    Faked, // used for testing only, each bolus/tbr will be automatically accepted and "faked"
    ForcedOpenLoop, // firmware=1.5, there are no commands, so UI instructions are shown
    Automatic // firmware=1.6, open loop and closed loop fully supported
}