package app.aaps.implementation.protection

// Core
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.protection.SecureEncrypt
import javax.inject.Inject

// Android KeyStore
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import app.aaps.core.objects.crypto.CryptoUtil
import app.aaps.core.utils.hexStringToByteArray
import java.io.IOException
import java.security.InvalidKeyException
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException
import java.security.NoSuchProviderException
import java.security.UnrecoverableKeyException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Singleton

/***
 * Implementation for class SecureEncrypt
 *
 * This provides functions for encrypting/decrypting plain text strings using the Android KeyStore.
 * The Android Keystore system stores cryptographic keys in a container to make them more difficult to extract
 * from the device. Once keys are in the keystore, they can be used cryptographic operations, with the key material
 * remaining non-exportable.
 */

@Singleton
class SecureEncryptImpl @Inject constructor(
    private var log: AAPSLogger,
    private var cryptoUtil: CryptoUtil
    ) : SecureEncrypt {

    companion object {
        // Internal "constant" stings
        const val MODULE = "ENCRYPT"
        const val HEX_STRING_SEPARATOR = ":"
    }

    /***
     * Encrypt plaintext secret
     * - plaintextSecret: Plain text string to be encrypted
     * - keystoreAlias: KeyStore alias name for encryption/decryption
     * Returns: encrypted or empty string
     */
    override fun encrypt(plaintextSecret: String, keystoreAlias: String): String {
        //
        if (!plaintextSecret.isEmpty()) {
            // Encrypt plaintextSecret string
            val classEncryptedData = keyStoreEncrypt(keystoreAlias, plaintextSecret)
            val secret = classEncryptedDataToString(classEncryptedData)
            log.info(LTag.CORE, "$MODULE: encrypt() stored encryption secret.")
            return secret
        }
        else {
            log.debug(LTag.CORE, "$MODULE: encrypt() not encrypting empty secret.")
        }
        return ""
    }

    /***
     * Decrypt plaintext string
     * - encryptedSecret: encrypted text string
     * Returns: decrypted text string
     */
    override fun decrypt(encryptedSecret: String): String {
        if (!encryptedSecret.isEmpty()) {
            // Prepare for decryption
            val classEncryptedData = stringToClassEncryptedData(encryptedSecret)
            // Decrypt string
            val decryptedSecret = keyStoreDecrypt(classEncryptedData)
            log.info(LTag.CORE, "$MODULE: decrypt() secret decrypted.")
            return decryptedSecret
        }
        else
            log.debug(LTag.CORE, "$MODULE: decrypt() empty not decrypting empty secret.")

        return ""
    }

    /**
     * Check if header part of the DataString is valid hash
     */
    override fun isValidDataString(data: String?): Boolean {
        with(data) {
            if (!isNullOrEmpty() && split(HEX_STRING_SEPARATOR).size > 1) {
                val dataContent = substringAfter(HEX_STRING_SEPARATOR)
                return (substringBefore(HEX_STRING_SEPARATOR) == cryptoUtil.sha256(dataContent))
            }
        }
        return false
    }

    /*************************************************************************
     * Private functions
    *************************************************************************/

    /***
     * getKeyFromKeyStore() generates new or gets existing alias key from local phones KeyStore
     * - keyAlias: KeyStore alias name to use
     * - forceNew: Force generate new key
     * Returns: New or existing KeyStore key for alias
     *
     * Note getKeyFromKeyStore runtime can throw the following exceptions:
     *  is KeyStoreException,
     *  is NoSuchAlgorithmException,
     *  is InvalidKeyException
     *  is UnrecoverableKeyException
     *  is NoSuchProviderException
     *  is IOException
     *
     */
    private fun getKeyFromKeyStore(keyAlias: String, forceNew: Boolean = false): SecretKey {
        // Get KeyStore instance
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        val keyStoreIsAvailable = keyStore.containsAlias(keyAlias)

        // Create new KeyStore alias or reuse existing key generation or retrieval
        if (!keyStoreIsAvailable || forceNew) {
            // Keystore alias does not exist in KeyStore: generate new key
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            keyGenerator.init(keyGenParameterSpec)
            // Return newly generated key
            return keyGenerator.generateKey()
        }
        // Else: Alias exists in KeyStore: retrieve existing key
        return retrieveSecretKeyFromKeyStore(keyAlias)
    }

    /***
     * Get secret key from local phones alias KeyStore
     * - keyAlias: KeyStore alias name to use
     * Returns: Existing KeyStore key for alias
     */
    private fun retrieveSecretKeyFromKeyStore(keyAlias: String): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        val secretKeyEntry = keyStore.getEntry(keyAlias, null) as KeyStore.SecretKeyEntry
        val secretKey = secretKeyEntry.secretKey
        return secretKey
    }

    /***
     * Convert hex string to ByteArray
     * - Hex formatted string
     * Returns ByteArray
     */
    private fun hexStringToByteArray(hexString: String): ByteArray {
        val len = hexString.length
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            data[i / 2] = ((Character.digit(hexString[i], 16) shl 4) + Character.digit(hexString[i + 1], 16)).toByte()
        }
        return data
    }

    /***
     * Data class holding encryption attributes
     * - ivHexString: Hex formatted KeyStore iv parameter
     * - encryptedDataHexString: Hex formatted, encrypted data string
     */
    data class ClassEncryptedData (
        var isValid: Boolean,
        var keyStoreAlias: String,
        var ivHexString: String,
        var encryptedDataHexString: String
    )

    /**
     * Get classEncryptedData into one single String formatted "<keyStoreAlis><separator><ivHexString><separator><encryptedDataHexString>" for easy storing.
     * - ClassEncryptedData object containing encryption data
     * Returns: plaintext String formatted "<hash><separator><keyStoreAlias><separator><ivHexString><separator><encryptedDataHexString>"
     */
    private fun classEncryptedDataToString(classEncryptedData: ClassEncryptedData): String
    {
        // Construct data string
        val data = buildString {
            append(classEncryptedData.keyStoreAlias)
            append(HEX_STRING_SEPARATOR)
            append(classEncryptedData.ivHexString)
            append(HEX_STRING_SEPARATOR)
            append(classEncryptedData.encryptedDataHexString)
        }
        // Return data string with header hash value
        return buildString {
            append(cryptoUtil.sha256(data)) // Hash to be able to validate
            append(HEX_STRING_SEPARATOR)    // <separator>
            append(data)                    // Data...
        }
    }

    /**
     * Input plaintext string formatted "<ivHexString><separator><encryptedDataHexString>"
     * Returns initialized ClassEncryptedData object
     */
    private fun stringToClassEncryptedData(dataString: String): ClassEncryptedData
    {
        var classEncryptedData = ClassEncryptedData(isValid = false, keyStoreAlias = "", ivHexString = "", encryptedDataHexString = "")
        if (isValidDataString(dataString)) {
            val data = dataString.split(HEX_STRING_SEPARATOR)
            if (data.size == 4)
                classEncryptedData = ClassEncryptedData(isValid = true, keyStoreAlias = data[1], ivHexString = data[2], encryptedDataHexString = data[3])
        }
        return classEncryptedData
    }

    /***
     * Encrypt plaintext data string using KeyStore alias
     * Returns: ClassEncryptedData
     */
    @OptIn(ExperimentalStdlibApi::class)
    private fun keyStoreEncrypt(keyAlias: String, originalDataString: String): ClassEncryptedData {
        // Initialize data class
        var classEncryptedData = ClassEncryptedData(isValid = false, keyStoreAlias = keyAlias, ivHexString = "", encryptedDataHexString = "")

        try {
            // Get secret key
            val secretKey = getKeyFromKeyStore(keyAlias)

            // Initialize Cipher for encryption & get iv
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val ivHex = cipher.iv.toHexString()

            // Encrypt Data
            val originalData = originalDataString.toByteArray()
            val encryptedData = cipher.doFinal(originalData)

            classEncryptedData = ClassEncryptedData (
                isValid = true,
                keyStoreAlias = keyAlias,
                ivHexString = ivHex,
                encryptedDataHexString = encryptedData.toHexString()
            )
            println("Encryption, isValid      : $classEncryptedData.isValid")
            println("Encryption, keyStoreAlias: $classEncryptedData.keyStoreAlias")
            println("Encryption, iv(hex)      : $classEncryptedData.ivHex")
            println("Encryption, Text(hex)    : $classEncryptedData.encryptedDataHexString")
        }
        catch (e: Exception) {
            when (e) {
                is KeyStoreException,
                is NoSuchAlgorithmException,
                is InvalidKeyException,
                is UnrecoverableKeyException,
                is NoSuchProviderException,
                is IOException -> {
                    log.error(LTag.CORE, "$MODULE: keyStoreEncrypt, msg=${e.message}, $e")
                }
            }
        }
        return classEncryptedData
    }

    /***
     * Decrypt plaintext data string using KeyStore alias
     * - keyAlias: KeyStore alias to use
     * - classEncryptedData: initialized ClassEncryptedData object
     * Returns: Decrypted plaintext string or empty string when error or no encrypted data
     */
    private fun keyStoreDecrypt(classEncryptedData: ClassEncryptedData): String {
        with (classEncryptedData) {
            if (!isValid || encryptedDataHexString.isEmpty()) {
                // There is no valid or encrypted data: return empty string
                return ""
            }
        }

        // Initialise
        var decryptedDataString = ""

        try {
            // Get decryption parameters
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv: ByteArray = classEncryptedData.ivHexString.hexStringToByteArray()
            val secretKey: SecretKey = retrieveSecretKeyFromKeyStore(classEncryptedData.keyStoreAlias)

            // Initialize Cipher instance we already have?
            val ivParameterSpec = GCMParameterSpec(128, iv) // Use GCMParameterSpec
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParameterSpec)

            // Decrypt
            val dataToDecrypt = hexStringToByteArray(classEncryptedData.encryptedDataHexString)
            val decryptedData = cipher.doFinal(dataToDecrypt)
            decryptedDataString = String(decryptedData)

            // Check the result:
            println("Encryption, decrypted text OK")
        }
        catch (e: Exception) {
            when (e) {
                is KeyStoreException,
                is NoSuchAlgorithmException,
                is InvalidKeyException,
                is UnrecoverableKeyException,
                is NoSuchProviderException,
                is IOException -> {
                    log.error(LTag.CORE, "$MODULE: keyStoreDecrypt, msg: ${e.message}, $e")
                }
            }
        }
        return decryptedDataString
    }

    /***
    * TESTING: Test/Debyg example code using Android KeyStore
    private fun doTest(): Boolean {
        val originalDataString = "My sensitive data"

        // Encrypt original data string
        var classEncryptedData = keyStoreEncrypt(keyAlias, originalDataString)
        /**
        Data class classEncryptedData object now holds keystore parameter iv (bytes) and encrypted data (bytes) as Hex String
        Function keyStoreDecrypt uses the key alias to retrieve the secret key and decipher the encrypted data using
        the secret key and iv
        **/
        // Make it one string for easy storing
        var testdata:String = getDataStringFromClassEncryptedData(classEncryptedData)

        // Try to get some encrypted data from elsewhere
        val decryptDebug = false
        if (decryptDebug) {
            // Debug: using fixed iv and encrypted string from previous encryption run (see logcat?)
            val testDataClass = ClassEncryptedData (
                ivHexString = "833fb80226e34b7289b3cad5",
                encryptedDataHexString = "f81583199a30642ec9c32ca2fd1d6e23ed15c874c42f2acde9b2b6774b1175fad6"
            )
            testdata = getDataStringFromClassEncryptedData(testDataClass)
        }

        // Now decrypt encryption result in decrypted data to original data string
        classEncryptedData = putDataStringToClassEncryptedData(testdata)
        val decryptedDataString = keyStoreDecrypt(keyAlias, classEncryptedData)

        if (originalDataString == decryptedDataString) {
            println("Encryption, OK!")
            return true
        } else {
            println("EncryptionERROR!")
            return false
        }
    }
    */

}
