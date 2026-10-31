/*
 * KEKS J-PAKE / ExtraData authentication for Dexcom G7 / ONE+.
 *
 * Translated to Kotlin from NightscoutFoundation/xDrip
 *   https://github.com/NightscoutFoundation/xDrip
 *   pin commit 1e86d9a2a52577ed2c30fbf7b69d75fd56e6918f (tag 2026.07.15)
 * Authorship per upstream comments: JamOrHam / jamorham. Upstream carries no
 * formal per-file "Copyright (C) <year> <name>" line; none is asserted or
 * invented here. See plugins/libkeks/NOTICE.
 *
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * Part of AndroidAPS (AGPL-3.0-or-later); these KEKS sources retain their
 * upstream GPL-3.0 terms. Free software, distributed WITHOUT ANY WARRANTY.
 * See https://www.gnu.org/licenses/gpl-3.0.html
 */
package jamorham.keks

import com.eveningoutpost.dexdrip.plugin.IPluginDA
import jamorham.keks.Config.Get.ALICE
import jamorham.keks.Config.Get.BOB
import jamorham.keks.Config.Get.GETDATA
import jamorham.keks.Config.Get.GETDATA2
import jamorham.keks.Config.Get.KEYCMD
import jamorham.keks.Config.Get.SPARAM
import jamorham.keks.crypto.KeksArrayIndexOutOfBoundsException
import jamorham.keks.crypto.KeksInvalidParameterException
import jamorham.keks.crypto.KeksLock
import jamorham.keks.crypto.KeksSecurityException
import jamorham.keks.crypto.withLock
import jamorham.keks.message.AuthChallengeTxMessage
import jamorham.keks.message.AuthRequestTxMessage2
import jamorham.keks.message.AuthStatusRxMessage
import jamorham.keks.message.CertInfoRxMessage
import jamorham.keks.message.CertInfoTxMessage.Companion.expectMyCert1
import jamorham.keks.message.CertInfoTxMessage.Companion.expectMyCert2
import jamorham.keks.message.SignChallengeTxMessage
import jamorham.keks.util.Log
import jamorham.keks.util.Util.arrayAppend
import jamorham.keks.util.Util.bytesToHex
import kotlin.concurrent.Volatile

/**
 * JamOrHam
 *
 * KEKS Plugin
 */
class Plugin : IPluginDA {

    val context = Context()

    @Volatile private var lastAuthTx2: AuthRequestTxMessage2? = null

    @Volatile private var state = 0

    @Volatile private var accumulator = ByteArray(0)

    @Volatile private var newFwchal = ByteArray(0)

    @Volatile private var keyToTestAgainst: ByteArray? = null

    private var expectedSize = 0

    private var presponse: ByteArray? = null

    private var alt = false

    private val stateLock = KeksLock()

    init {
        context.alice = ALICE.bytes
        context.bob = BOB.bytes
        context.keyA = KeyPair()
        context.KeyB = KeyPair()
    }

    constructor(password: String) {
        context.password = password
        context.ensurePasswordBytes()
    }

    constructor()

    private fun changeState(newState: Int) = stateLock.withLock {
        Log.d(TAG, "Changing state: " + stateToName(state) + " - > " + stateToName(newState))
        if (!dontClearAccumulator.contains(newState)) {
            accumulator = EMPTY
        }
        state = newState
    }

    override fun amConnected() {
        context.resetIfNotReady()
        changeState(RoundStart)
    }

    override fun bondNow(data: ByteArray?): Boolean =
        data.contentEquals(TIME_EXTENDED) ||
            data.contentEquals(TIME_EXTENDED2) ||
            data.contentEquals(TIME_EXTENDED3)

    private fun positionFromState(): Int = when (state) {
        Round1 -> 1
        Round2 -> 2
        Round3 -> 3
        else   -> 0
    }

    private fun parameterFromState(): Byte = when (state) {
        Round1 -> 0
        Round2 -> 1
        Round3 -> 2
        else   -> -1
    }

    private fun expectedBytesForState(): Int = when (state) {
        Round1, Round2, Round3                                                 -> Curve.PACKET_SIZE
        SendCertificate1, SendCertificate1out, SendCertificate2, SendCertificate2out ->
            if (expectedSize > 0) expectedSize else 0x179005
        SendKeyChallenge                                                       -> 64
        else                                                                   -> 0x602910
    }

    private fun fill(data: ByteArray): Boolean {
        accumulator = arrayAppend(accumulator, data)
        val expected = expectedBytesForState()
        Log.d(
            TAG,
            "Expected byte size for state: " + stateToName(state) + " -> " + expected +
                " so far " + accumulator.size,
        )
        return accumulator.size >= expectedBytesForState()
    }

    // Return true if we want to emit based on our state
    override fun receivedResponse(data: ByteArray?): Boolean {
        Log.d(TAG, "Received remote response: " + bytesToHex(data) + " when in " + stateToName(state))
        when (state) {
            RequestAuth          -> {
                if (!verifyChallenge(data!!)) {
                    context.reset()
                    if (context.sequence > 1) {
                        throw KeksSecurityException("Mismatch - wait")
                    } else {
                        return false
                    }
                }
                val challenge = ByteArray(8)
                // Checked here and not earlier: a packet too short to hold a challenge still has
                // to go through verifyChallenge first, because that is what reaches the "Mismatch
                // - wait" recovery the ONE+ driver relies on.
                requireLength(data, 9 + challenge.size)
                data.copyInto(challenge, 0, 9, 9 + challenge.size)
                context.challenge = challenge
                return true
            }

            ChallengeReply       -> {
                val status = AuthStatusRxMessage(data!!)
                if (status.needsRefresh()) {
                    context.reset()
                }
                if (!status.isAuthenticated()) {
                    Log.d(TAG, "Could not authenticate!")
                    context.reset()
                    if (!status.isBonded()) {
                        changeState(BondFailure)
                    } else {
                        changeState(Unknown)
                    }
                    return true
                } else {
                    if (status.isBonded()) {
                        Log.d(TAG, "Full success")
                        if (context.passwordBytes!!.size > 4) {
                            changeState(GetData)
                        } else {
                            changeState(GetData2)
                        }
                        return true
                    } else {
                        expectedSize = 0
                        presponse = null
                        if (context.passwordBytes!!.size > 4) {
                            changeState(Pairing)
                        } else {
                            if (context.validateParts()) {
                                changeState(SendCertificate0)
                            } else {
                                throw KeksInvalidParameterException("Missing QR code")
                            }
                        }
                        return true
                    }
                }
            }

            Pairing              -> return true

            SendCertificate1     -> {
                val rep = CertInfoRxMessage(data!!)
                if (rep.valid()) {
                    expectedSize = rep.size
                    changeState(SendCertificate1)
                    return true
                } else {
                    throw KeksInvalidParameterException("Invalid QR code 1")
                }
            }

            SendCertificate2     -> {
                val rep2 = CertInfoRxMessage(data!!)
                if (rep2.valid()) {
                    expectedSize = rep2.size
                    changeState(SendCertificate2)
                    return true
                } else {
                    throw KeksInvalidParameterException("Invalid QR code 2")
                }
            }

            SendKeyChallenge     -> {
                if (data!!.size > 2 && data[1].toInt() != 0) {
                    throw KeksInvalidParameterException("Invalid QR code 3")
                }
                presponse = Calc.challenger(context.partC!!, data)
                return true
            }

            SendKeyChallengeOut  -> return true
        }

        return false
    }

    override fun receivedResponse2(data: ByteArray?): Boolean = false

    override fun receivedResponse3(data: ByteArray?): Boolean = false

    private fun getAuthRequestTx2(): AuthRequestTxMessage2 {
        val message = AuthRequestTxMessage2(8, alt, newFwchal)
        lastAuthTx2 = message
        return message
    }

    override fun aNext(): Array<ByteArray?>? {
        Log.d(TAG, "Processing anext in state: " + stateToName(state))
        when (state) {
            RoundStart           ->
                return if (context.getRound3Packet() != null || context.savedKey != null) {
                    state = RequestAuth
                    arrayOf(getAuthRequestTx2().byteSequence, null)
                } else {
                    state = Round1
                    sequencePacket(null)
                }

            Round1               -> {
                state = Round2
                return sequencePacket(Calc.getRound1Packet(context).output())
            }

            Round2               -> {
                state = Round3
                return sequencePacket(Calc.getRound2Packet(context).output())
            }

            Round3               -> {
                state = RequestAuth
                // Written in this order on purpose: the auth request draws the single use token
                // first, then round 3 draws its own random number. Swapping the two would change
                // which random value lands where.
                val auth = getAuthRequestTx2().byteSequence
                return arrayOf(auth, Calc.getRound3Packet(context).output())
            }

            RequestAuth          -> {
                state = ChallengeReply
                return arrayOf(AuthChallengeTxMessage(Calc.calculateHash(context)!!).byteSequence, null)
            }

            ChallengeReply       -> {
                state = Unknown
                return arrayOf(TIME_EXTENDED, null)
            }

            SendCertificate0     -> {
                state = SendCertificate1
                return arrayOf(expectMyCert1(this), null)
            }

            SendCertificate1     -> {
                state = SendCertificate1out
                return arrayOf(null, context.partA)
            }

            SendCertificate1out  -> {
                state = SendCertificate2
                return arrayOf(expectMyCert2(this), null)
            }

            SendCertificate2     -> {
                state = SendCertificate2out
                return arrayOf(null, context.partB)
            }

            SendCertificate2out  -> {
                state = SendKeyChallenge
                return arrayOf(SignChallengeTxMessage().byteSequence, null)
            }

            SendKeyChallenge     -> {
                state = SendKeyChallengeOut
                return arrayOf(CHALLENGE_OUT, presponse)
            }

            SendKeyChallengeOut  -> {
                state = GetData
                return arrayOf(TIME_EXTENDED, null)
            }

            Pairing              -> {
                state = GetData
                return arrayOf(TIME_EXTENDED, null)
            }

            GetData              -> {
                state = Unknown
                return arrayOf<ByteArray?>(GETDATA.bytes)
            }

            GetData2             -> {
                state = Unknown
                return arrayOf<ByteArray?>(GETDATA2.bytes)
            }

            BondFailure          -> {
                state = Unknown
                return arrayOf(GETDATA.bytes, null, null)
            }
        }
        return null
    }

    override fun bNext(): Array<ByteArray?> = arrayOf()

    override fun cNext(): Array<ByteArray?> = arrayOf()

    override fun receivedData(data: ByteArray?): Boolean {
        Log.d(TAG, "Received data stream: " + bytesToHex(data))
        val full = fill(data!!)
        if (full) {
            context.packet[positionFromState()] = Packet.parse(accumulator)
            accumulator = EMPTY
            if (!validate()) {
                Log.d(TAG, "Failed validation " + stateToName(state))
                context.packet[positionFromState()] = null
                return false
            }
        }
        return full
    }

    override fun receivedData2(data: ByteArray?): Boolean = false

    override fun receivedData3(data: ByteArray?): Boolean = false

    override fun getPersistence(channel: Int): ByteArray? {
        return if (channel == 1) {
            val key = getSharedKey()
            key ?: ByteArray(0)
        } else if (channel == 3) {
            // The three tests are written out rather than hoisted, because getSharedKey() can
            // create context.savedKey as a side effect and the Java never reached it when there
            // was nothing to test against.
            if (keyToTestAgainst != null && getSharedKey() != null &&
                getSharedKey().contentEquals(keyToTestAgainst)
            ) {
                ByteArray(0)
            } else {
                null
            }
        } else {
            ByteArray(0)
        }
    }

    override fun setPersistence(channel: Int, data: ByteArray?): Boolean {
        if (channel == 1) {
            keyToTestAgainst = data
            return true
        }
        if (channel == 2) {
            if (data != null && data.size != 16) {
                return false
            }
            if (context.savedKey == null) {
                Log.d(TAG, "Updating saved key from loaded persistence data")
                context.savedKey = data
                return true
            } else {
                return false
            }
        }
        if (channel == 3 || channel == 4) {
            instance = null
        }
        if (channel == 6) {
            alt = data.contentEquals(SPARAM.bytes)
            newFwchal = data ?: EMPTY
        }
        if (channel == 7) {
            dontClearAccumulator.clear()
        }
        if (channel == 8) {
            context.partA = data
        }
        if (channel == 9) {
            context.partB = data
        }
        if (channel == 10) {
            context.partC = data
        }
        return false
    }

    override fun getStatus(): String = ""

    override fun getName(): String = "keks"

    private fun validate(): Boolean {
        val p = context.packet[positionFromState()]
        if (p == null) {
            Log.d(TAG, "Packet is null")
            return false
        }
        when (state) {
            Round1 -> return Calc.validateRound1Packet(context)
            Round2 -> return Calc.validateRound2Packet(context)
            Round3 -> return Calc.validateRound3Packet(context)
            SendCertificate1out, SendCertificate2out -> return true
        }
        Log.d(TAG, "Invalid state for validation: " + stateToName(state))
        return false
    }

    /**
     * The Java read straight past the end of a short packet and let `System.arraycopy` raise.
     * The ONE+ driver catches that type by name, so the same kind of failure is raised here.
     */
    private fun requireLength(data: ByteArray, needed: Int) {
        if (data.size < needed) {
            throw KeksArrayIndexOutOfBoundsException("packet is ${data.size} bytes, needs $needed")
        }
    }

    private fun verifyChallenge(data: ByteArray): Boolean {
        if (context.savedKey != null) return true
        context.challenge = lastAuthTx2!!.singleUseToken
        val h = Calc.calculateHash(context) ?: return false
        requireLength(data, 9)
        for (i in 0 until 8) {
            if (h[i] != data[i + 1]) return false
        }
        return true
    }

    private fun sequencePacket(packet: ByteArray?): Array<ByteArray?> {
        context.sequence++
        val param = parameterFromState()
        val command = if (param < 0) null else byteArrayOf(KEYCMD.bytes[0], param)
        return arrayOf(command, packet)
    }

    private fun getSharedKey(): ByteArray? {
        if (context.savedKey == null) {
            context.savedKey = Calc.getShortSharedKey(context)
        }
        return context.savedKey
    }

    companion object {

        private val dontClearAccumulator = HashSet<Int>()
        private const val TAG = "KEKS-Plugin"
        private val EMPTY = ByteArray(0)
        private val TIME_EXTENDED = Config.Get.TIME_EXTENDED.bytes
        private val TIME_EXTENDED2 = Config.Get.TIME_EXTENDED2.bytes
        private val TIME_EXTENDED3 = Config.Get.TIME_EXTENDED3.bytes
        private val CHALLENGE_OUT = Config.Get.CHALLENGE_OUT.bytes

        private const val Init = 0
        private const val Unknown = 370018
        private const val Scanning = 448130
        private const val Connecting = 432753
        private const val RoundStart = 789035
        private const val Pairing = 804167
        private const val Round1 = 674667
        private const val Round2 = 189857
        private const val Round3 = 588648
        private const val BondFailure = 162087
        private const val RequestAuth = 125320
        private const val ChallengeReply = 766662
        private const val SendCertificate0 = 975913
        private const val SendCertificate1 = 694702
        private const val SendCertificate2 = 230995
        private const val SendCertificate1out = 842681
        private const val SendCertificate2out = 558830
        private const val SendKeyChallenge = 486262
        private const val SendKeyChallengeOut = 327604
        private const val GetData = 734275
        private const val GetData2 = 199434

        private val instanceLock = KeksLock()

        @Volatile private var instance: Plugin? = null

        init {
            dontClearAccumulator.add(SendCertificate1)
            dontClearAccumulator.add(SendCertificate1out)
            dontClearAccumulator.add(SendCertificate2)
            dontClearAccumulator.add(SendCertificate2out)
        }

        private fun stateToName(state: Int): String = when (state) {
            Init                -> "Init"
            Unknown             -> "Unknown"
            Scanning            -> "Scanning"
            Connecting          -> "Connecting"
            RoundStart          -> "Round Start"
            Round1              -> "Round 1"
            Round2              -> "Round 2"
            Round3              -> "Round 3"
            BondFailure         -> "Bond Failure"
            Pairing             -> "Pairing"
            RequestAuth         -> "Request Auth"
            SendCertificate0    -> "Send Certificate 0"
            SendCertificate1    -> "Send Certificate 1"
            SendCertificate2    -> "Send Certificate 2"
            SendCertificate1out -> "Send Certificate 1 Out"
            SendCertificate2out -> "Send Certificate 2 Out"
            SendKeyChallenge    -> "Send Key Challenge"
            SendKeyChallengeOut -> "Send Key Challenge Out"
            ChallengeReply      -> "Challenge Reply"
            GetData             -> "Get Data"
            GetData2            -> "Get Data 2"
            else                -> "Error $state"
        }

        fun getInstance(password: String): Plugin = instanceLock.withLock {
            val current = instance
            if (current == null || current.context.password != password) {
                Log.d(TAG, "Creating new instance")
                instance = Plugin(password)
            }
            instance!!
        }
    }
}
