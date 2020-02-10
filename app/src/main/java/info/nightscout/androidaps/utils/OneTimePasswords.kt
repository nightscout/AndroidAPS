package info.nightscout.androidaps.utils

import android.util.Base64
import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator
import com.google.common.io.BaseEncoding
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.joda.time.DateTimeUtils
import java.net.URLEncoder
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OneTimePassword @Inject constructor(
    private val sp: SP,
    private val resourceHelper: ResourceHelper
) {

    private var key: SecretKey? = null
    private var pin: String = ""
    private val totp = TimeBasedOneTimePasswordGenerator()

    init {
        instance = this
        configure()
    }

    companion object {
        private lateinit var instance: OneTimePassword
        @JvmStatic
        fun getInstance(): OneTimePassword = instance
    }

    /**
     * If OTP Authenticator support is enabled by user
     */
    fun isEnabled(): Boolean {
        return sp.getBoolean(R.string.key_smscommunicator_otp_enabled, false);
    }

    /**
     * Name of master device (target of OTP)
     */
    fun name(): String {
        val defaultUserName = resourceHelper.gs(R.string.smscommunicator_default_user_display_name)
        var userName = sp.getString(R.string.key_smscommunicator_otp_name, defaultUserName).trim()
        if (userName.length == 0)
            userName = defaultUserName
        return userName
    }

    /**
     * Make sure if private key for TOTP is generated, creating it when necessary or requested
     */
    fun ensureKey(forceNewKey: Boolean = false) {
        var keyBytes: ByteArray = byteArrayOf()
        val strSecret = sp.getString(R.string.key_smscommunicator_otp_secret, "").trim()
        if (strSecret.length == 0 || forceNewKey) {
            val keyGenerator = KeyGenerator.getInstance(totp.getAlgorithm());
            keyGenerator.init(Constants.OTP_GENERATED_KEY_LENGTH_BITS);
            val generatedKey = keyGenerator.generateKey();
            keyBytes = generatedKey.encoded
            sp.putString(R.string.key_smscommunicator_otp_secret, Base64.encodeToString(keyBytes, Base64.NO_WRAP + Base64.NO_PADDING))
        } else {
            keyBytes = Base64.decode(strSecret, Base64.DEFAULT);
        }
        key = SecretKeySpec(keyBytes, 0, keyBytes.size, "SHA1")
    }

    private fun configure() {
        ensureKey()
        pin = sp.getString(R.string.key_smscommunicator_otp_pin, "").trim()
    }

    private fun generateOneTimePassword(counter: Long): String {
        if (key != null) {
            return String.format("%06d", totp.generateOneTimePassword(key, counter))
        } else {
            return ""
        }
    }

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

        if (!normalisedOtp.substring(6).equals(pin)) {
            return OneTimePasswordValidationResult.ERROR_WRONG_PIN
        }

        val milis: Long = DateTimeUtils.currentTimeMillis()
        val counter: Long = (milis / 30000L)

        var acceptableTokens: MutableList<String> = mutableListOf(generateOneTimePassword(counter))
        for (i in 0 until Constants.OTP_ACCEPT_OLD_TOKENS_COUNT) {
            acceptableTokens.add(generateOneTimePassword(counter - i - 1))
        }
        val candidateOtp = normalisedOtp.substring(0, 6)

        if (acceptableTokens.any { candidate -> candidateOtp.equals(candidate) }) {
            return OneTimePasswordValidationResult.OK
        }

        return OneTimePasswordValidationResult.ERROR_WRONG_OTP
    }

    /**
     * Return URI used to provision Authenticator apps
     */
    fun provisioningURI(): String? {
        val keyImm = key
        if (keyImm != null) {
            return "otpauth://totp/AndroidAPS:" + URLEncoder.encode(name(), "utf-8") + "?secret=" + BaseEncoding.base32().encode(keyImm.encoded).replace("=", "") + "&issuer=AndroidAPS"
        } else {
            return null
        }
    }

}