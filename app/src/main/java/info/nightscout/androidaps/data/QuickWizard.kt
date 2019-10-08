package info.nightscout.androidaps.data

import info.nightscout.androidaps.utils.SP
import org.json.JSONArray
import org.json.JSONObject

object QuickWizard {
    private var storage = JSONArray()

    init {
        setData(JSONArray(SP.getString("QuickWizard", "[]")))
    }

    fun getActive(): QuickWizardEntry? {
        for (i in 0 until storage.length()) {
            val entry = QuickWizardEntry(storage.get(i) as JSONObject, i)
            if (entry.isActive) return entry
        }
        return null
    }

    fun setData(newData: JSONArray) {
        storage = newData
    }

    fun save() {
        SP.putString("QuickWizard", storage.toString())
    }

    fun size(): Int = storage.length()

    operator fun get(position: Int): QuickWizardEntry =
        QuickWizardEntry(storage.get(position) as JSONObject, position)


    fun newEmptyItem(): QuickWizardEntry {
        return QuickWizardEntry()
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
