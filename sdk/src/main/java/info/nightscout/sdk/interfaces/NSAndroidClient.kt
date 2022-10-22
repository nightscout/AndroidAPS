package info.nightscout.sdk.interfaces

import info.nightscout.sdk.localmodel.Status
import info.nightscout.sdk.localmodel.entry.Sgv
import info.nightscout.sdk.localmodel.treatment.Treatment
import info.nightscout.sdk.remotemodel.LastModified

interface NSAndroidClient {

    suspend fun getVersion(): String
    suspend fun getStatus(): Status
    suspend fun getEntries(): String

    suspend fun getLastModified(): LastModified
    suspend fun getSgvs(): List<Sgv>
    suspend fun getSgvsModifiedSince(from: Long): List<Sgv>
    suspend fun getSgvsNewerThan(from: Long, limit: Long): List<Sgv>
    suspend fun getTreatmentsModifiedSince(from: Long, limit: Long): List<Treatment>
}