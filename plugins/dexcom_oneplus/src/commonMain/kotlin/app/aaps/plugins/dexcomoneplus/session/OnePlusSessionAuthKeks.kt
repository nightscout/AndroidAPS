package app.aaps.plugins.dexcomoneplus.session

import app.aaps.plugins.dexcomoneplus.OnePlusLog
import app.aaps.plugins.dexcomoneplus.OnePlusLogMarkers
import app.aaps.plugins.dexcomoneplus.gatt.OnePlusGattClient
import app.aaps.plugins.dexcomoneplus.gatt.OnePlusKeksNotifySource
import jamorham.keks.Plugin
import jamorham.keks.crypto.KeksArrayIndexOutOfBoundsException
import jamorham.keks.crypto.KeksInvalidParameterException
import jamorham.keks.crypto.KeksSecurityException

/**
 * KEKS (libkeks) handshake driver over [OnePlusGattClient].
 *
 * Provenance: NightscoutFoundation/xDrip libkeks at the pin in plugins/libkeks/NOTICE (GPL-3.0) via `:plugins:libkeks`.
 * Pump loop mirrors Ob1 `Ob1G5StateMachine.doNext` + Juggluco reconnect short-auth:
 * - Install guide certs + optional saved shared key (persistence channel 2)
 * - When bonded + saved key, libkeks skips Round1–3 (`aNext` → RequestAuth)
 * - Bond via [OnePlusGattClient.awaitBondComplete] (CCCD teardown while BONDING)
 * - Success only when Ob1 would enter GET_DATA (`aNext` length==1)
 *
 * Juggluco recovery (ChallengeReply): if AuthStatus `authenticated != 1`, wipe persisted key
 * ([AuthResult.invalidateSharedKey]) and clear the in-memory [jamorham.keks.Context.savedKey] and
 * the singleton, so the next attempt runs full J-PAKE. Soft recovery: do **not** [OnePlusGattClient.removeBond] on the
 * first short-auth reject (preload path) — forced unbond correlated with ADV blackout on Samsung.
 * Unbond only after a full J-PAKE AuthStatus reject while still OS-bonded, or on bond=3.
 *
 * ## Shared code
 *
 * This class is in commonMain, like `:plugins:libkeks`. The three exception types it catches come
 * from `jamorham.keks.crypto` for that reason: on Android they are still `java.lang.SecurityException`,
 * `java.security.InvalidParameterException` and `java.lang.ArrayIndexOutOfBoundsException`, so
 * nothing changed there.
 *
 * The sensor asks for a bond in the middle of an **unbonded** four-digit pairing, and
 * `requestAndroidBond` answers it through [OnePlusGattClient.createBond] and
 * [OnePlusGattClient.awaitBondComplete]. On platforms without a pairing API the GATT client may
 * return true there and let the OS pair on the next encrypted operation. A reconnect with a saved
 * shared key does not pass through `requestAndroidBond` at all.
 *
 * ⚠️ ASYNC IMPACT: blocks caller (bleExecutor) on [OnePlusGattClient.awaitKeksNotify] and
 * [OnePlusGattClient.awaitBondComplete]. Do not call from main.
 */
class OnePlusSessionAuthKeks(
    private val gatt: OnePlusGattClient,
    private val stepTimeoutMs: Long = 15_000L,
    /** Many 20-byte ExtraData chunks per 160-byte round (×3) plus Auth challenge / AuthStatus. */
    private val maxSteps: Int = 96,
    private val bondWaitMs: Long = 45_000L,
) : OnePlusSessionAuth {

    override fun authenticate(pairingCode: String, savedSharedKey: ByteArray?): AuthResult {
        if (!gatt.isConnected()) {
            return AuthResult(ok = false, message = "ONEPLUS_AUTH: GATT not connected")
        }
        // Drop singleton KEKS state left by a prior ChallengeReply / getPersistence —
        // otherwise RoundStart still sees context.savedKey and short-auths (auth=2) even when
        // AAPS preload=false (seen on Samsung after reconnect).
        forceFreshKeksInstance(pairingCode)
        val plugin = Plugin.getInstance(pairingCode)
        plugin.context.reset()
        // Ob1: Pref keks_p1..p3 → setPersistence(8..10) before handshake (xDrip Auto Configure QR).
        OnePlusKeksGuideCerts.install(plugin)

        val preload = savedSharedKey?.takeIf { it.size == 16 }
        val usedShortAuthPreload = preload != null
        if (preload != null) {
            plugin.setPersistence(2, preload.copyOf())
            OnePlusLog.i(
                "${OnePlusLogMarkers.SESSION}: KEKS saved shared key preloaded " +
                    "bonded=${gatt.isBonded()} (short-auth path if RoundStart)",
            )
        } else {
            OnePlusLog.i(
                "${OnePlusLogMarkers.SESSION}: KEKS full J-PAKE path " +
                    "(no preload, in-memory key cleared) bonded=${gatt.isBonded()}",
            )
        }

        plugin.amConnected()
        OnePlusLog.i(
            "${OnePlusLogMarkers.SESSION}: KEKS amConnected bonded=${gatt.isBonded()} " +
                "preload=${usedShortAuthPreload} savedKeyInCtx=${plugin.context.savedKey != null}",
        )

        // Ob1: first doNext after Auth CCCD setup (connect already enabled CCCDs).
        emitANext(plugin)?.let { return finalizeAuthResult(it, plugin) }

        var sawSharedKey = false
        repeat(maxSteps) { step ->
            if (!gatt.isConnected()) {
                return finalizeAuthResult(
                    AuthResult(ok = false, message = "ONEPLUS_AUTH: GATT disconnected"),
                    plugin,
                )
            }

            val notify = gatt.awaitKeksNotify(stepTimeoutMs)
                ?: return finalizeAuthResult(
                    AuthResult(ok = false, message = "ONEPLUS_AUTH: notify timeout step=$step"),
                    plugin,
                )

            if (plugin.bondNow(notify.payload)) {
                val bondOk = requestAndroidBond()
                if (!bondOk) {
                    return finalizeAuthResult(
                        AuthResult(
                            ok = false,
                            message = "ONEPLUS_AUTH: Android bond required / timed out — accept pairing prompt",
                        ),
                        plugin,
                    )
                }
                // TIME_EXTENDED is the bond trigger and leaves libkeks in GET_DATA. It is not a
                // response consumed by receivedResponse(GET_DATA), so advance immediately instead
                // of waiting for a notification the transmitter will never send.
                emitANext(plugin)?.let { return finalizeAuthResult(it, plugin) }
                return@repeat
            }

            if (notify.source == OnePlusKeksNotifySource.AUTHENTICATION) {
                evaluateAuthStatus(notify.payload, usedShortAuthPreload)?.let {
                    return finalizeAuthResult(it, plugin)
                }
            }

            val shouldEmit = try {
                when (notify.source) {
                    OnePlusKeksNotifySource.AUTHENTICATION -> plugin.receivedResponse(notify.payload)
                    OnePlusKeksNotifySource.EXTRA_DATA -> plugin.receivedData(notify.payload)
                }
            } catch (se: KeksSecurityException) {
                return finalizeAuthResult(
                    AuthResult(ok = false, message = "ONEPLUS_AUTH: ${se.message}"),
                    plugin,
                )
            } catch (ipe: KeksInvalidParameterException) {
                return finalizeAuthResult(
                    AuthResult(
                        ok = false,
                        message = "ONEPLUS_AUTH: ${ipe.message}",
                        invalidateSharedKey = ipe.message?.contains("Missing QR", ignoreCase = true) == true,
                    ),
                    plugin,
                )
            } catch (aioobe: KeksArrayIndexOutOfBoundsException) {
                // libkeks `Calc.challenger` copies 16 bytes from offset 2 with no length check, so a
                // short packet where a challenge was expected throws instead of failing auth. The
                // field log of 2026-08-25 hit this with a 3-byte AuthStatus arriving mid-handshake.
                // Upstream is vendored third party and stays untouched: the wrapper turns it into an
                // auth failure so the session retry machinery takes over.
                return finalizeAuthResult(
                    AuthResult(
                        ok = false,
                        message = "ONEPLUS_AUTH: KEKS short packet ${aioobe.message} (out-of-step handshake)",
                    ),
                    plugin,
                )
            } catch (ise: IllegalStateException) {
                // "Invalid result" from validateZeroKnowledgeProof: the round-1 packet did not
                // decode to a point on the curve. BouncyCastle raised this on Android and the
                // shared `KeksP256.multiply` keeps the same check and the same message. Same root
                // cause as above — bytes from another handshake — and same treatment.
                return finalizeAuthResult(
                    AuthResult(
                        ok = false,
                        message = "ONEPLUS_AUTH: KEKS proof rejected ${ise.message} (out-of-step handshake)",
                    ),
                    plugin,
                )
            } catch (npe: NullPointerException) {
                return finalizeAuthResult(
                    AuthResult(
                        ok = false,
                        message = "ONEPLUS_AUTH: KEKS NPE ${npe.message} (incomplete round state)",
                    ),
                    plugin,
                )
            }

            if (shouldEmit) {
                OnePlusLog.d(
                    "${OnePlusLogMarkers.SESSION}: KEKS emit after ${notify.source} " +
                        "${notify.payload.size}b step=$step",
                )
                emitANext(plugin)?.let { return finalizeAuthResult(it, plugin) }
            }

            // Only probe persistence after auth is progressing — getPersistence(1) can *create*
            // context.savedKey via getShortSharedKey and poison the next RoundStart.
            if (!sawSharedKey && plugin.context.savedKey != null) {
                sawSharedKey = true
                OnePlusLog.i(
                    "${OnePlusLogMarkers.SESSION}: KEKS context.savedKey present " +
                        "(${plugin.context.savedKey!!.size}b) step=$step",
                )
            }
        }

        return finalizeAuthResult(
            AuthResult(
                ok = false,
                message = "ONEPLUS_AUTH: KEKS handshake incomplete after $maxSteps steps " +
                    "(sharedKey=$sawSharedKey; need AuthStatus→GET_DATA)",
            ),
            plugin,
        )
    }

    /**
     * libkeks [Plugin.setPersistence] channel 3/4 nulls the singleton so the next
     * [Plugin.getInstance] allocates a clean Context (no leftover savedKey / Round3).
     */
    private fun forceFreshKeksInstance(pairingCode: String) {
        try {
            val stale = Plugin.getInstance(pairingCode)
            stale.context.reset()
            stale.setPersistence(3, ByteArray(0))
        } catch (_: Throwable) {
            // Best-effort — getInstance below still runs.
        }
    }

    /** On failure, wipe in-memory KEKS key so the next attempt cannot short-auth on garbage. */
    private fun finalizeAuthResult(result: AuthResult, plugin: Plugin): AuthResult {
        if (!result.ok) {
            try {
                plugin.context.reset()
            } catch (_: Throwable) {
            }
        }
        return result
    }

    /**
     * Juggluco ChallengeReply: `auth != 1` → wipe key and force full J-PAKE next.
     * Soft path (short-auth / preload): keep OS bond — removeBond after auth=2 caused ADV silence.
     * Hard path (full J-PAKE still rejected while bonded, or bond=3): then removeBond.
     */
    private fun evaluateAuthStatus(
        payload: ByteArray,
        usedShortAuthPreload: Boolean,
    ): AuthResult? {
        val status = OnePlusAuthStatusRx.parse(payload) ?: return null
        OnePlusLog.i(
            "${OnePlusLogMarkers.SESSION}: AuthStatus authenticated=${status.authenticated} " +
                "bonded=${status.bonded} localBonded=${gatt.isBonded()} preload=$usedShortAuthPreload",
        )

        if (status.needsKeyRefresh) {
            dropOsBondIfPresent("bond=3 key refresh")
            return AuthResult(
                ok = false,
                message = "ONEPLUS_AUTH: KEKS bond failure / key refresh required (bond=3)",
                invalidateSharedKey = true,
            )
        }

        if (!status.isAuthenticated) {
            // Short-auth with stale/wrong key (typical Samsung: auth=2 bonded=1 after EGV drop).
            // Soft recovery: wipe key only → next connect full J-PAKE while bond may remain.
            // Hard recovery: full J-PAKE already failed with local bond → removeBond.
            val viaShort = usedShortAuthPreload
            if (viaShort) {
                OnePlusLog.i(
                    "${OnePlusLogMarkers.SESSION}: soft key refresh after short-auth " +
                        "auth=${status.authenticated} — keep OS bond, full J-PAKE next",
                )
            } else {
                dropOsBondIfPresent("full-pair auth=${status.authenticated}")
            }
            OnePlusLog.e(
                "${OnePlusLogMarkers.ERROR}: AuthStatus rejected authenticated=${status.authenticated} " +
                    "(need ${OnePlusAuthStatusRx.AUTHENTICATED_OK}) " +
                    if (viaShort) "— soft short-auth key refresh (no removeBond)"
                    else "— full-pair auth failed",
            )
            return AuthResult(
                ok = false,
                message = if (viaShort) {
                    "ONEPLUS_AUTH: KEKS short-auth rejected auth=${status.authenticated} — key refresh required"
                } else {
                    "ONEPLUS_AUTH: KEKS AuthStatus rejected auth=${status.authenticated}"
                },
                invalidateSharedKey = true,
            )
        }

        // auth==1: do NOT createBond here. Ob1/Juggluco bond only on inbound TIME_EXTENDED
        // (bondNow). Early createBond on bonded=2 caused GATT status=19 before Pairing write.
        return null
    }

    /**
     * Ob1 `doNext` write side. Returns a terminal [AuthResult] when handshake ends;
     * null means continue waiting for notifies.
     */
    private fun emitANext(plugin: Plugin): AuthResult? {
        val next = try {
            plugin.aNext()
        } catch (npe: NullPointerException) {
            return AuthResult(
                ok = false,
                message = "ONEPLUS_AUTH: KEKS aNext NPE ${npe.message}",
            )
        }
        if (next == null) {
            OnePlusLog.d(
                "${OnePlusLogMarkers.SESSION}: KEKS aNext null — wait for more notifies",
            )
            return null
        }
        return when (next.size) {
            1 -> {
                // Ob1: length==1 → auth complete (GET_DATA). Do not write GETDATA here —
                // Control/EGV path owns post-auth traffic.
                val key = plugin.getPersistence(1)
                val keyBytes = if (key != null && key.isNotEmpty()) key.size else 0
                OnePlusLog.i(
                    "${OnePlusLogMarkers.SESSION}: KEKS auth complete (aNext length=1, key=${keyBytes}b)",
                )
                if (keyBytes == 0) {
                    AuthResult(ok = false, message = "ONEPLUS_AUTH: GET_DATA without shared key")
                } else {
                    AuthResult(ok = true, message = "keks_ok", sharedKey = key!!.copyOf())
                }
            }
            3 -> {
                AuthResult(
                    ok = false,
                    message = "ONEPLUS_AUTH: KEKS bond failure / key refresh required",
                    invalidateSharedKey = true,
                )
            }
            else -> {
                // Ob1 doNext: ExtraData chunks first (NO_RESPONSE), then Auth (DEFAULT).
                // Includes AuthChallenge (0x04) and TIME_EXTENDED after AuthStatus paths.
                if (next.isNotEmpty()) {
                    gatt.writeExtraData(next.getOrNull(1))
                    gatt.writeAuthentication(next.getOrNull(0))
                }
                null
            }
        }
    }

    private fun requestAndroidBond(): Boolean {
        if (gatt.isBonded()) return true
        return try {
            if (!gatt.createBond()) {
                OnePlusLog.e("${OnePlusLogMarkers.ERROR}: createBond returned false")
                return false
            }
            // Juggluco: BroadcastReceiver + CCCD teardown/restore (not a bare isBonded poll).
            gatt.awaitBondComplete(bondWaitMs)
        } catch (t: Throwable) {
            OnePlusLog.e("${OnePlusLogMarkers.ERROR}: createBond ${t.message}", t)
            false
        }
    }

    /**
     * Hard recovery only: drop OS bond after full-pair AuthStatus reject or bond=3.
     * Not used on first short-auth reject (see [evaluateAuthStatus] soft path).
     *
     * ⚠️ ASYNC IMPACT: [OnePlusGattClient.removeBond] may disconnect GATT and silence ADV briefly.
     */
    private fun dropOsBondIfPresent(reason: String) {
        if (!gatt.isBonded()) return
        val ok = try {
            gatt.removeBond()
        } catch (t: Throwable) {
            OnePlusLog.e("${OnePlusLogMarkers.ERROR}: removeBond $reason ${t.message}", t)
            false
        }
        OnePlusLog.i(
            "${OnePlusLogMarkers.SESSION}: Juggluco-style removeBond after $reason ok=$ok",
        )
    }
}
