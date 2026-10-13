package app.aaps.plugins.constraints.signatureVerifier

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.HandlerThread
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.PluginConstraints
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.constraints.R
import app.aaps.plugins.constraints.signatureVerifier.keys.SignatureVerifierLongKey
import org.spongycastle.util.encoders.Hex
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AndroidAPS is meant to be build by the user.
 * In case someone decides to leak a ready-to-use APK nonetheless, we can still disable it.
 * Self-compiled APKs with privately held certificates cannot and will not be disabled.
 */
@Suppress("PrivatePropertyName")
@Singleton
class SignatureVerifierPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    preferences: Preferences,
    private val context: Context,
    private val uiInteraction: UiInteraction
) : PluginBaseWithPreferences(
    pluginDescription = PluginDescription()
        .mainType(PluginType.CONSTRAINTS)
        .neverVisible(true)
        .alwaysEnabled(true)
        .showInList { false }
        .pluginName(R.string.signature_verifier),
    ownPreferences = listOf(SignatureVerifierLongKey::class.java),
    aapsLogger, rh, preferences
), PluginConstraints {

    private var handler: Handler? = null

    private val REVOKED_CERTS_URL = "https://raw.githubusercontent.com/nightscout/AndroidAPS/master/app/src/main/assets/revoked_certs.txt"
    private val UPDATE_INTERVAL = TimeUnit.DAYS.toMillis(1)

    private val lock: Any = arrayOfNulls<Any>(0)
    private var revokedCertsFile: File? = null
    private var revokedCerts: List<ByteArray>? = null
    override fun onStart() {
        super.onStart()
        handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)
        revokedCertsFile = File(context.filesDir, "revoked_certs.txt")
        handler?.post {
            loadLocalRevokedCerts()
            if (shouldDownloadCerts()) {
                try {
                    downloadAndSaveRevokedCerts()
                } catch (e: IOException) {
                    aapsLogger.error("Could not download revoked certs", e)
                }
            }
            if (hasIllegalSignature()) showNotification()
        }
    }

    override fun onStop() {
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
        uiInteraction.addNotification(Notification.INVALID_VERSION, rh.gs(R.string.running_invalid_version), Notification.URGENT)
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
        saveRevokedCerts(download)
        preferences.put(SignatureVerifierLongKey.LastRevokedCertCheck, System.currentTimeMillis())
        synchronized(lock) { revokedCerts = parseRevokedCertsFile(download) }
    }

    private fun loadLocalRevokedCerts() {
        try {
            var revokedCerts = readCachedDownloadedRevokedCerts()
            if (revokedCerts == null) revokedCerts = readRevokedCertsInAssets()
            synchronized(lock) { this.revokedCerts = parseRevokedCertsFile(revokedCerts) }
        } catch (e: IOException) {
            aapsLogger.error("Error in SignatureVerifierPlugin", e)
        }
    }

    @Throws(IOException::class)
    private fun saveRevokedCerts(revokedCerts: String) {
        val outputStream: OutputStream = FileOutputStream(revokedCertsFile)
        outputStream.write(revokedCerts.toByteArray(StandardCharsets.UTF_8))
        outputStream.close()
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

    private fun parseRevokedCertsFile(file: String?): List<ByteArray> {
        val revokedCerts: MutableList<ByteArray> = ArrayList()
        for (line in file!!.split("\n").toTypedArray()) {
            if (line.startsWith("#")) continue
            revokedCerts.add(Hex.decode(line.replace(" ", "").replace(":", "")))
        }
        return revokedCerts
    }
}