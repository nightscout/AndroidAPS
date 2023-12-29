package app.aaps.pump.equil.data

enum class AlarmMode(val command: Int) {
    MUTE(0),
    TONE(1),
    SHAKE(2),
    TONE_AND_SHAKE(3);

    companion object {
        fun fromInt(number: Int) = entries.firstOrNull { it.command == number } ?: TONE_AND_SHAKE
    }
}