package app.aaps.plugins.sync.nsclientV3.clientcontrol

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.nssdk.exceptions.NotAllowedNightscoutException
import app.aaps.core.nssdk.interfaces.NSAndroidClient
import app.aaps.core.nssdk.localmodel.authorization.NSAuthRole
import app.aaps.core.nssdk.localmodel.authorization.NSAuthSubject
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException

/**
 * The Nightscout tokens of the clients, made and removed by the master.
 *
 * Each client gets its own token with the role [ROLE_NAME]. It can read everything but write only to the settings
 * collection, and that is all a client needs: it uploads nothing and sends everything to the master there. Every time
 * the master uses the role it sets it back to [ROLE_PERMISSIONS] ([ensureRole]), so a change made in the Nightscout
 * admin tools does not give the clients more rights for long.
 *
 * A client token is a subject named [SUBJECT_PREFIX] + the client's name, with this one role and no other. The master
 * never changes or deletes a subject with other roles: those are the user's own tokens.
 *
 * Every call needs the master's own token to be an admin token.
 */
@SingleIn(AppScope::class)
@Inject
class ClientTokens(
    private val nsClientV3Plugin: () -> NSClientV3Plugin,
    private val aapsLogger: AAPSLogger
) {

    companion object {

        /** Lowercase on purpose: the Nightscout admin tools page lowercases role names when it saves a subject. */
        const val ROLE_NAME = "aaps-client"

        /**
         * Read everything, and write to the settings collection only. `api:settings:admin` is not more rights: Nightscout
         * asks for it to list the settings collection and to follow it over the websocket. The last one lets a client
         * snooze a Nightscout alarm.
         */
        val ROLE_PERMISSIONS = listOf("*:*:read", "api:settings:admin", "api:settings:create", "api:settings:update", "notifications:*:ack")
        const val ROLE_NOTES = "Made by AAPS for its client phones. AAPS sets it back if it is changed."
        const val SUBJECT_PREFIX = "aaps-"
        const val SUBJECT_NOTES = "Made by AAPS for a client phone."
    }

    sealed interface Result<out T> {

        data class Ok<T>(val value: T) : Result<T>

        /** Nightscout is not set up, paused or not started yet. */
        data object NotConnected : Result<Nothing>

        /** The master's token is not an admin token. */
        data object NotAllowed : Result<Nothing>

        /** A subject with this name has other roles: it is not a client token and is left alone. */
        data object NameTaken : Result<Nothing>

        data class Failed(val message: String?) : Result<Nothing>
    }

    /** The name of the token for the client [clientName]. */
    fun subjectName(clientName: String): String = SUBJECT_PREFIX + clientName.trim()

    /** True for a client token: a subject with the client role and nothing else. */
    fun isClientToken(subject: NSAuthSubject): Boolean = subject.roles == listOf(ROLE_NAME)

    /**
     * Sets the client role back to [ROLE_PERMISSIONS] if somebody changed it. With [create] it also stores the role when
     * it is missing; without, a master that has never made a client token does not get a role it does not use.
     */
    suspend fun ensureRole(create: Boolean): Result<Unit> = withClient { client ->
        ensureRoleWith(client, create)
        Result.Ok(Unit)
    }

    /** All subjects, without their tokens: for showing which token each client writes with. */
    suspend fun subjects(): Result<List<NSAuthSubject>> = withClient { client ->
        Result.Ok(client.getAuthSubjects().map { it.copy(accessToken = null) })
    }

    /**
     * The token for the client [clientName]: the client token that already has this name, or a new one. Sets the role
     * right first, so the token never works with more rights than [ROLE_PERMISSIONS].
     */
    suspend fun tokenFor(clientName: String): Result<NSAuthSubject> = withClient { client ->
        ensureRoleWith(client, create = true)
        val name = subjectName(clientName)
        val sameName = client.getAuthSubjects().filter { it.name == name }
        sameName.firstOrNull { isClientToken(it) && it.accessToken != null }?.let { return@withClient Result.Ok(it) }
        if (sameName.isNotEmpty()) return@withClient Result.NameTaken
        val id = client.createAuthSubject(NSAuthSubject(name = name, roles = listOf(ROLE_NAME), notes = SUBJECT_NOTES)).id
            ?: return@withClient Result.Failed("Nightscout did not return the id of the new token")
        // The answer to the create has no token: Nightscout makes it from the id when it lists the subjects.
        client.getAuthSubjects().firstOrNull { it.id == id && it.accessToken != null }?.let { Result.Ok(it) }
            ?: Result.Failed("The new token is not listed")
    }

    /** The client token with this id, with its token, for showing its QR code again. */
    suspend fun token(subjectId: String): Result<NSAuthSubject> = withClient { client ->
        client.getAuthSubjects().firstOrNull { it.id == subjectId && isClientToken(it) && it.accessToken != null }?.let { Result.Ok(it) }
            ?: Result.Failed("The token is not in Nightscout any more")
    }

    /** Deletes the client token with this id. A subject with other roles is not deleted. */
    suspend fun delete(subjectId: String): Result<Unit> = withClient { client ->
        val subject = client.getAuthSubjects().firstOrNull { it.id == subjectId } ?: return@withClient Result.Ok(Unit)
        if (!isClientToken(subject)) return@withClient Result.NameTaken
        client.deleteAuthSubject(subjectId)
        aapsLogger.info(LTag.NSCLIENT, "ClientControl: deleted the Nightscout token ${subject.name}")
        Result.Ok(Unit)
    }

    /**
     * Deletes the client tokens named [subjectName], the token a removed client wrote with. Returns how many were
     * deleted: none when the client used a token with other roles, which belongs to the user.
     */
    suspend fun deleteByName(subjectName: String): Result<Int> = withClient { client ->
        val tokens = client.getAuthSubjects().filter { it.name == subjectName && isClientToken(it) }
        tokens.forEach { subject -> subject.id?.let { client.deleteAuthSubject(it) } }
        if (tokens.isNotEmpty()) aapsLogger.info(LTag.NSCLIENT, "ClientControl: deleted the Nightscout token $subjectName")
        Result.Ok(tokens.size)
    }

    private suspend fun ensureRoleWith(client: NSAndroidClient, create: Boolean) {
        val stored = client.getAuthRoles().filter { it.name == ROLE_NAME && it.id != null }
        if (stored.isEmpty()) {
            if (create) {
                client.createAuthRole(NSAuthRole(name = ROLE_NAME, permissions = ROLE_PERMISSIONS, notes = ROLE_NOTES))
                aapsLogger.info(LTag.NSCLIENT, "ClientControl: stored the Nightscout role $ROLE_NAME")
            }
            return
        }
        // All of them, not the first: Nightscout uses the first role of a name, and which one that is can change.
        stored.filter { it.permissions.sorted() != ROLE_PERMISSIONS.sorted() }.forEach { role ->
            client.updateAuthRole(role.copy(permissions = ROLE_PERMISSIONS))
            aapsLogger.warn(LTag.NSCLIENT, "ClientControl: the Nightscout role $ROLE_NAME was changed to ${role.permissions}, set it back")
        }
    }

    private suspend fun <T> withClient(block: suspend (NSAndroidClient) -> Result<T>): Result<T> {
        val client = nsClientV3Plugin().nsAndroidClient ?: return Result.NotConnected
        return try {
            block(client)
        } catch (e: CancellationException) {
            throw e
        } catch (_: NotAllowedNightscoutException) {
            Result.NotAllowed
        } catch (e: Exception) {
            aapsLogger.error(LTag.NSCLIENT, "ClientControl: Nightscout token call failed: ${e.message}", e)
            Result.Failed(e.message)
        }
    }
}
