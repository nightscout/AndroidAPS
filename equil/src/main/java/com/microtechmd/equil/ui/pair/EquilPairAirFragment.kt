package com.microtechmd.equil.ui.pair

import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.Button
import androidx.navigation.fragment.findNavController
import com.microtechmd.equil.EquilConst
import com.microtechmd.equil.R
import com.microtechmd.equil.data.AlarmMode
import com.microtechmd.equil.driver.definition.BasalSchedule
import com.microtechmd.equil.manager.command.CmdAlarmSet
import com.microtechmd.equil.manager.command.CmdBasalSet
import com.microtechmd.equil.manager.command.CmdDevicesGet
import com.microtechmd.equil.manager.command.CmdStepSet
import com.microtechmd.equil.manager.command.CmdTimeSet
import info.nightscout.androidaps.extensions.runOnUiThread
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.queue.Callback
import info.nightscout.shared.logging.LTag
import javax.inject.Inject

// IMPORTANT: This activity needs to be called from RileyLinkSelectPreference (see pref_medtronic.xml as example)
class EquilPairAirFragment : EquilPairFragmentBase() {

    @Inject lateinit var profileFunction: ProfileFunction

    override fun getLayoutId(): Int {
        return R.layout.equil_pair_air_fragment
    }

    override fun getNextPageActionId(): Int? {
        return R.id.action_startEquilActivationFragment_to_startEquilPairConfirmFragment;
    }

    override fun getIndex(): Int {
        if ((activity as? EquilPairActivity)?.pair == false) {
            return 4
        }
        return 5
    }

    lateinit var buttonNext: Button
    lateinit var lytAction: View

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        buttonNext = view.findViewById<Button>(R.id.button_next);
        lytAction = view.findViewById<View>(R.id.lyt_action);
        buttonNext.alpha = 0.3f
        buttonNext.isClickable = false
        view.findViewById<Button>(R.id.button_air).setOnClickListener {
            context?.let {
                showLoading()
                setStep()
            }
        }
        view.findViewById<Button>(R.id.button_finish).setOnClickListener {
            context?.let {
                showLoading()
                if ((activity as? EquilPairActivity)?.pair == true) {
                    setAlarmMode()
                } else {
                    if (equilPumpPlugin.equilManager.basalSchedule == null) {
                        setProfile()
                    } else {
                        setTime()
                    }
                }
            }
        }
    }

    private fun setStep() {
        commandQueue.customCommand(CmdStepSet(), object : Callback() {
            override fun run() {
                if (activity == null) return
                aapsLogger.error(LTag.EQUILBLE, "result====" + result.success)
                if (result.success) {
                    dismissLoading()
                } else {
                    dismissLoading()
                    equilPumpPlugin.showToast(rh.gs(R.string.equil_error))
                }
            }
        })
    }

    private fun setTime() {
        showLoading()
        commandQueue.customCommand(CmdTimeSet(), object : Callback() {
            override fun run() {
                if (activity == null) return
                if (result.success) {
                    SystemClock.sleep(EquilConst.EQUIL_BLE_NEXT_CMD)
                    dismissLoading()
                    readFM()
                } else {
                    dismissLoading();
                    equilPumpPlugin.showToast(rh.gs(R.string.equil_error))
                }
            }
        })
    }

    private fun setAlarmMode() {
        showLoading()
        commandQueue.customCommand(CmdAlarmSet(AlarmMode.TONE_AND_SHAKE.command), object : Callback() {
            override fun run() {
                if (activity == null) return
                if (result.success) {
                    SystemClock.sleep(EquilConst.EQUIL_BLE_NEXT_CMD)
                    dismissLoading()
                    setProfile()
                } else {
                    dismissLoading();
                    equilPumpPlugin.showToast(rh.gs(R.string.equil_error))
                }
            }
        })
    }

    private fun readFM() {
        commandQueue.customCommand(CmdDevicesGet(), object : Callback() {
            override fun run() {
                if (activity == null) return
                aapsLogger.error(LTag.EQUILBLE, "CmdGetDevices result====" + result.success)
                if (result.success) {
                    equilPumpPlugin.equilManager.closeBle()
                    SystemClock.sleep(EquilConst.EQUIL_BLE_NEXT_CMD)
                    dismissLoading();
                    runOnUiThread {
                        // binding.navButtonsLayout.buttonNext.performClick()
                        val nextPage = getNextPageActionId()
                        if (nextPage != null) {
                            findNavController().navigate(nextPage)
                        }
                    }
                } else {
                    dismissLoading();
                    equilPumpPlugin.showToast(rh.gs(R.string.equil_error))
                }
            }
        })
    }

    private fun setProfile() {
        var profile = profileFunction.getProfile();
        if (profile == null) {
            setTime()
            return
        }
        val basalSchedule = BasalSchedule.mapProfileToBasalSchedule(profile)
        if (basalSchedule == null || basalSchedule.entries == null || basalSchedule.entries.size < 24) {
            setTime()
            return
        }
        showLoading()
        commandQueue.customCommand(CmdBasalSet(basalSchedule, profile), object : Callback() {
            override fun run() {
                if (activity == null) return
                aapsLogger.error(LTag.EQUILBLE, "CmdTimeSet result====" + result.success)
                if (result.success) {
                    SystemClock.sleep(EquilConst.EQUIL_BLE_NEXT_CMD)
                    dismissLoading()
                    setTime()
                } else {
                    dismissLoading();
                    equilPumpPlugin.showToast(rh.gs(R.string.equil_error))
                }

            }
        })
    }
}
