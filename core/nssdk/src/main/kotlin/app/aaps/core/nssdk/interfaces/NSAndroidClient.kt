package app.aaps.core.nssdk.interfaces

import app.aaps.core.nssdk.localmodel.Status
import app.aaps.core.nssdk.localmodel.devicestatus.NSDeviceStatus
import app.aaps.core.nssdk.localmodel.entry.NSSgvV3
import app.aaps.core.nssdk.localmodel.food.NSFood
import app.aaps.core.nssdk.localmodel.treatment.CreateUpdateResponse
import app.aaps.core.nssdk.localmodel.treatment.NSTreatment
import app.aaps.core.nssdk.remotemodel.LastModified
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
    suspend fun getSgvsModifiedSince(from: Long, limit: Int): ReadResponse<List<NSSgvV3>>
    suspend fun getSgvsNewerThan(from: Long, limit: Int): ReadResponse<List<NSSgvV3>>
    suspend fun createSgv(nsSgvV3: NSSgvV3): CreateUpdateResponse
    suspend fun updateSvg(nsSgvV3: NSSgvV3): CreateUpdateResponse

    suspend fun getTreatmentsNewerThan(createdAt: String, limit: Int): ReadResponse<List<NSTreatment>>
    suspend fun getTreatmentsModifiedSince(from: Long, limit: Int): ReadResponse<List<NSTreatment>>

    suspend fun createDeviceStatus(nsDeviceStatus: NSDeviceStatus): CreateUpdateResponse
    suspend fun getDeviceStatusModifiedSince(from: Long): List<NSDeviceStatus>

    suspend fun createProfileStore(remoteProfileStore: JSONObject): CreateUpdateResponse
    suspend fun getProfileModifiedSince(from: Long): ReadResponse<List<JSONObject>>
    suspend fun getLastProfileStore(): ReadResponse<List<JSONObject>>

    suspend fun createTreatment(nsTreatment: NSTreatment): CreateUpdateResponse
    suspend fun updateTreatment(nsTreatment: NSTreatment): CreateUpdateResponse
    suspend fun getFoods(limit: Int): ReadResponse<List<NSFood>>

    //suspend fun getFoodsModifiedSince(from: Long, limit: Int): ReadResponse<List<NSFood>>
    suspend fun createFood(nsFood: NSFood): CreateUpdateResponse
    suspend fun updateFood(nsFood: NSFood): CreateUpdateResponse
}