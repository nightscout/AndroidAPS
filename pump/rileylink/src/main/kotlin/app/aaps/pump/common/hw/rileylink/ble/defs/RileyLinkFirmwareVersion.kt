package app.aaps.pump.common.hw.rileylink.ble.defs

import java.util.regex.Pattern

enum class RileyLinkFirmwareVersion(val familyMembers: List<RileyLinkFirmwareVersionBase>) {
    Version1(listOf(RileyLinkFirmwareVersionBase.Version_0_0, RileyLinkFirmwareVersionBase.Version_0_9, RileyLinkFirmwareVersionBase.Version_1_0, RileyLinkFirmwareVersionBase.Version_1_x)),
    Version2(listOf(RileyLinkFirmwareVersionBase.Version_2_0, RileyLinkFirmwareVersionBase.Version_2_2, RileyLinkFirmwareVersionBase.Version_2_x)),
    Version3(listOf(RileyLinkFirmwareVersionBase.Version_3_x)),
    Version4(listOf(RileyLinkFirmwareVersionBase.Version_4_x)),
    Version2AndHigher(
        listOf(
            RileyLinkFirmwareVersionBase.Version_2_0, RileyLinkFirmwareVersionBase.Version_2_2, RileyLinkFirmwareVersionBase.Version_2_x,
            RileyLinkFirmwareVersionBase.Version_3_x,
            RileyLinkFirmwareVersionBase.Version_4_x
        )
    );

    override fun toString(): String = FIRMWARE_IDENTIFICATION_PREFIX + name

    @Suppress("SpellCheckingInspection")
    companion object {

        private const val FIRMWARE_IDENTIFICATION_PREFIX = "subg_rfspy "
        private val _version_pattern: Pattern = Pattern.compile("$FIRMWARE_IDENTIFICATION_PREFIX([0-9]+)\\.([0-9]+)")

        fun getByVersionString(versionString: String?): RileyLinkFirmwareVersionBase? {
            if (versionString != null) {
                val m = _version_pattern.matcher(versionString)
                if (m.find()) {
                    val major = m.group(1)?.toInt()
                    val minor = m.group(2)?.toInt()
                    val versionKey = "$major.$minor"
                    return RileyLinkFirmwareVersionBase.byVersionString(versionKey) ?: RileyLinkFirmwareVersionBase.defaultToLowestMajorVersion(major ?: -1)
                    // just in case there is new release that we don't cover
                    // example: 2.3 etc
                }
            }
            return RileyLinkFirmwareVersionBase.UnknownVersion
        }
    }
}
