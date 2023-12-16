package app.aaps.plugins.main.skins

import android.content.Context
import android.util.AttributeSet
import app.aaps.core.keys.AdaptiveListPreference
import dagger.android.HasAndroidInjector
import java.util.Vector
import javax.inject.Inject

class SkinListPreference(context: Context, attrs: AttributeSet?) : AdaptiveListPreference(context, attrs) {

    @Inject lateinit var skinProvider: SkinProvider

    init {
        (context.applicationContext as HasAndroidInjector).androidInjector().inject(this)
        val entries = Vector<CharSequence>()
        val values = Vector<CharSequence>()

        for (skin in skinProvider.list) {
            values.addElement(skin.javaClass.name)
            entries.addElement(context.getString(skin.description))
        }
        entryValues = values.toTypedArray()
        setEntries(entries.toTypedArray())
    }
}
