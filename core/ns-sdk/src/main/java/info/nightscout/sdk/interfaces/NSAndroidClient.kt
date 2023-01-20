package info.nightscout.sdk.interfaces

import info.nightscout.sdk.localmodel.Status
import info.nightscout.sdk.localmodel.devicestatus.NSDeviceStatus
import info.nightscout.sdk.localmodel.entry.NSSgvV3
import info.nightscout.sdk.localmodel.food.NSFood
import info.nightscout.sdk.localmodel.treatment.CreateUpdateResponse
import info.nightscout.sdk.localmodel.treatment.NSTreatment
import info.nightscout.sdk.remotemodel.LastModified
import org.json.JSONObject

interface NSAndroidClient {

    class ReadResponse<T>(
        val code: Int,
        val lastServerModified: Long?,
        val values: T
    )

    val lastStatus: Status?
    suspend fun getVersion(): String
    suspend fun getStatus(): Status
    suspend fun getLastModified(): LastModified

    suspend fun getSgvs(): ReadResponse<List<NSSgvV3>>
    suspend fun getSgvsModifiedSince(from: Long, limit: Long): ReadResponse<List<NSSgvV3>>
    suspend fun getSgvsNewerThan(from: Long, limit: Long): ReadResponse<List<NSSgvV3>>
    suspend fun createSvg(nsSgvV3: NSSgvV3): CreateUpdateResponse
    suspend fun updateSvg(nsSgvV3: NSSgvV3): CreateUpdateResponse

    suspend fun getTreatmentsNewerThan(createdAt: String, limit: Long): ReadResponse<List<NSTreatment>>
    suspend fun getTreatmentsModifiedSince(from: Long, limit: Long): ReadResponse<List<NSTreatment>>

    suspend fun createDeviceStatus(nsDeviceStatus: NSDeviceStatus): CreateUpdateResponse
    suspend fun getDeviceStatusModifiedSince(from: Long): List<NSDeviceStatus>

    suspend fun createProfileStore(remoteProfileStore: JSONObject): CreateUpdateResponse
    suspend fun getProfileModifiedSince(from: Long): ReadResponse<List<JSONObject>>
    suspend fun getLastProfileStore(): ReadResponse<List<JSONObject>>

    suspend fun createTreatment(nsTreatment: NSTreatment): CreateUpdateResponse
    suspend fun updateTreatment(nsTreatment: NSTreatment): CreateUpdateResponse
    suspend fun getFoods(limit: Long): ReadResponse<List<NSFood>>

    //suspend fun getFoodsModifiedSince(from: Long, limit: Long): ReadResponse<List<NSFood>>
    suspend fun createFood(nsFood: NSFood): CreateUpdateResponse
    suspend fun updateFood(nsFood: NSFood): CreateUpdateResponse
}