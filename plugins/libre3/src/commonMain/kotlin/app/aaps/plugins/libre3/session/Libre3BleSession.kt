package app.aaps.plugins.libre3.session

import app.aaps.plugins.libre3.Libre3Log
import app.aaps.plugins.libre3.Libre3LogMarkers
import app.aaps.plugins.libre3.crypto.Libre3AesBlock
import app.aaps.plugins.libre3.crypto.Libre3CryptoException
import app.aaps.plugins.libre3.crypto.Libre3DataPlaneCrypto
import app.aaps.plugins.libre3.crypto.Libre3FirstPairEphemeral
import app.aaps.plugins.libre3.crypto.Libre3FirstPairMaterial
import app.aaps.plugins.libre3.crypto.Libre3FirstPairPhase5Source
import app.aaps.plugins.libre3.crypto.Libre3PhoneCert
import app.aaps.plugins.libre3.crypto.Libre3RandomSource
import app.aaps.plugins.libre3.crypto.Libre3SensorCert
import app.aaps.plugins.libre3.crypto.Libre3SessionMaterial
import app.aaps.plugins.libre3.gatt.Libre3BluetoothUuids
import app.aaps.plugins.libre3.gatt.Libre3GattClient
import app.aaps.plugins.libre3.identity.Libre3SessionStore
import app.aaps.plugins.libre3.parse.Libre3GlucoseFrameAssembler
import kotlin.concurrent.Volatile

/**
 * Builds the block maker that the pairing messages use.
 *
 * The pairing plane and the glucose plane use different block makers, and mixing them is a safety
 * rule of this project. This is the pairing one. It is passed in so the session does not depend on
 * how it is built, and so it can be replaced in tests.
 */
fun interface Libre3PairingBlockFactory {

    /** @param phase5RawKey the stored pairing key of this sensor. */
    fun blockFor(phase5RawKey: ByteArray): Libre3AesBlock
}

/**
 * One whole Bluetooth session, from connect to a running glucose stream.
 *
 * The order of the steps is a safety order, not a style choice:
 *
 * 1. Ask the start policy what may be done at all. Nothing happens without a stored PIN.
 * 2. Connect and turn on the three pairing channels **before** any command goes out, otherwise the
 *    answers of the sensor are lost.
 * 3. Run the short reconnect, or the first pairing, but never one after the other.
 * 4. Only after the last pairing step, turn the seven data channels off and on again. Without that
 *    the link stays open and no glucose ever arrives.
 *
 * ⚠️ ASYNC IMPACT: [open] blocks until the session is up or has failed. It runs on the driver's
 * own executor, never on the main thread and never on the NFC thread.
 */
class Libre3BleSession(
    private val gatt: Libre3GattClient,
    private val store: Libre3SessionStore,
    private val pairingBlocks: Libre3PairingBlockFactory,
    private val random: Libre3RandomSource = Libre3RandomSource.secure(),
    /**
     * The keys a sensor certificate must be signed by. The sensor maker's own, unless a test says
     * otherwise. LibreCRKit `PairingFlow` takes the same list for the same reason.
     */
    private val sensorCertSigningKeys: List<ByteArray> = Libre3SensorCert.KNOWN_SIGNING_KEYS,
) {

    /** Joins the two pieces of every glucose message. Cleared whenever a link ends. */
    val glucoseFrames = Libre3GlucoseFrameAssembler()

    @Volatile
    private var dataPlane: Libre3DataPlaneCrypto? = null

    /** What came out of a session attempt. */
    sealed interface Result {

        /** The session is up. Glucose can now arrive. */
        data class Up(val material: Libre3SessionMaterial) : Result

        /** Nothing was even tried, and why. */
        data class Refused(val refusal: Libre3StartRefusal) : Result

        /**
         * The attempt failed.
         *
         * @param handshakeReached true when the sensor answered the first pairing message. That
         *   tells the retry policy whether trying the same key again can ever work.
         */
        data class Failed(val reason: String, val handshakeReached: Boolean) : Result
    }

    /**
     * @param firstPairAvailable whether this build can pair a sensor it has never seen.
     */
    fun open(firstPairAvailable: Boolean): Result = try {
        openOrFail(firstPairAvailable)
    } catch (t: Throwable) {
        // Any way out that is not a working session must still drop the link. Android allows only
        // a few Bluetooth clients per app, and one leaked per failed attempt means that after a
        // handful of retries nothing can connect again until AAPS is force stopped. Throwable, not
        // Exception: the first pairing needs a few megabytes, so running out of memory is the one
        // real case that would otherwise leak the link. An Error still goes on up afterwards,
        // because this class cannot sensibly carry on after one.
        clearSessionState()
        gatt.disconnect()
        if (t is Error) throw t
        Result.Failed(t.message ?: "the session could not be opened", handshakeReached = false)
    }

    private fun openOrFail(firstPairAvailable: Boolean): Result {
        val identity = store.loadIdentity()
        val keys = store.loadSessionKeys()
        val decision = Libre3SessionStartPolicy.decide(
            hasStoredSensor = identity != null,
            // The store only ever returns a sensor whose PIN really reached the disk, so a sensor
            // that is there at all has a finished write behind it.
            pinWriteFinished = identity != null,
            hasPairingKey = keys.hasPairingKey,
            firstPairAvailable = firstPairAvailable,
        )
        if (decision.start == Libre3SessionStart.BLOCKED || identity == null) {
            val refusal = decision.refusal ?: Libre3StartRefusal.NO_SENSOR_STORED
            Libre3Log.i("${Libre3LogMarkers.SESSION}: nothing sent, $refusal")
            return Result.Refused(refusal)
        }
        // Everything a first pairing needs from this build is made ready first, while no link is
        // open yet. A missing certificate or a scheme that cannot run is a property of the build,
        // not of the sensor, so it must not be reported as a pairing that the sensor refused: that
        // would send the user to scan the sensor again for a fault a scan cannot fix.
        val firstPairSetup = if (decision.start == Libre3SessionStart.FIRST_PAIR) {
            try {
                prepareFirstPair()
            } catch (e: Libre3CryptoException) {
                Libre3Log.e("${Libre3LogMarkers.ERROR}: no first pairing is possible, ${e.message}")
                return Result.Failed(
                    e.message ?: "a first pairing is not possible in this build",
                    handshakeReached = false,
                )
            }
        } else {
            null
        }

        // From here on the link may be opened, so every way out has to close it again.

        // One trace per attempt. It is what tells a refused answer apart from an answer that came
        // too late, which the ordinary log cannot do.
        val trace = Libre3PairingTrace()

        glucoseFrames.reset()

        // The link layer looks for the sensor on the air before it connects, so this call also
        // covers the time the sensor took to show up.
        gatt.connect(identity.bleAddress)
        if (!gatt.isConnected()) return failed("the sensor could not be reached", handshakeReached = false)
        trace.mark("link up")

        // The three pairing channels have to listen first, or the answers are lost.
        for (channel in Libre3BluetoothUuids.HANDSHAKE_CHANNELS) {
            if (!gatt.setNotify(channel, true)) {
                return failed("a pairing channel could not be opened", handshakeReached = false)
            }
        }
        trace.mark("pairing channels open")

        val auth = Libre3SessionAuth(Libre3GattHandshakeTransport(gatt), trace)
        val phoneR2 = ByteArray(16).also { random.nextBytes(it) }

        val material = try {
            when (decision.start) {
                Libre3SessionStart.CACHED_RECONNECT -> {
                    val pairingKey = keys.phase5RawKey
                        ?: return failed("the stored pairing key disappeared", handshakeReached = false)
                    Libre3Log.i("${Libre3LogMarkers.PAIRING}: short reconnect")
                    auth.runCachedReconnect(identity.blePin, pairingBlocks.blockFor(pairingKey), phoneR2)
                }

                Libre3SessionStart.FIRST_PAIR       -> {
                    Libre3Log.i("${Libre3LogMarkers.PAIRING}: first pairing")
                    val setup = firstPairSetup
                        ?: return failed("the first pairing was not made ready", handshakeReached = false)
                    runFirstPair(auth, setup, identity.blePin, phoneR2, trace)
                        ?: return failed("the first pairing key could not be stored", handshakeReached = true)
                }

                // The start policy already turned BLOCKED away above, before any link was opened.
                // This arm only exists to make the `when` cover every case. It still drops the
                // link, because an exit from here that did not would be the one way out of this
                // method that leaks a Bluetooth client.
                Libre3SessionStart.BLOCKED          ->
                    return failed("the start policy blocked the session after the link was open", handshakeReached = false)
            }
        } catch (e: Libre3HandshakeException) {
            Libre3Log.e("${Libre3LogMarkers.ERROR}: pairing failed, ${e.message}")
            // The sensor answered and then refused, so the stored key is no longer good.
            return failed(e.message ?: "the pairing failed", handshakeReached = true)
        } catch (e: Libre3CryptoException) {
            Libre3Log.e("${Libre3LogMarkers.ERROR}: pairing message could not be read, ${e.message}")
            return failed(e.message ?: "the pairing failed", handshakeReached = true)
        }

        // These are stored so they survive process death, refreshed on every
        // handshake. Nothing reads them back yet: this version always rebuilds the glucose crypto
        // from the handshake that just finished, which is why the write is `apply` and not a
        // blocking one. Only the pairing key above has to be on the disk before we go on.
        store.saveSessionKeys(material.kEnc, material.ivEnc)
        dataPlane = Libre3DataPlaneCrypto(material.kEnc, material.ivEnc)

        // Only now. The sensor looks at all seven before it starts sending.
        if (!armDataChannels()) {
            return failed("the sensor did not open its data channels", handshakeReached = true)
        }
        Libre3Log.i("${Libre3LogMarkers.SESSION}: session up, data channels open")
        return Result.Up(material)
    }

    /** What a first pairing needs from this build, all of it ready before any link is opened. */
    private class FirstPairSetup(
        val phoneCert: ByteArray,
        val ephemeral: Libre3FirstPairMaterial,
        /** The static scalar that belongs to this certificate, or null when it brings none. */
        val staticScalarWindowOverride: ByteArray?,
    )

    /**
     * Gets everything a first pairing needs out of this build.
     *
     * @throws Libre3CryptoException when the certificate does not ship, when the scheme cannot
     *   run, or when no draw of entropy was accepted. None of these is the sensor's doing.
     */
    private fun prepareFirstPair(): FirstPairSetup {
        val phoneCert = Libre3PhoneCert.bundled()
            ?: throw Libre3CryptoException("the phone certificate does not ship with this build")
        return FirstPairSetup(
            phoneCert = phoneCert.raw,
            ephemeral = Libre3FirstPairEphemeral.make(random),
            staticScalarWindowOverride = phoneCert.phase5StaticScalarWindowOverride,
        )
    }

    /**
     * The whole first pairing, and the one thing that must not be skipped afterwards.
     *
     * The key this makes is the only way back to the sensor. If it is not written to the disk, a
     * reconnect has nothing to reuse and the user has to scan the sensor again, so a failed write
     * is treated as a failed pairing rather than quietly ignored. The write happens **before**
     * `0x11` and Phase 5, so the heavy key work is done while the sensor is not yet waiting for
     * the last answer. A sensor that drops the link in that wait used to do so after the phone
     * had already sent `0x11` and then paused to derive the key.
     *
     * That order also settles what a later attempt must do, and it is worth writing down:
     *
     * - If the write fails, the sensor never receives Phase 5, so it was never authorised. The
     *   next attempt has no stored key, sends `0x01` again, and that is right.
     * - If the write works and a later step fails, the key is on the disk, so the next attempt
     *   takes the short `0x11` path, which is the only path an already active sensor accepts.
     *   LibreLoop says plainly that `0x01` on an active sensor fails at Phase 6, and this driver
     *   follows it.
     *
     * @return the session material, or null when the key could not be written.
     */
    private fun runFirstPair(
        auth: Libre3SessionAuth,
        setup: FirstPairSetup,
        blePin: ByteArray,
        phoneR2: ByteArray,
        trace: Libre3PairingTrace,
    ): Libre3SessionMaterial? {
        val ephemeral = setup.ephemeral
        val afterEph = auth.runFirstPairUntilEphemeral(
            phoneCert = setup.phoneCert,
            phoneEphemeralPublicKey72 = ephemeral.keyPair.publicKeyPadded72,
        )

        val sensorCert = Libre3SensorCert.parse(afterEph.sensorCert)
        // The reference refuses a certificate whose signature does not check out, and so does
        // this. Going on would build a key from a point nobody vouched for, and the pairing would
        // fail several steps later for a reason no log could explain.
        if (!sensorCert.isSignedByKnownKey(sensorCertSigningKeys)) {
            throw Libre3HandshakeException("the certificate of this sensor is not signed by a key we know")
        }
        trace.bytes("sensor static point", sensorCert.staticPublicKey)
        val phase5 = Libre3FirstPairPhase5Source.derive(
            material = ephemeral,
            sensorEphemeralPublicKey65 = afterEph.sensorEphemeralPublicKey,
            sensorStaticPublicKey65 = sensorCert.staticPublicKey,
            staticScalarWindowOverride = setup.staticScalarWindowOverride,
        )
        trace.mark(
            if (setup.staticScalarWindowOverride != null) {
                "pairing key derived, static scalar from the certificate"
            } else {
                "pairing key derived, static scalar from the entry source"
            }
        )
        trace.secret("key derivation source", phase5.source66)
        trace.secret("pairing key", phase5.rawKey)

        if (!store.savePhase5RawKeyAndWait(phase5.rawKey)) {
            Libre3Log.e("${Libre3LogMarkers.ERROR}: the new pairing key did not reach the disk")
            return null
        }
        trace.mark("pairing key written to disk")
        Libre3Log.i("${Libre3LogMarkers.PAIRING}: pairing key stored, a reconnect is now possible")

        return try {
            auth.runAuthorization(
                blePin = blePin,
                phase5Block = pairingBlocks.blockFor(phase5.rawKey),
                phoneR2 = phoneR2,
            )
        } catch (e: Libre3Phase5RefusedException) {
            // The sensor never authorised this phone, so the key just written is worthless. It is
            // dropped again, otherwise the next attempt would take the short reconnect path with a
            // key the sensor has never seen, and no scan could ever get out of that.
            val dropped = store.clearPhase5RawKeyAndWait()
            Libre3Log.w(
                "${Libre3LogMarkers.PAIRING}: the sensor refused the answer of this phone, " +
                    "so the new pairing key was dropped again, write finished=$dropped",
            )
            throw e
        }
    }

    /** Ends a failed attempt: the session state is undone, the link is dropped, then the reason. */
    private fun failed(reason: String, handshakeReached: Boolean): Result {
        clearSessionState()
        gatt.disconnect()
        return Result.Failed(reason, handshakeReached)
    }

    /**
     * Undoes what a half started session left behind.
     *
     * The same two fields that [close] clears, so that no way out can leave a crypto plane on a
     * session that is not up, or a piece of a message from a link that has ended.
     */
    private fun clearSessionState() {
        dataPlane = null
        glucoseFrames.reset()
    }

    /** The crypto of the running session, or null when no session is up. */
    fun dataPlaneCrypto(): Libre3DataPlaneCrypto? = dataPlane

    /**
     * Ends the session.
     *
     * Link level only. No command is written to the sensor, whatever the reason.
     */
    fun close(reason: Libre3DisconnectPolicy.Reason) {
        check(!Libre3DisconnectPolicy.mayWriteSensorCommand(reason)) {
            "no reason may ever write a command to the sensor"
        }
        dataPlane = null
        // A piece of a message left over from this link must never be joined to a piece from the
        // next one.
        glucoseFrames.reset()
        gatt.disconnect()
        Libre3Log.i("${Libre3LogMarkers.SESSION}: session closed, reason=$reason")
    }

    /** Turns the seven data channels off and then on again, in the order the sensor expects. */
    private fun armDataChannels(): Boolean {
        for (channel in Libre3BluetoothUuids.DATA_PLANE_CHANNELS) {
            gatt.setNotify(channel, false)
            if (!gatt.setNotify(channel, true)) {
                Libre3Log.w("${Libre3LogMarkers.SESSION}: channel $channel stayed shut")
                return false
            }
        }
        return true
    }
}
