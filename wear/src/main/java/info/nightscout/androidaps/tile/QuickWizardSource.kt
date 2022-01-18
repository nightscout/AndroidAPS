package info.nightscout.androidaps.tile

import android.content.Context
import android.content.res.Resources
import android.util.Base64
import android.util.Log
import androidx.preference.PreferenceManager
import com.google.android.gms.wearable.DataMap
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interaction.actions.BackgroundActionActivity
import java.util.*

object QuickWizardSource : TileSource {

    override fun getSelectedActions(context: Context): List<Action> {
        val quickList = mutableListOf<Action>()
        val quickMap = getDataMap(context)
        val sfm = secondsFromMidnight()

        for (quick in quickMap) {
            val validFrom = quick.getInt("from", 0)
            val validTo = quick.getInt("to", 0)
            val isActive = sfm in validFrom..validTo
            // use from and to to schedule new update for timeline, for now just refresh every minute
            val guid = quick.getString("guid", "")
            if (isActive && guid != "") {
                quickList.add(
                    Action(
                        buttonText = quick.getString("button_text", "?"),
                        buttonTextSub = "${quick.getInt("carbs", 0)} g",
                        iconRes = R.drawable.ic_quick_wizard,
                        activityClass = BackgroundActionActivity::class.java.name,
                        actionString = "quick_wizard $guid",
                        message = context.resources.getString(R.string.action_quick_wizard_confirmation),
                    )
                )
                Log.i(TAG, "getSelectedActions: active " + quick.getString("button_text", "?") + " guid=" + guid)
            } else {
                Log.i(TAG, "getSelectedActions: not active " + quick.getString("button_text", "?") + " guid=" + guid)
            }

        }

        return quickList
    }

    private fun getDataMap(context: Context): ArrayList<DataMap> {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        val key = context.resources.getString(R.string.key_quick_wizard_data_map)
        if (sharedPrefs.contains(key)) {
            val rawB64Data: String? = sharedPrefs.getString(key, null)
            val rawData: ByteArray = Base64.decode(rawB64Data, Base64.DEFAULT)
            try {
                val map = DataMap.fromByteArray(rawData)
                return map.getDataMapArrayList("quick_wizard")

            } catch (ex: IllegalArgumentException) {
                Log.e(TAG, "getSelectedActions: IllegalArgumentException ", ex)
            }
        }
        return arrayListOf()
    }

    private fun secondsFromMidnight(): Int {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        val passed: Long = System.currentTimeMillis() - c.timeInMillis

        return (passed / 1000).toInt()
    }

    override fun getResourceReferences(resources: Resources): List<Int> {
        return listOf(R.drawable.ic_quick_wizard)
    }

}
