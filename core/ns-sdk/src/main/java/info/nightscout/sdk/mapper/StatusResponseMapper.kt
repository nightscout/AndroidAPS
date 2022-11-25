package info.nightscout.sdk.mapper

import info.nightscout.sdk.localmodel.Status
import info.nightscout.sdk.remotemodel.RemoteStatusResponse

internal fun RemoteStatusResponse.toLocal() = Status(
    version = version,
    apiVersion = apiVersion,
    srvDate = srvDate,
    storage = storage.toLocal(),
    apiPermissions = apiPermissions.toLocal()
)
