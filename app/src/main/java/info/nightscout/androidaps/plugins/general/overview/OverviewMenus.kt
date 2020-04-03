package info.nightscout.androidaps.plugins.general.overview

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.ContextMenu
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.FragmentManager
import info.nightscout.androidaps.Config
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.ErrorHelperActivity
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.db.Source
import info.nightscout.androidaps.db.TempTarget
import info.nightscout.androidaps.dialogs.ProfileSwitchDialog
import info.nightscout.androidaps.dialogs.ProfileViewerDialog
import info.nightscout.androidaps.dialogs.TempTargetDialog
import info.nightscout.androidaps.events.EventRefreshOverview
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.interfaces.PumpDescription
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunction
import info.nightscout.androidaps.plugins.general.overview.OverviewFragment.CHARTTYPE
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.DefaultValueHelper
import info.nightscout.androidaps.utils.ToastUtils
import info.nightscout.androidaps.utils.buildHelper.BuildHelper
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OverviewMenus @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val resourceHelper: ResourceHelper,
    private val sp: SP,
    private val rxBus: RxBusWrapper,
    private val context: Context,
    private val buildHelper: BuildHelper,
    private val defaultValueHelper: DefaultValueHelper,
    private val activePlugin: ActivePluginProvider,
    private val profileFunction: ProfileFunction,
    private val commandQueue: CommandQueueProvider,
    private val configBuilderPlugin: ConfigBuilderPlugin,
    private val loopPlugin: LoopPlugin
) {

    fun setupChartMenu(chartButton: ImageButton) {
        chartButton.setOnClickListener { v: View ->
            val predictionsAvailable: Boolean = when {
                Config.APS      -> loopPlugin.lastRun?.request?.hasPredictions ?: false
                Config.NSCLIENT -> true
                else            -> false
            }
            //var item: MenuItem
            val dividerItem: MenuItem
            //var title: CharSequence
            var titleMaxChars = 0
            //var s: SpannableString
            val popup = PopupMenu(v.context, v)
            if (predictionsAvailable) {
                val item = popup.menu.add(Menu.NONE, CHARTTYPE.PRE.ordinal, Menu.NONE, "Predictions")
                val title = item.title
                if (titleMaxChars < title.length) titleMaxChars = title.length
                val s = SpannableString(title)
                s.setSpan(ForegroundColorSpan(resourceHelper.gc(R.color.prediction)), 0, s.length, 0)
                item.title = s
                item.isCheckable = true
                item.isChecked = sp.getBoolean("showprediction", true)
            }
            run {
                val item = popup.menu.add(Menu.NONE, CHARTTYPE.BAS.ordinal, Menu.NONE, resourceHelper.gs(R.string.overview_show_basals))
                val title = item.title
                if (titleMaxChars < title.length) titleMaxChars = title.length
                val s = SpannableString(title)
                s.setSpan(ForegroundColorSpan(resourceHelper.gc(R.color.basal)), 0, s.length, 0)
                item.title = s
                item.isCheckable = true
                item.isChecked = sp.getBoolean("showbasals", true)
            }
            run {
                val item = popup.menu.add(Menu.NONE, CHARTTYPE.ACTPRIM.ordinal, Menu.NONE, resourceHelper.gs(R.string.overview_show_activity))
                val title = item.title
                if (titleMaxChars < title.length) titleMaxChars = title.length
                val s = SpannableString(title)
                s.setSpan(ForegroundColorSpan(resourceHelper.gc(R.color.activity)), 0, s.length, 0)
                item.title = s
                item.isCheckable = true
                item.isChecked = sp.getBoolean("showactivityprimary", true)
                dividerItem = popup.menu.add("")
                dividerItem.isEnabled = false
            }
            run {
                val item = popup.menu.add(Menu.NONE, CHARTTYPE.IOB.ordinal, Menu.NONE, resourceHelper.gs(R.string.overview_show_iob))
                val title = item.title
                if (titleMaxChars < title.length) titleMaxChars = title.length
                val s = SpannableString(title)
                s.setSpan(ForegroundColorSpan(resourceHelper.gc(R.color.iob)), 0, s.length, 0)
                item.title = s
                item.isCheckable = true
                item.isChecked = sp.getBoolean("showiob", true)
            }
            run {
                val item = popup.menu.add(Menu.NONE, CHARTTYPE.COB.ordinal, Menu.NONE, resourceHelper.gs(R.string.overview_show_cob))
                val title = item.title
                if (titleMaxChars < title.length) titleMaxChars = title.length
                val s = SpannableString(title)
                s.setSpan(ForegroundColorSpan(resourceHelper.gc(R.color.cob)), 0, s.length, 0)
                item.title = s
                item.isCheckable = true
                item.isChecked = sp.getBoolean("showcob", true)
            }
            run {
                val item = popup.menu.add(Menu.NONE, CHARTTYPE.DEV.ordinal, Menu.NONE, resourceHelper.gs(R.string.overview_show_deviations))
                val title = item.title
                if (titleMaxChars < title.length) titleMaxChars = title.length
                val s = SpannableString(title)
                s.setSpan(ForegroundColorSpan(resourceHelper.gc(R.color.deviations)), 0, s.length, 0)
                item.title = s
                item.isCheckable = true
                item.isChecked = sp.getBoolean("showdeviations", false)
            }
            run {
                val item = popup.menu.add(Menu.NONE, CHARTTYPE.SEN.ordinal, Menu.NONE, resourceHelper.gs(R.string.overview_show_sensitivity))
                val title = item.title
                if (titleMaxChars < title.length) titleMaxChars = title.length
                val s = SpannableString(title)
                s.setSpan(ForegroundColorSpan(resourceHelper.gc(R.color.ratio)), 0, s.length, 0)
                item.title = s
                item.isCheckable = true
                item.isChecked = sp.getBoolean("showratios", false)
            }
            run {
                val item = popup.menu.add(Menu.NONE, CHARTTYPE.ACTSEC.ordinal, Menu.NONE, resourceHelper.gs(R.string.overview_show_activity))
                val title = item.title
                if (titleMaxChars < title.length) titleMaxChars = title.length
                val s = SpannableString(title)
                s.setSpan(ForegroundColorSpan(resourceHelper.gc(R.color.activity)), 0, s.length, 0)
                item.title = s
                item.isCheckable = true
                item.isChecked = sp.getBoolean("showactivitysecondary", true)
            }
            if (buildHelper.isDev()) {
                val item = popup.menu.add(Menu.NONE, CHARTTYPE.DEVSLOPE.ordinal, Menu.NONE, "Deviation slope")
                val title = item.title
                if (titleMaxChars < title.length) titleMaxChars = title.length
                val s = SpannableString(title)
                s.setSpan(ForegroundColorSpan(resourceHelper.gc(R.color.devslopepos)), 0, s.length, 0)
                item.title = s
                item.isCheckable = true
                item.isChecked = sp.getBoolean("showdevslope", false)
            }

            // Fairly good estimate for required divider text size...
            dividerItem.title = String(CharArray(titleMaxChars + 10)).replace("\u0000", "_")
            popup.setOnMenuItemClickListener {
                when (it.itemId) {
                    CHARTTYPE.PRE.ordinal      -> sp.putBoolean("showprediction", !it.isChecked)
                    CHARTTYPE.BAS.ordinal      -> sp.putBoolean("showbasals", !it.isChecked)
                    CHARTTYPE.IOB.ordinal      -> sp.putBoolean("showiob", !it.isChecked)
                    CHARTTYPE.COB.ordinal      -> sp.putBoolean("showcob", !it.isChecked)
                    CHARTTYPE.DEV.ordinal      -> sp.putBoolean("showdeviations", !it.isChecked)
                    CHARTTYPE.SEN.ordinal      -> sp.putBoolean("showratios", !it.isChecked)
                    CHARTTYPE.ACTPRIM.ordinal  -> sp.putBoolean("showactivityprimary", !it.isChecked)
                    CHARTTYPE.ACTSEC.ordinal   -> sp.putBoolean("showactivitysecondary", !it.isChecked)
                    CHARTTYPE.DEVSLOPE.ordinal -> sp.putBoolean("showdevslope", !it.isChecked)
                }
                rxBus.send(EventRefreshOverview("OnMenuItemClickListener"))
                return@setOnMenuItemClickListener true
            }
            chartButton.setImageResource(R.drawable.ic_arrow_drop_up_white_24dp)
            popup.setOnDismissListener { chartButton.setImageResource(R.drawable.ic_arrow_drop_down_white_24dp) }
            popup.show()
        }
    }

    fun createContextMenu(menu: ContextMenu, v: View) {
        when (v.id) {
            R.id.overview_apsmode       -> {
                val pumpDescription: PumpDescription = activePlugin.activePump.pumpDescription
                if (!profileFunction.isProfileValid("ContextMenuCreation")) return
                menu.setHeaderTitle(resourceHelper.gs(R.string.loop))
                if (loopPlugin.isEnabled(PluginType.LOOP)) {
                    menu.add(resourceHelper.gs(R.string.disableloop))
                    if (!loopPlugin.isSuspended) {
                        menu.add(resourceHelper.gs(R.string.suspendloopfor1h))
                        menu.add(resourceHelper.gs(R.string.suspendloopfor2h))
                        menu.add(resourceHelper.gs(R.string.suspendloopfor3h))
                        menu.add(resourceHelper.gs(R.string.suspendloopfor10h))
                    } else {
                        if (!loopPlugin.isDisconnected) {
                            menu.add(resourceHelper.gs(R.string.resume))
                        }
                    }
                }
                if (!loopPlugin.isEnabled(PluginType.LOOP)) {
                    menu.add(resourceHelper.gs(R.string.enableloop))
                }
                if (!loopPlugin.isDisconnected) {
                    showSuspendPump(menu, pumpDescription)
                } else {
                    menu.add(resourceHelper.gs(R.string.reconnect))
                }
            }

            R.id.overview_activeprofile -> {
                menu.setHeaderTitle(resourceHelper.gs(R.string.profile))
                menu.add(resourceHelper.gs(R.string.danar_viewprofile))
                if (activePlugin.activeProfileInterface.profile != null) {
                    menu.add(resourceHelper.gs(R.string.careportal_profileswitch))
                }
            }

            R.id.overview_temptarget    -> {
                menu.setHeaderTitle(resourceHelper.gs(R.string.careportal_temporarytarget))
                menu.add(resourceHelper.gs(R.string.custom))
                menu.add(resourceHelper.gs(R.string.eatingsoon))
                menu.add(resourceHelper.gs(R.string.activity))
                menu.add(resourceHelper.gs(R.string.hypo))
                if (activePlugin.activeTreatments.tempTargetFromHistory != null) {
                    menu.add(resourceHelper.gs(R.string.cancel))
                }
            }
        }
    }

    private fun showSuspendPump(menu: ContextMenu, pumpDescription: PumpDescription) {
        if (pumpDescription.tempDurationStep15mAllowed) menu.add(resourceHelper.gs(R.string.disconnectpumpfor15m))
        if (pumpDescription.tempDurationStep30mAllowed) menu.add(resourceHelper.gs(R.string.disconnectpumpfor30m))
        menu.add(resourceHelper.gs(R.string.disconnectpumpfor1h))
        menu.add(resourceHelper.gs(R.string.disconnectpumpfor2h))
        menu.add(resourceHelper.gs(R.string.disconnectpumpfor3h))
    }

    fun onContextItemSelected(item: MenuItem, manager: FragmentManager): Boolean {
        val profile = profileFunction.getProfile() ?: return true
        when (item.title) {
            resourceHelper.gs(R.string.disableloop)                                   -> {
                aapsLogger.debug("USER ENTRY: LOOP DISABLED")
                loopPlugin.setPluginEnabled(PluginType.LOOP, false)
                loopPlugin.setFragmentVisible(PluginType.LOOP, false)
                configBuilderPlugin.storeSettings("DisablingLoop")
                rxBus.send(EventRefreshOverview("suspendmenu"))
                commandQueue.cancelTempBasal(true, object : Callback() {
                    override fun run() {
                        if (!result.success) {
                            ToastUtils.showToastInUiThread(context, resourceHelper.gs(R.string.tempbasaldeliveryerror))
                        }
                    }
                })
                loopPlugin.createOfflineEvent(24 * 60) // upload 24h, we don't know real duration
                return true
            }

            resourceHelper.gs(R.string.enableloop)                                    -> {
                aapsLogger.debug("USER ENTRY: LOOP ENABLED")
                loopPlugin.setPluginEnabled(PluginType.LOOP, true)
                loopPlugin.setFragmentVisible(PluginType.LOOP, true)
                configBuilderPlugin.storeSettings("EnablingLoop")
                rxBus.send(EventRefreshOverview("suspendmenu"))
                loopPlugin.createOfflineEvent(0)
                return true
            }

            resourceHelper.gs(R.string.resume), resourceHelper.gs(R.string.reconnect) -> {
                aapsLogger.debug("USER ENTRY: RESUME")
                loopPlugin.suspendTo(0L)
                rxBus.send(EventRefreshOverview("suspendmenu"))
                commandQueue.cancelTempBasal(true, object : Callback() {
                    override fun run() {
                        if (!result.success) {
                            val i = Intent(context, ErrorHelperActivity::class.java)
                            i.putExtra("soundid", R.raw.boluserror)
                            i.putExtra("status", result.comment)
                            i.putExtra("title", resourceHelper.gs(R.string.tempbasaldeliveryerror))
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(i)
                        }
                    }
                })
                sp.putBoolean(R.string.key_objectiveusereconnect, true)
                loopPlugin.createOfflineEvent(0)
                return true
            }

            resourceHelper.gs(R.string.suspendloopfor1h)                              -> {
                aapsLogger.debug("USER ENTRY: SUSPEND 1h")
                loopPlugin.suspendLoop(60)
                rxBus.send(EventRefreshOverview("suspendmenu"))
                return true
            }

            resourceHelper.gs(R.string.suspendloopfor2h)                              -> {
                aapsLogger.debug("USER ENTRY: SUSPEND 2h")
                loopPlugin.suspendLoop(120)
                rxBus.send(EventRefreshOverview("suspendmenu"))
                return true
            }

            resourceHelper.gs(R.string.suspendloopfor3h)                              -> {
                aapsLogger.debug("USER ENTRY: SUSPEND 3h")
                loopPlugin.suspendLoop(180)
                rxBus.send(EventRefreshOverview("suspendmenu"))
                return true
            }

            resourceHelper.gs(R.string.suspendloopfor10h)                             -> {
                aapsLogger.debug("USER ENTRY: SUSPEND 10h")
                loopPlugin.suspendLoop(600)
                rxBus.send(EventRefreshOverview("suspendmenu"))
                return true
            }

            resourceHelper.gs(R.string.disconnectpumpfor15m)                          -> {
                aapsLogger.debug("USER ENTRY: DISCONNECT 15m")
                loopPlugin.disconnectPump(15, profile)
                rxBus.send(EventRefreshOverview("suspendmenu"))
                return true
            }

            resourceHelper.gs(R.string.disconnectpumpfor30m)                          -> {
                aapsLogger.debug("USER ENTRY: DISCONNECT 30m")
                loopPlugin.disconnectPump(30, profile)
                rxBus.send(EventRefreshOverview("suspendmenu"))
                return true
            }

            resourceHelper.gs(R.string.disconnectpumpfor1h)                           -> {
                aapsLogger.debug("USER ENTRY: DISCONNECT 1h")
                loopPlugin.disconnectPump(60, profile)
                sp.putBoolean(R.string.key_objectiveusedisconnect, true)
                rxBus.send(EventRefreshOverview("suspendmenu"))
                return true
            }

            resourceHelper.gs(R.string.disconnectpumpfor2h)                           -> {
                aapsLogger.debug("USER ENTRY: DISCONNECT 2h")
                loopPlugin.disconnectPump(120, profile)
                rxBus.send(EventRefreshOverview("suspendmenu"))
                return true
            }

            resourceHelper.gs(R.string.disconnectpumpfor3h)                           -> {
                aapsLogger.debug("USER ENTRY: DISCONNECT 3h")
                loopPlugin.disconnectPump(180, profile)
                rxBus.send(EventRefreshOverview("suspendmenu"))
                return true
            }

            resourceHelper.gs(R.string.careportal_profileswitch)                      -> {
                ProfileSwitchDialog().show(manager, "Overview")
            }

            resourceHelper.gs(R.string.danar_viewprofile)                             -> {
                val args = Bundle()
                args.putLong("time", DateUtil.now())
                args.putInt("mode", ProfileViewerDialog.Mode.RUNNING_PROFILE.ordinal)
                val pvd = ProfileViewerDialog()
                pvd.arguments = args
                pvd.show(manager, "ProfileViewDialog")
            }

            resourceHelper.gs(R.string.eatingsoon)                                    -> {
                aapsLogger.debug("USER ENTRY: TEMP TARGET EATING SOON")
                val target = Profile.toMgdl(defaultValueHelper.determineEatingSoonTT(), profileFunction.getUnits())
                val tempTarget = TempTarget()
                    .date(System.currentTimeMillis())
                    .duration(defaultValueHelper.determineEatingSoonTTDuration())
                    .reason(resourceHelper.gs(R.string.eatingsoon))
                    .source(Source.USER)
                    .low(target)
                    .high(target)
                activePlugin.activeTreatments.addToHistoryTempTarget(tempTarget)
            }

            resourceHelper.gs(R.string.activity)                                      -> {
                aapsLogger.debug("USER ENTRY: TEMP TARGET ACTIVITY")
                val target = Profile.toMgdl(defaultValueHelper.determineActivityTT(), profileFunction.getUnits())
                val tempTarget = TempTarget()
                    .date(DateUtil.now())
                    .duration(defaultValueHelper.determineActivityTTDuration())
                    .reason(resourceHelper.gs(R.string.activity))
                    .source(Source.USER)
                    .low(target)
                    .high(target)
                activePlugin.activeTreatments.addToHistoryTempTarget(tempTarget)
            }

            resourceHelper.gs(R.string.hypo)                                          -> {
                aapsLogger.debug("USER ENTRY: TEMP TARGET HYPO")
                val target = Profile.toMgdl(defaultValueHelper.determineHypoTT(), profileFunction.getUnits())
                val tempTarget = TempTarget()
                    .date(DateUtil.now())
                    .duration(defaultValueHelper.determineHypoTTDuration())
                    .reason(resourceHelper.gs(R.string.hypo))
                    .source(Source.USER)
                    .low(target)
                    .high(target)
                activePlugin.activeTreatments.addToHistoryTempTarget(tempTarget)
            }

            resourceHelper.gs(R.string.custom)                                        -> {
                TempTargetDialog().show(manager, "Overview")
            }

            resourceHelper.gs(R.string.cancel)                                        -> {
                aapsLogger.debug("USER ENTRY: TEMP TARGET CANCEL")
                val tempTarget = TempTarget()
                    .source(Source.USER)
                    .date(DateUtil.now())
                    .duration(0)
                    .low(0.0)
                    .high(0.0)
                activePlugin.activeTreatments.addToHistoryTempTarget(tempTarget)
            }
        }
        return false
    }

}