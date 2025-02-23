package app.aaps.pump.common.hw.rileylink.ble.defs

/**
 * Created by andy on 21/05/2018.
 */
@Suppress("EnumEntryName", "unused", "SpellCheckingInspection")
enum class CC111XRegister(val value: Byte) {

    sync1(0x00),
    sync0(0x01),
    pktlen(0x02),
    pktctrl1(0x03),
    pktctrl0(0x04),
    fsctrl1(0x07),
    freq2(0x09),
    freq1(0x0a),
    freq0(0x0b),
    mdmcfg4(0x0c),
    mdmcfg3(0x0d),
    mdmcfg2(0x0e),
    mdmcfg1(0x0f),
    mdmcfg0(0x10),
    deviatn(0x11),
    mcsm0(0x14),
    foccfg(0x15),
    agcctrl2(0x17),
    agcctrl1(0x18),
    agcctrl0(0x19),
    frend1(0x1a),
    frend0(0x1b),
    fscal3(0x1c),
    fscal2(0x1d),
    fscal1(0x1e),
    fscal0(0x1f),
    test1(0x24),
    test0(0x25),
    paTable0(0x2e),
    ;
}
