package app.aaps.core.interfaces.protection

interface SecureEncrypt {

    /***
     * Encrypt plaintext secret
     * - plaintextSecret: Plain text string to be encrypted
     * - keystoreAlias: KeyStore alias name for encryption/decryption
     * Returns: encrypted or empty string
     */
    fun encrypt(plaintextSecret: String, keystoreAlias: String): String

    /***
     * Decrypt plaintext string
     * - encryptedSecret: encrypted text string
     * Returns: decrypted text string
     */
    fun decrypt(encryptedSecret: String): String

    /**
     * Check if header part of the data string is valid hash
     */
    fun isValidDataString(data: String?): Boolean

}