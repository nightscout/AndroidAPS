package app.aaps.core.ui.elements

import android.content.Context
import android.util.AttributeSet
import androidx.core.content.edit
import androidx.preference.SeekBarPreference

/**
 * Variant of SeekBarPreference with built-in string->int conversion.
 *
 * The normal SeekBarPreference crashes if the associated value in the
 * SharedPreferences is not an int. This is a problem, because AAPS
 * exports all settings as strings. When importing settings again,
 * the former int value becomes a string value as a consequence.
 *
 * For this reason, this variant exists. It tries to first read the
 * initial preference value from the preferences as an int. If it is
 * not an int, ClassCastException is thrown. This is caught, and the
 * value is re-read as a string and then converted to an int.
 *
 * To use this in fragment XMLs, replace "SeekBarPreference" in them
 * with "app.aaps.core.ui.elements.IntSeekBarPreference".
 */
class IntSeekBarPreference @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : SeekBarPreference(context, attrs) {
    override fun onSetInitialValue(defaultValue: Any?) {
        val actualDefaultValue = if (defaultValue == null)
            0
        else
            (defaultValue as Int?) ?: 0

        val storedValue = try {
            getPersistedInt(actualDefaultValue)
        } catch (_: ClassCastException) {
            val keyToDelete = key
            // Remove the key manually. The setValue() function that is
            // used in the "value" property assignment below tries to look
            // up the existing stored value if it exists. If it does exist,
            // it tries to read the value - as an int. We then get another
            // ClassCastException. To avoid that, first delete the existing
            // value. This prevents setValue() from doing that int lookup.
            sharedPreferences?.edit {
                remove(keyToDelete)
            }
            getPersistedString(actualDefaultValue.toString()).toInt()
        }

        value = storedValue
    }
}