package app.aaps.core.interfaces.pump

enum class BlePreCheckResult {
    READY,
    BLE_NOT_SUPPORTED,
    BLE_NOT_ENABLED,
    PERMISSIONS_MISSING
}
