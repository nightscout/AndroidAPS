package app.aaps.core.interfaces.protection

/**
 * Hashes a password, and compares one against a stored hash.
 *
 * Deliberately not part of [PasswordCheck], which asks the *user* for a password and shows dialogs.
 * This one is only the maths, and callers also use it to decide whether a password has been set at
 * all - by checking the empty string against the stored hash.
 *
 * An interface because the hashing is real cryptography: Android has it in the JDK, and an Apple
 * target would reach for CryptoKit. Neither belongs in shared code.
 *
 * **The hash format is stored.** Whatever an implementation produces has to keep matching the hashes
 * already saved in every user's preferences, so this is not a place to change algorithm casually.
 */
interface PasswordHasher {

    /** @return the hash to store for [password], in the same format [checkPassword] expects. */
    fun hashPassword(password: String): String

    /** @return true if [password] hashes to [referenceHash]. */
    fun checkPassword(password: String, referenceHash: String): Boolean
}
