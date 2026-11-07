package app.aaps.plugins.sync.tidepool.elements

import app.aaps.core.data.model.BS
import app.aaps.core.data.model.CA
import app.aaps.core.data.model.ICfg
import app.aaps.core.interfaces.utils.DateUtil
import com.google.gson.annotations.Expose
import java.util.UUID

class WizardElement(carbs: CA, dateUtil: DateUtil, iCfg: ICfg) : BaseElement(carbs.timestamp, UUID.nameUUIDFromBytes(("AAPS-wizard" + carbs.timestamp).toByteArray()).toString(), dateUtil) {

    @Expose var units = "mg/dL"
    @Expose var carbInput: Double = 0.toDouble()
    @Expose var insulinCarbRatio: Double = 0.toDouble()
    @Expose var bolus: BolusElement? = null

    init {
        type = "wizard"
        carbInput = carbs.amount
        val fake = BS(
            amount = 0.0001,
            timestamp = carbs.timestamp,
            type = BS.Type.NORMAL,
            iCfg = iCfg
        )
        bolus = BolusElement(fake, dateUtil) // fake insulin record
    }
}

/* TODO fill the rest
{
    "type": "wizard",
    "bgInput": 16.152676653942503,
    "bgTarget": {
        "low": 3.6079861941795968,
        "high": 6.938434988806917
    },
    "bolus": "22239d4d592b48ae920b28971cceb48b",
    "carbInput": 57,
    "insulinCarbRatio": 24,
    "insulinOnBoard": 24.265,
    "insulinSensitivity": 4.329583433015516,
    "recommended": {
        "carb": 2.5,
        "correction": 2.25,
        "net": 0
    },
    "units": "mmol/L",
    "_active": true,
    "_groupId": "abcdef",
    "_schemaVersion": 0,
    "_version": 0,
    "clockDriftOffset": 0,
    "conversionOffset": 0,
    "createdTime": "2018-05-14T08:17:14.353Z",
    "deviceId": "DevId0987654321",
    "deviceTime": "2018-05-14T18:17:09",
    "guid": "18d90ea0-5915-4e95-a8b2-cb22819ce696",
    "id": "087c94ccdae84eb5a76b8205a244ec6b",
    "time": "2018-05-14T08:17:09.353Z",
    "timezoneOffset": 600,
    "uploadId": "SampleUploadId"
}
 */