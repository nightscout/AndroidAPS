package info.nightscout.sdk.mapper

import info.nightscout.sdk.localmodel.ApiPermission
import info.nightscout.sdk.localmodel.ApiPermissions
import info.nightscout.sdk.remotemodel.RemoteApiPermission
import info.nightscout.sdk.remotemodel.RemoteApiPermissions
import info.nightscout.sdk.remotemodel.read

internal fun RemoteApiPermissions.toLocal(): ApiPermissions =
    ApiPermissions(
        deviceStatus = deviceStatus.toLocal(),
        entries = entries.toLocal(),
        food = food.toLocal(),
        profile = profile.toLocal(),
        settings = settings.toLocal(),
        treatments = treatments.toLocal()
    )

internal fun RemoteApiPermission.toLocal(): ApiPermission =
    ApiPermission(create = create, read = read, update = update, delete = delete)

internal val RemoteApiPermission.create: Boolean
    get() = this.contains('c')

internal val RemoteApiPermission.read: Boolean
    get() = this.contains('r')

internal val RemoteApiPermission.update: Boolean
    get() = this.contains('u')

internal val RemoteApiPermission.delete: Boolean
    get() = this.contains('d')
