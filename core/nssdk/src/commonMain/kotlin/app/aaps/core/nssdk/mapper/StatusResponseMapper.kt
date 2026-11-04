package app.aaps.core.nssdk.mapper

import app.aaps.core.nssdk.localmodel.Status
import app.aaps.core.nssdk.remotemodel.RemoteStatusResponse

internal fun RemoteStatusResponse.toLocal() = Status(
    version = version,
    apiVersion = apiVersion,
    srvDate = srvDate,
    storage = storage.toLocal(),
    apiPermissions = apiPermissions.toLocal()
)
