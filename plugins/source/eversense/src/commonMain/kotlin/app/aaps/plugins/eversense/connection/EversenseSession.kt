package app.aaps.plugins.eversense.connection

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.pump.ble.BleLink
import app.aaps.core.interfaces.pump.ble.BleResult
import app.aaps.core.interfaces.pump.ble.BleUuid
import app.aaps.core.interfaces.pump.ble.CharacteristicId
import app.aaps.core.interfaces.pump.ble.CharacteristicProperty
import app.aaps.core.interfaces.pump.ble.LinkState
import app.aaps.core.interfaces.pump.ble.Subscription
import app.aaps.core.interfaces.pump.ble.WriteType
import app.aaps.plugins.eversense.callbacks.EversenseWatcher
import app.aaps.plugins.eversense.enums.EversenseAlarm
import app.aaps.plugins.eversense.enums.EversenseSecurityType
import app.aaps.plugins.eversense.exceptions.EversenseWriteException
import app.aaps.plugins.eversense.packets.Eversense365Communicator
import app.aaps.plugins.eversense.packets.EversenseBasePacket
import app.aaps.plugins.eversense.packets.EversenseE3Communicator
import app.aaps.plugins.eversense.packets.e3.EnterDiagnosticModePacket
import app.aaps.plugins.eversense.packets.e3.EversenseE3Packets
import app.aaps.plugins.eversense.packets.e3.ExitDiagnosticModePacket
import app.aaps.plugins.eversense.packets.e3.GetCurrentDatetimePacket
import app.aaps.plugins.eversense.packets.e3.SaveBondingInformationPacket
import app.aaps.plugins.eversense.packets.e3.SetCurrentDatetimePacket
import app.aaps.plugins.eversense.packets.e365.AuthIdentityPacket
import app.aaps.plugins.eversense.packets.e365.AuthStartPacket
import app.aaps.plugins.eversense.packets.e365.AuthWhoAmIPacket
import app.aaps.plugins.eversense.packets.e365.EnterDiagnosticMode365Packet
import app.aaps.plugins.eversense.packets.e365.Eversense365Packets
import app.aaps.plugins.eversense.packets.e365.ExitDiagnosticMode365Packet
import app.aaps.plugins.eversense.packets.e365.KeepAlivePacket
import app.aaps.plugins.eversense.packets.e365.PushAlarmWithDataPacket
import app.aaps.plugins.eversense.protocol.NotificationAssembler
import app.aaps.plugins.eversense.protocol.PacketTime
import app.aaps.plugins.eversense.storage.EversenseStore
import app.aaps.plugins.eversense.storage.edit
import app.aaps.plugins.eversense.util.DmsSignIn
import app.aaps.plugins.eversense.util.EversenseCrypto365Util
import app.aaps.plugins.eversense.util.EversenseHttp365Util
import app.aaps.plugins.eversense.util.StorageKeys
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * One connection to a transmitter, on one [BleLink]: the frames it receives, the packets it sends and the sign-in after
 * the connect. It lives inside its own [scope], which the driver cancels when the link ends; all its work ends with it,
 * so nothing of an old link reaches the next one. Everything runs on the driver's single thread.
 *
 * Work that sends packets (a sync, a calibration, a signal read) runs in [task], one at a time, as on the single
 * Bluetooth thread of the first version. A task must not start another task: it would wait for itself.
 */
internal class EversenseSession(
    private val link: BleLink,
    private val request: CharacteristicId,
    private val response: CharacteristicId,
    val security: EversenseSecurityType,
    private val payloadSize: Int,
    private val store: EversenseStore,
    override val aapsLogger: AAPSLogger,
    private val scope: CoroutineScope,
    private val host: Host
) : EversenseLink {

    /** What a session needs from the driver. */
    interface Host {

        val username: String
        val password: String
        val positioningMode: Boolean
        fun hasInternet(): Boolean
        fun watchers(): List<EversenseWatcher>
        fun authenticationSucceeded()
        fun authenticationFailed(shortcut: Boolean)

        /** Signed in and synced: the link is ready for calibrations and signal reads. */
        fun ready()

        /** A reading or a keep alive came: the link is alive. */
        fun dataReceived()

        /** The session wants the link closed; the driver connects again after its usual wait. */
        fun closeLink(reason: String)
    }

    val is365: Boolean get() = security == EversenseSecurityType.SecureV2

    internal var cryptoUtil = EversenseCrypto365Util(store, aapsLogger)

    /** Android picks "without response" for a characteristic that offers it, and the transmitter was always written so. */
    private val writeType = if (CharacteristicProperty.WRITE_WITHOUT_RESPONSE in link.services.characteristics[request].orEmpty()) WriteType.WITHOUT_RESPONSE
    else WriteType.WITH_RESPONSE

    private val notifications = NotificationAssembler(PacketTime::nowMillis) { notice ->
        when (notice) {
            NotificationAssembler.Notice.DUPLICATE -> aapsLogger.debug(LTag.BGSOURCE) { "Duplicate notification ignored" }
            else                                   -> aapsLogger.warn(LTag.BGSOURCE, "Notification assembly: $notice")
        }
    }

    private class Exchange(val packet: EversenseBasePacket) {

        val done = CompletableDeferred<Unit>()
    }

    private var pending: Exchange? = null
    private val writeLock = Mutex()
    private val taskLock = Mutex()

    /** Runs [block] when no other task runs; tasks run one at a time, in the order they came. */
    suspend fun <T> task(block: suspend EversenseSession.() -> T): T = taskLock.withLock { block() }

    /**
     * Runs [block] as a task of this link for a caller outside it (a calibration, a signal read). The work belongs to
     * the link: when the link ends first, the caller gets an [EversenseWriteException].
     */
    suspend fun <T> perform(block: suspend EversenseSession.() -> T): T {
        // A failure goes back to the caller as a value. As a failed child of the link's scope it would end the link,
        // and the driver's reconnect loop with it: a calibration the transmitter refuses is not a lost link.
        val work = scope.async {
            try {
                Result.success(task(block))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
        val outcome = try {
            work.await()
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()
            throw EversenseWriteException("The link ended")
        }
        return outcome.getOrThrow()
    }

    /** Starts [block] as a task of this link and does not wait for it. A failure is logged; it does not end the link. */
    fun launchTask(block: suspend EversenseSession.() -> Unit) {
        scope.launch {
            try {
                task(block)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                aapsLogger.error(LTag.BGSOURCE, "Eversense task failed: $e")
            }
        }
    }

    /** Starts receiving, turns on the notifications and signs in. Returns when the sign-in has run, ready or not. */
    suspend fun start() {
        scope.launch {
            link.incoming.collect {
                if (it.characteristic != response) return@collect
                // A frame that cannot be read is dropped; it must not end the link
                try {
                    receive(it.data)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    aapsLogger.error(LTag.BGSOURCE, "Cannot read a notification: $e")
                }
            }
        }
        val subscribed = link.subscribe(response, Subscription.NOTIFY)
        if (subscribed is BleResult.Failed) {
            aapsLogger.error(LTag.BGSOURCE, "Cannot turn on the notifications: ${subscribed.failure}")
            host.closeLink("notifications refused")
            return
        }
        task { if (is365) authenticate365() else authenticateE3() }
    }

    override fun disconnectAndScheduleReconnect() = host.closeLink("closed by the protocol")

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : EversenseBasePacket.Response> writePacket(packet: EversenseBasePacket, timeoutMs: Long): T = writeLock.withLock {
        currentCoroutineContext().ensureActive()
        if (link.state.value != LinkState.Up) throw EversenseWriteException("The link is down")
        packet.log = aapsLogger
        val frame = packet.buildRequest(cryptoUtil, payloadSize)
        val exchange = Exchange(packet)
        // Set before the write: the answer can come before the write is confirmed
        pending = exchange
        try {
            val written = link.write(request, frame, writeType)
            if (written is BleResult.Failed) throw EversenseWriteException("BLE write failed: ${written.failure}")
            if (withTimeoutOrNull(timeoutMs) { exchange.done.await() } == null) {
                // Do not let a late reply be taken for the answer to the next packet on the same link
                host.closeLink("no answer in $timeoutMs ms")
                throw EversenseWriteException("Packet response timed out after ${timeoutMs}ms")
            }
            if (packet.isErrorResponse) throw EversenseWriteException("Transmitter rejected the packet")
            packet.parseResponse() as? T ?: throw EversenseWriteException("Invalid packet response")
        } finally {
            if (pending === exchange) pending = null
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun receive(rawData: ByteArray) {
        aapsLogger.debug(LTag.BGSOURCE) { "Received data: ${rawData.toHexString()}" }
        if (rawData.isEmpty()) return
        var data = rawData
        if (is365) {
            data = notifications.accept(rawData) ?: return
            if (data[0] != Eversense365Packets.AuthenticateResponseId) {
                data = cryptoUtil.decrypt(data)
                if (data.isEmpty()) {
                    // A decrypt/MAC failure means the session key no longer matches the transmitter, so retrying the
                    // shortcut is sure to fail the same way. A full WhoAmI and DMS sign-in on the next connect recovers
                    // on the first retry instead of after the shortcut failure count.
                    if (host.hasInternet()) {
                        aapsLogger.error(LTag.BGSOURCE, "Failed to decrypt data — requesting full re-auth on next connect")
                        cryptoUtil.disallowUseShortcut()
                    } else {
                        aapsLogger.warn(LTag.BGSOURCE, "Failed to decrypt data offline — retaining registered identity for a new local handshake")
                    }
                    host.closeLink("decrypt failed")
                    return
                }
            }
        }

        if (!is365 && EversenseE3Packets.isPushPacket(data[0])) {
            aapsLogger.debug(LTag.BGSOURCE) { "Keep Alive packet received (E3)!" }
            host.dataReceived()
            launchTask { e3KeepAlive() }
            return
        }

        if (data.size >= 2 && Eversense365Packets.isKeepAlivePacket(data[0], data[1])) {
            aapsLogger.debug(LTag.BGSOURCE) { "Keep Alive packet received (365)!" }
            val packet = KeepAlivePacket()
            packet.appendData(data.toUByteArray())
            val keepAlive = packet.parseResponse() ?: return
            host.dataReceived()
            val fourHalfMinAgo = PacketTime.nowMillis() - 270_000L
            if (keepAlive.glucoseDatetime > fourHalfMinAgo) launchTask {
                Eversense365Communicator.readGlucose(this, store, host.watchers())
                Eversense365Communicator.fullSync(this, store, host.watchers())
            }
            return
        }
        if (data.size >= 2 && data[0] == Eversense365Packets.NotificationResponseId && data[1] == Eversense365Packets.NotificationAlarmWithData) {
            val packet = PushAlarmWithDataPacket()
            packet.appendData(data.toUByteArray())
            val alarm = packet.parseResponse() ?: run {
                aapsLogger.warn(LTag.BGSOURCE, "Push alarm packet too short -> skipping")
                return
            }
            if (alarm.alarm.code == EversenseAlarm.UNKNOWN) {
                // Unrecognized or bogus codes (the removed TxDocked/TxUndocked 68/69) are not a real alarm, as in the
                // iOS EversenseKit
                aapsLogger.warn(LTag.BGSOURCE, "Received unknown push alarm code: ${alarm.alarm.codeRaw}")
                return
            }
            aapsLogger.info(LTag.BGSOURCE, "Push alarm received: ${alarm.alarm.code.name}")
            host.watchers().forEach { it.onAlarmReceived(alarm.alarm) }
            return
        }
        if (Eversense365Packets.isNotificationPacket(data[0])) {
            aapsLogger.warn(LTag.BGSOURCE, "Unknown notification packet received")
            return
        }

        val exchange = pending ?: run {
            aapsLogger.warn(LTag.BGSOURCE, "No packet waits for an answer -> skipping")
            return
        }
        answer(exchange, data)
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun answer(exchange: Exchange, data: ByteArray) {
        val packet = exchange.packet
        val spec = packet.spec
        // SecureV2 rejects a command with [0xff, command type, error code]. The exchange ends as rejected rather than
        // timing out and closing an authenticated link (seen: ff 0e 05 for SetAppVersion).
        if (is365 && data.size == 3 && data[0] == 0xff.toByte() && data[1] == spec.typeId) {
            aapsLogger.warn(LTag.BGSOURCE, "365 command rejected - type: ${data[1].toInt() and 0xff}, error: ${data[2].toInt() and 0xff}")
            packet.isErrorResponse = true
            exchange.done.complete(Unit)
            return
        }
        // 0x80 is an error only when it is not the expected answer: ReadSingleByteSerialFlashRegister answers 0xAA
        if (EversenseE3Packets.isErrorPacket(data[0]) && spec.responseId != data[0]) {
            aapsLogger.error(LTag.BGSOURCE, "Transmitter returned an error response")
            aapsLogger.debug(LTag.BGSOURCE) { "Error response data: ${data.toHexString()}" }
            packet.isErrorResponse = true
            exchange.done.complete(Unit)
            return
        }
        if (!is365) {
            if (!packet.skipResponseIdValidation && spec.responseId != data[0]) {
                aapsLogger.warn(LTag.BGSOURCE, "Incorrect responseId - expected: ${spec.responseId}, got: ${data[0]}")
                return
            }
        } else {
            if (spec.responseId != data[0]) {
                aapsLogger.warn(LTag.BGSOURCE, "Incorrect responseId - expected: ${spec.responseId}, got: ${data[0]}")
                return
            }
            if (data.size < 2 || spec.typeId != data[1]) {
                aapsLogger.warn(LTag.BGSOURCE, "Incorrect responseType - expected: ${spec.typeId}, got: ${data.getOrNull(1)}")
                return
            }
        }
        packet.appendData(data.toUByteArray())
        exchange.done.complete(Unit)
    }

    /**
     * The E3 keep alive: set the transmitter clock first when it drifted, so the reading has phone time (the official
     * app does the same), then read the glucose and sync.
     */
    private suspend fun e3KeepAlive() {
        try {
            val currentDatetime = writePacket<GetCurrentDatetimePacket.Response>(GetCurrentDatetimePacket())
            if (currentDatetime.needsTimeSync) {
                aapsLogger.info(LTag.BGSOURCE, "Clock drift detected before glucose read — syncing transmitter clock")
                writePacket<SetCurrentDatetimePacket.Response>(SetCurrentDatetimePacket())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            aapsLogger.warn(LTag.BGSOURCE, "Pre-glucose clock sync failed (non-fatal): $e")
        }
        EversenseE3Communicator.readGlucose(this, store, host.watchers())
        EversenseE3Communicator.fullSync(this, store, host.watchers())
    }

    private suspend fun authenticateE3() {
        aapsLogger.info(LTag.BGSOURCE, "Starting auth flow E3...")
        try {
            writePacket<SaveBondingInformationPacket.SaveBondingInformationResponse>(SaveBondingInformationPacket())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The link stays; the driver's attempt deadline ends it when it never becomes ready
            aapsLogger.error(LTag.BGSOURCE, "Auth flow E3 failed: $e")
            return
        }
        aapsLogger.info(LTag.BGSOURCE, "E3 auth complete — notifying watchers")
        host.ready()
        host.watchers().forEach { it.onTransmitterReady() }
        // After a reconnect the positioning guide is still open: the transmitter left diagnostic mode with the link
        if (host.positioningMode) try {
            setDiagnosticMode(true)
            aapsLogger.info(LTag.BGSOURCE, "Re-enabled Diagnostic Mode after reconnect")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            aapsLogger.warn(LTag.BGSOURCE, "Failed to re-enable diagnostic mode after reconnect: $e")
        }
        EversenseE3Communicator.fullSync(this, store, host.watchers(), force = true)
    }

    private suspend fun authenticate365() {
        var shortcutAttempt = false
        try {
            shortcutAttempt = cryptoUtil.canUseShortcut()
            if (!cryptoUtil.generateKeyPairIfNotExists()) {
                host.closeLink("no key pair")
                return
            }
            if (!cryptoUtil.canUseShortcut()) {
                // A 365 registers with the Eversense server, and that needs the login. With none (an E3 was chosen
                // without one), say so as a refused login: the overview shows it and the wizard asks for the login.
                if (host.username.isEmpty() || host.password.isEmpty()) {
                    aapsLogger.warn(LTag.BGSOURCE, "365 registration needs the Eversense login, and none is stored")
                    DmsSignIn.remember(store, DmsSignIn.Outcome.REFUSED)
                    host.closeLink("no login for the registration")
                    return
                }
                if (!host.hasInternet()) {
                    aapsLogger.warn(LTag.BGSOURCE, "365 registration requires internet; retaining stored identity and retrying later")
                    host.closeLink("no internet for the registration")
                    return
                }
                val whoAmI = writePacket<AuthWhoAmIPacket.Response>(AuthWhoAmIPacket(cryptoUtil.getClientId()))
                val login = withTimeoutOrNull(SERVER_TIMEOUT) { EversenseHttp365Util.login(aapsLogger, store) } ?: run {
                    host.closeLink("no sign-in")
                    return
                }
                // Kept for the cloud upload, so it needs no new sign-in
                store.edit(commit = true) {
                    putString(StorageKeys.ACCESS_TOKEN, login.access_token)
                    putLong(StorageKeys.ACCESS_TOKEN_EXPIRY, PacketTime.nowMillis() + login.expires_in * 1000L)
                }
                val fleet = withTimeoutOrNull(SERVER_TIMEOUT) {
                    EversenseHttp365Util.getFleetSecretV2(
                        aapsLogger = aapsLogger,
                        preferences = store,
                        accessToken = login.access_token,
                        serialNumber = whoAmI.serialNumber,
                        nonce = whoAmI.nonce,
                        flags = whoAmI.flags,
                        publicKey = cryptoUtil.getClientPublicKey()
                    )
                } ?: run {
                    host.closeLink("no transmitter certificate")
                    return
                }
                @OptIn(ExperimentalStdlibApi::class)
                writePacket<AuthIdentityPacket.Response>(AuthIdentityPacket(fleet.Result.Certificate?.hexToByteArray() ?: byteArrayOf()))
                cryptoUtil.allowUseShortcut()
            }

            val signature = cryptoUtil.generateEphem() ?: run {
                host.closeLink("no ephemeral key")
                return
            }
            val start = writePacket<AuthStartPacket.Response>(AuthStartPacket(cryptoUtil.getStartSecret(signature)))
            cryptoUtil.generateSessionKey(start.sessionPublicKey)
            host.authenticationSucceeded()

            aapsLogger.info(LTag.BGSOURCE, "365 auth complete — ready for full sync")
            Eversense365Communicator.fullSync(this, store, host.watchers(), force = true)
            // Read the glucose now, so readings do not wait for the next keep alive after a reconnect
            try {
                Eversense365Communicator.readGlucose(this, store, host.watchers())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                aapsLogger.warn(LTag.BGSOURCE, "[365] readGlucose after auth failed (non-fatal): $e")
            }
            aapsLogger.info(LTag.BGSOURCE, "365 transmitter ready — notifying watchers")
            host.ready()
            host.watchers().forEach { it.onTransmitterReady() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A lost link is not a failed sign-in
            if (!scope.isActive || link.state.value != LinkState.Up) return
            host.authenticationFailed(shortcutAttempt)
            aapsLogger.error(LTag.BGSOURCE, "[365] authV2 failed", e)
            host.closeLink("sign-in failed")
        }
    }

    /** Diagnostic (positioning) mode on or off. Call it inside a [task]. */
    suspend fun setDiagnosticMode(enabled: Boolean) {
        if (is365) {
            if (enabled) writePacket<EnterDiagnosticMode365Packet.Response>(EnterDiagnosticMode365Packet())
            else writePacket<ExitDiagnosticMode365Packet.Response>(ExitDiagnosticMode365Packet())
        } else {
            if (enabled) writePacket<EnterDiagnosticModePacket.Response>(EnterDiagnosticModePacket())
            else writePacket<ExitDiagnosticModePacket.Response>(ExitDiagnosticModePacket())
        }
        aapsLogger.info(LTag.BGSOURCE, "Diagnostic mode set to $enabled (${if (is365) "365" else "E3"})")
    }

    companion object {

        val SERVICE: BleUuid = BleUuid.of("c3230001-9308-47ae-ac12-3d030892a211")
        val E3_REQUEST = CharacteristicId(SERVICE, BleUuid.of("6eb0f021-a7ba-7e7d-66c9-6d813f01d273"))
        val E3_RESPONSE = CharacteristicId(SERVICE, BleUuid.of("6eb0f024-bd60-7aaa-25a7-0029573f4f23"))
        val V2_REQUEST = CharacteristicId(SERVICE, BleUuid.of("c3230002-9308-47ae-ac12-3d030892a211"))
        val V2_RESPONSE = CharacteristicId(SERVICE, BleUuid.of("c3230003-9308-47ae-ac12-3d030892a211"))

        /** The 365 registration's two server calls: each may take this long. */
        val SERVER_TIMEOUT = 65.seconds
    }
}
