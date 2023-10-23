package app.aaps.plugins.sync.tidepool.elements

import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.database.entities.Bolus
import com.google.gson.annotations.Expose
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