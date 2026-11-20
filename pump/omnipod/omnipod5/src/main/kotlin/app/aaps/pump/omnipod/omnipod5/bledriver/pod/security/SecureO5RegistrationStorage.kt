package app.aaps.pump.omnipod.omnipod5.bledriver.pod.security

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.omnipod.omnipod5.bledriver.comm.pair.O5RegistrationData
import app.aaps.pump.omnipod.omnipod5.keys.O5StringNonPreferenceKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Persists O5 registration (credential) data across app restarts, encrypted at rest via
 * [AndroidKeystoreAesCipher] - the persistence-layer counterpart to
 * [app.aaps.pump.omnipod.omnipod5.bledriver.comm.pair.O5RegistrationData]'s in-memory
 * registry, mirroring OmnipodKit's O5CertificateKeychain.swift.
 *
 * Call [loadAndInstallAll] once at app startup (before any O5 pairing/connection code
 * runs) to restore previously-imported credentials into the in-memory registry. Call
 * [persistEntry] whenever a new credential is imported (e.g. via a future settings screen
 * accepting a packed string or keypair file) so it survives the next cold start.
 *
 * Note on scope: only [O5RegistrationData.O5RegistrationSource.IMPORTED] entries are
 * persisted here - BUILT_IN entries are, by definition, re-derived from the compiled
 * ServiceLoader-discovered module on every app start, and DOWNLOADED entries aren't a
 * pattern this codebase actually implements yet (see O5RegistrationData's class doc).
 */
@SingleIn(AppScope::class)
class SecureO5RegistrationStorage @Inject constructor(
    private val logger: AAPSLogger,
    private val preferences: Preferences,
    private val cipher: O5RegistrationCipher
) {

    private val gson = Gson()

    /** JSON-serializable shape for one persisted entry (Gson can't serialize the real
     *  [O5RegistrationData.O5RegistrationSource] enum's name reliably across app updates
     *  if it's ever reordered, so persist the name as a plain string defensively). */
    private data class PersistedEntry(
        val controllerId: Long,
        val privateKeyHex: String,
        val publicKeyHex: String,
        val intermediateCABase64: String,
        val tlsCertificateBase64: String,
        val sourceName: String
    )

    /**
     * Loads any previously-persisted, imported registration entries and installs them into
     * [O5RegistrationData]'s in-memory registry. Safe to call even if nothing has ever been
     * persisted (no-ops). Logs and skips (rather than throwing) if decryption fails - e.g.
     * after a device restore where the Keystore key itself didn't survive, which is
     * expected Android Keystore behavior, not a bug.
     */
    fun loadAndInstallAll() {
        val encrypted = preferences.getIfExists(O5StringNonPreferenceKey.RegistrationData)
        if (encrypted.isNullOrEmpty()) return

        try {
            val json = String(cipher.decrypt(encrypted), Charsets.UTF_8)
            val entries = parseEntries(json)
            for (entry in entries) {
                val data = O5RegistrationData(
                    controllerId = entry.controllerId,
                    privateKeyHex = entry.privateKeyHex,
                    publicKeyHex = entry.publicKeyHex,
                    intermediateCABase64 = entry.intermediateCABase64,
                    tlsCertificateBase64 = entry.tlsCertificateBase64
                )
                val source = try {
                    O5RegistrationData.O5RegistrationSource.valueOf(entry.sourceName)
                } catch (_: IllegalArgumentException) {
                    O5RegistrationData.O5RegistrationSource.IMPORTED
                }
                O5RegistrationData.install(data, source)
            }
            logger.debug(LTag.PUMPCOMM, "Restored ${entries.size} persisted O5 registration entr${if (entries.size == 1) "y" else "ies"}")
        } catch (ex: Exception) {
            logger.error(LTag.PUMPCOMM, "Failed to load/decrypt persisted O5 registration data", ex)
        }
    }

    /**
     * Persists [data] (with the given [source]) so it survives app restarts, alongside any
     * other already-persisted entries. Re-encrypts and re-saves the entire entry set - simple
     * and safe for the very small number of entries (realistically 1) this ever holds.
     */
    fun persistEntry(data: O5RegistrationData, source: O5RegistrationData.O5RegistrationSource) {
        try {
            val current = loadPersistedEntriesOnly().filterNot { it.controllerId == data.controllerId }
            val updated = current + PersistedEntry(
                controllerId = data.controllerId,
                privateKeyHex = data.privateKeyHex,
                publicKeyHex = data.publicKeyHex,
                intermediateCABase64 = data.intermediateCABase64,
                tlsCertificateBase64 = data.tlsCertificateBase64,
                sourceName = source.name
            )
            saveEntries(updated)
            logger.debug(LTag.PUMPCOMM, "Persisted O5 registration entry for controller 0x%08X".format(data.controllerId))
        } catch (ex: Exception) {
            logger.error(LTag.PUMPCOMM, "Failed to encrypt/persist O5 registration data", ex)
        }
    }

    /** Removes a persisted entry (e.g. when the user deletes a saved credential). Does not
     *  affect the in-memory registry - call [O5RegistrationData.remove] separately if the
     *  entry should also stop being usable immediately, not just on next restart. */
    fun removeEntry(controllerId: Long) {
        try {
            val current = loadPersistedEntriesOnly().filterNot { it.controllerId == controllerId }
            saveEntries(current)
        } catch (ex: Exception) {
            logger.error(LTag.PUMPCOMM, "Failed to remove persisted O5 registration entry", ex)
        }
    }

    private fun loadPersistedEntriesOnly(): List<PersistedEntry> {
        val encrypted = preferences.getIfExists(O5StringNonPreferenceKey.RegistrationData)
        if (encrypted.isNullOrEmpty()) return emptyList()
        return try {
            parseEntries(String(cipher.decrypt(encrypted), Charsets.UTF_8))
        } catch (ex: Exception) {
            logger.error(LTag.PUMPCOMM, "Failed to decrypt existing O5 registration data before update", ex)
            emptyList()
        }
    }

    private fun saveEntries(entries: List<PersistedEntry>) {
        val json = gson.toJson(entries)
        val encrypted = cipher.encrypt(json.toByteArray(Charsets.UTF_8))
        preferences.put(O5StringNonPreferenceKey.RegistrationData, encrypted)
    }

    private fun parseEntries(json: String): List<PersistedEntry> {
        val type = object : TypeToken<List<PersistedEntry>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }
}
