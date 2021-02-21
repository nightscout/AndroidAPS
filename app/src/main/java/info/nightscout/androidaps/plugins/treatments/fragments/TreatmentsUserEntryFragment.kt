package info.nightscout.androidaps.plugins.treatments.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.UserEntry
import info.nightscout.androidaps.databinding.TreatmentsUserEntryFragmentBinding
import info.nightscout.androidaps.databinding.TreatmentsUserEntryItemBinding
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import javax.inject.Inject

class TreatmentsUserEntryFragment : DaggerFragment() {

    @Inject lateinit var repository: AppRepository
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var dateUtil: DateUtil

    private val disposable = CompositeDisposable()

    private var _binding: TreatmentsUserEntryFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        TreatmentsUserEntryFragmentBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerview.setHasFixedSize(true)
        binding.recyclerview.layoutManager = LinearLayoutManager(view.context)

        disposable += repository
            .getAllUserEntries()
            .observeOn(aapsSchedulers.main)
            .subscribe { list -> binding.recyclerview.swapAdapter(UserEntryAdapter(list), true) }
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        disposable.clear()
        _binding = null
    }

    inner class UserEntryAdapter internal constructor(var entries: List<UserEntry>) : RecyclerView.Adapter<UserEntryAdapter.UserEntryViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserEntryViewHolder {
            val view: View = LayoutInflater.from(parent.context).inflate(R.layout.treatments_user_entry_item, parent, false)
            return UserEntryViewHolder(view)
        }

        override fun onBindViewHolder(holder: UserEntryViewHolder, position: Int) {
            val current = entries[position]
            holder.binding.date.text = dateUtil.dateAndTimeAndSecondsString(current.timestamp)
            holder.binding.action.text = action2String(current.action)
            if (current.s != "") holder.binding.s.text = current.s else holder.binding.s.visibility = View.GONE
            if (current.d1 != 0.0) holder.binding.d1.text = current.d1.toString() else holder.binding.d1.visibility = View.GONE
            if (current.d2 != 0.0) holder.binding.d2.text = current.d2.toString() else holder.binding.d2.visibility = View.GONE
            if (current.i1 != 0) holder.binding.i1.text = current.i1.toString() else holder.binding.i1.visibility = View.GONE
            if (current.i2 != 0) holder.binding.i2.text = current.i2.toString() else holder.binding.i2.visibility = View.GONE
        }

        inner class UserEntryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

            val binding = TreatmentsUserEntryItemBinding.bind(itemView)
        }

        override fun getItemCount(): Int = entries.size

        private fun action2String(action: UserEntry.Action): String {
            return when (action) {
                UserEntry.Action.BOLUS -> resourceHelper.gs(R.string.uel_bolus)
                UserEntry.Action.BOLUS_WIZARD -> resourceHelper.gs(R.string.uel_bolus_wizard)
                UserEntry.Action.BOLUS_ADVISOR -> resourceHelper.gs(R.string.uel_bolus_advisor)
                UserEntry.Action.BOLUS_RECORD -> resourceHelper.gs(R.string.uel_bolus_record)
                UserEntry.Action.EXTENDED_BOLUS -> resourceHelper.gs(R.string.uel_extended_bolus)
                UserEntry.Action.SUPERBOLUS_TBR -> resourceHelper.gs(R.string.uel_superbolus_tbr)
                UserEntry.Action.CARBS -> resourceHelper.gs(R.string.uel_carbs)

                UserEntry.Action.TEMP_BASAL -> resourceHelper.gs(R.string.uel_temp_basal)
                UserEntry.Action.TT -> resourceHelper.gs(R.string.uel_tt)
                UserEntry.Action.TT_ACTIVITY -> resourceHelper.gs(R.string.uel_tt_activity)
                UserEntry.Action.TT_EATING_SOON -> resourceHelper.gs(R.string.uel_tt_eating_soon)
                UserEntry.Action.TT_HYPO -> resourceHelper.gs(R.string.uel_tt_hypo)
                UserEntry.Action.NEW_PROFILE -> resourceHelper.gs(R.string.uel_new_profile)
                UserEntry.Action.CLONE_PROFILE -> resourceHelper.gs(R.string.uel_clone_profile)
                UserEntry.Action.STORE_PROFILE -> resourceHelper.gs(R.string.uel_store_profile)
                UserEntry.Action.PROFILE_SWITCH -> resourceHelper.gs(R.string.uel_profile_switch)
                UserEntry.Action.PROFILE_SWITCH_CLONED -> resourceHelper.gs(R.string.uel_profile_switch_clone)
                UserEntry.Action.CLOSED_LOOP_MODE -> resourceHelper.gs(R.string.uel_closed_loop_mode)
                UserEntry.Action.LGS_LOOP_MODE -> resourceHelper.gs(R.string.uel_lgs_loop_mode)
                UserEntry.Action.OPEN_LOOP_MODE -> resourceHelper.gs(R.string.uel_open_loop_mode)
                UserEntry.Action.LOOP_DISABLED -> resourceHelper.gs(R.string.uel_loop_disabled)
                UserEntry.Action.LOOP_ENABLED -> resourceHelper.gs(R.string.uel_loop_enabled)
                UserEntry.Action.RECONNECT -> resourceHelper.gs(R.string.uel_reconnect)
                UserEntry.Action.DISCONNECT_15M -> resourceHelper.gs(R.string.uel_disconnect_15m)
                UserEntry.Action.DISCONNECT_30M -> resourceHelper.gs(R.string.uel_disconnect_30m)
                UserEntry.Action.DISCONNECT_1H -> resourceHelper.gs(R.string.uel_disconnect_1h)
                UserEntry.Action.DISCONNECT_2H -> resourceHelper.gs(R.string.uel_disconnect_2h)
                UserEntry.Action.DISCONNECT_3H -> resourceHelper.gs(R.string.uel_disconnect_3h)
                UserEntry.Action.RESUME -> resourceHelper.gs(R.string.uel_resume)
                UserEntry.Action.SUSPEND_1H -> resourceHelper.gs(R.string.uel_suspend_1h)
                UserEntry.Action.SUSPEND_2H -> resourceHelper.gs(R.string.uel_suspend_2h)
                UserEntry.Action.SUSPEND_3H -> resourceHelper.gs(R.string.uel_suspend_3h)
                UserEntry.Action.SUSPEND_10H -> resourceHelper.gs(R.string.uel_suspend_10h)
                UserEntry.Action.HW_PUMP_ALLOWED -> resourceHelper.gs(R.string.uel_hw_pump_allowed)
                UserEntry.Action.CLEAR_PAIRING_KEYS -> resourceHelper.gs(R.string.uel_clear_pairing_keys)
                UserEntry.Action.ACCEPTS_TEMP_BASAL -> resourceHelper.gs(R.string.uel_accepts_temp_basal)
                UserEntry.Action.CANCEL_TEMP_BASAL -> resourceHelper.gs(R.string.uel_cancel_temp_basal)
                UserEntry.Action.CANCEL_EXTENDED_BOLUS -> resourceHelper.gs(R.string.uel_cancel_extended_bolus)

                UserEntry.Action.CAREPORTAL -> resourceHelper.gs(R.string.uel_careportal)
                UserEntry.Action.CALIBRATION -> resourceHelper.gs(R.string.uel_calibration)
                UserEntry.Action.INSULIN_CHANGE -> resourceHelper.gs(R.string.uel_insulin_change)
                UserEntry.Action.PRIME_BOLUS -> resourceHelper.gs(R.string.uel_prime_bolus)
                UserEntry.Action.SITE_CHANGE -> resourceHelper.gs(R.string.uel_site_change)
                UserEntry.Action.TREATMENT -> resourceHelper.gs(R.string.uel_treatment)
                UserEntry.Action.CAREPORTAL_NS_REFRESH -> resourceHelper.gs(R.string.uel_careportal_ns_refresh)
                UserEntry.Action.PROFILE_SWITCH_NS_REFRESH -> resourceHelper.gs(R.string.uel_profile_switch_ns_refresh)
                UserEntry.Action.TREATMENTS_NS_REFRESH -> resourceHelper.gs(R.string.uel_treat_ns_refresh)
                UserEntry.Action.TT_NS_REFRESH -> resourceHelper.gs(R.string.uel_tt_ns_refresh)
                UserEntry.Action.AUTOMATION_REMOVED -> resourceHelper.gs(R.string.uel_autom_removed)
                UserEntry.Action.BG_REMOVED -> resourceHelper.gs(R.string.uel_bg_removed)
                UserEntry.Action.CAREPORTAL_REMOVED -> resourceHelper.gs(R.string.uel_removed_carep)
                UserEntry.Action.EXTENDED_BOLUS_REMOVED -> resourceHelper.gs(R.string.uel_removed_eb)
                UserEntry.Action.FOOD_REMOVED -> resourceHelper.gs(R.string.uel_food_removed)
                UserEntry.Action.PROFILE_REMOVED -> resourceHelper.gs(R.string.uel_remove_profile)
                UserEntry.Action.PROFILE_SWITCH_REMOVED -> resourceHelper.gs(R.string.uel_removed_profile_switch)
                UserEntry.Action.RESTART_EVENTS_REMOVED -> resourceHelper.gs(R.string.uel_removed_restart_events)
                UserEntry.Action.TREATMENT_REMOVED -> resourceHelper.gs(R.string.uel_removed_treatment)
                UserEntry.Action.TT_REMOVED -> resourceHelper.gs(R.string.uel_removed_tt)
                UserEntry.Action.NS_PAUSED -> resourceHelper.gs(R.string.uel_ns_paused)
                UserEntry.Action.NS_QUEUE_CLEARED -> resourceHelper.gs(R.string.uel_ns_queue_cleared)
                UserEntry.Action.NS_SETTINGS_COPIED -> resourceHelper.gs(R.string.uel_ns_settings_copied)
                UserEntry.Action.ERROR_DIALOG_OK -> resourceHelper.gs(R.string.uel_error_dialog_ok)
                UserEntry.Action.ERROR_DIALOG_MUTE  -> resourceHelper.gs(R.string.uel_error_dialog_mute)
                UserEntry.Action.ERROR_DIALOG_MUTE_5MIN -> resourceHelper.gs(R.string.uel_error_dialog_mute_5min)
                UserEntry.Action.OBJECTIVE_UNSTARTED -> resourceHelper.gs(R.string.uel_objective_unstarted)
                UserEntry.Action.OBJECTIVES_SKIPPED -> resourceHelper.gs(R.string.uel_objectives_skipped)
                UserEntry.Action.STAT_RESET -> resourceHelper.gs(R.string.uel_stat_reset)
                UserEntry.Action.DELETE_LOGS -> resourceHelper.gs(R.string.uel_delete_logs)
                UserEntry.Action.DELETE_FUTURE_TREATMENTS -> resourceHelper.gs(R.string.uel_delete_future_treatments)
                UserEntry.Action.EXPORT_SETTINGS -> resourceHelper.gs(R.string.uel_export_settings)
                UserEntry.Action.IMPORT_SETTINGS -> resourceHelper.gs(R.string.uel_import_settings)
                UserEntry.Action.RESET_DATABASES -> resourceHelper.gs(R.string.uel_reset_databases)

                UserEntry.Action.OTP_EXPORT -> resourceHelper.gs(R.string.uel_otp_export)
                UserEntry.Action.OTP_RESET -> resourceHelper.gs(R.string.uel_otp_reset)
                UserEntry.Action.SMS_BASAL -> resourceHelper.gs(R.string.uel_sms_basal)
                UserEntry.Action.SMS_BOLUS -> resourceHelper.gs(R.string.uel_sms_bolus)
                UserEntry.Action.SMS_CAL -> resourceHelper.gs(R.string.uel_sms_cal)
                UserEntry.Action.SMS_CARBS -> resourceHelper.gs(R.string.uel_sms_carbs)
                UserEntry.Action.SMS_EXTENDED_BOLUS -> resourceHelper.gs(R.string.uel_sms_extended)
                UserEntry.Action.SMS_LOOP_DISABLED -> resourceHelper.gs(R.string.uel_sms_loop_disable)
                UserEntry.Action.SMS_LOOP_ENABLED -> resourceHelper.gs(R.string.uel_sms_loop_enable)
                UserEntry.Action.SMS_LOOP_RESUME -> resourceHelper.gs(R.string.uel_sms_loop_resume)
                UserEntry.Action.SMS_LOOP_SUSPEND -> resourceHelper.gs(R.string.uel_sms_loop_suspend)
                UserEntry.Action.SMS_PROFILE -> resourceHelper.gs(R.string.uel_sms_profile)
                UserEntry.Action.SMS_PUMP_CONNECT -> resourceHelper.gs(R.string.uel_sms_pump_connect)
                UserEntry.Action.SMS_PUMP_DISCONNECT -> resourceHelper.gs(R.string.uel_sms_pump_disconnect)
                UserEntry.Action.SMS_SMS -> resourceHelper.gs(R.string.uel_sms_sms)
                UserEntry.Action.SMS_TT -> resourceHelper.gs(R.string.uel_sms_target)

                else -> "To be defined"

            }
        }
    }
}