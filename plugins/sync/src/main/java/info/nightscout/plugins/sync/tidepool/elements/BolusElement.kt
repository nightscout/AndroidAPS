package info.nightscout.plugins.sync.tidepool.elements

import app.aaps.interfaces.utils.DateUtil
import com.google.gson.annotations.Expose
import info.nightscout.database.entities.Bolus
import java.util.UUID

class BolusElement(bolus: Bolus, dateUtil: DateUtil) : BaseElement(bolus.timestamp, UUID.nameUUIDFromBytes(("AAPS-bolus" + bolus.timestamp).toByteArray()).toString(), dateUtil) {

    @Expose var subType = "normal"
    @Expose var normal: Double = 0.0
    @Expose var expectedNormal: Double = 0.0

    init {
        type = "bolus"
        normal = bolus.amount
        expectedNormal = bolus.amount
    }
}