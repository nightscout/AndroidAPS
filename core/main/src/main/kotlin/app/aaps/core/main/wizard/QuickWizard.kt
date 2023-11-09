package app.aaps.core.main.wizard

import app.aaps.core.interfaces.sharedPreferences.SP
import dagger.android.HasAndroidInjector
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuickWizard @Inject constructor(
    private val sp: SP,
    private val injector: HasAndroidInjector
) {

    private var storage = JSONArray()

    init {
        setData(JSONArray(sp.getString(app.aaps.core.utils.R.string.key_quickwizard, "[]")))
        setGuidsForOldEntries()
    }

    private fun setGuidsForOldEntries() {
        // for migration purposes; guid is a new required property
        for (i in 0 until storage.length()) {
            val entry = QuickWizardEntry(injector).from(storage.get(i) as JSONObject, i)
            if (entry.guid() == "") {
                val guid = UUID.randomUUID().toString()
                entry.storage.put("guid", guid)
            }
        }
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
        sp.putString(app.aaps.core.utils.R.string.key_quickwizard, storage.toString())
    }

    fun size(): Int = storage.length()

    operator fun get(position: Int): QuickWizardEntry =
        QuickWizardEntry(injector).from(storage.get(position) as JSONObject, position)

    fun list(): ArrayList<QuickWizardEntry> =
        ArrayList<QuickWizardEntry>().also {
            for (i in 0 until size()) it.add(get(i))
        }

    fun get(guid: String): QuickWizardEntry? {
        for (i in 0 until storage.length()) {
            val entry = QuickWizardEntry(injector).from(storage.get(i) as JSONObject, i)
            if (entry.guid() == guid) {
                return entry
            }
        }
        return null
    }

    fun move(from: Int, to: Int) {
        //Log.i("QuickWizard", "moveItem: $from $to")
        val fromEntry = storage[from] as JSONObject
        storage.remove(from)
        addToPos(to, fromEntry, storage)
        save()
    }

    fun removePos(pos: Int, jsonObj: JSONObject?, jsonArr: JSONArray) {
        for (i in jsonArr.length() downTo pos + 1) {
            jsonArr.put(i, jsonArr[i - 1])
        }
        jsonArr.put(pos, jsonObj)
    }

    private fun addToPos(pos: Int, jsonObj: JSONObject?, jsonArr: JSONArray) {
        for (i in jsonArr.length() downTo pos + 1) {
            jsonArr.put(i, jsonArr[i - 1])
        }
        jsonArr.put(pos, jsonObj)
    }

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
