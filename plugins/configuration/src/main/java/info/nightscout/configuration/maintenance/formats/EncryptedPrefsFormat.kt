package info.nightscout.configuration.maintenance.formats

import info.nightscout.core.main.R
import info.nightscout.core.utils.CryptoUtil
import info.nightscout.core.utils.hexStringToByteArray
import info.nightscout.core.utils.toHex
import info.nightscout.interfaces.maintenance.PrefFileNotFoundError
import info.nightscout.interfaces.maintenance.PrefFormatError
import info.nightscout.interfaces.maintenance.PrefIOError
import info.nightscout.interfaces.maintenance.PrefMetadata
import info.nightscout.interfaces.maintenance.PrefMetadataMap
import info.nightscout.interfaces.maintenance.Prefs
import info.nightscout.interfaces.maintenance.PrefsFormat
import info.nightscout.interfaces.maintenance.PrefsMetadataKey
import info.nightscout.interfaces.maintenance.PrefsStatus
import info.nightscout.interfaces.storage.Storage
import info.nightscout.shared.interfaces.ResourceHelper
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.LinkedList
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EncryptedPrefsFormat @Inject constructor(
    private var rh: ResourceHelper,
    private var cryptoUtil: CryptoUtil,
    private var storage: Storage
) : PrefsFormat {

    companion object {

        private const val KEY_CONSCIENCE = "if you remove/change this, please make sure you know the consequences!"
        private val FORMAT_TEST_REGEX = Regex("(\"format\"\\s*:\\s*\"aaps_[^\"]*\")")
    }

    override fun isPreferencesFile(file: File, preloadedContents: String?): Boolean {
        return if (file.absolutePath.endsWith(".json")) {
            val contents = preloadedContents ?: storage.getFileContents(file)
            FORMAT_TEST_REGEX.containsMatchIn(contents)
            try {
                // test valid JSON object
                JSONObject(contents)
                true
            } catch (e: Exception) {
                false
            }
        } else {
            false
        }
    }

    override fun savePreferences(file: File, prefs: Prefs, masterPassword: String?) {

        val container = JSONObject()
        val content = JSONObject()
        val meta = JSONObject()

        val encStatus = prefs.metadata[PrefsMetadataKey.ENCRYPTION]?.status ?: PrefsStatus.OK
        var encrypted = encStatus == PrefsStatus.OK && masterPassword != null

        try {
            for ((key, value) in prefs.values.toSortedMap()) {
                content.put(key, value)
            }

            for ((metaKey, metaEntry) in prefs.metadata) {
                if (metaKey == PrefsMetadataKey.FILE_FORMAT)
                    continue
                if (metaKey == PrefsMetadataKey.ENCRYPTION)
                    continue
                meta.put(metaKey.key, metaEntry.value)
            }

            container.put(PrefsMetadataKey.FILE_FORMAT.key, if (encrypted) PrefsFormat.FORMAT_KEY_ENC else PrefsFormat.FORMAT_KEY_NOENC)
            container.put("metadata", meta)

            val security = JSONObject()
            security.put("file_hash", "--to-be-calculated--")
            var encodedContent = ""

            if (encrypted) {
                val salt = cryptoUtil.mineSalt()
                val rawContent = content.toString()
                val contentAttempt = cryptoUtil.encrypt(masterPassword!!, salt, rawContent)
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

            storage.putFileContents(file, fileContents)

        } catch (e: FileNotFoundException) {
            throw PrefFileNotFoundError(file.absolutePath)
        } catch (e: IOException) {
            throw PrefIOError(file.absolutePath)
        }
    }

    override fun loadPreferences(file: File, masterPassword: String?): Prefs {

        val entries: MutableMap<String, String> = mutableMapOf()
        val issues = LinkedList<String>()
        try {

            val jsonBody = storage.getFileContents(file)
            val fileContents = jsonBody.replace(Regex("(?is)(\"file_hash\"\\s*:\\s*\")([^\"]*)(\")"), "$1--to-be-calculated--$3")
            val calculatedFileHash = cryptoUtil.hmac256(fileContents, KEY_CONSCIENCE)
            val container = JSONObject(jsonBody)
            val metadata: MutableMap<PrefsMetadataKey, PrefMetadata> = loadMetadata(container)

            if (container.has(PrefsMetadataKey.FILE_FORMAT.key) && container.has("security") && container.has("content")) {
                val fileFormat = container.getString(PrefsMetadataKey.FILE_FORMAT.key)
                val security = container.getJSONObject("security")
                val encrypted = fileFormat == PrefsFormat.FORMAT_KEY_ENC
                var secure: PrefsStatus = PrefsStatus.OK
                var decryptedOk = false
                var contentJsonObj: JSONObject? = null
                var insecurityReason = rh.gs(R.string.prefdecrypt_settings_tampered)

                if (security.has("file_hash")) {
                    if (calculatedFileHash != security.getString("file_hash")) {
                        secure = PrefsStatus.ERROR
                        issues.add(rh.gs(R.string.prefdecrypt_issue_modified))
                    }
                } else {
                    secure = PrefsStatus.ERROR
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
                                        secure = PrefsStatus.ERROR
                                        issues.add(rh.gs(R.string.prefdecrypt_issue_modified))
                                    }

                                } catch (e: JSONException) {
                                    secure = PrefsStatus.ERROR
                                    issues.add(rh.gs(R.string.prefdecrypt_issue_parsing))
                                }

                            } else {
                                secure = PrefsStatus.ERROR
                                issues.add(rh.gs(R.string.prefdecrypt_issue_wrong_pass))
                                insecurityReason = rh.gs(R.string.prefdecrypt_wrong_password)
                            }

                        } else {
                            secure = PrefsStatus.ERROR
                            issues.add(rh.gs(R.string.prefdecrypt_issue_wrong_format))
                        }
                    } else {
                        secure = PrefsStatus.ERROR
                        issues.add(rh.gs(R.string.prefdecrypt_issue_wrong_algorithm))
                    }

                } else {

                    if (secure == PrefsStatus.OK) {
                        secure = PrefsStatus.WARN
                    }

                    if (!(security.has("algorithm") && security.get("algorithm") == "none")) {
                        secure = PrefsStatus.ERROR
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

                val issuesStr: String? = if (issues.size > 0) issues.joinToString("\n") else null
                val encryptionDescStr = if (encrypted) {
                    if (secure == PrefsStatus.OK) rh.gs(R.string.prefdecrypt_settings_secure) else insecurityReason
                } else {
                    if (secure != PrefsStatus.ERROR) rh.gs(R.string.prefdecrypt_settings_unencrypted) else rh.gs(R.string.prefdecrypt_settings_tampered)
                }

                metadata[PrefsMetadataKey.ENCRYPTION] = PrefMetadata(encryptionDescStr, secure, issuesStr)
            }

            return Prefs(entries, metadata)

        } catch (e: FileNotFoundException) {
            throw PrefFileNotFoundError(file.absolutePath)
        } catch (e: IOException) {
            throw PrefIOError(file.absolutePath)
        } catch (e: JSONException) {
            throw PrefFormatError("Malformed preferences JSON file: $e")
        }
    }

    override fun loadMetadata(contents: String?): PrefMetadataMap {
        contents?.let {
            return try {
                loadMetadata(JSONObject(contents))
            } catch (e: Exception) {
                mutableMapOf()
            }
        }
        return mutableMapOf()
    }

    private fun loadMetadata(container: JSONObject): MutableMap<PrefsMetadataKey, PrefMetadata> {
        val metadata: MutableMap<PrefsMetadataKey, PrefMetadata> = mutableMapOf()
        if (container.has(PrefsMetadataKey.FILE_FORMAT.key) && container.has("security") && container.has("content") && container.has("metadata")) {
            val fileFormat = container.getString(PrefsMetadataKey.FILE_FORMAT.key)
            if ((fileFormat != PrefsFormat.FORMAT_KEY_ENC) && (fileFormat != PrefsFormat.FORMAT_KEY_NOENC)) {
                metadata[PrefsMetadataKey.FILE_FORMAT] = PrefMetadata(rh.gs(R.string.metadata_format_other), PrefsStatus.ERROR)
            } else {
                val meta = container.getJSONObject("metadata")
                metadata[PrefsMetadataKey.FILE_FORMAT] = PrefMetadata(fileFormat, PrefsStatus.OK)
                for (key in meta.keys()) {
                    val metaKey = PrefsMetadataKey.fromKey(key)
                    if (metaKey != null) {
                        metadata[metaKey] = PrefMetadata(meta.getString(key), PrefsStatus.OK)
                    }
                }
            }
        } else {
            metadata[PrefsMetadataKey.FILE_FORMAT] = PrefMetadata(rh.gs(R.string.prefdecrypt_wrong_json), PrefsStatus.ERROR)
        }

        return metadata
    }

}