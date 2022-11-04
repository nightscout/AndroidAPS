package info.nightscout.androidaps.plugins.pump.eopatch.alarm

import android.content.Intent
import android.net.Uri
import info.nightscout.androidaps.plugins.pump.eopatch.R
import info.nightscout.androidaps.plugins.pump.eopatch.code.AlarmCategory
import java.util.*
import java.util.function.Function
import java.util.stream.Collectors
import java.util.stream.Stream

enum class AlarmCode(messageResId: Int) {
    A002(R.string.string_a002),  //"Empty reservoir"
    A003(R.string.string_a003),  //"Patch expired"
    A004(R.string.string_a004),  //"Occlusion"
    A005(R.string.string_a005),  //"Power on self test failure"
    A007(R.string.string_a007),  //"Inappropriate temperature"
    A016(R.string.string_a016),  //"Needle insertion Error"
    A018(R.string.string_a018),  //"Patch battery Error"
    A019(R.string.string_a019),  //"Patch battery Error"
    A020(R.string.string_a020),  //"Patch activation Error"
    A022(R.string.string_a022),  //"Patch Error"
    A023(R.string.string_a023),  //"Patch Error"
    A034(R.string.string_a034),  //"Patch Error"
    A041(R.string.string_a041),  //"Patch Error"
    A042(R.string.string_a042),  //"Patch Error"
    A043(R.string.string_a043),  //"Patch Error"
    A044(R.string.string_a044),  //"Patch Error"
    A106(R.string.string_a106),  //"Patch Error"
    A107(R.string.string_a107),  //"Patch Error"
    A108(R.string.string_a108),  //"Patch Error"
    A116(R.string.string_a116),  //"Patch Error"
    A117(R.string.string_a117),  //"Patch Error"
    A118(R.string.string_a118),  //"Patch Error"
    B000(R.string.string_b000),
    B001(R.string.string_b001),  //"End of insulin suspend"
    B003(R.string.string_b003),  //"Low reservoir"
    B005(R.string.string_b005),  //"Patch operating life expired"
    B006(R.string.string_b006),  //"Patch will expire soon"
    B012(R.string.string_b012),  //"Incomplete Patch activation"
    B018(R.string.string_b018);  //"Patch battery low"

    val type: Char = name[0]
    val code: Int = name.substring(1).toInt()
    val resId: Int = messageResId

    val alarmCategory: AlarmCategory
        get() = when (type) {
            TYPE_ALARM -> AlarmCategory.ALARM
            TYPE_ALERT -> AlarmCategory.ALERT
            else       -> AlarmCategory.NONE
        }

    val aeCode: Int
        get() {
            when (type) {
                TYPE_ALARM -> return code + 100
                TYPE_ALERT -> return code
            }
            return -1
        }

    val isPatchOccurrenceAlert: Boolean
        get() = this == B003 || this == B005 || this == B006 || this == B018

    val isPatchOccurrenceAlarm: Boolean
        get() = this == A002 || this == A003 || this == A004 || this == A018 || this == A019 || this == A022
            || this == A023 || this == A034 || this == A041 || this == A042 || this == A043 || this == A044 || this == A106
            || this == A107 || this == A108 || this == A116 || this == A117 || this == A118

    companion object {
        const val TYPE_ALARM = 'A'
        const val TYPE_ALERT = 'B'

        private const val SCHEME = "alarmkey"
        private const val ALARM_KEY_PATH = "alarmkey"
        private const val QUERY_CODE = "alarmcode"

        private val NAME_MAP = Stream.of(*values())
            .collect(Collectors.toMap({ obj: AlarmCode -> obj.name }, Function.identity()))

        fun fromStringToCode(name: String): AlarmCode? {
            return NAME_MAP[name]
        }

        fun findByPatchAeCode(aeCode: Int): AlarmCode? {
            return if (aeCode > 100) {
                fromStringToCode(String.format(Locale.US, "A%03d", aeCode - 100))
            } else fromStringToCode(String.format(Locale.US, "B%03d", aeCode))
        }

        @JvmStatic
        fun getUri(alarmCode: AlarmCode): Uri {
            return Uri.Builder()
                .scheme(SCHEME)
                .authority("info.nightscout.androidaps")
                .path(ALARM_KEY_PATH)
                .appendQueryParameter(QUERY_CODE, alarmCode.name)
                .build()
        }

        @JvmStatic
        fun getAlarmCode(uri: Uri): AlarmCode? {
            if (SCHEME == uri.scheme && ALARM_KEY_PATH == uri.lastPathSegment) {
                val code = uri.getQueryParameter(QUERY_CODE)
                if (code.isNullOrBlank()) {
                    return null
                }
                return fromStringToCode(code)
            }
            return null
        }

        @JvmStatic
        fun fromIntent(intent: Intent): AlarmCode? {
            return intent.data?.let { getAlarmCode(it) }
        }
    }
}