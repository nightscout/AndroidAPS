package app.aaps.pump.danars.encryption

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleEncryption @Inject constructor() {

    val deviceName = UByteArray(10)
    val passKey = UByteArray(2)
    val cfPassKey = UByteArray(2)
    val password = UByteArray(2)
    val timeInfo = UByteArray(6)
    var pairingKey: UByteArray? = null
    var randomPairingKey: UByteArray? = null
    var ble5PairingKey: UByteArray? = null
    var encryptionBle5Key = UByteArray(3)
    var randomSyncKey: UByte = 0x00u
    var connectionState = 0
    var securityVersion: EncryptionType = EncryptionType.ENCRYPTION_DEFAULT

    fun getEncryptedPacket(opcode: Int, bytes: ByteArray?, deviceName: String?): ByteArray {
        assert(deviceName == null || deviceName.length == 10)
        setDeviceName(deviceName)

        when (opcode) {
            DANAR_PACKET__OPCODE_ENCRYPTION__PUMP_CHECK          -> {
                connectionState = 0
                val size = 9 + 10
                val array = UByteArray(size)

                array[DANAR_PACKET__START_POS] = DANAR_PACKET__START_PACKET
                array[DANAR_PACKET__START_POS + 1] = DANAR_PACKET__START_PACKET
                array[DANAR_PACKET__LENGTH_POS] = (size - 7).toUByte()
                array[DANAR_PACKET__TYPE_POS] = DANAR_PACKET__TYPE_ENCRYPTION_REQUEST
                @Suppress("KotlinConstantConditions")
                array[4] = opcode.toUByte()

                for (i in 0..<10) array[5 + i] = this.deviceName[i]

                val checkSum = generateCrc(array.from(3, size - 7))
                array[size - 4] = (checkSum shr 8).toUByte()
                array[size - 3] = checkSum.toUByte()
                array[size - 2] = DANAR_PACKET__END_PACKET
                array[size - 1] = DANAR_PACKET__END_PACKET

                encodeArrayBySn(array)
                return array.toByteArray()
            }

            DANAR_PACKET__OPCODE_ENCRYPTION__CHECK_PASSKEY       -> {
                connectionState = 0
                val size = 9 + (bytes?.size ?: 0)
                val array = UByteArray(size)

                array[DANAR_PACKET__START_POS] = DANAR_PACKET__START_PACKET
                array[DANAR_PACKET__START_POS + 1] = DANAR_PACKET__START_PACKET
                array[DANAR_PACKET__LENGTH_POS] = (size - 7).toUByte()
                array[DANAR_PACKET__TYPE_POS] = DANAR_PACKET__TYPE_ENCRYPTION_REQUEST
                array[DANAR_PACKET__OPCODE_POS] = opcode.toUByte()

                bytes?.let { bytes ->
                    for (i in 0..<bytes.size) {
                        passKey[0 + i] = encodeByteBySn(bytes[i].toUByte())
                        array[5 + i] = passKey[0 + i]
                    }
                }

                val checkSum = generateCrc(array.from(3, size - 7))
                array[size - 4] = (checkSum shr 8).toUByte()
                array[size - 3] = checkSum.toUByte()
                array[size - 2] = DANAR_PACKET__END_PACKET
                array[size - 1] = DANAR_PACKET__END_PACKET

                encodeArrayBySn(array)
                return array.toByteArray()
            }

            DANAR_PACKET__OPCODE_ENCRYPTION__PASSKEY_REQUEST     -> {
                connectionState = 0
                val size = 9
                val array = UByteArray(size)

                array[DANAR_PACKET__START_POS] = DANAR_PACKET__START_PACKET
                array[DANAR_PACKET__START_POS + 1] = DANAR_PACKET__START_PACKET
                array[DANAR_PACKET__LENGTH_POS] = (size - 7).toUByte()
                array[DANAR_PACKET__TYPE_POS] = DANAR_PACKET__TYPE_ENCRYPTION_REQUEST
                array[DANAR_PACKET__OPCODE_POS] = opcode.toUByte()

                val checkSum = generateCrc(array.from(3, size - 7))
                array[size - 4] = (checkSum shr 8).toUByte()
                array[size - 3] = checkSum.toUByte()
                array[size - 2] = DANAR_PACKET__END_PACKET
                array[size - 1] = DANAR_PACKET__END_PACKET

                encodeArrayBySn(array)
                return array.toByteArray()
            }

            DANAR_PACKET__OPCODE_ENCRYPTION__TIME_INFORMATION    -> {
                connectionState = 1

                val byteArray = bytes?.toUByteArray()
                if (securityVersion == EncryptionType.ENCRYPTION_BLE5) {
                    // hide used ID little bit
                    byteArray?.let { byteArray ->
                        byteArray[1] = BLE_UNIQUE_APP_ID1 xor 0x1au
                        byteArray[2] = BLE_UNIQUE_APP_ID2 xor 0xc0u
                        byteArray[3] = BLE_UNIQUE_APP_ID3 xor 0xa9u
                        byteArray[1] = byteArray[1] xor 0x1au
                        byteArray[2] = byteArray[2] xor 0xc0u
                        byteArray[3] = byteArray[3] xor 0xa9u
                    }
                }

                val size = 9 + (bytes?.size ?: 0)
                val array = UByteArray(size)

                array[DANAR_PACKET__START_POS] = DANAR_PACKET__START_PACKET
                array[DANAR_PACKET__START_POS + 1] = DANAR_PACKET__START_PACKET
                array[DANAR_PACKET__LENGTH_POS] = (size - 7).toUByte()
                array[DANAR_PACKET__TYPE_POS] = DANAR_PACKET__TYPE_ENCRYPTION_REQUEST
                array[DANAR_PACKET__OPCODE_POS] = opcode.toUByte()

                bytes?.let { bytes -> for (i in 0..<bytes.size) array[5 + i] = byteArray!![i] }

                val checkSum = generateCrc(array.from(3, size - 7))
                array[size - 4] = (checkSum shr 8).toUByte()
                array[size - 3] = checkSum.toUByte()
                array[size - 2] = DANAR_PACKET__END_PACKET
                array[size - 1] = DANAR_PACKET__END_PACKET

                encodeArrayBySn(array)
                return array.toByteArray()
            }

            DANAR_PACKET__OPCODE_ENCRYPTION__GET_PUMP_CHECK      -> {
                connectionState = 0

                val size = 9
                val array = UByteArray(size)

                array[DANAR_PACKET__START_POS] = DANAR_PACKET__START_PACKET
                array[DANAR_PACKET__START_POS + 1] = DANAR_PACKET__START_PACKET
                array[DANAR_PACKET__LENGTH_POS] = (size - 7).toUByte()
                array[DANAR_PACKET__TYPE_POS] = DANAR_PACKET__TYPE_ENCRYPTION_REQUEST
                array[DANAR_PACKET__OPCODE_POS] = opcode.toUByte()

                val checkSum = generateCrc(array.from(3, size - 7))
                array[size - 4] = (checkSum shr 8).toUByte()
                array[size - 3] = checkSum.toUByte()
                array[size - 2] = DANAR_PACKET__END_PACKET
                array[size - 1] = DANAR_PACKET__END_PACKET

                encodeArrayBySn(array)
                return array.toByteArray()
            }

            DANAR_PACKET__OPCODE_ENCRYPTION__GET_EASY_MENU_CHECK -> {
                connectionState = 0

                val size = 9
                val array = UByteArray(size)

                array[DANAR_PACKET__LENGTH_POS] = (size - 7).toUByte()
                array[DANAR_PACKET__TYPE_POS] = DANAR_PACKET__TYPE_ENCRYPTION_REQUEST
                array[DANAR_PACKET__OPCODE_POS] = opcode.toUByte()

                val checkSum = generateCrc(array.from(3, size - 7))
                array[size - 4] = (checkSum shr 8).toUByte()
                array[size - 3] = checkSum.toUByte()
                array[size - 2] = DANAR_PACKET__END_PACKET
                array[size - 1] = DANAR_PACKET__END_PACKET

                encodeArrayBySn(array)
                return array.toByteArray()
            }

            else                                                 -> {
                val size = 9 + (bytes?.size ?: 0)
                val array = UByteArray(size)

                array[DANAR_PACKET__START_POS] = DANAR_PACKET__START_PACKET
                array[DANAR_PACKET__START_POS + 1] = DANAR_PACKET__START_PACKET
                array[DANAR_PACKET__LENGTH_POS] = (size - 7).toUByte()
                array[DANAR_PACKET__TYPE_POS] = DANAR_PACKET__TYPE_COMMAND
                array[DANAR_PACKET__OPCODE_POS] = opcode.toUByte()

                bytes?.let { bytes -> for (i in 0..<bytes.size) array[5 + i] = bytes[i].toUByte() }

                val checkSum = generateCrc(array.from(3, size - 7))
                array[size - 4] = (checkSum shr 8).toUByte()
                array[size - 3] = checkSum.toUByte()
                array[size - 2] = DANAR_PACKET__END_PACKET
                array[size - 1] = DANAR_PACKET__END_PACKET

                encodeArrayBySn(array)
                if (securityVersion == EncryptionType.ENCRYPTION_DEFAULT) {
                    encodeArrayByTime(array, timeInfo)
                    encodeArrayByPassword(array, password)
                    encodeArrayByCfPassKey(array)
                }
                return array.toByteArray()
            }
        }
    }

    fun getDecryptedPacket(bytes: ByteArray): ByteArray? {
        val array = bytes.toUByteArray()
        val size = array.size

        encodeArrayBySn(array)
        if (connectionState == 2 && securityVersion == EncryptionType.ENCRYPTION_DEFAULT) {
            encodeArrayByTime(array, timeInfo)
            encodeArrayByPassword(array, password)
            encodeArrayByCfPassKey(array)
        }

        if (array[DANAR_PACKET__LENGTH_POS] != (size - 7).toUByte())
            return null

        val checkSum = generateCrc(array.from(3, size - 7))
        val checkSum1 = (checkSum shr 8).toUByte()
        val checkSum2 = checkSum.toUByte()
        if (checkSum1 != array[size - 4] || checkSum2 != array[size - 3])
            return null

        val arr = UByteArray(size - 7)
        for (i in 0..<size - 7) arr[i] = array[i + 3]
        if (arr[0] == DANAR_PACKET__TYPE_ENCRYPTION_RESPONSE &&
            arr[1] == DANAR_PACKET__OPCODE_ENCRYPTION__CHECK_PASSKEY.toUByte() &&
            arr[2] == 0x00.toUByte()
        ) {
            cfPassKey[0] = passKey[0]
            cfPassKey[1] = passKey[1]
        }
        if (arr[0] == DANAR_PACKET__TYPE_ENCRYPTION_RESPONSE &&
            arr[1] == DANAR_PACKET__OPCODE_ENCRYPTION__PASSKEY_RETURN.toUByte()
        ) {
            passKey[0] = arr[2]
            passKey[1] = arr[3]
            cfPassKey[0] = passKey[0]
            cfPassKey[1] = passKey[1]
            arr[2] = encodeByteBySn(arr[2])
            arr[3] = encodeByteBySn(arr[3])
        }

        if (arr[0] == DANAR_PACKET__TYPE_ENCRYPTION_RESPONSE &&
            arr[1] == DANAR_PACKET__OPCODE_ENCRYPTION__TIME_INFORMATION.toUByte()
        ) {
            when (securityVersion) {
                EncryptionType.ENCRYPTION_RSv3 -> connectionState = if (pairingKey == null && randomPairingKey == null) 1 else 2
                EncryptionType.ENCRYPTION_BLE5 -> connectionState = if (ble5PairingKey == null) 1 else 2

                else                           -> {
                    if (size - 7 != 10) return null
                    connectionState = 2
                    for (i in 0..5) timeInfo[i] = arr[2 + i]
                    for (i in 0..1) password[i] = arr[8 + i]
                    password[0] = password[0] xor 0x87u
                    password[1] = password[1] xor 0x0Du
                }
            }
        }
        return arr.toByteArray()
    }

    fun setPairingKeys(pairingKeyParam: ByteArray, randomPairingKeyParam: ByteArray, randomSyncKeyParam: Byte) {
        pairingKey = pairingKeyParam.toUByteArray()
        randomPairingKey = randomPairingKeyParam.toUByteArray()
        if (randomSyncKeyParam == 0.toByte()) {
            initialRandomSyncKey()
        } else {
            // Encryption Random Sync Key
            randomSyncKey = randomSyncKeyParam.toUByte()
            decryptionRandomSyncKey()
        }
    }

    val bleEncryptionMatrix = ubyteArrayOf(
        0x63u, 0x7cu, 0x77u, 0x7bu, 0xf2u, 0x6bu, 0x6fu, 0xc5u, 0x30u, 0x01u,
        0x67u, 0x2bu, 0xfeu, 0xd7u, 0xabu, 0x76u, 0x6cu, 0x70u, 0x48u, 0x50u,
        0xfdu, 0xedu, 0xb9u, 0xdau, 0x5eu, 0x15u, 0x46u, 0x57u, 0xa7u, 0x8du,
        0x9du, 0x84u, 0xb7u, 0xfdu, 0x93u, 0x26u, 0x36u, 0x3fu, 0xf7u, 0xccu,
        0x34u, 0xa5u, 0xe5u, 0xf1u, 0x71u, 0xd8u, 0x31u, 0x15u, 0x47u, 0xf1u,
        0x1au, 0x71u, 0x1du, 0x29u, 0xc5u, 0x89u, 0x6fu, 0xb7u, 0x62u, 0x0eu,
        0xaau, 0x18u, 0xbeu, 0x1bu, 0x09u, 0x83u, 0x2cu, 0x1au, 0x1bu, 0x6eu,
        0x5au, 0xa0u, 0x52u, 0x3bu, 0xd6u, 0xb3u, 0x29u, 0xe3u, 0x2fu, 0x84u,
        0x53u, 0xd1u, 0xa0u, 0xedu, 0x20u, 0xfcu, 0xb1u, 0x5bu, 0x6au, 0xcbu,
        0xbeu, 0x39u, 0x4au, 0x4cu, 0x58u, 0xcfu, 0xb0u, 0x54u, 0xbbu, 0x16u
    )

    fun setBle5Key(ble5Key: ByteArray) {
        ble5PairingKey = ble5Key.toUByteArray()
        ble5PairingKey?.let { ble5PairingKey ->
            encryptionBle5Key[0] = bleEncryptionMatrix[((ble5PairingKey[0] - 0x30u) * 10u + (ble5PairingKey[1] - 0x30u)).toInt()]
            encryptionBle5Key[1] = bleEncryptionMatrix[((ble5PairingKey[2] - 0x30u) * 10u + (ble5PairingKey[3] - 0x30u)).toInt()]
            encryptionBle5Key[2] = bleEncryptionMatrix[((ble5PairingKey[4] - 0x30u) * 10u + (ble5PairingKey[5] - 0x30u)).toInt()]
        }

    }

    fun setEnhancedEncryption(securityVersion: EncryptionType) {
        this.securityVersion = securityVersion
    }

    val encryptionMatrix = ubyteArrayOf(
        0x63u, 0x7cu, 0x77u, 0x7bu, 0xf2u, 0x6bu, 0x6fu, 0xc5u, 0x30u, 0x01u, 0x67u, 0x2bu, 0xfeu, 0xd7u, 0xabu, 0x76u,
        0xcau, 0x82u, 0xc9u, 0x7du, 0xfau, 0x59u, 0x47u, 0xf0u, 0xadu, 0xd4u, 0xa2u, 0xafu, 0x9cu, 0xa4u, 0x72u, 0xc0u,
        0xb7u, 0xfdu, 0x93u, 0x26u, 0x36u, 0x3fu, 0xf7u, 0xccu, 0x34u, 0xa5u, 0xe5u, 0xf1u, 0x71u, 0xd8u, 0x31u, 0x15u,
        0x04u, 0xc7u, 0x23u, 0xc3u, 0x18u, 0x96u, 0x05u, 0x9au, 0x07u, 0x12u, 0x80u, 0xe2u, 0xebu, 0x27u, 0xb2u, 0x75u,
        0x09u, 0x83u, 0x2cu, 0x1au, 0x1bu, 0x6eu, 0x5au, 0xa0u, 0x52u, 0x3bu, 0xd6u, 0xb3u, 0x29u, 0xe3u, 0x2fu, 0x84u,
        0x53u, 0xd1u, 0x00u, 0xedu, 0x20u, 0xfcu, 0xb1u, 0x5bu, 0x6au, 0xcbu, 0xbeu, 0x39u, 0x4au, 0x4cu, 0x58u, 0xcfu,
        0xd0u, 0xefu, 0xaau, 0xfbu, 0x43u, 0x4du, 0x33u, 0x85u, 0x45u, 0xf9u, 0x02u, 0x7fu, 0x50u, 0x3cu, 0x9fu, 0xa8u,
        0x51u, 0xa3u, 0x40u, 0x8fu, 0x92u, 0x9du, 0x38u, 0xf5u, 0xbcu, 0xb6u, 0xdau, 0x21u, 0x10u, 0xffu, 0xf3u, 0xd2u,
        0xcdu, 0x0cu, 0x13u, 0xecu, 0x5fu, 0x97u, 0x44u, 0x17u, 0xc4u, 0xa7u, 0x7eu, 0x3du, 0x64u, 0x5du, 0x19u, 0x73u,
        0x60u, 0x81u, 0x4fu, 0xdcu, 0x22u, 0x2au, 0x90u, 0x88u, 0x46u, 0xeeu, 0xb8u, 0x14u, 0xdeu, 0x5eu, 0x0bu, 0xdbu,
        0xe0u, 0x32u, 0x3au, 0x0au, 0x49u, 0x06u, 0x24u, 0x5cu, 0xc2u, 0xd3u, 0xacu, 0x62u, 0x91u, 0x95u, 0xe4u, 0x79u,
        0xe7u, 0xc8u, 0x37u, 0x6du, 0x8du, 0xd5u, 0x4eu, 0xa9u, 0x6cu, 0x56u, 0xf4u, 0xeau, 0x65u, 0x7au, 0xaeu, 0x08u,
        0xbau, 0x78u, 0x25u, 0x2eu, 0x1cu, 0xa6u, 0xb4u, 0xc6u, 0xe8u, 0xddu, 0x74u, 0x1fu, 0x4bu, 0xbdu, 0x8bu, 0x8au,
        0x70u, 0x3eu, 0xb5u, 0x66u, 0x48u, 0x03u, 0xf6u, 0x0eu, 0x61u, 0x35u, 0x57u, 0xb9u, 0x86u, 0xc1u, 0x1du, 0x9eu,
        0xe1u, 0xf8u, 0x98u, 0x11u, 0x69u, 0xd9u, 0x8eu, 0x94u, 0x9bu, 0x1eu, 0x87u, 0xe9u, 0xceu, 0x55u, 0x28u, 0xdfu,
        0x8cu, 0xa1u, 0x89u, 0x0du, 0xbfu, 0xe6u, 0x42u, 0x68u, 0x41u, 0x99u, 0x2du, 0x0fu, 0xb0u, 0x54u, 0xbbu, 0x16u
    )

    fun encryptSecondLevelPacket(bytes: ByteArray): ByteArray {
        val array = bytes.toUByteArray()
        val size = array.size


        if (securityVersion == EncryptionType.ENCRYPTION_RSv3) {
            if (array[DANAR_PACKET__START_POS] == DANAR_PACKET__START_PACKET && array[DANAR_PACKET__START_POS + 1] == DANAR_PACKET__START_PACKET) {
                array[DANAR_PACKET__START_POS] = DANAR_PACKET__ENCRYPTION_START
                array[DANAR_PACKET__START_POS + 1] = DANAR_PACKET__ENCRYPTION_START
            }

            if (array[size - 2] == DANAR_PACKET__END_PACKET && array[size - 1] == DANAR_PACKET__END_PACKET) {
                array[size - 2] = DANAR_PACKET__ENCRYPTION_END
                array[size - 1] = DANAR_PACKET__ENCRYPTION_END
            }
            for (i in 0..<size) {
                pairingKey?.let { pairingKey ->
                    randomPairingKey?.let { randomPairingKey ->
                        array[i] = array[i] xor pairingKey[0]
                        array[i] = array[i].minusUByte(randomSyncKey)
                        array[i] = array[i].switchLoHi()
                        array[i] = array[i].plusUByte(pairingKey[1])
                        array[i] = array[i] xor pairingKey[2]
                        array[i] = array[i].switchLoHi()
                        array[i] = array[i].minusUByte(pairingKey[3])
                        array[i] = array[i] xor pairingKey[4]
                        array[i] = array[i].switchLoHi()
                        array[i] = array[i] xor pairingKey[5]
                        array[i] = array[i] xor randomSyncKey
                        array[i] = array[i] xor encryptionMatrix[pairingKey[0]]
                        array[i] = array[i].plusUByte(encryptionMatrix[pairingKey[1]])
                        array[i] = array[i].minusUByte(encryptionMatrix[pairingKey[2]])
                        array[i] = array[i].switchLoHi()
                        array[i] = array[i] xor encryptionMatrix[pairingKey[3]]
                        array[i] = array[i].plusUByte(encryptionMatrix[pairingKey[4]])
                        array[i] = array[i].minusUByte(encryptionMatrix[pairingKey[5]])
                        array[i] = array[i].switchLoHi()
                        array[i] = array[i] xor encryptionMatrix[randomPairingKey[0]]
                        array[i] = array[i].plusUByte(encryptionMatrix[randomPairingKey[1]])
                        array[i] = array[i].minusUByte(encryptionMatrix[randomPairingKey[2]])
                        randomSyncKey = array[i]
                    } ?: error("randomPairingKey is null")
                } ?: error("pairingKey is null")
            }
        } else if (securityVersion == EncryptionType.ENCRYPTION_BLE5) {
            if (array[DANAR_PACKET__START_POS] == DANAR_PACKET__START_PACKET && array[DANAR_PACKET__START_POS + 1] == DANAR_PACKET__START_PACKET) {
                array[DANAR_PACKET__START_POS] = DANAI_PACKET__ENCRYPTION_START
                array[DANAR_PACKET__START_POS + 1] = DANAI_PACKET__ENCRYPTION_START
            }

            if (array[size - 2] == DANAR_PACKET__END_PACKET && array[size - 1] == DANAR_PACKET__END_PACKET) {
                array[size - 2] = DANAI_PACKET__ENCRYPTION_END
                array[size - 1] = DANAI_PACKET__ENCRYPTION_END
            }
            for (i in 0..<size) {
                array[i] = array[i].plusUByte(encryptionBle5Key[0] and 0xffu)
                array[i] = array[i].switchLoHi()
                array[i] = array[i].minusUByte(encryptionBle5Key[1] and 0xffu)
                array[i] = array[i] xor (encryptionBle5Key[2] and 0xffu)
            }
        }
        return array.toByteArray()
    }

    fun decryptSecondLevelPacket(bytes: ByteArray): ByteArray {
        val size = bytes.size
        val array = bytes.toUByteArray()
        if (size > 0) {
            for (i in 0..<size) {
                if (securityVersion == EncryptionType.ENCRYPTION_RSv3) {
                    val tempData = array[i]
                    randomPairingKey?.let { randomPairingKey ->
                        pairingKey?.let { pairingKey ->
                            array[i] = array[i].plusUByte(encryptionMatrix[randomPairingKey[2]])
                            array[i] = array[i].minusUByte(encryptionMatrix[randomPairingKey[1]])
                            array[i] = array[i] xor encryptionMatrix[randomPairingKey[0]]
                            array[i] = array[i].switchLoHi()
                            array[i] = array[i].plusUByte(encryptionMatrix[pairingKey[5]])
                            array[i] = array[i].minusUByte(encryptionMatrix[pairingKey[4]])
                            array[i] = array[i] xor encryptionMatrix[pairingKey[3]]
                            array[i] = array[i].switchLoHi()
                            array[i] = array[i].plusUByte(encryptionMatrix[pairingKey[2]])
                            array[i] = array[i].minusUByte(encryptionMatrix[pairingKey[1]])
                            array[i] = array[i] xor encryptionMatrix[pairingKey[0]]
                            array[i] = array[i] xor randomSyncKey
                            array[i] = array[i] xor pairingKey[5]
                            array[i] = array[i].switchLoHi()
                            array[i] = array[i] xor pairingKey[4]
                            array[i] = array[i].plusUByte(pairingKey[3])
                            array[i] = array[i].switchLoHi()
                            array[i] = array[i] xor pairingKey[2]
                            array[i] = array[i].minusUByte(pairingKey[1])
                            array[i] = array[i].switchLoHi()
                            array[i] = array[i].plusUByte(randomSyncKey)
                            array[i] = array[i] xor pairingKey[0]
                            randomSyncKey = tempData
                        } ?: error("pairingKey is null")
                    } ?: error("randomPairingKey is null")
                } else if (securityVersion == EncryptionType.ENCRYPTION_BLE5) {
                    array[i] = array[i] xor encryptionBle5Key[2]
                    array[i] = array[i].plusUByte(encryptionBle5Key[1])
                    array[i] = array[i].switchLoHi()
                    array[i] = array[i].minusUByte(encryptionBle5Key[0])
                }
            }
            if (securityVersion == EncryptionType.ENCRYPTION_RSv3) {
                if (array[DANAR_PACKET__START_POS] == DANAR_PACKET__ENCRYPTION_START && array[DANAR_PACKET__START_POS + 1] == DANAR_PACKET__ENCRYPTION_START) {
                    array[DANAR_PACKET__START_POS] = DANAR_PACKET__START_PACKET
                    array[DANAR_PACKET__START_POS + 1] = DANAR_PACKET__START_PACKET
                }

                if (array[size - 2] == DANAR_PACKET__ENCRYPTION_END && array[size - 1] == DANAR_PACKET__ENCRYPTION_END) {
                    array[size - 2] = DANAR_PACKET__END_PACKET
                    array[size - 1] = DANAR_PACKET__END_PACKET
                }
            }
        }
        return array.toByteArray()
    }

    private fun setDeviceName(deviceName: String?) {
        deviceName?.let { deviceName ->
            for (i in 0 until 10)
                this.deviceName[i] = deviceName[i].code.toUByte()
        }
    }

    fun generateCrc(buffer: UByteArray): UInt {
        var crc: UShort = 0u

        for (i in 0..<buffer.size) {
            val byte = buffer[i]
            var result = crc.ushr(8) or crc.shl(8)
            result = result.xor(byte)
            result = result.xor(result.and(0xFFu).ushr(4))
            result = result.xor(result.shl(12))

            when (securityVersion) {
                EncryptionType.ENCRYPTION_DEFAULT ->
                    result = result xor (result.and(0xFFu).shl(3) or result.and(0xFFu).ushr(2).shl(5))

                EncryptionType.ENCRYPTION_RSv3    ->
                    result = result xor
                        if (connectionState == 0 || connectionState == 1)
                            result.and(0xFFu).shl(3) or result.and(0xFFu).ushr(2).shl(5)
                        else
                            result.and(0xFFu).shl(5) or result.and(0xFFu).ushr(4).shl(2)

                EncryptionType.ENCRYPTION_BLE5    ->
                    result = if (connectionState == 0 || connectionState == 1)
                        result xor (result.and(0xFFu).shl(3) or result.and(0xFFu).ushr(2).shl(5))
                    else
                        result xor (result.and(0xFFu).shl(4) or result.and(0xFFu).ushr(3).shl(2))
            }
            crc = result
        }

        return crc.toUInt()
    }

    /**
     * Encode by serial number
     */
    fun encodeArrayBySn(data: UByteArray) {
        val codingBytes = UByteArray(3)
        for (i in 0..<10) {
            if (i < 3) codingBytes[0] = codingBytes[0].plusUByte(deviceName[i])
            else if (i < 8) codingBytes[1] = codingBytes[1].plusUByte(deviceName[i])
            else codingBytes[2] = codingBytes[2].plusUByte(deviceName[i])
        }
        for (i in 0..<data.size - 5)
            data[i + 3] = data[i + 3] xor codingBytes[i % 3]
    }

    fun encodeByteBySn(data: UByte): UByte {
        var encodingByte: UByte = 0x00u
        for (i in 0..<10) encodingByte = encodingByte.plusUByte(deviceName[i])
        return (data xor encodingByte)
    }

    fun encodeArrayByTime(data: UByteArray, time: UByteArray) {
        var encodingByte: UByte = 0x00u
        for (i in 0..<6) encodingByte = encodingByte.plusUByte(time[i])
        for (i in 0..<data.size - 5) data[i + 3] = data[i + 3] xor encodingByte
    }

    fun encodeArrayByPassword(data: UByteArray, password: UByteArray) {
        val encodingByte: UByte = password[0].plusUByte(password[1])
        for (i in 0..<data.size - 5) data[i + 3] = data[i + 3] xor encodingByte
    }

    fun encodeArrayByCfPassKey(data: UByteArray) {
        for (i in 0..<data.size - 5) data[i + 3] = data[i + 3] xor cfPassKey[(i + 1) % 2]
    }

    private fun UByte.shr(bitCount: Int): UByte = toUInt().shr(bitCount).toUByte()
    private fun UByte.shl(bitCount: Int): UByte = toUInt().shl(bitCount).toUByte()
    private fun UByte.switchLoHi(): UByte = (shr(4) and 0x0fu) or (shl(4) and 0xf0u)
    private operator fun UByteArray.get(index: UByte): UByte = get(index.toInt())
    private infix fun UShort.ushr(bitCount: Int): UShort = toInt().ushr(bitCount).toUShort()
    private infix fun UShort.shl(bitCount: Int): UShort = toInt().shl(bitCount).toUShort()
    private infix fun UShort.xor(uByte: UByte): UShort = xor(uByte.toUShort())
    private infix fun UShort.and(uByte: UByte): UShort = and(uByte.toUShort())
    private fun UByteArray.from(start: Int, lengthAfter: Int): UByteArray = copyOfRange(start, start + lengthAfter)
    private fun UByte.plusUByte(other: UByte): UByte = plus(other).toUByte()
    private fun UByte.minusUByte(other: UByte): UByte = minus(other).toUByte()

    fun initialRandomSyncKey() {
        pairingKey?.let { pairingKey ->
            randomSyncKey = 0x00u
            randomSyncKey = randomSyncKey xor pairingKey[0]
            randomSyncKey = randomSyncKey.plusUByte(pairingKey[1])
            randomSyncKey = randomSyncKey.switchLoHi()
            randomSyncKey = randomSyncKey xor pairingKey[2]
            randomSyncKey = randomSyncKey.minusUByte(pairingKey[3])
            randomSyncKey = randomSyncKey.switchLoHi()
            randomSyncKey = randomSyncKey xor pairingKey[4]
            randomSyncKey = randomSyncKey.switchLoHi()
            randomSyncKey = randomSyncKey xor pairingKey[5]
        } ?: error("pairingKey is null")
    }

    fun decryptionRandomSyncKey() {
        randomPairingKey?.let { randomPairingKey ->
            randomSyncKey = randomSyncKey.plusUByte(randomPairingKey[2])
            randomSyncKey = randomSyncKey.switchLoHi()
            randomSyncKey = randomSyncKey xor randomPairingKey[1]
            randomSyncKey = randomSyncKey.switchLoHi()
            randomSyncKey = randomSyncKey.minusUByte(randomPairingKey[0])
            randomSyncKey = randomSyncKey.switchLoHi()
        } ?: error("randomPairingKey is null")
    }

    companion object {

        const val BLE_UNIQUE_APP_ID1: UByte = 0x0Du
        const val BLE_UNIQUE_APP_ID2: UByte = 0x11u
        const val BLE_UNIQUE_APP_ID3: UByte = 0x06u

        const val DANAR_PACKET__START_POS = 0
        const val DANAR_PACKET__LENGTH_POS = 2
        const val DANAR_PACKET__TYPE_POS = 3
        const val DANAR_PACKET__OPCODE_POS = 4
        const val DANAR_PACKET__PARAMETERS_POS = 5

        const val DANAR_PACKET__START_LEN = 2
        const val DANAR_PACKET__LENGTH_LEN = 1
        const val DANAR_PACKET__TYPE_LEN = 1
        const val DANAR_PACKET__OPCODE_LEN = 1
        const val DANAR_PACKET__CHECKSUM_LEN = 2
        const val DANAR_PACKET__END_LEN = 2

        const val DANAR_PACKET__START_PACKET: UByte = 0xA5u
        const val DANAR_PACKET__END_PACKET: UByte = 0x5Au

        const val DANAR_PACKET__ENCRYPTION_START: UByte = 0x7Au
        const val DANAR_PACKET__ENCRYPTION_END: UByte = 0x2Eu

        const val DANAI_PACKET__ENCRYPTION_START: UByte = 0xAAu
        const val DANAI_PACKET__ENCRYPTION_END: UByte = 0xEEu

        const val DANAR_PACKET__TYPE_ENCRYPTION_REQUEST: UByte = 0x01u
        const val DANAR_PACKET__TYPE_ENCRYPTION_RESPONSE: UByte = 0x02u
        const val DANAR_PACKET__TYPE_COMMAND: UByte = 0xA1u
        const val DANAR_PACKET__TYPE_RESPONSE = 0xB2
        const val DANAR_PACKET__TYPE_NOTIFY = 0xC3
        const val DANAR_PACKET__OPCODE_ENCRYPTION__PUMP_CHECK = 0x00
        const val DANAR_PACKET__OPCODE_ENCRYPTION__TIME_INFORMATION = 0x01
        const val DANAR_PACKET__OPCODE_ENCRYPTION__CHECK_PASSKEY = 0xD0
        const val DANAR_PACKET__OPCODE_ENCRYPTION__PASSKEY_REQUEST = 0xD1
        const val DANAR_PACKET__OPCODE_ENCRYPTION__PASSKEY_RETURN = 0xD2

        // Easy Mode
        const val DANAR_PACKET__OPCODE_ENCRYPTION__GET_PUMP_CHECK = 0xF3
        const val DANAR_PACKET__OPCODE_ENCRYPTION__GET_EASY_MENU_CHECK = 0xF4
        const val DANAR_PACKET__OPCODE_NOTIFY__DELIVERY_COMPLETE = 0x01
        const val DANAR_PACKET__OPCODE_NOTIFY__DELIVERY_RATE_DISPLAY = 0x02
        const val DANAR_PACKET__OPCODE_NOTIFY__ALARM = 0x03
        const val DANAR_PACKET__OPCODE_NOTIFY__MISSED_BOLUS_ALARM = 0x04
        const val DANAR_PACKET__OPCODE_REVIEW__INITIAL_SCREEN_INFORMATION = 0x02
        const val DANAR_PACKET__OPCODE_REVIEW__DELIVERY_STATUS = 0x03
        const val DANAR_PACKET__OPCODE_REVIEW__GET_PASSWORD = 0x04
        const val DANAR_PACKET__OPCODE_REVIEW__BOLUS_AVG = 0x10
        const val DANAR_PACKET__OPCODE_REVIEW__BOLUS = 0x11
        const val DANAR_PACKET__OPCODE_REVIEW__DAILY = 0x12
        const val DANAR_PACKET__OPCODE_REVIEW__PRIME = 0x13
        const val DANAR_PACKET__OPCODE_REVIEW__REFILL = 0x14
        const val DANAR_PACKET__OPCODE_REVIEW__BLOOD_GLUCOSE = 0x15
        const val DANAR_PACKET__OPCODE_REVIEW__CARBOHYDRATE = 0x16
        const val DANAR_PACKET__OPCODE_REVIEW__TEMPORARY = 0x17
        const val DANAR_PACKET__OPCODE_REVIEW__SUSPEND = 0x18
        const val DANAR_PACKET__OPCODE_REVIEW__ALARM = 0x19
        const val DANAR_PACKET__OPCODE_REVIEW__BASAL = 0x1A
        const val DANAR_PACKET__OPCODE_REVIEW__ALL_HISTORY = 0x1F
        const val DANAR_PACKET__OPCODE_REVIEW__GET_SHIPPING_INFORMATION = 0x20
        const val DANAR_PACKET__OPCODE_REVIEW__GET_PUMP_CHECK = 0x21
        const val DANAR_PACKET__OPCODE_REVIEW__GET_USER_TIME_CHANGE_FLAG = 0x22
        const val DANAR_PACKET__OPCODE_REVIEW__SET_USER_TIME_CHANGE_FLAG_CLEAR = 0x23
        const val DANAR_PACKET__OPCODE_REVIEW__GET_MORE_INFORMATION = 0x24
        const val DANAR_PACKET__OPCODE_REVIEW__SET_HISTORY_UPLOAD_MODE = 0x25
        const val DANAR_PACKET__OPCODE_REVIEW__GET_TODAY_DELIVERY_TOTAL = 0x26
        const val DANAR_PACKET__OPCODE_BOLUS__GET_STEP_BOLUS_INFORMATION = 0x40
        const val DANAR_PACKET__OPCODE_BOLUS__GET_EXTENDED_BOLUS_STATE = 0x41
        const val DANAR_PACKET__OPCODE_BOLUS__GET_EXTENDED_BOLUS = 0x42
        const val DANAR_PACKET__OPCODE_BOLUS__GET_DUAL_BOLUS = 0x43
        const val DANAR_PACKET__OPCODE_BOLUS__SET_STEP_BOLUS_STOP = 0x44
        const val DANAR_PACKET__OPCODE_BOLUS__GET_CARBOHYDRATE_CALCULATION_INFORMATION = 0x45
        const val DANAR_PACKET__OPCODE_BOLUS__GET_EXTENDED_MENU_OPTION_STATE = 0x46
        const val DANAR_PACKET__OPCODE_BOLUS__SET_EXTENDED_BOLUS = 0x47
        const val DANAR_PACKET__OPCODE_BOLUS__SET_DUAL_BOLUS = 0x48
        const val DANAR_PACKET__OPCODE_BOLUS__SET_EXTENDED_BOLUS_CANCEL = 0x49
        const val DANAR_PACKET__OPCODE_BOLUS__SET_STEP_BOLUS_START = 0x4A
        const val DANAR_PACKET__OPCODE_BOLUS__GET_CALCULATION_INFORMATION = 0x4B
        const val DANAR_PACKET__OPCODE_BOLUS__GET_BOLUS_RATE = 0x4C
        const val DANAR_PACKET__OPCODE_BOLUS__SET_BOLUS_RATE = 0x4D
        const val DANAR_PACKET__OPCODE_BOLUS__GET_CIR_CF_ARRAY = 0x4E
        const val DANAR_PACKET__OPCODE_BOLUS__SET_CIR_CF_ARRAY = 0x4F
        const val DANAR_PACKET__OPCODE_BOLUS__GET_BOLUS_OPTION = 0x50
        const val DANAR_PACKET__OPCODE_BOLUS__SET_BOLUS_OPTION = 0x51
        const val DANAR_PACKET__OPCODE_BOLUS__GET_24_CIR_CF_ARRAY = 0x52
        const val DANAR_PACKET__OPCODE_BOLUS__SET_24_CIR_CF_ARRAY = 0x53
        const val DANAR_PACKET__OPCODE_BASAL__SET_TEMPORARY_BASAL = 0x60
        const val DANAR_PACKET__OPCODE_BASAL__TEMPORARY_BASAL_STATE = 0x61
        const val DANAR_PACKET__OPCODE_BASAL__CANCEL_TEMPORARY_BASAL = 0x62
        const val DANAR_PACKET__OPCODE_BASAL__GET_PROFILE_NUMBER = 0x63
        const val DANAR_PACKET__OPCODE_BASAL__SET_PROFILE_NUMBER = 0x64
        const val DANAR_PACKET__OPCODE_BASAL__GET_PROFILE_BASAL_RATE = 0x65
        const val DANAR_PACKET__OPCODE_BASAL__SET_PROFILE_BASAL_RATE = 0x66
        const val DANAR_PACKET__OPCODE_BASAL__GET_BASAL_RATE = 0x67
        const val DANAR_PACKET__OPCODE_BASAL__SET_BASAL_RATE = 0x68
        const val DANAR_PACKET__OPCODE_BASAL__SET_SUSPEND_ON = 0x69
        const val DANAR_PACKET__OPCODE_BASAL__SET_SUSPEND_OFF = 0x6A
        const val DANAR_PACKET__OPCODE_OPTION__GET_PUMP_TIME = 0x70
        const val DANAR_PACKET__OPCODE_OPTION__SET_PUMP_TIME = 0x71
        const val DANAR_PACKET__OPCODE_OPTION__GET_USER_OPTION = 0x72
        const val DANAR_PACKET__OPCODE_OPTION__SET_USER_OPTION = 0x73
        const val DANAR_PACKET__OPCODE_BASAL__APS_SET_TEMPORARY_BASAL = 0xC1
        const val DANAR_PACKET__OPCODE__APS_HISTORY_EVENTS = 0xC2
        const val DANAR_PACKET__OPCODE__APS_SET_EVENT_HISTORY = 0xC3

        // v3 specific
        const val DANAR_PACKET__OPCODE_REVIEW__GET_PUMP_DEC_RATIO = 0x80
        const val DANAR_PACKET__OPCODE_GENERAL__GET_SHIPPING_VERSION = 0x81

        // Easy Mode
        const val DANAR_PACKET__OPCODE_OPTION__GET_EASY_MENU_OPTION = 0x74
        const val DANAR_PACKET__OPCODE_OPTION__SET_EASY_MENU_OPTION = 0x75
        const val DANAR_PACKET__OPCODE_OPTION__GET_EASY_MENU_STATUS = 0x76
        const val DANAR_PACKET__OPCODE_OPTION__SET_EASY_MENU_STATUS = 0x77
        const val DANAR_PACKET__OPCODE_OPTION__GET_PUMP_UTC_AND_TIME_ZONE = 0x78
        const val DANAR_PACKET__OPCODE_OPTION__SET_PUMP_UTC_AND_TIME_ZONE = 0x79
        const val DANAR_PACKET__OPCODE_OPTION__GET_PUMP_TIME_ZONE = 0x7A
        const val DANAR_PACKET__OPCODE_OPTION__SET_PUMP_TIME_ZONE = 0x7B
        const val DANAR_PACKET__OPCODE_ETC__SET_HISTORY_SAVE = 0xE0
        const val DANAR_PACKET__OPCODE_ETC__KEEP_CONNECTION = 0xFF
    }
}
