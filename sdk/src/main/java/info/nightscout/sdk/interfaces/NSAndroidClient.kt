package info.nightscout.sdk.interfaces

import info.nightscout.sdk.localmodel.Status
import info.nightscout.sdk.localmodel.entry.Sgv
import info.nightscout.sdk.localmodel.treatment.Treatment

interface NSAndroidClient {

    suspend fun getVersion(): String
    suspend fun getStatus(): Status
    suspend fun getEntries(): String
    suspend fun getSgvs(): List<Sgv>
    suspend fun getSgvsModifiedSince(from: Long): List<Sgv>
    suspend fun getTreatmentsModifiedSince(from: Long): List<Treatment>
}