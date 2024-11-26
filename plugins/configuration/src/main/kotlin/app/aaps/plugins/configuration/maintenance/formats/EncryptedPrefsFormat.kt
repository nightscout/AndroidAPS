package app.aaps.plugins.configuration.maintenance.formats

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import app.aaps.core.interfaces.maintenance.PrefMetadata
import app.aaps.core.interfaces.maintenance.PrefsMetadataKey
import app.aaps.core.interfaces.maintenance.PrefsStatus
import app.aaps.core.interfaces.protection.SecureEncrypt
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.storage.Storage
import app.aaps.core.objects.crypto.CryptoUtil
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.core.utils.hexStringToByteArray
import app.aaps.core.utils.toHex
import app.aaps.plugins.configuration.R
import app.aaps.plugins.configuration.maintenance.PrefsMetadataKeyImpl
import app.aaps.plugins.configuration.maintenance.data.PrefFileNotFoundError
import app.aaps.plugins.configuration.maintenance.data.PrefFormatError
import app.aaps.plugins.configuration.maintenance.data.PrefIOError
import app.aaps.plugins.configuration.maintenance.data.PrefMetadataMap
import app.aaps.plugins.configuration.maintenance.data.Prefs
import app.aaps.plugins.configuration.maintenance.data.PrefsFormat
import app.aaps.plugins.configuration.maintenance.data.PrefsStatusImpl
import org.json.JSONException
import org.json.JSONObject
import java.io.FileNotFoundException
import java.io.IOException
import java.util.LinkedList
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EncryptedPrefsFormat @Inject constructor(
    private val rh: ResourceHelper,
    private val cryptoUtil: CryptoUtil,
    private val storage: Storage,
    private val context: Context
) : PrefsFormat {

    @Inject lateinit var secureEncrypt: SecureEncrypt

    companion object {

        private const val KEY_CONSCIENCE = "if you remove/change this, please make sure you know the consequences!"
        private val FORMAT_TEST_REGEX = Regex("(\"format\"\\s*:\\s*\"aaps_[^\"]*\")")
    }

    override fun isPreferencesFile(file: DocumentFile, preloadedContents: String?): Boolean {
        return if (file.name?.endsWith(".json") == true) {
            try {
                val contents = preloadedContents ?: storage.getFileContents(context.contentResolver, file)

                FORMAT_TEST_REGEX.containsMatchIn(contents)
                try {
                    // test valid JSON object
                    JSONObject(contents)
                    true
                } catch (_: Exception) {
                    false
                }
            } catch (_: SecurityException) {
                ToastUtils.errorToast(context, rh.gs(R.string.error_accessing_filesystem_select_aaps_directory_properly))
                false
            }
        } else false
    }

    override fun savePreferences(file: DocumentFile, prefs: Prefs, masterPassword: String?) {

        val container = JSONObject()
        val content = JSONObject()
        val meta = JSONObject()

        val encStatus = prefs.metadata[PrefsMetadataKeyImpl.ENCRYPTION]?.status ?: PrefsStatusImpl.OK
        var encrypted = encStatus == PrefsStatusImpl.OK && masterPassword != null

        try {
            for ((key, value) in prefs.values.toSortedMap()) {
                content.put(key, value)
            }

            for ((metaKey, metaEntry) in prefs.metadata) {
                if (metaKey == PrefsMetadataKeyImpl.FILE_FORMAT)
                    continue
                if (metaKey == PrefsMetadataKeyImpl.ENCRYPTION)
                    continue
                meta.put(metaKey.key, metaEntry.value)
            }

            container.put(PrefsMetadataKeyImpl.FILE_FORMAT.key, PrefsFormat.FORMAT_KEY_ENC)
            container.put("metadata", meta)

            val security = JSONObject()
            security.put("file_hash", "--to-be-calculated--")
            var encodedContent = ""

            if (encrypted) {
                val salt = cryptoUtil.mineSalt()
                val rawContent = content.toString()

                var masterPasswordUnencrypted = masterPassword
                if (secureEncrypt.isValidDataString(masterPassword)) {
                    // Password contains valid data string so assuming this is a valid encrypted password
                    val decryptionResult = secureEncrypt.decrypt(masterPassword!!)
                    if (decryptionResult.isNotEmpty()) {
                        // Password could be decrypted
                        masterPasswordUnencrypted = decryptionResult
                    }
                }

                val contentAttempt = cryptoUtil.encrypt(masterPasswordUnencrypted!!, salt, rawContent)

                if (contentAttempt != null) {
                    encodedContent = contentAttempt
                    security.put("algorithm", "v1")
                    security.put("salt", salt.toHex())
                    security.put("content_hash", cryptoUtil.sha256(rawContent))
                } else {
                    // fallback when encryption does not work
                    encrypted = false
                }
            }

            if (!encrypted) {
                security.put("algorithm", "none")
            }

            container.put("security", security)
            container.put("content", if (encrypted) encodedContent else content)

            var fileContents = container.toString(2)
            val fileHash = cryptoUtil.hmac256(fileContents, KEY_CONSCIENCE)

            fileContents = fileContents.replace(Regex("(\"file_hash\"\\s*:\\s*\")(--to-be-calculated--)(\")"), "$1$fileHash$3")

            storage.putFileContents(context.contentResolver, file, fileContents)

        } catch (_: FileNotFoundException) {
            throw PrefFileNotFoundError(file.name ?: "UNKNOWN")
        } catch (_: IOException) {
            throw PrefIOError(file.name ?: "UNKNOWN")
        } catch (_: SecurityException) {
            ToastUtils.errorToast(context, rh.gs(R.string.error_accessing_filesystem_select_aaps_directory_properly))
            throw PrefFileNotFoundError(file.name ?: "UNKNOWN")
        }
    }

    override fun loadPreferences(contents: String, masterPassword: String?): Prefs {

        val entries: MutableMap<String, String> = mutableMapOf()
        val issues = LinkedList<String>()
        try {

            val jsonBody = contents
            val fileContents = jsonBody.replace(Regex("(?is)(\"file_hash\"\\s*:\\s*\")([^\"]*)(\")"), "$1--to-be-calculated--$3")
            val calculatedFileHash = cryptoUtil.hmac256(fileContents, KEY_CONSCIENCE)
            val container = JSONObject(jsonBody)
            val metadata: MutableMap<PrefsMetadataKey, PrefMetadata> = loadMetadata(container)

            if (container.has(PrefsMetadataKeyImpl.FILE_FORMAT.key) && container.has("security") && container.has("content")) {
                val fileFormat = container.getString(PrefsMetadataKeyImpl.FILE_FORMAT.key)
                val security = container.getJSONObject("security")
                val encrypted = fileFormat == PrefsFormat.FORMAT_KEY_ENC
                var secure: PrefsStatus = PrefsStatusImpl.OK
                var decryptedOk = false
                var contentJsonObj: JSONObject? = null
                var insecurityReason = rh.gs(R.string.prefdecrypt_settings_tampered)

                if (security.has("file_hash")) {
                    if (calculatedFileHash != security.getString("file_hash")) {
                        secure = PrefsStatusImpl.ERROR
                        issues.add(rh.gs(R.string.prefdecrypt_issue_modified))
                    }
                } else {
                    secure = PrefsStatusImpl.ERROR
                    issues.add(rh.gs(R.string.prefdecrypt_issue_missing_file_hash))
                }

                if (encrypted) {
                    if (security.has("algorithm") && security.get("algorithm") == "v1") {
                        if (security.has("salt") && security.has("content_hash")) {

                            val salt = security.getString("salt").hexStringToByteArray()
                            val decrypted = cryptoUtil.decrypt(masterPassword!!, salt, container.getString("content"))

                            if (decrypted != null) {
                                try {
                                    val contentHash = cryptoUtil.sha256(decrypted)

                                    if (contentHash == security.getString("content_hash")) {
                                        contentJsonObj = JSONObject(decrypted)
                                        decryptedOk = true
                                    } else {
                                        secure = PrefsStatusImpl.ERROR
                                        issues.add(rh.gs(R.string.prefdecrypt_issue_modified))
                                    }

                                } catch (_: JSONException) {
                                    secure = PrefsStatusImpl.ERROR
                                    issues.add(rh.gs(R.string.prefdecrypt_issue_parsing))
                                }

                            } else {
                                secure = PrefsStatusImpl.ERROR
                                issues.add(rh.gs(R.string.prefdecrypt_issue_wrong_pass))
                                insecurityReason = rh.gs(R.string.prefdecrypt_wrong_password)
                            }

                        } else {
                            secure = PrefsStatusImpl.ERROR
                            issues.add(rh.gs(R.string.prefdecrypt_issue_wrong_format))
                        }
                    } else {
                        secure = PrefsStatusImpl.ERROR
                        issues.add(rh.gs(R.string.prefdecrypt_issue_wrong_algorithm))
                    }

                } else {

                    if (secure == PrefsStatusImpl.OK) {
                        secure = PrefsStatusImpl.WARN
                    }

                    if (!(security.has("algorithm") && security.get("algorithm") == "none")) {
                        secure = PrefsStatusImpl.ERROR
                        issues.add(rh.gs(R.string.prefdecrypt_issue_wrong_algorithm))
                    }

                    contentJsonObj = container.getJSONObject("content")
                    decryptedOk = true
                }

                if (decryptedOk && contentJsonObj != null) {
                    for (key in contentJsonObj.keys()) {
                        entries[key] = contentJsonObj[key].toString()
                    }
                }

                val issuesStr: String? = if (issues.isNotEmpty()) issues.joinToString("\n") else null
                val encryptionDescStr = if (encrypted) {
                    if (secure == PrefsStatusImpl.OK) rh.gs(R.string.prefdecrypt_settings_secure) else insecurityReason
                } else {
                    if (secure != PrefsStatusImpl.ERROR) rh.gs(R.string.prefdecrypt_settings_unencrypted) else rh.gs(R.string.prefdecrypt_settings_tampered)
                }

                metadata[PrefsMetadataKeyImpl.ENCRYPTION] = PrefMetadata(encryptionDescStr, secure, issuesStr)
            }

            return Prefs(entries, metadata)

        } catch (_: FileNotFoundException) {
            throw PrefFileNotFoundError("")
        } catch (_: IOException) {
            throw PrefIOError("")
        } catch (e: JSONException) {
            throw PrefFormatError("Malformed preferences JSON file: $e")
        }
    }

    override fun loadMetadata(contents: String?): PrefMetadataMap {
        contents?.let {
            return try {
                loadMetadata(JSONObject(contents))
            } catch (_: Exception) {
                mutableMapOf()
            }
        }
        return mutableMapOf()
    }

    private fun loadMetadata(container: JSONObject): MutableMap<PrefsMetadataKey, PrefMetadata> {
        val metadata: MutableMap<PrefsMetadataKey, PrefMetadata> = mutableMapOf()
        if (container.has(PrefsMetadataKeyImpl.FILE_FORMAT.key) && container.has("security") && container.has("content") && container.has("metadata")) {
            val fileFormat = container.getString(PrefsMetadataKeyImpl.FILE_FORMAT.key)
            if ((fileFormat != PrefsFormat.FORMAT_KEY_ENC)) {
                metadata[PrefsMetadataKeyImpl.FILE_FORMAT] = PrefMetadata(rh.gs(R.string.metadata_format_other), PrefsStatusImpl.ERROR)
            } else {
                val meta = container.getJSONObject("metadata")
                metadata[PrefsMetadataKeyImpl.FILE_FORMAT] = PrefMetadata(fileFormat, PrefsStatusImpl.OK)
                for (key in meta.keys()) {
                    val metaKey = PrefsMetadataKeyImpl.fromKey(key)
                    if (metaKey != null) {
                        metadata[metaKey] = PrefMetadata(meta.getString(key), PrefsStatusImpl.OK)
                    }
                }
            }
        } else {
            metadata[PrefsMetadataKeyImpl.FILE_FORMAT] = PrefMetadata(rh.gs(R.string.prefdecrypt_wrong_json), PrefsStatusImpl.ERROR)
        }

        return metadata
    }

}