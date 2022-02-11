package info.nightscout.androidaps.plugins.pump.eopatch.alarm

import android.content.Intent
import android.net.Uri
import info.nightscout.androidaps.plugins.pump.eopatch.R
import info.nightscout.androidaps.plugins.pump.eopatch.code.AlarmCategory
import java.util.*
import java.util.function.Function
import java.util.stream.Collectors
import java.util.stream.Stream

enum class AlarmCode(defaultName: String, messageResId: Int) {
    A002("Empty reservoir", R.string.string_a002),
    A003("Patch expired", R.string.string_a003),
    A004("Occlusion", R.string.string_a004),
    A005("Power on self test failure", R.string.string_a005),
    A007("Inappropriate temperature", R.string.string_a007),
    A016("Needle insertion Error", R.string.string_a016),
    A018("Patch battery Error", R.string.string_a018),
    A019("Patch battery Error", R.string.string_a019),
    A020("Patch activation Error", R.string.string_a020),
    A022("Patch Error", R.string.string_a022),
    A023("Patch Error", R.string.string_a023),
    A034("Patch Error", R.string.string_a034),
    A041("Patch Error", R.string.string_a041),
    A042("Patch Error", R.string.string_a042),
    A043("Patch Error", R.string.string_a043),
    A044("Patch Error", R.string.string_a044),
    A106("Patch Error", R.string.string_a106),
    A107("Patch Error", R.string.string_a107),
    A108("Patch Error", R.string.string_a108),
    A116("Patch Error", R.string.string_a116),
    A117("Patch Error", R.string.string_a117),
    A118("Patch Error", R.string.string_a118),
    B001("End of insulin suspend", R.string.string_b001),
    B003("Low reservoir", R.string.string_b003),
    B005("Patch operating life expired", R.string.string_b005),
    B006("Patch will expire soon", R.string.string_b006),
    B012("Incomplete Patch activation", R.string.string_b012),
    B018("Patch battery low", R.string.string_b018);

    val type: Char
    val code: Int
    val resId: Int
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

    val osAlarmId: Int
        get() = (when (type) {
            TYPE_ALARM -> 10000
            TYPE_ALERT -> 20000
            else -> 0
        } + code ) * 1000 + 1

    val isPatchOccurrenceAlert: Boolean
        get() = this == B003 || this == B005 || this == B006 || this == B018

    val isPatchOccurrenceAlarm: Boolean
        get() = this == A002 || this == A003 || this == A004 || this == A018 || this == A019 || this == A022
            || this == A023 || this == A034 || this == A041 || this == A042 || this == A043 || this == A044 || this == A106
            || this == A107 || this == A108 || this == A116 || this == A117 || this == A118

    init {
        type = name[0]
        this.code = name.substring(1).toInt()
        resId = messageResId
    }

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
                .authority("com.eoflow.eomapp")
                .path(ALARM_KEY_PATH)
                .appendQueryParameter(QUERY_CODE, alarmCode.name)
                .build();
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