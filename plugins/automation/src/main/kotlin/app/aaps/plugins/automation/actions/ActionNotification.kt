package app.aaps.plugins.automation.actions

import javax.inject.Provider
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.logging.AAPSLogger
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import app.aaps.core.data.model.TE
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.objects.extensions.asAnnouncement
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.utils.lenientString
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.InputString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ActionNotification(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    pumpEnactResultProvider: Provider<PumpEnactResult>,
    private val rxBus: RxBus,
    private val notificationManager: NotificationManager,
    private val persistenceLayer: PersistenceLayer,
    private val dateUtil: DateUtil
) : Action(aapsLogger, rh, pumpEnactResultProvider) {


    var text = InputString()

    override fun friendlyName(): Int = app.aaps.core.ui.R.string.notification
    override fun shortDescription(): String = rh.gs(R.string.notification_message, text.value)
    override fun composeIcon() = Icons.Filled.Notifications
    override fun elementType() = ElementType.ANNOUNCEMENT

    override suspend fun doAction(): PumpEnactResult {
        notificationManager.post(NotificationId.AUTOMATION_MESSAGE, text.value)
        persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(
            therapyEvent = TE.asAnnouncement(text.value),
            timestamp = dateUtil.now(),
            action = app.aaps.core.data.ue.Action.TREATMENT,
            source = Sources.Automation,
            note = text.value,
            listValues = listOf()
        )
        rxBus.send(EventRefreshOverview("ActionNotification"))
        return pumpEnactResultProvider.get().success(true).comment(app.aaps.core.ui.R.string.ok)
    }

    override fun toJSON(): String =
        buildJsonObject {
            put("type", this@ActionNotification.javaClass.simpleName)
            put("data", buildJsonObject { put("text", text.value) })
        }.toString()

    override fun fromJSON(data: String): Action {
        val o = jsonOf(data)
        text.value = o.lenientString("text", "")
        return this
    }

    override fun hasDialog(): Boolean = true

    override fun isValid(): Boolean = text.value.isNotEmpty()
}