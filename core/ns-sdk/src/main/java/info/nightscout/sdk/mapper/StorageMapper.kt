package info.nightscout.sdk.mapper

import info.nightscout.sdk.localmodel.Storage
import info.nightscout.sdk.remotemodel.RemoteStorage

internal fun RemoteStorage.toLocal() = Storage(storage = storage, version = version)
