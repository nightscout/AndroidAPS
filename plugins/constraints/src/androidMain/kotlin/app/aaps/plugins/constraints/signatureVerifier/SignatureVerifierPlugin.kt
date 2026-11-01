package app.aaps.plugins.constraints.signatureVerifier

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.HandlerThread
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.PluginConstraints
import app.aaps.core.interfaces.di.APS
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.EnforcedState
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.constraints.ConstraintsStrings
import app.aaps.plugins.constraints.signatureVerifier.keys.SignatureVerifierLongKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntKey
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import org.spongycastle.util.encoders.Hex
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import kotlin.time.Duration.Companion.days

/**
 * AAPS is meant to be build by the user.
 * In case someone decides to leak a ready-to-use APK nonetheless, we can still disable it.
 * Self-compiled APKs with privately held certificates cannot and will not be disabled.
 */
@Suppress("PrivatePropertyName")
@ContributesIntoMap(AppScope::class, binding = binding<PluginBase>())
@APS
@IntKey(830)
@SingleIn(AppScope::class)
@Inject
class SignatureVerifierPlugin(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    preferences: Preferences,
    private val context: Context,
    notificationManager: NotificationManager
) : PluginBaseWithPreferences(
    pluginDescription = PluginDescription()
        .mainType(PluginType.CONSTRAINTS)
        .enforce(EnforcedState.Enabled)
        .showInList { false }
        .pluginName(ConstraintsStrings.signature_verifier),
    ownPreferences = SignatureVerifierLongKey.entries,
    aapsLogger, rh, preferences, notificationManager
), PluginConstraints {

    private var handler: Handler? = null

    private val REVOKED_CERTS_URL = "https://raw.githubusercontent.com/nightscout/AndroidAPS/master/app/src/main/assets/revoked_certs.txt"
    private val UPDATE_INTERVAL = 1.days.inWholeMilliseconds

    /** A usable line holds only hex digits, in pairs. Anything else cannot be decoded. */
    private val HEX_LINE = Regex("^([0-9a-fA-F]{2})+$")

    private val lock: Any = arrayOfNulls<Any>(0)
    private var revokedCertsFile: File? = null
    private var revokedCerts: List<ByteArray>? = null
    override suspend fun onStart() {
        super.onStart()
        handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)
        revokedCertsFile = File(context.filesDir, "revoked_certs.txt")
        handler?.post {
            // This runs on a plain HandlerThread. Anything that escapes here is an uncaught
            // exception and kills the whole app, so nothing may leave this block.
            @Suppress("TooGenericExceptionCaught")
            try {
                loadLocalRevokedCerts()
                if (shouldDownloadCerts()) {
                    try {
                        downloadAndSaveRevokedCerts()
                    } catch (e: IOException) {
                        aapsLogger.error("Could not download revoked certs", e)
                    }
                }
                if (hasIllegalSignature()) showNotification()
            } catch (e: Exception) {
                aapsLogger.error("Error in SignatureVerifierPlugin", e)
            }
        }
    }

    override suspend fun onStop() {
        handler?.removeCallbacksAndMessages(null)
        handler?.looper?.quit()
        handler = null
        super.onStop()
    }

    override fun isLoopInvocationAllowed(value: Constraint<Boolean>): Constraint<Boolean> {
        if (hasIllegalSignature()) {
            showNotification()
            value.set(false)
        }
        if (shouldDownloadCerts()) {
            handler?.post {
                try {
                    downloadAndSaveRevokedCerts()
                } catch (e: IOException) {
                    aapsLogger.error("Could not download revoked certs", e)
                }
            }
        }
        return value
    }

    private fun showNotification() {
        notificationManager.post(NotificationId.INVALID_VERSION, ConstraintsStrings.running_invalid_version)
    }

    private fun hasIllegalSignature(): Boolean {
        try {
            synchronized(lock) {
                if (revokedCerts == null) return false
                // TODO Change after raising min API to 28
                @Suppress("DEPRECATION", "PackageManagerGetSignatures")
                val signatures = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
                if (signatures != null) {
                    for (signature in signatures) {
                        val digest = MessageDigest.getInstance("SHA256")
                        val fingerprint = digest.digest(signature.toByteArray())
                        for (cert in revokedCerts!!) {
                            if (cert.contentEquals(fingerprint)) {
                                return true
                            }
                        }
                    }
                }
            }
        } catch (e: PackageManager.NameNotFoundException) {
            aapsLogger.error("Error in SignatureVerifierPlugin", e)
        } catch (e: NoSuchAlgorithmException) {
            aapsLogger.error("Error in SignatureVerifierPlugin", e)
        }
        return false
    }

    fun shortHashes(): List<String> {
        val hashes: MutableList<String> = ArrayList()
        try {
            // TODO Change after raising min API to 28
            @Suppress("DEPRECATION", "PackageManagerGetSignatures")
            val signatures = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
            if (signatures != null) {
                for (signature in signatures) {
                    val digest = MessageDigest.getInstance("SHA256")
                    val fingerprint = digest.digest(signature.toByteArray())
                    val hash = Hex.toHexString(fingerprint)
                    aapsLogger.debug("Found signature: $hash")
                    aapsLogger.debug("Found signature (short): " + singleCharMap(fingerprint))
                    hashes.add(singleCharMap(fingerprint))
                }
            }
        } catch (e: PackageManager.NameNotFoundException) {
            aapsLogger.error("Error in SignatureVerifierPlugin", e)
        } catch (e: NoSuchAlgorithmException) {
            aapsLogger.error("Error in SignatureVerifierPlugin", e)
        }
        return hashes
    }

    @Suppress("SpellCheckingInspection")
    var map =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!\"§$%&/()=?,.-;:_<>|°^`´\\@€*'#+~{}[]¿¡áéíóúàèìòùöäü`ÁÉÍÓÚÀÈÌÒÙÖÄÜßÆÇÊËÎÏÔŒÛŸæçêëîïôœûÿĆČĐŠŽćđšžñΑΒΓΔΕΖΗΘΙΚΛΜΝΞΟΠΡ\u03A2ΣΤΥΦΧΨΩαβγδεζηθικλμνξοπρςστυφχψωϨϩϪϫϬϭϮϯϰϱϲϳϴϵ϶ϷϸϹϺϻϼϽϾϿЀЁЂЃЄЅІЇЈЉЊЋЌЍЎЏАБВГДЕЖЗ"

    private fun singleCharMap(array: ByteArray): String {
        val sb = StringBuilder()
        for (b in array) {
            sb.append(map[b.toInt() and 0xFF])
        }
        return sb.toString()
    }

    private fun shouldDownloadCerts(): Boolean {
        return System.currentTimeMillis() - preferences.get(SignatureVerifierLongKey.LastRevokedCertCheck) >= UPDATE_INTERVAL
    }

    @Throws(IOException::class) private fun downloadAndSaveRevokedCerts() {
        val download = downloadRevokedCerts()
        // Check the answer before it is stored. A captive portal or a block page answers with
        // HTTP 200 and HTML, so the download itself does not fail. The saved file is preferred
        // over the list in the assets on every later start, so a bad answer must never be saved.
        val parsed = parseRevokedCertsFile(download)
        if (parsed.isEmpty()) {
            aapsLogger.error("Downloaded revoked certs file holds no usable line, keeping the old list")
            return
        }
        saveRevokedCerts(download)
        preferences.put(SignatureVerifierLongKey.LastRevokedCertCheck, System.currentTimeMillis())
        synchronized(lock) { revokedCerts = parsed }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun loadLocalRevokedCerts() {
        try {
            val cached = readCachedDownloadedRevokedCerts()
            synchronized(lock) { this.revokedCerts = parseRevokedCertsFile(cached ?: readRevokedCertsInAssets()) }
        } catch (e: Exception) {
            aapsLogger.error("Error in SignatureVerifierPlugin", e)
            // The saved file cannot be read. Drop it and use the list shipped in the assets, so
            // the next start does not fail in the same way again.
            runCatching { revokedCertsFile?.delete() }
            runCatching {
                synchronized(lock) { this.revokedCerts = parseRevokedCertsFile(readRevokedCertsInAssets()) }
            }
        }
    }

    @Throws(IOException::class)
    private fun saveRevokedCerts(revokedCerts: String) {
        val target = revokedCertsFile ?: return
        // Write to a temporary file and rename it. A download that stops half way then leaves the
        // old, working file in place instead of a half written one.
        val temp = File(target.parentFile, target.name + ".tmp")
        FileOutputStream(temp).use { it.write(revokedCerts.toByteArray(StandardCharsets.UTF_8)) }
        if (!temp.renameTo(target)) {
            temp.delete()
            throw IOException("Could not replace the revoked certs file")
        }
    }

    @Throws(IOException::class) private fun downloadRevokedCerts(): String {
        val connection = URL(REVOKED_CERTS_URL).openConnection()
        return readInputStream(connection.getInputStream())
    }

    @Throws(IOException::class)
    fun readInputStream(inputStream: InputStream): String {
        return try {
            val os = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            var read: Int
            while (inputStream.read(buffer).also { read = it } != -1) {
                os.write(buffer, 0, read)
            }
            os.flush()
            String(os.toByteArray(), StandardCharsets.UTF_8)
        } finally {
            inputStream.close()
        }
    }

    @Throws(IOException::class) private fun readRevokedCertsInAssets(): String {
        val inputStream = context.assets.open("revoked_certs.txt")
        return readInputStream(inputStream)
    }

    @Throws(IOException::class)
    private fun readCachedDownloadedRevokedCerts(): String? {
        return if (!revokedCertsFile!!.exists()) null else readInputStream(FileInputStream(revokedCertsFile))
    }

    /**
     * Reads the certificate hashes out of the revoked certs file.
     *
     * The text can come from the network or from a file on disk, so it can be damaged. A line that
     * cannot be read is skipped and logged. It is never thrown, because one bad line must not stop
     * the app from starting.
     */
    private fun parseRevokedCertsFile(file: String?): List<ByteArray> {
        val revokedCerts: MutableList<ByteArray> = ArrayList()
        for (rawLine in (file ?: "").split("\n")) {
            // trim() also removes the \r of a Windows line ending, which Hex.decode would reject.
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            val line = trimmed.replace(" ", "").replace(":", "")
            if (!HEX_LINE.matches(line)) {
                aapsLogger.error("Skipping a damaged line in the revoked certs file")
                continue
            }
            revokedCerts.add(Hex.decode(line))
        }
        return revokedCerts
    }
}
