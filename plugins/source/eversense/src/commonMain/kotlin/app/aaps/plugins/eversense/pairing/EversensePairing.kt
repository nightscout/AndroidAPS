package app.aaps.plugins.eversense.pairing

import app.aaps.core.interfaces.pump.ble.ScannedDevice
import app.aaps.core.interfaces.source.CgmAttention
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Pairing an Eversense transmitter: the login, the transmitter, and what the user has to fix. What the pairing wizard
 * and the plugin's screen need from the driver, so that both are shared code and a driver on another platform only
 * implements this.
 *
 * The login and the transmitter are stored by the driver, not in the AAPS settings: they are not exported, and a new
 * phone pairs again (a 365 registers again anyway).
 */
interface EversensePairing {

    /** The stored login, or null when there is none. */
    val account: StateFlow<EversenseAccount?>

    /** An E3 used without a login: its link needs none. The Eversense cloud upload is off then. */
    val withoutSignIn: StateFlow<Boolean>

    /** The name of the paired transmitter, or null when none is paired. */
    val transmitter: StateFlow<String?>

    /** What the user has to fix, or null: see [cgmAttention]. */
    val attention: StateFlow<CgmAttention?>

    /**
     * Signs in to the Eversense server that [model] uses. The login is stored only when the server accepts it, so a
     * mistyped password never replaces a working one.
     */
    suspend fun signIn(username: String, password: String, model: EversenseModel): SignInResult

    /** Forgets the login and closes the link; the transmitter stays paired. */
    fun signOut()

    /**
     * Uses an E3 without a login (a stored one is forgotten). Only for an E3: a 365 registers this phone with the
     * Eversense server, and that needs the login.
     */
    fun useWithoutSignIn()

    /** Eversense transmitters in range, as they are found, for about ten seconds. */
    fun scan(): Flow<List<ScannedDevice>>

    /** Why a scan cannot find anything now, or null: asked when a scan has ended with nothing. */
    fun scanProblem(): ScanProblem? = null

    /**
     * Pairs [transmitter]: stores it, connects and waits until the link is ready - for a 365 after its online
     * registration. A transmitter paired before is forgotten.
     */
    suspend fun pair(transmitter: ScannedDevice): PairResult

    /** Forgets the transmitter and closes the link; the login stays, so a replacement pairs without signing in. */
    fun unpair()
}

/** Why a scan cannot run. */
enum class ScanProblem { BLUETOOTH_OFF, NOT_ALLOWED }

/**
 * Which Eversense, and so which Eversense server the login belongs to. The E3 and the 365 sign in at different places,
 * and a 365 has a server in the USA and one for other countries. The driver finds out the model itself once connected;
 * this only picks the server for the login.
 */
enum class EversenseModel {

    E3,
    E365_USA,
    E365_OTHER
}

/**
 * @param model the model chosen at sign-in, or null for a login stored before the model was asked
 */
data class EversenseAccount(val username: String, val model: EversenseModel?)

enum class SignInResult {

    OK,

    /** The server refused the username or the password. */
    REFUSED,

    /** The server could not be reached. */
    NO_CONNECTION,

    /** The server answered with an error that is not about the login. */
    SERVER_ERROR
}

enum class PairResult {

    /** Connected and ready: readings follow. */
    READY,

    /** Not ready within the wait: out of range, not in pairing mode, held by the official app, or no internet. */
    TIMED_OUT,

    /** The link cannot be opened: Eversense is not the active BG source, Bluetooth is off, or the login is missing (only an E3 links without one). */
    NOT_STARTED
}

/**
 * What the user has to fix: nothing to start with (no login, unless an E3 is used [withoutSignIn], or no transmitter),
 * or a login the server refused. Signing out or in again clears a refusal. Without a login it means the transmitter is
 * a 365, which cannot register without one.
 */
fun cgmAttention(account: EversenseAccount?, transmitter: String?, signInRefused: Boolean, withoutSignIn: Boolean = false): CgmAttention? = when {
    (account == null && !withoutSignIn) || transmitter == null -> CgmAttention.NOT_PAIRED
    signInRefused                                              -> CgmAttention.SIGN_IN_FAILED
    else                                                       -> null
}
