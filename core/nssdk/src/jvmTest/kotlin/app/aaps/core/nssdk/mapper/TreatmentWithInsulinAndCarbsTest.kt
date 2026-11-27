package app.aaps.core.nssdk.mapper

import app.aaps.core.nssdk.localmodel.treatment.EventType
import app.aaps.core.nssdk.localmodel.treatment.NSBolus
import app.aaps.core.nssdk.localmodel.treatment.NSCarbs
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * One Nightscout document can hold insulin and carbs together, for example a "Meal Bolus" entered
 * in the Nightscout careportal or written by another uploader. AAPS stores a bolus and carbs as two
 * records, so such a document must give both. Before, it gave the bolus only and the carbs were lost.
 */
class TreatmentWithInsulinAndCarbsTest {

    private val date = 1785992179555

    @Test
    fun `a document with insulin and carbs gives a bolus and carbs`() {
        val json = """
            {"_id":"6a7413f54d1ba8c2e419e30e","eventType":"Meal Bolus","date":$date,
             "insulin":3,"carbs":40,"notes":"Lunch","enteredBy":"careportal","utcOffset":120,
             "isValid":true,"created_at":"2026-08-06T04:56:19.555Z","identifier":"meal-1",
             "srvModified":1785992181588,"srvCreated":1785992181588}
        """.trimIndent()

        val treatments = json.toNSTreatments()

        assertThat(treatments).hasSize(2)
        val bolus = treatments[0] as NSBolus
        assertThat(bolus.insulin).isEqualTo(3.0)
        assertThat(bolus.date).isEqualTo(date)
        assertThat(bolus.identifier).isEqualTo("meal-1")
        assertThat(bolus.utcOffset).isEqualTo(120)
        assertThat(bolus.isValid).isTrue()
        val carbs = treatments[1] as NSCarbs
        assertThat(carbs.carbs).isEqualTo(40.0)
        assertThat(carbs.date).isEqualTo(date)
        assertThat(carbs.identifier).isEqualTo("meal-1")
        assertThat(carbs.utcOffset).isEqualTo(120)
        assertThat(carbs.isValid).isTrue()
        assertThat(carbs.eventType).isEqualTo(EventType.MEAL_BOLUS)
        assertThat(carbs.notes).isEqualTo("Lunch")
        assertThat(carbs.duration).isEqualTo(0)
    }

    /** A deleted document must reach both records, so both come back as not valid. */
    @Test
    fun `a deleted document with insulin and carbs gives two records that are not valid`() {
        val json = """{"eventType":"Meal Bolus","date":$date,"insulin":3,"carbs":40,"isValid":false,"identifier":"meal-1"}"""

        val treatments = json.toNSTreatments()

        assertThat(treatments).hasSize(2)
        assertThat(treatments.map { it.isValid }).containsExactly(false, false)
        assertThat(treatments.map { it.identifier }).containsExactly("meal-1", "meal-1")
    }

    @Test
    fun `a document with insulin only gives one bolus`() {
        val json = """{"eventType":"Correction Bolus","date":$date,"insulin":0.25,"carbs":null,"isValid":true,"identifier":"bolus-1"}"""

        val treatments = json.toNSTreatments()

        assertThat(treatments).hasSize(1)
        assertThat((treatments[0] as NSBolus).insulin).isEqualTo(0.25)
    }

    @Test
    fun `a document with insulin and zero carbs gives one bolus`() {
        val json = """{"eventType":"Meal Bolus","date":$date,"insulin":3,"carbs":0,"isValid":true,"identifier":"bolus-1"}"""

        val treatments = json.toNSTreatments()

        assertThat(treatments).hasSize(1)
        assertThat(treatments[0]).isInstanceOf(NSBolus::class.java)
    }

    @Test
    fun `a document with carbs only gives one carbs record`() {
        val json = """{"eventType":"Carb Correction","date":$date,"carbs":4,"insulin":null,"isValid":true,"identifier":"carbs-1"}"""

        val treatments = json.toNSTreatments()

        assertThat(treatments).hasSize(1)
        assertThat((treatments[0] as NSCarbs).carbs).isEqualTo(4.0)
    }

    /**
     * In a "Combo Bolus" from the careportal the duration is the length of the extended insulin.
     * The carbs of that document are a meal eaten now, so they must not be spread over that time.
     */
    @Test
    fun `the carbs of a document with insulin do not take the duration of the document`() {
        val json = """
            {"eventType":"Combo Bolus","date":$date,"insulin":2,"carbs":30,"duration":120,
             "durationInMilliseconds":7200000,"splitNow":50,"splitExt":50,"isValid":true,"identifier":"combo-1"}
        """.trimIndent()

        val treatments = json.toNSTreatments()

        assertThat(treatments).hasSize(2)
        assertThat((treatments[1] as NSCarbs).carbs).isEqualTo(30.0)
        assertThat((treatments[1] as NSCarbs).duration).isEqualTo(0)
    }

    @Test
    fun `a document with carbs only keeps its duration`() {
        val json = """{"eventType":"Carb Correction","date":$date,"carbs":30,"duration":120,"isValid":true,"identifier":"carbs-1"}"""

        val treatments = json.toNSTreatments()

        assertThat(treatments).hasSize(1)
        assertThat((treatments[0] as NSCarbs).duration).isEqualTo(7200000)
    }

    @Test
    fun `a document with carbs and zero insulin gives one carbs record`() {
        val json = """{"eventType":"Meal Bolus","date":$date,"carbs":40,"insulin":0,"isValid":true,"identifier":"carbs-1"}"""

        val treatments = json.toNSTreatments()

        assertThat(treatments).hasSize(1)
        assertThat((treatments[0] as NSCarbs).carbs).isEqualTo(40.0)
    }

    /**
     * Negative carbs are not an error. The carbs dialog of AAPS writes them to take carbs on board
     * away, and a follower must get them to show the same carbs on board as the master.
     */
    @Test
    fun `a document with negative carbs still gives a carbs record`() {
        val json = """{"eventType":"Carb Correction","date":$date,"carbs":-10,"isValid":true,"identifier":"carbs-1"}"""

        val treatments = json.toNSTreatments()

        assertThat(treatments).hasSize(1)
        assertThat((treatments[0] as NSCarbs).carbs).isEqualTo(-10.0)
    }

    @Test
    fun `a document that cannot be used gives no record`() {
        assertThat("""{"eventType":"Something New","date":$date,"isValid":true}""".toNSTreatments()).isEmpty()
        assertThat("{}".toNSTreatments()).isEmpty()
    }
}
