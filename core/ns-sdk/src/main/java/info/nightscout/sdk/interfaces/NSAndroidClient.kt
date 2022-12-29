package info.nightscout.sdk.interfaces

import info.nightscout.sdk.localmodel.Status
import info.nightscout.sdk.localmodel.entry.NSSgvV3
import info.nightscout.sdk.localmodel.food.NSFood
import info.nightscout.sdk.localmodel.treatment.CreateUpdateResponse
import info.nightscout.sdk.localmodel.treatment.NSTreatment
import info.nightscout.sdk.remotemodel.LastModified
import info.nightscout.sdk.remotemodel.RemoteDeviceStatus

interface NSAndroidClient {

    class ReadResponse<T>(
        val lastServerModified: Long,
        val values: T
    )

    val lastStatus: Status?
    suspend fun getVersion(): String
    suspend fun getStatus(): Status
    suspend fun getEntries(): String

    suspend fun getLastModified(): LastModified
    suspend fun getSgvs(): List<NSSgvV3>
    suspend fun getSgvsModifiedSince(from: Long, limit: Long): ReadResponse<List<NSSgvV3>>
    suspend fun getSgvsNewerThan(from: Long, limit: Long): List<NSSgvV3>
    suspend fun getTreatmentsNewerThan(createdAt: String, limit: Long): List<NSTreatment>
    suspend fun getTreatmentsModifiedSince(from: Long, limit: Long): ReadResponse<List<NSTreatment>>
    suspend fun getDeviceStatusModifiedSince(from: Long): List<RemoteDeviceStatus>
    suspend fun createTreatment(nsTreatment: NSTreatment): CreateUpdateResponse
    suspend fun updateTreatment(nsTreatment: NSTreatment): CreateUpdateResponse
    suspend fun getFoods(limit: Long): List<NSFood>
    //suspend fun getFoodsModifiedSince(from: Long, limit: Long): ReadResponse<List<NSFood>>
    suspend fun createFood(nsFood: NSFood): CreateUpdateResponse
    suspend fun updateFood(nsFood: NSFood): CreateUpdateResponse
}