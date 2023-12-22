package app.aaps.pump.insight.descriptors

enum class BasalProfile(val id: Int) {
    PROFILE_1(31),
    PROFILE_2(227),
    PROFILE_3(252),
    PROFILE_4(805),
    PROFILE_5(826);

    companion object {

        fun fromId(id: Int) = values().firstOrNull { it.id == id }
    }
}