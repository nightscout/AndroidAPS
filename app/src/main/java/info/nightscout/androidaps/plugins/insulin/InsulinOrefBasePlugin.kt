package info.nightscout.androidaps.plugins.insulin

import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.Iob
import info.nightscout.androidaps.interfaces.InsulinInterface
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.plugins.bus.RxBus.send
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
import info.nightscout.androidaps.plugins.treatments.Treatment

/**
 * Created by adrian on 13.08.2017.
 */
abstract class InsulinOrefBasePlugin() : PluginBase(PluginDescription()
    .mainType(PluginType.INSULIN)
    .fragmentClass(InsulinFragment::class.java.name)
    .shortName(R.string.insulin_shortname)
    .visibleByDefault(false)
), InsulinInterface {

    private var lastWarned: Long = 0
    override fun getDia(): Double {
        val dia = userDefinedDia
        return if (dia >= MIN_DIA) {
            dia
        } else {
            sendShortDiaNotification(dia)
            MIN_DIA
        }
    }

    open fun sendShortDiaNotification(dia: Double) {
        if (System.currentTimeMillis() - lastWarned > 60 * 1000) {
            lastWarned = System.currentTimeMillis()
            val notification = Notification(Notification.SHORT_DIA, String.format(notificationPattern, dia, MIN_DIA), Notification.URGENT)
            send(EventNewNotification(notification))
        }
    }

    private val notificationPattern: String
        get() = MainApp.gs(R.string.dia_too_short)

    open val userDefinedDia: Double
        get() {
            val profile = ProfileFunctions.getInstance().getProfile()
            return profile?.dia ?: MIN_DIA
        }

    fun iobCalcForTreatment(treatment: Treatment, time: Long): Iob {
        return this.iobCalcForTreatment(treatment, time, 0.0)
    }

    override fun iobCalcForTreatment(treatment: Treatment, time: Long, dia: Double): Iob {
        val result = Iob()
        val peak = peak
        if (treatment.insulin != 0.0) {
            val bolusTime = treatment.date
            val t = (time - bolusTime) / 1000.0 / 60.0
            val td = getDia() * 60 //getDIA() always >= MIN_DIA
            val tp = peak.toDouble()
            // force the IOB to 0 if over DIA hours have passed
            if (t < td) {
                val tau = tp * (1 - tp / td) / (1 - 2 * tp / td)
                val a = 2 * tau / td
                val S = 1 / (1 - a + (1 + a) * Math.exp(-td / tau))
                result.activityContrib = treatment.insulin * (S / Math.pow(tau, 2.0)) * t * (1 - t / td) * Math.exp(-t / tau)
                result.iobContrib = treatment.insulin * (1 - S * (1 - a) * ((Math.pow(t, 2.0) / (tau * td * (1 - a)) - t / tau - 1) * Math.exp(-t / tau) + 1))
            }
        }
        return result
    }

    override fun getComment(): String {
        var comment = commentStandardText()
        val userDia = userDefinedDia
        if (userDia < MIN_DIA) {
            comment += "\n" + String.format(MainApp.gs(R.string.dia_too_short), userDia, MIN_DIA)
        }
        return comment
    }

    abstract val peak: Int
    abstract fun commentStandardText(): String

    companion object {
        const val MIN_DIA = 5.0
    }
}