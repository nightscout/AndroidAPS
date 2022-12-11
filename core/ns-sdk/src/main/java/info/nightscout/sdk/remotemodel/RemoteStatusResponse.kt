package info.nightscout.sdk.remotemodel

import com.google.gson.annotations.SerializedName

internal data class NSResponse<T>(val result: T?)

internal data class RemoteStatusResponse(
    @SerializedName("version") val version: String,
    @SerializedName("apiVersion") val apiVersion: String,
    @SerializedName("srvDate") val srvDate: Long,
    @SerializedName("storage") val storage: RemoteStorage,
    @SerializedName("apiPermissions") val apiPermissions: RemoteApiPermissions
)

internal data class RemoteStorage(
    @SerializedName("storage") val storage: String,
    @SerializedName("version") val version: String
)

internal data class RemoteCreateUpdateResponse(
    @SerializedName("identifier") val identifier: String?,
    @SerializedName("isDeduplication") val isDeduplication: Boolean?,
    @SerializedName("deduplicatedIdentifier") val deduplicatedIdentifier: String?,
    @SerializedName("lastModified") val lastModified: Long?
)

internal data class RemoteApiPermissions(
    @SerializedName("devicestatus") val deviceStatus: RemoteApiPermission,
    @SerializedName("entries") val entries: RemoteApiPermission,
    @SerializedName("food") val food: RemoteApiPermission,
    @SerializedName("profile") val profile: RemoteApiPermission,
    @SerializedName("settings") val settings: RemoteApiPermission,
    @SerializedName("treatments") val treatments: RemoteApiPermission
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
