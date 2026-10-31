package app.aaps.plugins.sync.nsclientV3.clientcontrol

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.keys.LongNonKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.nssdk.localmodel.configuration.NSRunningConfiguration
import app.aaps.core.nssdk.utils.MasterDocSigning
import app.aaps.plugins.sync.nsclientV3.SettingsIdentifiers
import app.aaps.plugins.sync.nsclientV3.extensions.toRunningConfiguration
import app.aaps.plugins.sync.nsclientV3.nsClientBaseUrl
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Client side: opens the master's settings docs (cold and hot) and refuses the ones the master did not sign.
 *
 * A doc is `{ …, schemaVersion: 2, signed: "<text>", signature: "<base64>" }`, where the text is the JSON
 * `{ signedAt, runningConfig }` (`MasterDocSigning`). With the master's key known for this Nightscout, a doc is taken
 * only when its signature fits and it is not older than the last one taken (an older one is a replay: a genuine doc put
 * back by somebody else). Without a key (a phone set up by hand that only reads Nightscout, or the moments before the
 * first pong) a doc is taken as before.
 */
@SingleIn(AppScope::class)
@Inject
class MasterDocVerifier(
    private val preferences: Preferences,
    private val nsClientRepository: NSClientRepository,
    private val aapsLogger: AAPSLogger
) {

    /** The configuration in a doc. [signedAt] is the master's time of signing; null for a doc taken without a key. */
    class Opened(val configuration: NSRunningConfiguration, val signedAt: Long?)

    /** The configuration in the doc [identifier], or null when this client must not use it (already logged). */
    fun open(identifier: String, doc: JsonObject): Opened? {
        val signedText = (doc[SIGNED] as? JsonPrimitive)?.contentOrNull
        val key = keyForThisNightscout()
        if (key == null) {
            // No key to check with: as before. An old doc (runningConfig as an object) is taken too.
            return signedText?.let { opened(it) }?.let { Opened(it.configuration, null) }
                ?: doc.toString().toRunningConfiguration()?.let { Opened(it, null) }
        }
        val signature = (doc[SIGNATURE] as? JsonPrimitive)?.contentOrNull
        if (signedText == null || signature == null) return refuse(identifier, "not signed by the master")
        if (!MasterDocSigning.verify(key, identifier, signedText, signature)) return refuse(identifier, "signature is not the master's")
        val opened = opened(signedText) ?: return refuse(identifier, "signed, but not readable")
        val mark = markFor(identifier) ?: return opened
        val signedAt = opened.signedAt ?: return refuse(identifier, "no signing time")
        // The same doc again (the catch-up load after the websocket push) is fine; an older one is not.
        if (signedAt < preferences.get(mark)) return refuse(identifier, "older than the last one, a replay")
        preferences.put(mark, signedAt)
        return opened
    }

    /**
     * Keeps the master's public key for its settings docs, from a pong or the master's QR code, for the Nightscout this
     * phone uses now. A new key starts the replay order again: it is a new master, with its own clock.
     */
    fun keepKey(key: String) {
        val url = nsClientBaseUrl(preferences.get(StringKey.NsClientUrl))
        if (key == preferences.get(StringNonKey.NsClientControlMasterSigningKey) && url == preferences.get(StringNonKey.NsClientControlMasterSigningKeyUrl)) return
        preferences.put(StringNonKey.NsClientControlMasterSigningKey, key)
        preferences.put(StringNonKey.NsClientControlMasterSigningKeyUrl, url)
        preferences.put(LongNonKey.NsClientControlColdSignedAt, 0L)
        preferences.put(LongNonKey.NsClientControlHotSignedAt, 0L)
        aapsLogger.debug(LTag.NSCLIENT, "ClientControl: the master's key for its settings docs is kept")
    }

    /** The key, if it was given for the Nightscout this phone uses now. Another address means another master. */
    private fun keyForThisNightscout(): String? {
        val key = preferences.get(StringNonKey.NsClientControlMasterSigningKey)
        if (key.isEmpty()) return null
        val url = nsClientBaseUrl(preferences.get(StringKey.NsClientUrl))
        return key.takeIf { url == preferences.get(StringNonKey.NsClientControlMasterSigningKeyUrl) }
    }

    private fun opened(signedText: String): Opened? {
        val signed = runCatching { lenientJson.parseToJsonElement(signedText) as? JsonObject }.getOrNull() ?: return null
        val signedAt = (signed[SIGNED_AT] as? JsonPrimitive)?.longOrNull
        val configuration = signed.toString().toRunningConfiguration() ?: return null
        return Opened(configuration, signedAt)
    }

    private fun markFor(identifier: String): LongNonKey? = when (identifier) {
        SettingsIdentifiers.COLD  -> LongNonKey.NsClientControlColdSignedAt
        SettingsIdentifiers.STATE -> LongNonKey.NsClientControlHotSignedAt
        else                      -> null
    }

    private fun refuse(identifier: String, why: String): Opened? {
        aapsLogger.warn(LTag.NSCLIENT, "ClientControl: settings doc $identifier refused: $why")
        nsClientRepository.addLog("✕ SETTINGS", "$identifier refused: $why")
        return null
    }

    companion object {

        /** The doc fields. Written by `RunningConfigurationPublisher`, which uses the same names. */
        const val SIGNED = "signed"
        const val SIGNATURE = "signature"
        const val SIGNED_AT = "signedAt"
        const val RUNNING_CONFIG = "runningConfig"

        private val lenientJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
}
