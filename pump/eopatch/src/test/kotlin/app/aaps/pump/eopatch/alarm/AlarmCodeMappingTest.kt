package app.aaps.pump.eopatch.alarm

import android.content.Intent
import android.net.Uri
import app.aaps.pump.eopatch.code.AlarmCategory
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the conversions on [AlarmCode]: the patch's own error number, the name, and the Uri the
 * notification carries.
 *
 * `findByPatchAeCode` is what turns a number the patch reported into the alarm the user is shown -
 * an empty reservoir, an occlusion, a failed needle insertion. It is built by formatting the number
 * back into a name string, and `aeCode` goes the other way by arithmetic on the name. Nothing makes
 * the two agree, so they are checked against each other here for every code that exists.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AlarmCodeMappingTest {

    // ---- the patch's error number ----

    /** Every alarm must be findable from the number the patch reports for it. */
    @Test
    fun everyAlarmRoundTripsThroughItsPatchErrorNumber() {
        val broken = AlarmCode.entries.filter { AlarmCode.findByPatchAeCode(it.aeCode) != it }

        assertThat(broken.map { it.name }).isEmpty()
    }

    /** Alarms are offset by 100, alerts are not - that offset is the whole of the A/B split. */
    @Test
    fun alarmsAreOffsetByAHundredAndAlertsAreNot() {
        val wrong = AlarmCode.entries.filter {
            when (it.type) {
                AlarmCode.TYPE_ALARM -> it.aeCode != it.code + 100
                AlarmCode.TYPE_ALERT -> it.aeCode != it.code
                else                 -> true
            }
        }

        assertThat(wrong.map { it.name }).isEmpty()
    }

    @Test
    fun noTwoAlarmsShareAPatchErrorNumber() {
        val codes = AlarmCode.entries.map { it.aeCode }

        assertThat(codes.toSet()).hasSize(codes.size)
    }

    @Test
    fun anErrorNumberThePatchNeverSendsFindsNothing() {
        assertThat(AlarmCode.findByPatchAeCode(999)).isNull()
        assertThat(AlarmCode.findByPatchAeCode(-1)).isNull()
        assertThat(AlarmCode.findByPatchAeCode(600)).isNull()
    }

    /** 0 is a real alert (B000), not a "nothing reported" value, so it must resolve. */
    @Test
    fun zeroIsARealAlertRatherThanNothing() {
        assertThat(AlarmCode.findByPatchAeCode(0)).isEqualTo(AlarmCode.B000)
    }

    // ---- the name ----

    @Test
    fun everyAlarmIsFoundByItsOwnName() {
        val broken = AlarmCode.entries.filter { AlarmCode.fromStringToCode(it.name) != it }

        assertThat(broken.map { it.name }).isEmpty()
    }

    @Test
    fun anUnknownNameFindsNothing() {
        assertThat(AlarmCode.fromStringToCode("Z999")).isNull()
        assertThat(AlarmCode.fromStringToCode("")).isNull()
    }

    @Test
    fun theCategoryFollowsTheLetterTheNameStartsWith() {
        val wrong = AlarmCode.entries.filter {
            val expected = if (it.name.startsWith("A")) AlarmCategory.ALARM else AlarmCategory.ALERT
            it.alarmCategory != expected
        }

        assertThat(wrong.map { it.name }).isEmpty()
    }

    /** The two "patch occurrence" lists must not overlap, or one alarm would be both kinds. */
    @Test
    fun noAlarmIsBothAnOccurrenceAlarmAndAnOccurrenceAlert() {
        val both = AlarmCode.entries.filter { it.isPatchOccurrenceAlarm && it.isPatchOccurrenceAlert }

        assertThat(both.map { it.name }).isEmpty()
    }

    // ---- the Uri the notification carries ----

    @Test
    fun everyAlarmSurvivesTheTripThroughItsUri() {
        val broken = AlarmCode.entries.filter { AlarmCode.getAlarmCode(AlarmCode.getUri(it)) != it }

        assertThat(broken.map { it.name }).isEmpty()
    }

    @Test
    fun aUriFromSomewhereElseIsNotReadAsAnAlarm() {
        assertThat(AlarmCode.getAlarmCode(Uri.parse("https://example.com/alarmkey?alarmcode=A002"))).isNull()
        assertThat(AlarmCode.getAlarmCode(Uri.parse("alarmkey://info.nightscout.androidaps/other?alarmcode=A002"))).isNull()
    }

    @Test
    fun aUriWithoutACodeIsNotReadAsAnAlarm() {
        assertThat(AlarmCode.getAlarmCode(Uri.parse("alarmkey://info.nightscout.androidaps/alarmkey"))).isNull()
        assertThat(AlarmCode.getAlarmCode(Uri.parse("alarmkey://info.nightscout.androidaps/alarmkey?alarmcode="))).isNull()
    }

    @Test
    fun anAlarmIsReadBackFromTheIntentThatCarriesIt() {
        val intent = Intent().setData(AlarmCode.getUri(AlarmCode.A004))

        assertThat(AlarmCode.fromIntent(intent)).isEqualTo(AlarmCode.A004)
    }

    /** An intent with no data at all must be ignored rather than throw. */
    @Test
    fun anIntentWithoutDataIsIgnored() {
        assertThat(AlarmCode.fromIntent(Intent())).isNull()
    }
}
