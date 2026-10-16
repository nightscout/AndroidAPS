package app.aaps.core.nssdk.remotemodel

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class NSResponse<T>(val result: T? = null)

@Serializable
internal data class RemoteStatusResponse(
    @SerialName("version") val version: String,
    @SerialName("apiVersion") val apiVersion: String,
    @SerialName("srvDate") val srvDate: Long,
    @SerialName("storage") val storage: RemoteStorage,
    @SerialName("apiPermissions") val apiPermissions: RemoteApiPermissions
)

@Serializable
internal data class RemoteStorage(
    @SerialName("storage") val storage: String,
    @SerialName("version") val version: String
)

@Serializable
internal data class RemoteCreateUpdateResponse(
    @SerialName("identifier") val identifier: String? = null,
    @SerialName("isDeduplication") val isDeduplication: Boolean? = null,
    @SerialName("deduplicatedIdentifier") val deduplicatedIdentifier: String? = null,
    @SerialName("lastModified") val lastModified: Long? = null
)

@Serializable
internal data class RemoteApiPermissions(
    @SerialName("devicestatus") val deviceStatus: RemoteApiPermission,
    @SerialName("entries") val entries: RemoteApiPermission,
    @SerialName("food") val food: RemoteApiPermission,
    @SerialName("profile") val profile: RemoteApiPermission,
    @SerialName("settings") val settings: RemoteApiPermission,
    @SerialName("treatments") val treatments: RemoteApiPermission
)

internal typealias RemoteApiPermission = String

internal val RemoteApiPermission.create: Boolean
    get() = this.contains('c')

internal val RemoteApiPermission.read: Boolean
    get() = this.contains('r')

internal val RemoteApiPermission.update: Boolean
    get() = this.contains('u')

internal val RemoteApiPermission.delete: Boolean
    get() = this.contains('d')

internal val RemoteApiPermission.readCreate: Boolean
    get() = this.read && this.create

internal val RemoteApiPermission.full: Boolean
    get() = this.create && this.read && this.update && this.delete
