package app.aaps.plugins.main.general.smsCommunicator.otp

import android.util.Base64
import app.aaps.core.data.configuration.Constants
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import com.eatthepath.otp.HmacOneTimePasswordGenerator
import com.google.common.io.BaseEncoding
import java.net.URLEncoder
import java.util.Locale
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OneTimePassword @Inject constructor(
    private val preferences: Preferences,
    private val rh: ResourceHelper,
    private val dateUtil: DateUtil
) {

    private var key: SecretKey? = null
    private var pin: String = ""
    private val totp = HmacOneTimePasswordGenerator()

    init {
        configure()
    }

    /**
     * Name of master device (target of OTP)
     */
    fun name(): String {
        val defaultUserName = rh.gs(app.aaps.core.ui.R.string.patient_name_default)
        var userName = preferences.get(StringKey.GeneralPatientName).replace(":", "").trim()
        if (userName.isEmpty())
            userName = defaultUserName
        return userName
    }

    /**
     * Make sure if private key for TOTP is generated, creating it when necessary or requested
     */
    fun ensureKey(forceNewKey: Boolean = false) {
        val keyBytes: ByteArray
        val strSecret = preferences.get(StringNonKey.SmsOtpSecret).trim()
        if (strSecret.isEmpty() || forceNewKey) {
            val keyGenerator = KeyGenerator.getInstance(totp.algorithm)
            keyGenerator.init(Constants.OTP_GENERATED_KEY_LENGTH_BITS)
            val generatedKey = keyGenerator.generateKey()
            keyBytes = generatedKey.encoded
            preferences.put(StringNonKey.SmsOtpSecret, Base64.encodeToString(keyBytes, Base64.NO_WRAP + Base64.NO_PADDING))
        } else {
            keyBytes = Base64.decode(strSecret, Base64.DEFAULT)
        }
        key = SecretKeySpec(keyBytes, 0, keyBytes.size, "SHA1")
    }

    private fun configure() {
        try {
            ensureKey()
        } catch (_: Exception) {
            preferences.put(StringKey.SmsOtpPassword, "")
            ensureKey()
        }
        pin = preferences.get(StringKey.SmsOtpPassword).trim()
    }

    private fun generateOneTimePassword(counter: Long): String =
        key?.let { String.format(Locale.getDefault(), "%06d", totp.generateOneTimePassword(key, counter)) } ?: ""

    /**
     * Check if given OTP+PIN is valid
     */
    fun checkOTP(otp: String): OneTimePasswordValidationResult {
        configure()
        val normalisedOtp = otp.replace(" ", "").replace("-", "").trim()

        if (pin.length < 3) {
            return OneTimePasswordValidationResult.ERROR_WRONG_PIN
        }

        if (normalisedOtp.length != (6 + pin.length)) {
            return OneTimePasswordValidationResult.ERROR_WRONG_LENGTH
        }

        if (normalisedOtp.substring(6) != pin) {
            return OneTimePasswordValidationResult.ERROR_WRONG_PIN
        }

        val counter: Long = dateUtil.now() / 30000L

        val acceptableTokens: MutableList<String> = mutableListOf(generateOneTimePassword(counter))
        for (i in 0 until Constants.OTP_ACCEPT_OLD_TOKENS_COUNT) {
            acceptableTokens.add(generateOneTimePassword(counter - i - 1))
        }
        val candidateOtp = normalisedOtp.substring(0, 6)

        if (acceptableTokens.any { candidate -> candidateOtp == candidate }) {
            return OneTimePasswordValidationResult.OK
        }

        return OneTimePasswordValidationResult.ERROR_WRONG_OTP
    }

    /**
     * Return URI used to provision Authenticator apps
     */
    fun provisioningURI(): String? =
        key?.let {
            "otpauth://totp/AndroidAPS:" + URLEncoder.encode(name(), "utf-8").replace("+", "%20") + "?secret=" + BaseEncoding.base32().encode(it.encoded).replace("=", "") + "&issuer=AndroidAPS"
        }

    /**
     * Return secret used to provision Authenticator apps, in Base32 format
     */
    fun provisioningSecret(): String? =
        key?.let { BaseEncoding.base32().encode(it.encoded).replace("=", "") }

}