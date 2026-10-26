package app.aaps.desktop.shell.secure

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.PointerByReference
import java.util.Base64

/**
 * Keys in the Secret Service through libsecret: GNOME Keyring, KWallet, or anything else that offers
 * `org.freedesktop.secrets` on the session bus. Stored in the default (login) collection under schema
 * [SCHEMA_NAME] with one attribute, `alias = <namespace>/<alias>`.
 *
 * libsecret takes the password as a C string, so the key is stored Base64-encoded.
 *
 * ## No keyring is not the same as a locked keyring
 *
 * When nothing offers the Secret Service on the bus, or there is no session bus at all (a minimal
 * desktop, WSL, a session over SSH), there is no keyring to protect anything: [KeyVaultAbsentException],
 * and the desktop falls back to [FileKeyVault] with a warning. When a keyring exists but refuses, for
 * example because it stays locked, it may hold the real keys: [KeyVaultUnavailableException], and the
 * app does not start until it answers. A second key made in a file meanwhile would compete with the
 * real one.
 */
class LinuxSecretServiceKeyVault(private val namespace: String) : KeyVault {

    override val name = "Secret Service (libsecret, default collection)"
    override val isOsBacked = true

    // Loaded here, so that a missing libsecret reaches KeyVaults.pick as a LinkageError from this
    // constructor, which sends it to the file vault.
    private val secret: LibSecret = Native.load("secret-1", LibSecret::class.java)
    private val glib: GLib = Native.load("glib-2.0", GLib::class.java)
    private val schema = SecretSchema.create(SCHEMA_NAME, ATTRIBUTE)

    override fun load(alias: String): ByteArray? {
        val error = PointerByReference()
        val found = secret.secret_password_lookup_sync(schema, null, error, ATTRIBUTE, attribute(alias), null)
        check(error, "read the key for $alias")
        if (found == null) return null
        return try {
            Base64.getDecoder().decode(found.getString(0))
        } finally {
            secret.secret_password_free(found)
        }
    }

    override fun store(alias: String, key: ByteArray) {
        val error = PointerByReference()
        val stored = secret.secret_password_store_sync(schema, null, "AAPS $namespace $alias", Base64.getEncoder().encodeToString(key), null, error, ATTRIBUTE, attribute(alias), null)
        check(error, "store the key for $alias")
        if (!stored) throw KeyVaultUnavailableException("The keyring did not store the key for $alias")
    }

    override fun delete(alias: String): Boolean {
        val error = PointerByReference()
        val removed = secret.secret_password_clear_sync(schema, null, error, ATTRIBUTE, attribute(alias), null)
        check(error, "delete the key for $alias")
        return removed
    }

    private fun attribute(alias: String) = "$namespace/$alias"

    /** Turns a GError into the right exception, and frees it. */
    private fun check(error: PointerByReference, what: String) {
        val gError = error.value ?: return
        try {
            val domain = glib.g_quark_to_string(gError.getInt(0)).orEmpty()
            val code = gError.getInt(4)
            val message = gError.getPointer(8)?.getString(0).orEmpty()
            val text = "Could not $what: $message ($domain $code)"
            val noKeyring = (domain == DBUS_ERROR_DOMAIN && code in NO_SERVICE_CODES) || domain == IO_ERROR_DOMAIN
            throw if (noKeyring) KeyVaultAbsentException(text) else KeyVaultUnavailableException(text)
        } finally {
            glib.g_error_free(gError)
        }
    }

    @Suppress("FunctionName")
    private interface LibSecret : Library {

        fun secret_password_store_sync(
            schema: SecretSchema, collection: String?, label: String, password: String,
            cancellable: Pointer?, error: PointerByReference, vararg attributes: Any?
        ): Boolean

        fun secret_password_lookup_sync(schema: SecretSchema, cancellable: Pointer?, error: PointerByReference, vararg attributes: Any?): Pointer?
        fun secret_password_clear_sync(schema: SecretSchema, cancellable: Pointer?, error: PointerByReference, vararg attributes: Any?): Boolean
        fun secret_password_free(password: Pointer?)
    }

    @Suppress("FunctionName")
    private interface GLib : Library {

        fun g_quark_to_string(quark: Int): String?
        fun g_error_free(error: Pointer)
    }

    /** `SecretSchemaAttribute` from libsecret. */
    @Structure.FieldOrder("name", "type")
    open class SecretSchemaAttribute : Structure() {

        @JvmField var name: String? = null
        @JvmField var type: Int = 0
    }

    /**
     * `SecretSchema` from libsecret: a name, flags, 32 attribute slots ended by an empty one, and
     * reserved fields that must be there for the size to be right.
     */
    @Structure.FieldOrder("name", "flags", "attributes", "reserved", "reserved1", "reserved2", "reserved3", "reserved4", "reserved5", "reserved6", "reserved7")
    class SecretSchema : Structure() {

        @JvmField var name: String? = null
        @JvmField var flags: Int = SECRET_SCHEMA_NONE

        // Created whole here: JNA works out the size of the struct from this array, so it must have all
        // 32 slots before the struct is first used. The unused slots stay empty, which ends the list.
        @JvmField var attributes: Array<SecretSchemaAttribute> = Array(ATTRIBUTE_SLOTS) { SecretSchemaAttribute() }
        @JvmField var reserved: Int = 0
        @JvmField var reserved1: Pointer? = null
        @JvmField var reserved2: Pointer? = null
        @JvmField var reserved3: Pointer? = null
        @JvmField var reserved4: Pointer? = null
        @JvmField var reserved5: Pointer? = null
        @JvmField var reserved6: Pointer? = null
        @JvmField var reserved7: Pointer? = null

        companion object {

            fun create(schemaName: String, attributeName: String): SecretSchema = SecretSchema().apply {
                name = schemaName
                attributes[0].name = attributeName
                attributes[0].type = SECRET_SCHEMA_ATTRIBUTE_STRING
                write()
            }
        }
    }

    companion object {

        const val SCHEMA_NAME = "app.aaps.desktop"
        private const val ATTRIBUTE = "alias"
        private const val ATTRIBUTE_SLOTS = 32
        private const val SECRET_SCHEMA_NONE = 0
        private const val SECRET_SCHEMA_ATTRIBUTE_STRING = 0

        private const val DBUS_ERROR_DOMAIN = "g-dbus-error-quark"
        private const val IO_ERROR_DOMAIN = "g-io-error-quark"

        /** `G_DBUS_ERROR_SERVICE_UNKNOWN` and `G_DBUS_ERROR_NAME_HAS_NO_OWNER`: nobody offers the Secret Service. */
        private val NO_SERVICE_CODES = setOf(2, 3)
    }
}

/** The OS has the API, but no key store is running for this user. There is nothing to protect keys with. */
class KeyVaultAbsentException(message: String) : Exception(message)
