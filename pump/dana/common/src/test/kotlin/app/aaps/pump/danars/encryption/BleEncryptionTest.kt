package app.aaps.pump.danars.encryption

import app.aaps.shared.tests.TestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BleEncryptionTest() : TestBase() {

    lateinit var sut: BleEncryption

    @BeforeEach
    fun prepareMock() {
        sut = BleEncryption()
    }

    @Test
    fun ble5Flow() {
        val deviceName = "UHH00002TI"
        Assertions.assertTrue(
            sut.getEncryptedPacket(BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__PUMP_CHECK, null, deviceName)
                .contentEquals(byteArrayOf(-91, -91, 12, -28, -14, -56, -83, -70, -83, -43, -62, -83, -41, -90, -44, 12, -58, 90, 90))
        )
        Assertions.assertTrue(
            sut.getDecryptedPacket(byteArrayOf(-91, -91, 14, -25, -14, -46, -82, -65, -108, -75, -29, -91, -43, -57, -82, -47, -61, -122, 95, 90, 90))
                .contentEquals(byteArrayOf(2, 0, 79, 75, 77, 9, 80, 17, 56, 48, 53, 51, 52, 49))
        )
        sut.setEnhancedEncryption(EncryptionType.ENCRYPTION_BLE5)
        sut.setBle5Key(byteArrayOf(56, 48, 53, 51, 52, 49))
        Assertions.assertTrue(
            sut.getEncryptedPacket(BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__TIME_INFORMATION, byteArrayOf(0, 0, 0, 0), null)
                .contentEquals(byteArrayOf(-91, -91, 6, -28, -13, -99, -24, -29, -101, -70, 83, 90, 90))
        )
        Assertions.assertTrue(
            sut.getDecryptedPacket(byteArrayOf(-91, -91, 3, -25, -13, -99, -49, -37, 90, 90))
                .contentEquals(byteArrayOf(2, 1, 0))
        )
        Assertions.assertTrue(
            sut.getEncryptedPacket(BleEncryption.DANAR_PACKET__OPCODE_ETC__KEEP_CONNECTION, byteArrayOf(), null)
                .contentEquals(byteArrayOf(-91, -91, 2, 68, 13, -42, -108, 90, 90))
        )
        Assertions.assertTrue(
            sut.encryptSecondLevelPacket(byteArrayOf(-91, -91, 2, 68, 13, -42, -108, 90, 90))
                .contentEquals(byteArrayOf(19, 19, -119, -11, 120, -52, -16, 78, 78))
        )
        Assertions.assertTrue(
            sut.getEncryptedPacket(BleEncryption.DANAR_PACKET__OPCODE_REVIEW__GET_SHIPPING_INFORMATION, byteArrayOf(), null)
                .contentEquals(byteArrayOf(-91, -91, 2, 68, -46, -5, 14, 90, 90))
        )
        Assertions.assertTrue(
            sut.encryptSecondLevelPacket(byteArrayOf(-91, -91, 2, 68, -46, -5, 14, 90, 90))
                .contentEquals(byteArrayOf(19, 19, -119, -11, -116, 30, 72, 78, 78))
        )
        Assertions.assertTrue(
            sut.decryptSecondLevelPacket(byteArrayOf(19, 19))
                .contentEquals(byteArrayOf(-86, -86))
        )
        Assertions.assertTrue(
            sut.getDecryptedPacket(byteArrayOf(-86, -86, 18, 87, -46, -56, -83, -70, -83, -43, -62, -83, -41, -90, -44, -93, -96, -36, -15, -11, -118, 15, -23, -18, -18))
                .contentEquals(byteArrayOf(-78, 32, 85, 72, 72, 48, 48, 48, 48, 50, 84, 73, 70, 82, 65, 20, 7, 23))
        )
        Assertions.assertTrue(
            sut.getEncryptedPacket(BleEncryption.DANAR_PACKET__OPCODE__APS_HISTORY_EVENTS, byteArrayOf(0, 1, 1, 0, 0, 0), null)
                .contentEquals(byteArrayOf(-91, -91, 8, 68, 48, -99, -28, -13, -99, -27, -14, -11, -22, 90, 90))
        )
        Assertions.assertTrue(
            sut.getDecryptedPacket(byteArrayOf(-86, -86, 13, 87, 48, -102, 70, -13, -11, 6, 37, -92, -27, -14, -99, -39, -31, 46, -18, -18))
                .contentEquals(byteArrayOf(-78, -62, 7, -93, 1, 104, -29, -41, 57, 0, 0, 0, 60))
        )
    }

    @Test
    fun standardFlow() {
        val deviceName = "RLI00006DB"
        Assertions.assertTrue(
            sut.getEncryptedPacket(BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__PUMP_CHECK, null, deviceName)
                .contentEquals(byteArrayOf(-91, -91, 12, -26, -10, -44, -85, -65, -74, -41, -58, -74, -47, -78, -60, -70, 11, 90, 90))
        )
        Assertions.assertTrue(
            sut.getDecryptedPacket(byteArrayOf(-91, -91, 4, -27, -10, -55, -84, 51, 112, 90, 90))
                .contentEquals(byteArrayOf(2, 0, 79, 75))
        )
        sut.setEnhancedEncryption(EncryptionType.ENCRYPTION_DEFAULT)
        Assertions.assertTrue(
            sut.getEncryptedPacket(BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__CHECK_PASSKEY, byteArrayOf(44, 79), null)
                .contentEquals(byteArrayOf(-91, -91, 4, -26, 38, -55, -53, 126, -94, 90, 90))
        )
        Assertions.assertTrue(
            sut.getDecryptedPacket(byteArrayOf(-91, -91, 3, -27, 38, -122, 87, 69, 90, 90))
                .contentEquals(byteArrayOf(2, -48, 0))
        )
        Assertions.assertTrue(
            sut.getEncryptedPacket(BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__TIME_INFORMATION, null, null)
                .contentEquals(byteArrayOf(-91, -91, 2, -26, -9, -113, 119, 90, 90))
        )
        Assertions.assertTrue(
            sut.getDecryptedPacket(byteArrayOf(-91, -91, 10, -27, -9, -97, -19, -16, -109, -34, -36, 16, -5, 0, -118, 90, 90))
                .contentEquals(byteArrayOf(2, 1, 25, 10, 6, 21, 57, 42, -106, 28))
        )
        Assertions.assertTrue(
            sut.getEncryptedPacket(BleEncryption.DANAR_PACKET__OPCODE_ETC__KEEP_CONNECTION, byteArrayOf(), null)
                .contentEquals(byteArrayOf(-91, -91, 2, -23, -59, 56, 21, 90, 90))
        )
        Assertions.assertTrue(
            sut.getDecryptedPacket(byteArrayOf(-91, -91, 3, -6, -59, 41, -78, 40, 90, 90))
                .contentEquals(byteArrayOf(-78, -1, 0))
        )
        Assertions.assertTrue(
            sut.getEncryptedPacket(BleEncryption.DANAR_PACKET__OPCODE__APS_HISTORY_EVENTS, byteArrayOf(0, 1, 1, 0, 0, 0), null)
                .contentEquals(byteArrayOf(-91, -91, 8, -23, -8, 41, 42, 88, 74, 72, 58, -44, 76, 90, 90))
        )
        Assertions.assertTrue(
            sut.getDecryptedPacket(byteArrayOf(-91, -91, 13, -6, -8, 44, 50, 83, 79, 94, 35, 53, 43, 92, 74, 72, -80, 37, 90, 90))
                .contentEquals(byteArrayOf(-78, -62, 5, 25, 10, 5, 22, 25, 28, 0, 5, 0, 0))
        )
    }

    @Test
    fun rsV3Flow() {
        val deviceName = "RLI00007DB"
        Assertions.assertTrue(
            sut.getEncryptedPacket(BleEncryption.DANAR_PACKET__OPCODE_ENCRYPTION__PUMP_CHECK, null, deviceName)
                .contentEquals(byteArrayOf(-91, -91, 12, -26, -9, -44, -85, -66, -74, -41, -57, -74, -48, -77, -60, -93, -46, 90, 90))
        )
        Assertions.assertTrue(
            sut.getDecryptedPacket(byteArrayOf(-91, -91, 9, -27, -9, -55, -84, -70, -125, -73, -26, -108, -41, -95, 90, 90))
                .contentEquals(byteArrayOf(2, 0, 79, 75, 77, 5, 80, 17, 18))
        )
        sut.setEnhancedEncryption(EncryptionType.ENCRYPTION_RSv3)
        sut.setPairingKeys(byteArrayOf(75, -59, 93, -35, -29, 55), byteArrayOf(-69, -71, -55), 0)
        Assertions.assertTrue(
            sut.getEncryptedPacket(BleEncryption.DANAR_PACKET__OPCODE_NOTIFY__DELIVERY_COMPLETE, byteArrayOf(0), null)
                .contentEquals(byteArrayOf(-91, -91, 3, -26, -10, -122, -25, -74, 90, 90))
        )
        Assertions.assertTrue(
            sut.getDecryptedPacket(byteArrayOf(-91, -91, 3, -27, -10, -122, -51, -34, 90, 90))
                .contentEquals(byteArrayOf(2, 1, 0))
        )
        Assertions.assertTrue(
            sut.getEncryptedPacket(BleEncryption.DANAR_PACKET__OPCODE_ETC__KEEP_CONNECTION, byteArrayOf(), null)
                .contentEquals(byteArrayOf(-91, -91, 2, 70, 8, -66, 76, 90, 90))
        )
        Assertions.assertTrue(
            sut.encryptSecondLevelPacket(byteArrayOf(-91, -91, 2, 70, 8, -66, 76, 90, 90))
                .contentEquals(byteArrayOf(-60, 7, 116, -45, -34, -43, 5, 88, -72))
        )
        Assertions.assertTrue(
            sut.decryptSecondLevelPacket(byteArrayOf(94, 106, 22, -94, -28, -118, 86, -105, 91, 4))
                .contentEquals(byteArrayOf(-91, -91, 3, 85, 8, -122, -87, 3, 90, 90))
        )
        Assertions.assertTrue(
            sut.getDecryptedPacket(byteArrayOf(-91, -91, 3, 85, 8, -122, -87, 3, 90, 90))
                .contentEquals(byteArrayOf(-78, -1, 0))
        )

        Assertions.assertTrue(
            sut.getEncryptedPacket(BleEncryption.DANAR_PACKET__OPCODE_REVIEW__GET_SHIPPING_INFORMATION, byteArrayOf(), null)
                .contentEquals(byteArrayOf(-91, -91, 2, 70, -41, -124, -54, 90, 90))
        )
        Assertions.assertTrue(
            sut.encryptSecondLevelPacket(byteArrayOf(-91, -91, 2, 70, -41, -124, -54, 90, 90))
                .contentEquals(byteArrayOf(-106, 86, 40, 91, 120, 116, 89, -88, 91))
        )
        Assertions.assertTrue(
            sut.decryptSecondLevelPacket(byteArrayOf(29, 99, -106, 31, 119, 78, 125, 73, 1, -102, -45, -55, 43, 10, 22, -63, -121, -46, 38, 102))
                .contentEquals(byteArrayOf(-91, -91, 18, 85, -41, -44, -85, -66, -74, -41, -57, -74, -48, -77, -60, -92, -83, -51, -10, -4))
        )
        Assertions.assertTrue(
            sut.decryptSecondLevelPacket(byteArrayOf(25, 74, -9, 105, 101))
                .contentEquals(byteArrayOf(-121, -4, -45, 90, 90))
        )
        Assertions.assertTrue(
            sut.getDecryptedPacket(byteArrayOf(-91, -91, 18, 85, -41, -44, -85, -66, -74, -41, -57, -74, -48, -77, -60, -92, -83, -51, -10, -4, -121, -4, -45, 90, 90))
                .contentEquals(byteArrayOf(-78, 32, 82, 76, 73, 48, 48, 48, 48, 55, 68, 66, 67, 90, 75, 17, 11, 1))
        )

    }
}