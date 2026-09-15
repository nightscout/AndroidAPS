package app.aaps.core.nssdk.localmodel.treatment

class CreateUpdateResponse(
    val response: Int,
    val identifier: String?,
    val isDeduplication: Boolean? = false,
    val deduplicatedIdentifier: String? = null,
    val lastModified: Long? = null,
    val errorResponse: String? = null
)