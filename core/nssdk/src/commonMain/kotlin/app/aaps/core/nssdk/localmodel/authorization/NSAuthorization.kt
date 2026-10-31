package app.aaps.core.nssdk.localmodel.authorization

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A role of the Nightscout authorization, as `/api/v2/authorization/roles` sends and takes it.
 *
 * The built-in roles (`admin`, `readable`, `denied` ...) are listed too, but without an [id]: they live in the server
 * code and cannot be changed. A role with an [id] is stored and can be replaced as a whole.
 */
@Serializable
data class NSAuthRole(
    @SerialName("_id") val id: String? = null,
    val name: String,
    /** Shiro-style permissions, for example `api:settings:update` or `*:*:read`. */
    val permissions: List<String> = emptyList(),
    val notes: String? = null
)

/**
 * A subject of the Nightscout authorization: one access token and the roles it has, as
 * `/api/v2/authorization/subjects` sends and takes it.
 *
 * Nightscout writes the [name] into the `subject` field of every API v3 document the token creates or replaces. Names
 * are not unique: only [id] identifies a subject.
 */
@Serializable
data class NSAuthSubject(
    @SerialName("_id") val id: String? = null,
    val name: String,
    val roles: List<String> = emptyList(),
    val notes: String? = null,
    /**
     * SENSITIVE: the access token. Nightscout makes it from [id] and its API secret and sends it only when the subjects
     * are listed. Null on a subject that is being created.
     */
    val accessToken: String? = null
) {

    /** Hides the token, so a log line that prints a subject cannot leak it. */
    override fun toString(): String =
        "NSAuthSubject(id=$id, name=$name, roles=$roles, notes=$notes, accessToken=${if (accessToken == null) "null" else "****"})"
}
