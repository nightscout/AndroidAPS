package app.aaps.implementation.maintenance.formats

import app.aaps.core.interfaces.maintenance.PrefMetadata
import app.aaps.core.interfaces.maintenance.PrefMetadataMap
import app.aaps.core.interfaces.maintenance.Prefs
import app.aaps.core.interfaces.maintenance.PrefsMetadataKey
import app.aaps.core.interfaces.maintenance.PrefsStatus
import app.aaps.core.interfaces.protection.SecureEncrypt
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.objects.crypto.CryptoPrimitives
import app.aaps.core.utils.hexStringToByteArray
import app.aaps.implementation.ImplementationStrings
import app.aaps.implementation.maintenance.PrefsMetadataKeyImpl
import app.aaps.implementation.maintenance.data.PrefFormatError
import app.aaps.implementation.maintenance.data.PrefsFormatKey
import app.aaps.implementation.maintenance.data.PrefsStatusImpl
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * The AAPS settings export format, with no file access in it.
 *
 * This is the part that had to stop being Android's. The Android class it came from mixed three
 * jobs - picking a document, writing bytes, and the format itself - and only the third is the same
 * everywhere. An export is a file people carry between devices, so a second implementation for iOS
 * would have been a second format that merely looked alike, and the first file that failed to open
 * would be someone's settings.
 *
 * ## The layout, which is fixed
 *
 * ```
 * { "metadata": {...}, "security": { "salt", "file_hash", "content_hash", "algorithm" },
 *   "format": "aaps_encrypted", "content": "<base64>" }
 * ```
 *
 * `content` is base64 of `[1 byte iv length][iv][ciphertext and tag]`, the key is PBKDF2-HMAC-SHA1
 * over the salt at 50000 rounds, and the cipher is AES-GCM with a 128 bit tag. Those numbers are not
 * choices left to an implementation: they are what every file already written uses. They live in
 * [CryptoPrimitives]'s callers rather than in the crypto, which is told and never assumes.
 *
 * ## Why the pretty printing does not have to match Android's
 *
 * `file_hash` is an HMAC over the file's **own** text, with the hash field itself blanked to a
 * placeholder first. A reader recomputes it the same way from the bytes in front of it, so a writer
 * only has to agree with itself. Two platforms may lay their JSON out differently and still read
 * each other perfectly. This is worth knowing before someone tries to make the output match
 * `org.json` character for character, which is not possible to hold over time and is not needed.
 */
@OptIn(ExperimentalEncodingApi::class)
class PrefsFormatCodec(
    private val crypto: CryptoPrimitives,
    private val textResolver: TextResolver,
    private val secureEncrypt: SecureEncrypt
) {

    /**
     * Renders [prefs] as the text of an export file.
     *
     * Encrypts when a password is given and the metadata does not say encryption is off. If
     * encryption is asked for and cannot be done, the file is written unencrypted and says so in its
     * own `algorithm` field rather than pretending - the same fallback the Android writer has.
     */
    fun encode(prefs: Prefs, masterPassword: String?): String {
        val encStatus = prefs.metadata[PrefsMetadataKeyImpl.ENCRYPTION]?.status ?: PrefsStatusImpl.OK
        var encrypted = encStatus == PrefsStatusImpl.OK && masterPassword != null

        val content = buildJsonObject {
            // Sorted by key, the same order the Android writer used, so the same settings give
            // the same bytes on either platform rather than only the same meaning.
            prefs.values.entries.sortedBy { it.key }.forEach { (key, value) -> put(key, JsonPrimitive(value)) }
        }
        val meta = buildJsonObject {
            prefs.metadata
                .filterKeys { it != PrefsMetadataKeyImpl.FILE_FORMAT && it != PrefsMetadataKeyImpl.ENCRYPTION }
                .forEach { (metaKey, metaEntry) -> put(metaKey.key, JsonPrimitive(metaEntry.value)) }
        }

        var encodedContent = ""
        val security = mutableMapOf<String, String>()
        security[FILE_HASH] = HASH_PLACEHOLDER

        if (encrypted) {
            val rawContent = content.toString()
            val salt = crypto.randomBytes(SALT_BYTES)
            val attempt = encryptContent(plainPassword(masterPassword!!), salt, rawContent)
            if (attempt != null) {
                encodedContent = attempt
                security["algorithm"] = "v1"
                security["salt"] = salt.toHex()
                security["content_hash"] = crypto.sha256(rawContent)
            } else {
                encrypted = false
            }
        }
        if (!encrypted) security["algorithm"] = "none"

        val container = buildJsonObject {
            put("metadata", meta)
            put("security", buildJsonObject { security.forEach { (k, v) -> put(k, JsonPrimitive(v)) } })
            put(PrefsMetadataKeyImpl.FILE_FORMAT.key, JsonPrimitive(PrefsFormatKey.FORMAT_KEY_ENC))
            if (encrypted) put("content", JsonPrimitive(encodedContent)) else put("content", content)
        }

        val body = pretty.encodeToString(JsonObject.serializer(), container)
        // The hash covers the text with its own field blanked, so a reader can redo the same sum.
        return body.replace(HASH_FIELD_REGEX, "$1${crypto.hmac256(body, KEY_CONSCIENCE)}$3")
    }

    /** True when [contents] looks like one of our export files, without trying to open it. */
    fun looksLikePreferences(contents: String): Boolean =
        FORMAT_TEST_REGEX.containsMatchIn(contents) && runCatching { Json.parseToJsonElement(contents).jsonObject }.isSuccess

    /**
     * Reads an export file.
     *
     * Never throws for a wrong password or a changed file - those are ordinary things and come back
     * as a status and a list of issues on the `ENCRYPTION` metadata, so the caller can show the user
     * what is wrong with the file they picked. Only a file that is not this format at all throws.
     */
    @Suppress("LongMethod", "CyclomaticComplexMethod")
    fun decode(contents: String, masterPassword: String?): Prefs {
        val entries = mutableMapOf<String, String>()
        val issues = mutableListOf<String>()

        val blanked = contents.replace(STORED_HASH_REGEX, "$1$HASH_PLACEHOLDER$3")
        val calculatedFileHash = crypto.hmac256(blanked, KEY_CONSCIENCE)

        val container = runCatching { Json.parseToJsonElement(contents).jsonObject }
            .getOrElse { throw PrefFormatError("Malformed preferences JSON file: $it") }
        val metadata = readMetadata(container).toMutableMap()

        if (container.containsKey(PrefsMetadataKeyImpl.FILE_FORMAT.key) && container.containsKey("security") && container.containsKey("content")) {
            val fileFormat = container[PrefsMetadataKeyImpl.FILE_FORMAT.key]?.jsonPrimitive?.content
            val security = container["security"]?.jsonObject ?: JsonObject(emptyMap())
            val encrypted = fileFormat == PrefsFormatKey.FORMAT_KEY_ENC
            var secure: PrefsStatus = PrefsStatusImpl.OK
            var decryptedOk = false
            var contentObject: JsonObject? = null
            var insecurityReason = gs(ImplementationStrings.prefdecrypt_settings_tampered)

            val storedHash = security["file_hash"]?.jsonPrimitive?.content
            if (storedHash == null) {
                secure = PrefsStatusImpl.ERROR
                issues.add(gs(ImplementationStrings.prefdecrypt_issue_missing_file_hash))
            } else if (storedHash != calculatedFileHash) {
                secure = PrefsStatusImpl.ERROR
                issues.add(gs(ImplementationStrings.prefdecrypt_issue_modified))
            }

            if (encrypted) {
                val algorithm = security["algorithm"]?.jsonPrimitive?.content
                val salt = security["salt"]?.jsonPrimitive?.content
                val contentHash = security["content_hash"]?.jsonPrimitive?.content
                when {
                    algorithm != "v1"                 -> {
                        secure = PrefsStatusImpl.ERROR
                        issues.add(gs(ImplementationStrings.prefdecrypt_issue_wrong_algorithm))
                    }

                    salt == null || contentHash == null -> {
                        secure = PrefsStatusImpl.ERROR
                        issues.add(gs(ImplementationStrings.prefdecrypt_issue_wrong_format))
                    }

                    else                              -> {
                        val cipherText = container["content"]?.jsonPrimitive?.content ?: ""
                        // The shared reader, which lower-cases first. A private one here indexed
                        // straight into a lower-case table, so an upper-case salt gave indexOf() = -1
                        // and a garbage key - and the user was told the password was wrong, about a
                        // correct password and an intact file. AAPS writes lower-case, so its own
                        // exports were safe; anything hand-edited or written by another tool was not.
                        val decrypted = masterPassword?.let { decryptContent(it, salt.hexStringToByteArray(), cipherText) }
                        if (decrypted == null) {
                            secure = PrefsStatusImpl.ERROR
                            issues.add(gs(ImplementationStrings.prefdecrypt_issue_wrong_pass))
                            insecurityReason = gs(ImplementationStrings.prefdecrypt_wrong_password)
                        } else if (crypto.sha256(decrypted) != contentHash) {
                            secure = PrefsStatusImpl.ERROR
                            issues.add(gs(ImplementationStrings.prefdecrypt_issue_modified))
                        } else {
                            val parsed = runCatching { Json.parseToJsonElement(decrypted).jsonObject }.getOrNull()
                            if (parsed == null) {
                                secure = PrefsStatusImpl.ERROR
                                issues.add(gs(ImplementationStrings.prefdecrypt_issue_parsing))
                            } else {
                                contentObject = parsed
                                decryptedOk = true
                            }
                        }
                    }
                }
            } else {
                if (secure == PrefsStatusImpl.OK) secure = PrefsStatusImpl.WARN
                if (security["algorithm"]?.jsonPrimitive?.content != "none") {
                    secure = PrefsStatusImpl.ERROR
                    issues.add(gs(ImplementationStrings.prefdecrypt_issue_wrong_algorithm))
                }
                // A file that says it is some other format lands here, and its content is still the
                // base64 string an encrypted file carries rather than the object this path wants.
                // Android reached the same conclusion by letting org.json refuse the cast; saying it
                // outright keeps the error the callers already handle.
                contentObject = container["content"] as? JsonObject
                    ?: throw PrefFormatError("Malformed preferences JSON file: content is not an object")
                decryptedOk = true
            }

            if (decryptedOk && contentObject != null) {
                contentObject.forEach { (key, value) -> entries[key] = value.jsonPrimitive.content }
            }

            val encryptionDescription = if (encrypted) {
                if (secure == PrefsStatusImpl.OK) gs(ImplementationStrings.prefdecrypt_settings_secure) else insecurityReason
            } else {
                if (secure != PrefsStatusImpl.ERROR) gs(ImplementationStrings.prefdecrypt_settings_unencrypted)
                else gs(ImplementationStrings.prefdecrypt_settings_tampered)
            }
            metadata[PrefsMetadataKeyImpl.ENCRYPTION] =
                PrefMetadata(encryptionDescription, secure, issues.takeIf { it.isNotEmpty() }?.joinToString("\n"))
        }

        return Prefs(entries, metadata)
    }

    /** The metadata alone, for listing files without decrypting any of them. */
    fun decodeMetadata(contents: String?): PrefMetadataMap {
        contents ?: return emptyMap()
        return runCatching { readMetadata(Json.parseToJsonElement(contents).jsonObject) }.getOrElse { emptyMap() }
    }

    private fun readMetadata(container: JsonObject): PrefMetadataMap {
        val metadata = mutableMapOf<PrefsMetadataKey, PrefMetadata>()
        val complete = container.containsKey(PrefsMetadataKeyImpl.FILE_FORMAT.key) && container.containsKey("security") &&
            container.containsKey("content") && container.containsKey("metadata")
        if (!complete) {
            metadata[PrefsMetadataKeyImpl.FILE_FORMAT] =
                PrefMetadata(gs(ImplementationStrings.prefdecrypt_wrong_json), PrefsStatusImpl.ERROR)
            return metadata
        }
        val fileFormat = container[PrefsMetadataKeyImpl.FILE_FORMAT.key]?.jsonPrimitive?.content
        if (fileFormat != PrefsFormatKey.FORMAT_KEY_ENC) {
            metadata[PrefsMetadataKeyImpl.FILE_FORMAT] =
                PrefMetadata(gs(ImplementationStrings.metadata_format_other), PrefsStatusImpl.ERROR)
            return metadata
        }
        metadata[PrefsMetadataKeyImpl.FILE_FORMAT] = PrefMetadata(fileFormat, PrefsStatusImpl.OK)
        container["metadata"]?.jsonObject?.forEach { (key, value) ->
            PrefsMetadataKeyImpl.fromKey(key)?.let { metaKey ->
                metadata[metaKey] = PrefMetadata(value.jsonPrimitive.content, PrefsStatusImpl.OK)
            }
        }
        return metadata
    }

    /**
     * The password to encrypt with, which may arrive still wrapped.
     *
     * A master password kept for the user is held encrypted by the platform's keystore or keychain,
     * so what reaches here can be either the password itself or that wrapper. Unwrapping is the same
     * decision on both platforms, so it is made once here rather than in each shell.
     *
     * Only the writing side does this. Importing asks the user for the password directly, so what it
     * gets is already plain - and the Android reader has always treated it that way.
     */
    private fun plainPassword(candidate: String): String =
        if (secureEncrypt.isValidDataString(candidate)) secureEncrypt.decrypt(candidate).ifEmpty { candidate } else candidate

    /** `[1 byte iv length][iv][ciphertext and tag]`, base64. The layout every existing file uses. */
    private fun encryptContent(passphrase: String, salt: ByteArray, raw: String): String? {
        val key = crypto.pbkdf2(passphrase, salt, PBKDF2_ITERATIONS, AES_KEY_BITS)
        val iv = crypto.randomBytes(IV_BYTES)
        val cipherText = crypto.aesGcmEncrypt(key, iv, raw.encodeToByteArray(), TAG_BITS)
        val envelope = ByteArray(1 + iv.size + cipherText.size)
        envelope[0] = iv.size.toByte()
        iv.copyInto(envelope, 1)
        cipherText.copyInto(envelope, 1 + iv.size)
        return Base64.encode(envelope)
    }

    private fun decryptContent(passphrase: String, salt: ByteArray, encoded: String): String? {
        val envelope = runCatching { Base64.decode(encoded) }.getOrNull() ?: return null
        if (envelope.isEmpty()) return null
        val ivLength = envelope[0].toInt()
        if (ivLength <= 0 || envelope.size < 1 + ivLength) return null
        val iv = envelope.copyOfRange(1, 1 + ivLength)
        val cipherText = envelope.copyOfRange(1 + ivLength, envelope.size)
        val key = crypto.pbkdf2(passphrase, salt, PBKDF2_ITERATIONS, AES_KEY_BITS)
        return crypto.aesGcmDecrypt(key, iv, cipherText, TAG_BITS)?.decodeToString()
    }

    private fun gs(ref: TextRef): String = textResolver.gs(ref)

    private fun ByteArray.toHex(): String =
        joinToString("") { b -> HEX[(b.toInt() shr 4) and 0xF].toString() + HEX[b.toInt() and 0xF] }

    private companion object {

        private const val KEY_CONSCIENCE = "if you remove/change this, please make sure you know the consequences!"
        private const val HASH_PLACEHOLDER = "--to-be-calculated--"
        private const val FILE_HASH = "file_hash"
        private const val HEX = "0123456789abcdef"

        private const val PBKDF2_ITERATIONS = 50000
        private const val AES_KEY_BITS = 256
        private const val TAG_BITS = 128
        private const val IV_BYTES = 12
        private const val SALT_BYTES = 32

        private val FORMAT_TEST_REGEX = Regex("(\"format\"\\s*:\\s*\"aaps_[^\"]*\")")
        private val HASH_FIELD_REGEX = Regex("(\"file_hash\"\\s*:\\s*\")($HASH_PLACEHOLDER)(\")")
        private val STORED_HASH_REGEX = Regex("(?is)(\"file_hash\"\\s*:\\s*\")([^\"]*)(\")")

        private val pretty = Json { prettyPrint = true; prettyPrintIndent = "  " }
    }
}
