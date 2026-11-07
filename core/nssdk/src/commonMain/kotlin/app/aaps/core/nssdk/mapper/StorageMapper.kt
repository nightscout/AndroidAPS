package app.aaps.core.nssdk.mapper

import app.aaps.core.nssdk.localmodel.Storage
import app.aaps.core.nssdk.remotemodel.RemoteStorage

internal fun RemoteStorage.toLocal() = Storage(storage = storage, version = version)
