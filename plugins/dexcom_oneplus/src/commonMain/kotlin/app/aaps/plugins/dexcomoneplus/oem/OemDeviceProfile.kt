package app.aaps.plugins.dexcomoneplus.oem

/**
 * BLE/runtime knobs for a phone OEM family (Q9 v1).
 * Per-OEM BLE defaults; do not hardcode GATT here.
 */
data class OemDeviceProfile(
    val id: OemProfileId,
    /** GATT connect attempt timeout. Values above ~30 s only bind when autoConnect=true;
     *  Android's hard connect (autoConnect=false) gives up around 30 s. */
    val connectTimeoutMs: Long,
    /** Max connect attempts before surfacing failure. */
    val connectRetryCount: Int,
    /** Delay between connect retries. */
    val connectRetryDelayMs: Long,
    /** Preferred ATT MTU request (OS may negotiate lower). */
    val preferredMtu: Int,
    /** When true, `DexcomOnePlusSessionService` runs as a connectedDevice foreground service. */
    val useForegroundService: Boolean,
    /** Faster / denser reconnect backoff when true. */
    val aggressiveReconnect: Boolean,
    /**
     * Sleep after GATT [android.bluetooth.BluetoothGatt.close] before next connectGatt.
     * Ob1/Samsung: avoids status 133/147 races on immediate reconnect.
     */
    val postCloseSettleMs: Long = 2_000L,
    /** Pause after stopScan / ADV sighting before connectGatt (Ob1 CONNECT_NOW ≈ 500 ms). */
    val scanHandoffMs: Long = 500L,
    /**
     * On reconnect attempts, LE-scan until the target MAC advertises (or timeout).
     * 0 = skip (MAC-only connectGatt).
     */
    val preConnectScanMs: Long = 0L,
    /**
     * When true, request MTU on CONNECTED before discover.
     * When false, discover (+ CCCD) first — Ob1 never requests MTU; safer on Samsung.
     */
    val requestMtuOnConnect: Boolean = true,
    /** Call hidden BluetoothGatt.refresh() before close / reconnect. */
    val useGattRefresh: Boolean = false,
    /**
     * Use connectGatt(autoConnect=true) when attempt index >= this.
     * -1 = never (hard connect only).
     */
    val autoConnectFromAttempt: Int = -1,
    /** Optional pause after services discovered before CCCD (Ob1 Samsung pairing workaround). */
    val postDiscoverDelayMs: Long = 0L,
    /**
     * When true and [preConnectScanMs] > 0, abort connect on ADV miss for **attempt 0 only**
     * (see driver). Later attempts connect by known MAC after a best-effort scan.
     */
    val requireAdvBeforeConnect: Boolean = false,
    /**
     * Force `BluetoothGatt.CONNECTION_PRIORITY_HIGH` (7.5 ms interval) on connect. Juggluco's
     * Dex / ONE+ path does **not** — an aggressive interval correlated with the transmitter
     * terminating the link (peer status 19) right after the post-auth `0x4E`. Default off: let
     * the OS negotiate the interval, as Juggluco does.
     */
    val forceHighConnectionPriority: Boolean = false,
    /**
     * Request a larger ATT MTU. Juggluco's Dex / ONE+ path **never** requests MTU (stays at 23,
     * which fits an EGV frame); requesting 517→23 mid-connection is an extra exchange the ONE+
     * dislikes. Default off. [requestMtuOnConnect] only chooses *when* to request, if this is on.
     */
    val requestMtu: Boolean = false,
)

enum class OemProfileId {
    PIXEL,
    SAMSUNG,
    MOTOROLA,
    GENERIC_FALLBACK,
}
