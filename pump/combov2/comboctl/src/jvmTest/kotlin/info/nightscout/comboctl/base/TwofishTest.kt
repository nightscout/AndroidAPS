package info.nightscout.comboctl.base

import app.aaps.shared.tests.TestBase
import kotlin.test.Test
import kotlin.test.assertEquals

class TwofishTest : TestBase() {

    // The test datasets in the unit tests below originate from the
    // Two-Fish known answers test dataset (the twofish-kat.zip
    // archive). It can be downloaded from:
    // https://www.schneier.com/academic/twofish/

    @Test
    fun checkProcessedSubkeys() {
        // From the ecb_ival.txt test file in the twofish-kat.zip
        // Two-Fish known answers test dataset. 128-bit keysize.

        val key = byteArrayOfInts(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
        val expectedSubKeys = intArrayOf(
            0x52C54DDE.toInt(), 0x11F0626D.toInt(), // Input whiten
            0x7CAC9D4A.toInt(), 0x4D1B4AAA.toInt(),
            0xB7B83A10.toInt(), 0x1E7D0BEB.toInt(), // Output whiten
            0xEE9C341F.toInt(), 0xCFE14BE4.toInt(),
            0xF98FFEF9.toInt(), 0x9C5B3C17.toInt(), // Round subkeys
            0x15A48310.toInt(), 0x342A4D81.toInt(),
            0x424D89FE.toInt(), 0xC14724A7.toInt(),
            0x311B834C.toInt(), 0xFDE87320.toInt(),
            0x3302778F.toInt(), 0x26CD67B4.toInt(),
            0x7A6C6362.toInt(), 0xC2BAF60E.toInt(),
            0x3411B994.toInt(), 0xD972C87F.toInt(),
            0x84ADB1EA.toInt(), 0xA7DEE434.toInt(),
            0x54D2960F.toInt(), 0xA2F7CAA8.toInt(),
            0xA6B8FF8C.toInt(), 0x8014C425.toInt(),
            0x6A748D1C.toInt(), 0xEDBAF720.toInt(),
            0x928EF78C.toInt(), 0x0338EE13.toInt(),
            0x9949D6BE.toInt(), 0xC8314176.toInt(),
            0x07C07D68.toInt(), 0xECAE7EA7.toInt(),
            0x1FE71844.toInt(), 0x85C05C89.toInt(),
            0xF298311E.toInt(), 0x696EA672.toInt()
        )

        val keyObject = Twofish.processKey(key)

        assertEquals(expectedSubKeys.toList(), keyObject.subKeys.toList())
    }

    @Test
    fun checkPermutationTablesAndMDSMatrixMultiplyTables() {
        // From the ecb_tbl.txt test file in the twofish-kat.zip
        // Two-Fish known answers test dataset. 128-bit keysize.

        class TestVector(key: String, plaintext: String, ciphertext: String) {

            val keyArray: ByteArray
            val plaintextArray: ByteArray
            val ciphertextArray: ByteArray

            init {
                keyArray = hexstringToByteArray(key)
                plaintextArray = hexstringToByteArray(plaintext)
                ciphertextArray = hexstringToByteArray(ciphertext)
            }

            private fun hexstringToByteArray(hexstring: String): ByteArray {
                val array = ByteArray(hexstring.length / 2)

                for (i in array.indices) {
                    val hexcharStr = hexstring.substring(IntRange(i * 2, i * 2 + 1))
                    array[i] = Integer.parseInt(hexcharStr, 16).toByte()
                }

                return array
            }
        }

        val testVectors = arrayOf(
            TestVector(
                key = "00000000000000000000000000000000",
                plaintext = "00000000000000000000000000000000",
                ciphertext = "9F589F5CF6122C32B6BFEC2F2AE8C35A"
            ),
            TestVector(
                key = "00000000000000000000000000000000",
                plaintext = "9F589F5CF6122C32B6BFEC2F2AE8C35A",
                ciphertext = "D491DB16E7B1C39E86CB086B789F5419"
            ),
            TestVector(
                key = "9F589F5CF6122C32B6BFEC2F2AE8C35A",
                plaintext = "D491DB16E7B1C39E86CB086B789F5419",
                ciphertext = "019F9809DE1711858FAAC3A3BA20FBC3"
            ),
            TestVector(
                key = "D491DB16E7B1C39E86CB086B789F5419",
                plaintext = "019F9809DE1711858FAAC3A3BA20FBC3",
                ciphertext = "6363977DE839486297E661C6C9D668EB"
            ),
            TestVector(
                key = "019F9809DE1711858FAAC3A3BA20FBC3",
                plaintext = "6363977DE839486297E661C6C9D668EB",
                ciphertext = "816D5BD0FAE35342BF2A7412C246F752"
            ),
            TestVector(
                key = "6363977DE839486297E661C6C9D668EB",
                plaintext = "816D5BD0FAE35342BF2A7412C246F752",
                ciphertext = "5449ECA008FF5921155F598AF4CED4D0"
            ),
            TestVector(
                key = "816D5BD0FAE35342BF2A7412C246F752",
                plaintext = "5449ECA008FF5921155F598AF4CED4D0",
                ciphertext = "6600522E97AEB3094ED5F92AFCBCDD10"
            ),
            TestVector(
                key = "5449ECA008FF5921155F598AF4CED4D0",
                plaintext = "6600522E97AEB3094ED5F92AFCBCDD10",
                ciphertext = "34C8A5FB2D3D08A170D120AC6D26DBFA"
            ),
            TestVector(
                key = "6600522E97AEB3094ED5F92AFCBCDD10",
                plaintext = "34C8A5FB2D3D08A170D120AC6D26DBFA",
                ciphertext = "28530B358C1B42EF277DE6D4407FC591"
            ),
            TestVector(
                key = "34C8A5FB2D3D08A170D120AC6D26DBFA",
                plaintext = "28530B358C1B42EF277DE6D4407FC591",
                ciphertext = "8A8AB983310ED78C8C0ECDE030B8DCA4"
            ),
            TestVector(
                key = "28530B358C1B42EF277DE6D4407FC591",
                plaintext = "8A8AB983310ED78C8C0ECDE030B8DCA4",
                ciphertext = "48C758A6DFC1DD8B259FA165E1CE2B3C"
            ),
            TestVector(
                key = "8A8AB983310ED78C8C0ECDE030B8DCA4",
                plaintext = "48C758A6DFC1DD8B259FA165E1CE2B3C",
                ciphertext = "CE73C65C101680BBC251C5C16ABCF214"
            ),
            TestVector(
                key = "48C758A6DFC1DD8B259FA165E1CE2B3C",
                plaintext = "CE73C65C101680BBC251C5C16ABCF214",
                ciphertext = "C7ABD74AA060F78B244E24C71342BA89"
            ),
            TestVector(
                key = "CE73C65C101680BBC251C5C16ABCF214",
                plaintext = "C7ABD74AA060F78B244E24C71342BA89",
                ciphertext = "D0F8B3B6409EBCB666D29C916565ABFC"
            ),
            TestVector(
                key = "C7ABD74AA060F78B244E24C71342BA89",
                plaintext = "D0F8B3B6409EBCB666D29C916565ABFC",
                ciphertext = "DD42662908070054544FE09DA4263130"
            ),
            TestVector(
                key = "D0F8B3B6409EBCB666D29C916565ABFC",
                plaintext = "DD42662908070054544FE09DA4263130",
                ciphertext = "7007BACB42F7BF989CF30F78BC50EDCA"
            ),
            TestVector(
                key = "DD42662908070054544FE09DA4263130",
                plaintext = "7007BACB42F7BF989CF30F78BC50EDCA",
                ciphertext = "57B9A18EE97D90F435A16F69F0AC6F16"
            ),
            TestVector(
                key = "7007BACB42F7BF989CF30F78BC50EDCA",
                plaintext = "57B9A18EE97D90F435A16F69F0AC6F16",
                ciphertext = "06181F0D53267ABD8F3BB28455B198AD"
            ),
            TestVector(
                key = "57B9A18EE97D90F435A16F69F0AC6F16",
                plaintext = "06181F0D53267ABD8F3BB28455B198AD",
                ciphertext = "81A12D8449E9040BAAE7196338D8C8F2"
            ),
            TestVector(
                key = "06181F0D53267ABD8F3BB28455B198AD",
                plaintext = "81A12D8449E9040BAAE7196338D8C8F2",
                ciphertext = "BE422651C56F2622DA0201815A95A820"
            ),
            TestVector(
                key = "81A12D8449E9040BAAE7196338D8C8F2",
                plaintext = "BE422651C56F2622DA0201815A95A820",
                ciphertext = "113B19F2D778473990480CEE4DA238D1"
            ),
            TestVector(
                key = "BE422651C56F2622DA0201815A95A820",
                plaintext = "113B19F2D778473990480CEE4DA238D1",
                ciphertext = "E6942E9A86E544CF3E3364F20BE011DF"
            ),
            TestVector(
                key = "113B19F2D778473990480CEE4DA238D1",
                plaintext = "E6942E9A86E544CF3E3364F20BE011DF",
                ciphertext = "87CDC6AA487BFD0EA70188257D9B3859"
            ),
            TestVector(
                key = "E6942E9A86E544CF3E3364F20BE011DF",
                plaintext = "87CDC6AA487BFD0EA70188257D9B3859",
                ciphertext = "D5E2701253DD75A11A4CFB243714BD14"
            ),
            TestVector(
                key = "87CDC6AA487BFD0EA70188257D9B3859",
                plaintext = "D5E2701253DD75A11A4CFB243714BD14",
                ciphertext = "FD24812EEA107A9E6FAB8EABE0F0F48C"
            ),
            TestVector(
                key = "D5E2701253DD75A11A4CFB243714BD14",
                plaintext = "FD24812EEA107A9E6FAB8EABE0F0F48C",
                ciphertext = "DAFA84E31A297F372C3A807100CD783D"
            ),
            TestVector(
                key = "FD24812EEA107A9E6FAB8EABE0F0F48C",
                plaintext = "DAFA84E31A297F372C3A807100CD783D",
                ciphertext = "A55ED2D955EC8950FC0CC93B76ACBF91"
            ),
            TestVector(
                key = "DAFA84E31A297F372C3A807100CD783D",
                plaintext = "A55ED2D955EC8950FC0CC93B76ACBF91",
                ciphertext = "2ABEA2A4BF27ABDC6B6F278993264744"
            ),
            TestVector(
                key = "A55ED2D955EC8950FC0CC93B76ACBF91",
                plaintext = "2ABEA2A4BF27ABDC6B6F278993264744",
                ciphertext = "045383E219321D5A4435C0E491E7DE10"
            ),
            TestVector(
                key = "2ABEA2A4BF27ABDC6B6F278993264744",
                plaintext = "045383E219321D5A4435C0E491E7DE10",
                ciphertext = "7460A4CD4F312F32B1C7A94FA004E934"
            ),
            TestVector(
                key = "045383E219321D5A4435C0E491E7DE10",
                plaintext = "7460A4CD4F312F32B1C7A94FA004E934",
                ciphertext = "6BBF9186D32C2C5895649D746566050A"
            ),
            TestVector(
                key = "7460A4CD4F312F32B1C7A94FA004E934",
                plaintext = "6BBF9186D32C2C5895649D746566050A",
                ciphertext = "CDBDD19ACF40B8AC0328C80054266068"
            ),
            TestVector(
                key = "6BBF9186D32C2C5895649D746566050A",
                plaintext = "CDBDD19ACF40B8AC0328C80054266068",
                ciphertext = "1D2836CAE4223EAB5066867A71B1A1C3"
            ),
            TestVector(
                key = "CDBDD19ACF40B8AC0328C80054266068",
                plaintext = "1D2836CAE4223EAB5066867A71B1A1C3",
                ciphertext = "2D7F37121D0D2416D5E2767FF202061B"
            ),
            TestVector(
                key = "1D2836CAE4223EAB5066867A71B1A1C3",
                plaintext = "2D7F37121D0D2416D5E2767FF202061B",
                ciphertext = "D70736D1ABC7427A121CC816CD66D7FF"
            ),
            TestVector(
                key = "2D7F37121D0D2416D5E2767FF202061B",
                plaintext = "D70736D1ABC7427A121CC816CD66D7FF",
                ciphertext = "AC6CA71CBCBEDCC0EA849FB2E9377865"
            ),
            TestVector(
                key = "D70736D1ABC7427A121CC816CD66D7FF",
                plaintext = "AC6CA71CBCBEDCC0EA849FB2E9377865",
                ciphertext = "307265FF145CBBC7104B3E51C6C1D6B4"
            ),
            TestVector(
                key = "AC6CA71CBCBEDCC0EA849FB2E9377865",
                plaintext = "307265FF145CBBC7104B3E51C6C1D6B4",
                ciphertext = "934B7DB4B3544854DBCA81C4C5DE4EB1"
            ),
            TestVector(
                key = "307265FF145CBBC7104B3E51C6C1D6B4",
                plaintext = "934B7DB4B3544854DBCA81C4C5DE4EB1",
                ciphertext = "18759824AD9823D5961F84377D7EAEBF"
            ),
            TestVector(
                key = "934B7DB4B3544854DBCA81C4C5DE4EB1",
                plaintext = "18759824AD9823D5961F84377D7EAEBF",
                ciphertext = "DEDDAC6029B01574D9BABB099DC6CA6C"
            ),
            TestVector(
                key = "18759824AD9823D5961F84377D7EAEBF",
                plaintext = "DEDDAC6029B01574D9BABB099DC6CA6C",
                ciphertext = "5EA82EEA2244DED42CCA2F835D5615DF"
            ),
            TestVector(
                key = "DEDDAC6029B01574D9BABB099DC6CA6C",
                plaintext = "5EA82EEA2244DED42CCA2F835D5615DF",
                ciphertext = "1E3853F7FFA57091771DD8CDEE9414DE"
            ),
            TestVector(
                key = "5EA82EEA2244DED42CCA2F835D5615DF",
                plaintext = "1E3853F7FFA57091771DD8CDEE9414DE",
                ciphertext = "5C2EBBF75D31F30B5EA26EAC8782D8D1"
            ),
            TestVector(
                key = "1E3853F7FFA57091771DD8CDEE9414DE",
                plaintext = "5C2EBBF75D31F30B5EA26EAC8782D8D1",
                ciphertext = "3A3CFA1F13A136C94D76E5FA4A1109FF"
            ),
            TestVector(
                key = "5C2EBBF75D31F30B5EA26EAC8782D8D1",
                plaintext = "3A3CFA1F13A136C94D76E5FA4A1109FF",
                ciphertext = "91630CF96003B8032E695797E313A553"
            ),
            TestVector(
                key = "3A3CFA1F13A136C94D76E5FA4A1109FF",
                plaintext = "91630CF96003B8032E695797E313A553",
                ciphertext = "137A24CA47CD12BE818DF4D2F4355960"
            ),
            TestVector(
                key = "91630CF96003B8032E695797E313A553",
                plaintext = "137A24CA47CD12BE818DF4D2F4355960",
                ciphertext = "BCA724A54533C6987E14AA827952F921"
            ),
            TestVector(
                key = "137A24CA47CD12BE818DF4D2F4355960",
                plaintext = "BCA724A54533C6987E14AA827952F921",
                ciphertext = "6B459286F3FFD28D49F15B1581B08E42"
            ),
            TestVector(
                key = "BCA724A54533C6987E14AA827952F921",
                plaintext = "6B459286F3FFD28D49F15B1581B08E42",
                ciphertext = "5D9D4EEFFA9151575524F115815A12E0"
            )
        )

        for (testVector in testVectors) {
            val keyObject = Twofish.processKey(testVector.keyArray)

            val computedCiphertext = Twofish.blockEncrypt(testVector.plaintextArray, 0, keyObject)
            assertEquals(testVector.ciphertextArray.toList(), computedCiphertext.toList())

            val computedPlaintext = Twofish.blockDecrypt(testVector.ciphertextArray, 0, keyObject)
            assertEquals(testVector.plaintextArray.toList(), computedPlaintext.toList())
        }
    }
}
