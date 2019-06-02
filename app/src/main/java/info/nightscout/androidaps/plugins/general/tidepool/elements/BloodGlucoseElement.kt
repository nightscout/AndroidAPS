package info.nightscout.androidaps.plugins.general.tidepool.elements

import com.google.gson.annotations.Expose

class BloodGlucoseElement : BaseElement() {

    @Expose
    var subType: String = "manual"
    @Expose
    var units: String = "mg/dL"
    @Expose
    var value: Int = 0

/* TODO: from careportal ????
    fun fromBloodTest(bloodtest: BloodTest): BloodGlucoseElement {
        val bg = BloodGlucoseElement()
        bg.populate(bloodtest.timestamp, bloodtest.uuid)

        bg.subType = "manual" // TODO
        bg.value = bloodtest.mgdl.toInt()
        return bg
    }

    fun fromBloodTests(bloodTestList: List<BloodTest>?): List<BloodGlucoseElement>? {
        if (bloodTestList == null) return null
        val results = LinkedList<BloodGlucoseElement>()
        for (bt in bloodTestList) {
            results.add(fromBloodTest(bt))
        }
        return results
    }
   */
}