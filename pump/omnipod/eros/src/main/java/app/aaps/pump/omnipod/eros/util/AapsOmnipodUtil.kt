package app.aaps.pump.omnipod.eros.util

import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.pump.omnipod.common.R
import app.aaps.pump.omnipod.eros.driver.definition.AlertType
import app.aaps.pump.omnipod.eros.driver.manager.ErosPodStateManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializer
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.format.ISODateTimeFormat
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.SingleIn

/**
 * Created by andy on 4/8/19. Was Java.
 *
 * Gson stays: this is JVM only code and the pod state it serialises is an existing stored format, so
 * swapping the serializer would change what is written to disk.
 *
 * javax `@Singleton`, not Metro's `@SingleIn`: Dagger owns this and hands it to Metro via `PumpLeaves`.
 */
@SingleIn(AppScope::class)
class AapsOmnipodUtil @Inject constructor(
    private val rh: ResourceHelper
) {

    // Public val, not a private val plus a getter: Kotlin callers use `aapsOmnipodUtil.gsonInstance`
    // and Java callers use `getGsonInstance()`. A public val compiles to exactly that getter, so both work.
    val gsonInstance: Gson = createGson()

    private fun createGson(): Gson =
        GsonBuilder()
            .registerTypeAdapter(
                DateTime::class.java,
                JsonSerializer<DateTime> { dateTime, _, _ -> JsonPrimitive(ISODateTimeFormat.dateTime().print(dateTime)) }
            )
            .registerTypeAdapter(
                DateTime::class.java,
                JsonDeserializer { json, _, _ -> ISODateTimeFormat.dateTime().parseDateTime(json.asString) }
            )
            .registerTypeAdapter(
                DateTimeZone::class.java,
                JsonSerializer<DateTimeZone> { timeZone, _, _ -> JsonPrimitive(timeZone.id) }
            )
            .registerTypeAdapter(
                DateTimeZone::class.java,
                JsonDeserializer { json, _, _ -> DateTimeZone.forID(json.asString) }
            )
            .create()

    fun getTranslatedActiveAlerts(podStateManager: ErosPodStateManager): List<String> =
        podStateManager.activeAlerts.alertSlots.map { alertSlot ->
            translateAlertType(podStateManager.getConfiguredAlertType(alertSlot))
        }

    private fun translateAlertType(alertType: AlertType?): String = when (alertType) {
        null                                  -> rh.gs(R.string.omnipod_common_alert_unknown_alert)
        AlertType.FINISH_PAIRING_REMINDER     -> rh.gs(R.string.omnipod_common_alert_finish_pairing_reminder)
        AlertType.FINISH_SETUP_REMINDER       -> rh.gs(R.string.omnipod_common_alert_finish_setup_reminder_reminder)
        AlertType.EXPIRATION_ALERT            -> rh.gs(R.string.omnipod_common_alert_expiration)
        AlertType.EXPIRATION_ADVISORY_ALERT   -> rh.gs(R.string.omnipod_common_alert_expiration_advisory)
        AlertType.SHUTDOWN_IMMINENT_ALARM     -> rh.gs(R.string.omnipod_common_alert_shutdown_imminent)
        AlertType.LOW_RESERVOIR_ALERT         -> rh.gs(R.string.omnipod_common_alert_low_reservoir)
        else                                  -> alertType.name
    }
}
