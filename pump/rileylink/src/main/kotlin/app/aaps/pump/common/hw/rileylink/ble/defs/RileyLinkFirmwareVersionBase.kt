package app.aaps.pump.common.hw.rileylink.ble.defs

@Suppress("EnumEntryName")
enum class RileyLinkFirmwareVersionBase(val major: Int?, val minor: Int?, val versionKey: String) {

    Version_0_0(0, 0, "0.0"), // just for defaulting
    Version_0_9(0, 9, "0.9"),
    Version_1_0(1, 0, "1.0"),
    Version_1_x(1, null, "1.x"),
    Version_2_0(2, 0, "2.0"),
    Version_2_2(2, 2, "2.2"),
    Version_2_x(2, null, "2.x"),
    Version_3_x(3, null, "3.x"),
    Version_4_x(4, null, "4.x"),
    UnknownVersion(null, null, "???")
    ;

    fun isSameVersion(versionSources: RileyLinkFirmwareVersion): Boolean =
        versionSources.familyMembers.contains(this)

    companion object {

        fun byVersionString(versionString: String) = entries.firstOrNull { it.versionKey == versionString }
        fun defaultToLowestMajorVersion(major: Int) = byVersionString("$major.x") ?: UnknownVersion
    }
}