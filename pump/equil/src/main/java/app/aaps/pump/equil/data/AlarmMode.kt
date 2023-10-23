package app.aaps.pump.equil.data

enum class AlarmMode(val command: Int) {
    TONE(1),
    SHAKE(2),
    TONE_AND_SHAKE(3),
    MUTE(0)
}