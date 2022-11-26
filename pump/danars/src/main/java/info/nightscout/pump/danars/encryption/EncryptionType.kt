package info.nightscout.pump.danars.encryption

enum class EncryptionType(val type: Int) {
    ENCRYPTION_DEFAULT(0),
    ENCRYPTION_RSv3(1),
    ENCRYPTION_BLE5(2)
}