package app.aaps.plugins.dexcomoneplus.oem

import app.aaps.plugins.dexcomoneplus.OnePlusLog

/**
 * Resolves OEM BLE defaults from [onePlusDeviceManufacturer] / [onePlusDeviceModel].
 */
object DeviceProfileRegistry {

    const val LOG_MARKER = "DEXCOM_ONEPLUS_OEM_PROFILE"

    /** Pixel 6/7/8 family — Juggluco never requestMtu before discover on Dex path. */
    val PixelDefault: OemDeviceProfile = OemDeviceProfile(
        id = OemProfileId.PIXEL,
        connectTimeoutMs = 30_000L,
        connectRetryCount = 3,
        connectRetryDelayMs = 2_000L,
        preferredMtu = 517,
        useForegroundService = true,
        aggressiveReconnect = false,
        postCloseSettleMs = 2_000L,
        scanHandoffMs = 500L,
        preConnectScanMs = 2_000L,
        requestMtuOnConnect = false,
        useGattRefresh = true,
        autoConnectFromAttempt = 2,
        postDiscoverDelayMs = 0L,
    )

    /**
     * Samsung S22–S25 — patient connect / reconnect (status 147 / 133 common).
     * Field logs: blind autoConnect=false without fresh ADV → 147; success needed ADV + autoConnect.
     */
    val SamsungDefault: OemDeviceProfile = OemDeviceProfile(
        id = OemProfileId.SAMSUNG,
        connectTimeoutMs = 45_000L,
        connectRetryCount = 5,
        connectRetryDelayMs = 10_000L,
        preferredMtu = 517,
        useForegroundService = true,
        aggressiveReconnect = true,
        postCloseSettleMs = 6_000L,
        scanHandoffMs = 500L,
        preConnectScanMs = 8_000L,
        requestMtuOnConnect = false,
        useGattRefresh = true,
        autoConnectFromAttempt = 0,
        postDiscoverDelayMs = 1_000L,
        requireAdvBeforeConnect = true,
    )

    /**
     * Motorola — same failure as Samsung, and it used to land on [GenericFallback].
     *
     * The fallback connects on attempt 0 even when the pre-connect scan hears nothing
     * (`requireAdvBeforeConnect` defaults to false) and only reaches for `autoConnect` from the third
     * attempt. That is the exact combination the Samsung profile exists to avoid: a blind hard
     * connect without a fresh advertisement fails, and the retry ladder then costs minutes. So this
     * profile takes the two settings that fixed Samsung — wait for the ADV, and let the platform
     * hold the connection with `autoConnect` from the first attempt — with a scan window long enough
     * to actually hear a sensor that advertises on its own duty cycle.
     *
     * MTU stays at the safe 185: there is no field measurement on this stack yet, and
     * [OemDeviceProfile.requestMtuOnConnect] is false anyway.
     */
    val MotorolaDefault: OemDeviceProfile = OemDeviceProfile(
        id = OemProfileId.MOTOROLA,
        connectTimeoutMs = 45_000L,
        connectRetryCount = 5,
        connectRetryDelayMs = 8_000L,
        preferredMtu = 185,
        useForegroundService = true,
        aggressiveReconnect = true,
        postCloseSettleMs = 4_000L,
        scanHandoffMs = 500L,
        preConnectScanMs = 8_000L,
        requestMtuOnConnect = false,
        useGattRefresh = true,
        autoConnectFromAttempt = 0,
        postDiscoverDelayMs = 1_000L,
        requireAdvBeforeConnect = true,
    )

    /**
     * Unknown OEM — conservative everywhere, including the advertisement gate.
     *
     * It used to be conservative on timeouts only: `requireAdvBeforeConnect` was left at its false
     * default and `autoConnectFromAttempt` at 2, so an unknown phone connected blind on the first two
     * attempts after a 3 s scan. That is the combination the Samsung profile exists to avoid, and two
     * independent field logs landed on it — a Motorola and a CUBOT KING KONG MINI 3 — because this is
     * where every phone that is not a Pixel or a Samsung ends up. Being the default, it has to be the
     * safest of the three, not the boldest.
     */
    val GenericFallback: OemDeviceProfile = OemDeviceProfile(
        id = OemProfileId.GENERIC_FALLBACK,
        connectTimeoutMs = 60_000L,
        connectRetryCount = 5,
        connectRetryDelayMs = 8_000L,
        preferredMtu = 185,
        useForegroundService = true,
        aggressiveReconnect = true,
        postCloseSettleMs = 4_000L,
        scanHandoffMs = 500L,
        preConnectScanMs = 8_000L,
        requestMtuOnConnect = false,
        useGattRefresh = true,
        autoConnectFromAttempt = 0,
        postDiscoverDelayMs = 1_000L,
        requireAdvBeforeConnect = true,
    )

    fun byId(id: OemProfileId): OemDeviceProfile = when (id) {
        OemProfileId.PIXEL -> PixelDefault
        OemProfileId.SAMSUNG -> SamsungDefault
        OemProfileId.MOTOROLA -> MotorolaDefault
        OemProfileId.GENERIC_FALLBACK -> GenericFallback
    }

    /**
     * Picks a profile for the running device (or [override]).
     * Always logs [LOG_MARKER] for logcat / QA grepping.
     */
    fun resolve(
        manufacturer: String = onePlusDeviceManufacturer(),
        model: String = onePlusDeviceModel(),
        override: OemProfileId? = null,
    ): OemDeviceProfile {
        val profile = when {
            override != null -> byId(override)
            isPixel(manufacturer) -> PixelDefault
            isSamsung(manufacturer) -> SamsungDefault
            isMotorola(manufacturer) -> MotorolaDefault
            else -> GenericFallback
        }
        OnePlusLog.i(
            "$LOG_MARKER id=${profile.id} manufacturer=$manufacturer model=$model " +
                "override=$override connectTimeoutMs=${profile.connectTimeoutMs} " +
                "retry=${profile.connectRetryCount} mtu=${profile.preferredMtu} " +
                "fgsFlag=${profile.useForegroundService} aggressiveReconnect=${profile.aggressiveReconnect} " +
                "settleMs=${profile.postCloseSettleMs} preScanMs=${profile.preConnectScanMs} " +
                "mtuOnConnect=${profile.requestMtuOnConnect} autoConnectFrom=${profile.autoConnectFromAttempt} " +
                "requireAdv=${profile.requireAdvBeforeConnect}",
        )
        return profile
    }

    private fun isPixel(manufacturer: String): Boolean {
        val m = manufacturer.lowercase()
        return m.contains("google")
    }

    private fun isSamsung(manufacturer: String): Boolean {
        val m = manufacturer.lowercase()
        return m.contains("samsung")
    }

    /** The reported manufacturer stays "motorola" on these phones, whoever owns the brand. */
    private fun isMotorola(manufacturer: String): Boolean {
        val m = manufacturer.lowercase()
        return m.contains("motorola")
    }
}
