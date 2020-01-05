package info.nightscout.androidaps.plugins.general.automation.triggers

import android.content.Context
import android.content.ContextWrapper
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.common.base.Optional
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunction
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.services.LocationService
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.json.JSONException
import org.json.JSONObject
import javax.inject.Inject
import kotlin.reflect.full.primaryConstructor

abstract class Trigger(val mainApp: MainApp) {
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var sp : SP
    @Inject lateinit var locationService: LocationService
    @Inject lateinit var treatmentsPlugin: TreatmentsPlugin
    @Inject lateinit var configBuilderPlugin: ConfigBuilderPlugin
    @Inject lateinit var iobCobCalculatorPlugin: IobCobCalculatorPlugin

    var connector: TriggerConnector? = null

    init {
        mainApp.androidInjector().inject(this)
    }

    abstract fun shouldRun(): Boolean
    abstract fun toJSON(): String
    abstract fun fromJSON(data: String): Trigger

    abstract fun friendlyName(): Int
    abstract fun friendlyDescription(): String
    abstract fun icon(): Optional<Int?>
    abstract fun duplicate(): Trigger


    companion object {
        @JvmStatic
        fun scanForActivity(cont: Context?): AppCompatActivity? {
            if (cont == null) return null
            else if (cont is AppCompatActivity) return cont
            else if (cont is ContextWrapper) return scanForActivity(cont.baseContext)
            return null
        }
    }

    open fun generateDialog(root: LinearLayout) {
        val title = TextView(root.context)
        title.setText(friendlyName())
        root.addView(title)
    }

    fun instantiate(obj: JSONObject): Trigger? {
        try {
            val type = obj.getString("type")
            val data = obj.getJSONObject("data")
            val clazz = Class.forName(type).kotlin
            return (clazz.primaryConstructor?.call(mainApp) as Trigger).fromJSON(data?.toString()
                ?: "")
        } catch (e: ClassNotFoundException) {
            aapsLogger.error("Unhandled exception", e)
        } catch (e: InstantiationException) {
            aapsLogger.error("Unhandled exception", e)
        } catch (e: IllegalAccessException) {
            aapsLogger.error("Unhandled exception", e)
        } catch (e: JSONException) {
            aapsLogger.error("Unhandled exception", e)
        }
        return null
    }
}