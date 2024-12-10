package app.aaps.pump.equil.data

enum class RunMode(val command: Int) {
    RUN(1),
    STOP(2),
    SUSPEND(0),
    NONE(-1)
}