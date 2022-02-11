package info.nightscout.androidaps.plugins.pump.eopatch.extension

import android.text.InputFilter
import android.widget.EditText

internal fun EditText.setRange(min: Int, max: Int) {
    filters = arrayOf(InputFilter { source, _, _, dest, _, _ ->
        try {
            val input = Integer.parseInt(dest.toString() + source.toString())

            if (input in min..max) {
                return@InputFilter null
            }
        } catch (e: NumberFormatException) {
            e.printStackTrace()
        }

        return@InputFilter ""
    })
}
