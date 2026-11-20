/*
 * KEKS J-PAKE / ExtraData authentication for Dexcom G7 / ONE+.
 *
 * Translated to Kotlin from NightscoutFoundation/xDrip
 *   https://github.com/NightscoutFoundation/xDrip
 *   pin commit 1e86d9a2a52577ed2c30fbf7b69d75fd56e6918f (tag 2026.07.15)
 * Authorship per upstream comments: JamOrHam / jamorham. Upstream carries no
 * formal per-file "Copyright (C) <year> <name>" line; none is asserted or
 * invented here. See plugins/libkeks/NOTICE.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * Part of AndroidAPS (AGPL-3.0-or-later); these KEKS sources retain their
 * upstream GPL-3.0 terms. Free software, distributed WITHOUT ANY WARRANTY.
 * See https://www.gnu.org/licenses/gpl-3.0.html
 */
package jamorham.keks

import jamorham.keks.util.Util.hexStringToByteArray

/**
 * JamOrHam
 *
 * KEKS configuration
 */
object Config {

    private val KEY1 = arrayOf(
        hex(
            "039503316070A26884ECD320106080A26884ECD330107030240040A08F620F921D6251ACB39895DB5C49AB3" +
                "0033E94D7E61C9EFE8F67107E3E0F5DC4E85FDB3EF05CCAA14C33C30D63F23D51F26A1624619087F06F06743031AEA9",
        ),
        hex(
            "03183920100003316070A26884ECD320106080A26884ECD33010704097037720101040025351C2844410F98" +
                "15BE04794FBC0AF098652F1AF232648BADB684F3506FB30D10AA06080A26884ECD33010701A4430240040A08F620F9" +
                "21D6251ACB39895DB5C49AB30033E94D7E61C9EFE8F67107E3E0F5DC4E85FDB3EF05CCAA14C33C30D63F23D51F26A1" +
                "624619087F06F06743031AEA9",
        ),
    )
    private val KEY2 = arrayOf(
        hex(
            "039503316070A26884ECD320106080A26884ECD330107030240040840CCEF4364F4F87D6B0D58F393050FC0" +
                "ED7301604CFF5F03EAFF546E5EDAC66E0359991C66A8C9F91DE3F4E7CFD4936E26C593835EFE80951A79AAAACC5B285",
        ),
        hex(
            "03183920100003316070A26884ECD320106080A26884ECD33010704097037720101040028DC3343BBD703B1" +
                "D62614568237CC3CD786CA6926D7B492D33A5FCC10590A9CA0AA06080A26884ECD33010701A4430240040840CCEF43" +
                "64F4F87D6B0D58F393050FC0ED7301604CFF5F03EAFF546E5EDAC66E0359991C66A8C9F91DE3F4E7CFD4936E26C593" +
                "835EFE80951A79AAAACC5B285",
        ),
    )
    private val REFERENCE_B = hex("1A808807F7E953C754A02E0D3C51FA2D2ED3D7699430A51D910D4FBCBA3EEF2F")
    private val ALICE_B = hex("36C69656E647")
    private val BOB_B = hex("375627675627")
    private val CMD1 = byteArrayOf(0x0A)
    private val SPARAMB = byteArrayOf(0x02)
    private val TIME_EXTENDEDB = byteArrayOf(0x06, 0x19)
    private val TIME_EXTENDED2B = byteArrayOf(0xFF.toByte(), 0x06, 0x01)
    private val TIME_EXTENDED3B = byteArrayOf(0x06, 0x00)
    private val PREFIX_B = byteArrayOf(0x30, 0x30)
    private val ONE_GET_DATA_4E_CMD = byteArrayOf(0x4e, 0x0a, 0xa9.toByte())
    private val GET_DATA_4E_CMD = byteArrayOf(0x4e)
    private val CHALLENGE_OUTB = byteArrayOf(0x0d, 0x00, 0x02)

    /**
     * `Util.hexStringToByteArray` turns each pair of hex digits around, so these texts are not
     * read the way they look. That is upstream's own doing and the sensor expects the result.
     */
    private fun hex(text: String): ByteArray =
        hexStringToByteArray(text) ?: error("KEKS configuration value is not readable")

    enum class Get(val bytes: ByteArray) {
        SPARAM(SPARAMB),
        TIME_EXTENDED(TIME_EXTENDEDB),
        TIME_EXTENDED2(TIME_EXTENDED2B),
        TIME_EXTENDED3(TIME_EXTENDED3B),
        PREFIX(PREFIX_B),
        KEYCMD(CMD1),
        KEY1A(KEY1[0]),
        KEY1B(KEY1[1]),
        KEY2A(KEY2[0]),
        KEY2B(KEY2[1]),
        ALICE(ALICE_B),
        BOB(BOB_B),
        REFERENCE(REFERENCE_B),
        GETDATA(ONE_GET_DATA_4E_CMD),
        GETDATA2(GET_DATA_4E_CMD),
        CHALLENGE_OUT(CHALLENGE_OUTB),
    }
}
