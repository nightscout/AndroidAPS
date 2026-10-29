package app.aaps.plugins.libre3.crypto.firstpair

import app.aaps.plugins.libre3.crypto.Libre3CryptoException
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * The published vectors of the digest of the first pairing scheme.
 *
 * Every value below is copied from LibreCRKit
 * `Tests/LibreCRKitTests/FirstPairSourceSliceTests.swift` at pin `a86b92f`. They are the only way
 * to tell a right port from a wrong one: a single wrong bit here would still produce a key, and
 * that key would simply be refused by a real sensor with no useful message.
 */
class Libre3FirstPairDigestTest {

    @Test
    fun `the fresh context and the first four bytes match the published vectors`() {
        val initial = init679f48Context()
        assertThat(Vectors.sha256(initial))
            .isEqualTo("2df6726428a8e0ab82f3a9807e742ee08fae532a00f8da456b2978ef4a93e6c8")
        assertThat(Vectors.hex(initial.copyOfRange(0, 72))).isEqualTo(
            "000000000000000005000402050203010503050404010603020105040504050703" +
                "010604050703050006020605050606020704070503040303030704070602010704" +
                "010206030501"
        )

        val updated = update67aa8cLen4Initial(initial, bytesOf(0, 0, 0, 1))
        assertThat(Vectors.sha256(updated))
            .isEqualTo("bcfc48c06814b940af45b26c60b35161014f3131cbfa9bcb7a3bdd178b7cc179")
        assertThat(Vectors.hex(updated.copyOfRange(updated.size - 72, updated.size))).isEqualTo(
            "0000000000000000000000000000000000000000000000000000000000000000" +
                "000000000400000067e6096a85ae67bb72f36e3c3af54fa57f520e518c68059b" +
                "abd9831f19cde05b"
        )
    }

    @Test
    fun `the first four bytes are refused when the size or the flag is wrong`() {
        val initial = init679f48Context()
        assertThrows<Libre3CryptoException> { update67aa8cLen4Initial(initial, bytesOf(1, 2, 3)) }

        val flagged = initial.copyOf()
        flagged[0x1a4] = 2
        assertThrows<Libre3CryptoException> { update67aa8cLen4Initial(flagged, bytesOf(0, 0, 0, 1)) }
    }

    @Test
    fun `the waiting words become the state blocks, as published`() {
        val updated = update67aa8cLen4Initial(init679f48Context(), bytesOf(0, 0, 0, 1))
        val applied = apply67eb94PendingBlocks(updated)

        assertThat(Vectors.sha256(applied))
            .isEqualTo("144260d5df72c48af66d02ee64b7ab3e901b4f5b6c1a69cf41020bc8028c8425")
        assertThat(Vectors.hex(applied.copyOfRange(0x114, 0x1a4))).isEqualTo(
            "060604030004050007010507070303070200050206010400020205020203070503" +
                "070602040500000701040300070303030700070400020207020705010103050006" +
                "010606070202060204020100030507040600000004070002060404050203060702" +
                "050704030600020402060404060006060301040705020701070701050206030707" +
                "000306050504000700040403"
        )
    }

    @Test
    fun `the block encoder matches both published vectors`() {
        assertThat(Vectors.hex(encode67d630Block(bytesOf(0, 0, 0, 1)))).isEqualTo(
            "050604040404020504060402060202020206050502020604020605040202050605" +
                "020506060402050506020202050606040506020206060202060604020606060402"
        )
        assertThat(Vectors.hex(encode67d630Block(ByteArray(16) { it.toByte() }))).isEqualTo(
            "040600060104000207000305060202050606000500030307000302040003050305" +
                "060004000100000502070400070301050500000302030206060104020606060502"
        )
    }

    @Test
    fun `the block encoder refuses an empty block and a block over sixteen bytes`() {
        assertThrows<Libre3CryptoException> { encode67d630Block(ByteArray(0)) }
        assertThrows<Libre3CryptoException> { encode67d630Block(ByteArray(17)) }
    }

    @Test
    fun `the waiting bytes reach the context, as published`() {
        val updated = update67aa8cLen4Initial(init679f48Context(), bytesOf(0, 0, 0, 1))
        val applied = apply67eb94WithPendingRawAdapter(updated)

        assertThat(Vectors.sha256(applied))
            .isEqualTo("7311b8040cd2b3c972246f43f27662024b9b64d2c9b117a0571a3eef59e759c1")
        assertThat(Vectors.hex(applied.copyOfRange(0x08, 0x4a))).isEqualTo(
            "050202020202040604020404020506050502050506040404060206040506060406" +
                "060505040406050504040406060204020507000102050605050504050502040404"
        )
    }

    @Test
    fun `a block that fills the fourth slot runs the round function, as published`() {
        val context = Vectors.make679f48Context(contextLength = 0uL, blockIndex = 3u)
        val encoded = encode67d630Block(ByteArray(16) { it.toByte() })
        val applied = apply67dd7cUpdateUntilDF80(context, encoded, 16)

        assertThat(Vectors.sha256(applied))
            .isEqualTo("dff592f458a6f495de02a3c60bf4c0f2c46692df8d67020a65bcdf1277ce2030")
        assertThat(Vectors.hex(applied.copyOfRange(0x114, 0x1a4))).isEqualTo(
            "050700050504040205060504010701000006060302070207060503050701060406" +
                "040104030206070100070704010400040006050500000202030200040700060003" +
                "050502070702050503060203010406050606000007070103000306010705030507" +
                "040500000301070101020600010401070102000205050204060007030003060703" +
                "050403060205070004010000"
        )
    }

    @Test
    fun `a whole set of earlier blocks gives the published slice`() {
        val previous = Vectors.pattern(2 * 66, 5, 2)
        val updates = previousDescriptorBlocksToDD7CInputs(previous)
        assertThat(updates.size).isEqualTo(132)
        assertThat(Vectors.sha256(updates))
            .isEqualTo("44e2abbbeb7fa7615a64007196fcdbbfb396ed9fe8e48e6b048404e8f96a2730")

        val context = finalized679f48ContextFromInputs(previous)
        assertThat(Vectors.sha256(context))
            .isEqualTo("8a57624059dee8d2679edd1b2e6de78f8d1856a871eb268d59f309943d10aa11")

        assertThat(Vectors.hex(deriveFrom679f48Inputs(previous, offset = 0, length = 16))).isEqualTo(
            "040400020506020406010204030101020502070705070404000302040501050505" +
                "010004030304070206030607070000000005000102030205000107030202050000"
        )
    }

    @Test
    fun `the two raw block builders and their slices match the published vectors`() {
        val raw = Vectors.pattern(2 * 66, 3, 1)
        assertThat(Vectors.sha256(constructor670978Ptr28Blocks(raw)))
            .isEqualTo("706be35f728909a58b2924e4ddb1a8aad7a725fa7f614445cd92feefb611de1c")
        assertThat(Vectors.sha256(constructor670a54Ptr10Blocks(raw)))
            .isEqualTo("914e5cf5c7677d9c570a74351fffbd879f75aca534c08426f3042eb2c6212d2b")

        assertThat(Vectors.hex(deriveFrom660448RawDescriptor(raw, offset = 0, length = 16))).isEqualTo(
            "040401010705040302070407030002030400040004030507070305050106050402" +
                "070401040604040707070702010000030500040203000304030103030004060301"
        )

        val first = Vectors.pattern(66, 5, 2)
        val second = Vectors.pattern(66, 7, 4)
        assertThat(Vectors.hex(deriveFrom64d774RawStreams(first, second, offset = 0, length = 16))).isEqualTo(
            "040406010201060506000205040004000004010001010207060607060607050502" +
                "000107040306040106020704040002000600030200070603040004010004010207"
        )
    }
}
