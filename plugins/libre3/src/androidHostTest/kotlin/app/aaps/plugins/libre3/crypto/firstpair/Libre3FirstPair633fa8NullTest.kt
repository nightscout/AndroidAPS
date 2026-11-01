package app.aaps.plugins.libre3.crypto.firstpair

import app.aaps.plugins.libre3.crypto.Libre3CryptoException
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * The published vectors of the null branch of `633fa8`.
 *
 * Copied from LibreCRKit `Tests/LibreCRKitTests/FirstPairSourceSliceTests.swift` at pin `a86b92f`.
 */
class Libre3FirstPair633fa8NullTest {

    private val entropy = ByteArray(0x11a) { ((it * 11 + 3) and 0xff).toByte() }

    @Test
    fun `the whole null branch matches the published vectors`() {
        val sources = builder633fa8NullEntrySourcesFromInvariantEntry()
        assertThat(sources.prologueSource.size).isEqualTo(0x11a)
        assertThat(Vectors.sha256(sources.prologueSource))
            .isEqualTo("b2b08a579ebd69e28c8bbb33b19317c3d4c22cce9ec2a6d60eb81e49c7729115")
        assertThat(Vectors.sha256(Vectors.packUInt32LE(sources.check1SourceWords)))
            .isEqualTo("4b29cac325304080f0e7b82a92ffe3ef9c3e252aac748ab6e776673fe7e73db6")
        assertThat(Vectors.sha256(Vectors.packUInt32LE(sources.check2SourceWords)))
            .isEqualTo("8905c6b8cd1d1fec875c168212d3555909e4554ee63432a54744a404db243e4d")

        val initial = builder633fa8NullInitialFromEntropy(entropy, sources.prologueSource)
        assertThat(Vectors.sha256(initial.maskedEntropy))
            .isEqualTo("23db0a42e5599a320a6384094203a1ecf34a1f7517c94c7fd47267708c760834")
        assertThat(Vectors.sha256(initial.cf0))
            .isEqualTo("9f50c4c539508ffd0d5a87b37d60997bb1622db044b55662c4d1d6d8bad6f532")
        assertThat(Vectors.sha256(initial.e10))
            .isEqualTo("0d0d59d1394d720b3d30d2a5f0ae4af4e811d2c9c690767b95d603883164cba5")
        assertThat(Vectors.sha256(initial.seedInputs))
            .isEqualTo("296b545eb6c3114d4b731abf59bbcb43e1e34b321e787683228ceb02c11d9cc2")
        assertThat(Vectors.sha256(initial.seedBlocks))
            .isEqualTo("30f124b2c0d6cd19c0bbf4e4f8cf1974e5766ed3171e9556e69979108e74626f")

        val loop = builder633fa8NullFirstLoopFromBlocks(initial.seedBlocks)
        assertThat(Vectors.hex(loop.finalTLane)).isEqualTo("060504020202040404020204040404010504")
        assertThat(Vectors.sha256(Vectors.packUInt32LE(loop.scheduleWords)))
            .isEqualTo("652ce3a7810e6b09bf6ce92f7029f7a79599a95db235538aea7e84bec65e21f0")
        assertThat(loop.scheduleWords.copyOfRange(0, 4).toList())
            .isEqualTo(listOf(0x77de69c8u, 0xc857bd48u, 0x65000b63u, 0xa6ddb53bu))
        assertThat(loop.scheduleWords.copyOfRange(16, 20).toList())
            .isEqualTo(listOf(0x7c13a2ceu, 0xe082b5bau, 0xbfaf4d29u, 0xc67887e7u))

        val acceptance = builder633fa8NullScheduleAcceptance(
            loop.scheduleWords, sources.check1SourceWords, sources.check2SourceWords,
        )
        assertThat(acceptance.firstOK).isTrue()
        assertThat(acceptance.secondOK).isTrue()

        val rejectedWords = loop.scheduleWords.copyOf()
        rejectedWords[19] = rejectedWords[19] xor 1u
        val rejected = builder633fa8NullScheduleAcceptance(
            rejectedWords, sources.check1SourceWords, sources.check2SourceWords,
        )
        assertThat(rejected.firstOK).isFalse()
        assertThat(rejected.secondOK).isFalse()

        val postAccept = builder633fa8NullPostAcceptBlocks(loop.scheduleWords)
        assertThat(postAccept.blocks4080.size).isEqualTo(20 * 16)
        assertThat(postAccept.blocks3f40.size).isEqualTo(20 * 16)
        assertThat(Vectors.sha256(postAccept.blocks4080))
            .isEqualTo("a8732537d6be3b54f8d00663ae3d0461ed7974b6861b1095b0d16730c08f9c86")
        assertThat(Vectors.sha256(postAccept.blocks3f40))
            .isEqualTo("8975ea6381dc1f9149d202522d21abf7105e2faf2888a306b5122d3c8f6f0b7c")
        assertThat(Vectors.hex(postAccept.blocks4080.copyOfRange(0, 16)))
            .isEqualTo("01070705070306040206010301020603")
        assertThat(Vectors.hex(postAccept.blocks3f40.copyOfRange(0, 16)))
            .isEqualTo("02030203010200000706020600030203")

        val prelude = builder633fa8NullPreludeSourceFromPostAccept(postAccept.blocks4080, postAccept.blocks3f40)
        assertThat(prelude.size).isEqualTo(0x10a)
        assertThat(Vectors.sha256(prelude))
            .isEqualTo("ed4e5c29dff15da45590bf9bc4ea8b7124af32f51f3add5e786cf77fd36c747d")
        assertThat(Vectors.hex(prelude.copyOfRange(0, 16))).isEqualTo("05000204070405070006020301060606")
        assertThat(Vectors.hex(prelude.copyOfRange(prelude.size - 16, prelude.size)))
            .isEqualTo("06040206050602040404020605060204")

        assertThat(builder633fa8NullPreludeSourceFromEntropy(entropy)).isEqualTo(prelude)

        val scalar = builder633fa8NullScalarWindowFromEntropy(entropy)
        assertThat(Vectors.sha256(scalar))
            .isEqualTo("c4f2357511bf2071de2a5478a5d3d8a17c2b4da7b46c6cb46f4834ecb3a2f2ba")
        assertThat(Vectors.hex(scalar)).isEqualTo(
            "3b588dd68f20da5f883993332cabcda6576645712cdd039d0a8195f4b1c0b52e" +
                "0000000000000000000000000000000000000000000000000000000000000000000000000000"
        )
        assertThat(builder633fa8ScalarWindowFromPreludeSource(prelude)).isEqualTo(scalar)
    }

    @Test
    fun `the entropy loop stops on the first accepted draw`() {
        var entropyCalls = 0
        val result = builder633fa8NullScalarWindowFromEntropySource(maxAttempts = 3) { requestedCount ->
            entropyCalls += 1
            assertThat(requestedCount).isEqualTo(0x11a)
            entropy
        }
        assertThat(entropyCalls).isEqualTo(1)
        assertThat(result.scalarWindow).isEqualTo(builder633fa8NullScalarWindowFromEntropy(entropy))
        assertThat(result.entropy11A).isEqualTo(entropy)
        assertThat(result.attempts).isEqualTo(1)
    }

    @Test
    fun `the entropy loop refuses a run of no draws at all`() {
        assertThrows<Libre3CryptoException> {
            builder633fa8NullScalarWindowFromEntropySource(maxAttempts = 0) { entropy }
        }
    }
}
