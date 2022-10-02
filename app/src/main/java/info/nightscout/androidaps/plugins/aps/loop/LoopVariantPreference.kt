package info.nightscout.androidaps.plugins.aps.loop

import android.content.Context
import android.content.SharedPreferences
import android.util.AttributeSet
import androidx.preference.DropDownPreference
import androidx.preference.PreferenceDataStore
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.core.R
import info.nightscout.shared.sharedPreferences.SP
import java.util.*
import javax.inject.Inject

class LoopVariantPreference(context: Context, attrs: AttributeSet?)
    : DropDownPreference(context, attrs) {


    @Inject lateinit var sp: SP

    private var pluginFolder: String? = null

    init {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.LoopVariantPreference, 0, 0)
        pluginFolder = typedArray.getString(R.styleable.LoopVariantPreference_pluginFolder)
        key = "key_${pluginFolder}_variant";
        val entries = Vector<CharSequence>()
        entries.add(DEFAULT)

        val list = context.assets.list("$pluginFolder/")
        list?.forEach {
            if (!it.endsWith(".js"))
                entries.add(it)
        }

        entryValues = entries.toTypedArray()
        setEntries(entries.toTypedArray())
        setDefaultValue(sp.getString(key, DEFAULT))
    }

    companion object {
        const val DEFAULT = "default"

        fun getVariantFileName(sp: SP, pluginFolder: String) : String
        {
            return when (val variant = sp.getString("key_${pluginFolder}_variant", DEFAULT)) {
                DEFAULT -> "$pluginFolder/determine-basal.js"
                else    -> "$pluginFolder/$variant/determine-basal.js"
            }
        }
    }
}
