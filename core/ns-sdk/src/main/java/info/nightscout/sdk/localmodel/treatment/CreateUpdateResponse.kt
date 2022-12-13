package info.nightscout.sdk.localmodel.treatment

class CreateUpdateResponse(
    val identifier: String?,
    val isDeduplication: Boolean? = false,
    val deduplicatedIdentifier: String? = null,
    val lastModified: Long? = null
)