package info.nightscout.androidaps.plugins.pump.omnipod.dash.history.data

import com.github.guepardoapps.kulid.ULID
import info.nightscout.androidaps.plugins.pump.omnipod.common.definition.OmnipodCommandType
import java.nio.ByteBuffer

data class HistoryRecord(
    val id: String, // ULID
    val createdAt: Long, // creation date of the record
    val date: Long, // when event actually happened
    val commandType: OmnipodCommandType,
    val initialResult: InitialResult,
    val record: Record?,
    val resolvedResult: ResolvedResult?,
    val resolvedAt: Long?
) {
    fun pumpId(): Long {
        val entropy = ULID.getEntropy(id)
        return ByteBuffer.wrap(entropy).getLong()
    }
}
