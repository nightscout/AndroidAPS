package info.nightscout.androidaps.utils.wizard

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuickWizard @Inject constructor(
    private val sp: SP,
    private val injector: HasAndroidInjector
) {

    private var storage = JSONArray()

    init {
        setData(JSONArray(sp.getString(R.string.key_quickwizard, "[]")))
    }

    fun getActive(): QuickWizardEntry? {
        for (i in 0 until storage.length()) {
            val entry = QuickWizardEntry(injector).from(storage.get(i) as JSONObject, i)
            if (entry.isActive()) return entry
        }
        return null
    }

    fun setData(newData: JSONArray) {
        storage = newData
    }

    fun save() {
        sp.putString(R.string.key_quickwizard, storage.toString())
    }

    fun size(): Int = storage.length()

    operator fun get(position: Int): QuickWizardEntry =
        QuickWizardEntry(injector).from(storage.get(position) as JSONObject, position)

    fun newEmptyItem(): QuickWizardEntry {
        return QuickWizardEntry(injector)
    }

    fun addOrUpdate(newItem: QuickWizardEntry) {
        if (newItem.position == -1)
            storage.put(newItem.storage)
        else
            storage.put(newItem.position, newItem.storage)
        save()
    }

    fun remove(position: Int) {
        storage.remove(position)
        save()
    }
}
