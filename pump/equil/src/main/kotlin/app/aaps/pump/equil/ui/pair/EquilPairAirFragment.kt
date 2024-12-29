package app.aaps.pump.equil.ui.pair

import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.Button
import androidx.navigation.fragment.findNavController
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.ui.extensions.runOnUiThread
import app.aaps.pump.equil.EquilConst
import app.aaps.pump.equil.R
import app.aaps.pump.equil.driver.definition.BasalSchedule
import app.aaps.pump.equil.keys.EquilIntKey
import app.aaps.pump.equil.manager.command.CmdAlarmSet
import app.aaps.pump.equil.manager.command.CmdBasalSet
import app.aaps.pump.equil.manager.command.CmdDevicesGet
import app.aaps.pump.equil.manager.command.CmdStepSet
import app.aaps.pump.equil.manager.command.CmdTimeSet
import javax.inject.Inject

// IMPORTANT: This activity needs to be called from RileyLinkSelectPreference (see pref_medtronic.xml as example)
class EquilPairAirFragment : EquilPairFragmentBase() {

    @Inject lateinit var profileFunction: ProfileFunction

    override fun getLayoutId(): Int {
        return R.layout.equil_pair_air_fragment
    }

    override fun getNextPageActionId(): Int {
        return R.id.action_startEquilActivationFragment_to_startEquilPairConfirmFragment
    }

    override fun getIndex(): Int {
        if ((activity as? EquilPairActivity)?.pair == false) {
            return 4
        }
        return 5
    }

    private lateinit var buttonNext: Button
    private lateinit var lytAction: View

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        buttonNext = view.findViewById(R.id.button_next)
        lytAction = view.findViewById(R.id.lyt_action)
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
                    if (equilManager.equilState?.basalSchedule == null) {
                        setProfile()
                    } else {
                        setTime()
                    }
                }
            }
        }
    }

    private fun setStep() {
        commandQueue.customCommand(CmdStepSet(false, EquilConst.EQUIL_STEP_AIR, aapsLogger, sp, equilManager), object : Callback() {
            override fun run() {
                if (activity == null) return
                aapsLogger.debug(LTag.PUMPCOMM, "result====" + result.success)
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
        commandQueue.customCommand(CmdTimeSet(aapsLogger, sp, equilManager), object : Callback() {
            override fun run() {
                if (activity == null) return
                if (result.success) {
                    SystemClock.sleep(EquilConst.EQUIL_BLE_NEXT_CMD)
                    dismissLoading()
                    readFM()
                } else {
                    dismissLoading()
                    equilPumpPlugin.showToast(rh.gs(R.string.equil_error))
                }
            }
        })
    }

    private fun setAlarmMode() {
        showLoading()
        val mode = preferences.get(EquilIntKey.EquilTone)
        commandQueue.customCommand(CmdAlarmSet(mode, aapsLogger, sp, equilManager), object : Callback() {
            override fun run() {
                if (activity == null) return
                if (result.success) {
                    SystemClock.sleep(EquilConst.EQUIL_BLE_NEXT_CMD)
                    dismissLoading()
                    setProfile()
                } else {
                    dismissLoading()
                    equilPumpPlugin.showToast(rh.gs(R.string.equil_error))
                }
            }
        })
    }

    private fun readFM() {
        commandQueue.customCommand(CmdDevicesGet(aapsLogger, sp, equilManager), object : Callback() {
            override fun run() {
                if (activity == null) return
                aapsLogger.debug(LTag.PUMPCOMM, "CmdGetDevices result====" + result.success)
                if (result.success) {
                    dismissLoading()
                    runOnUiThread {
                        // binding.navButtonsLayout.buttonNext.performClick()
                        val nextPage = getNextPageActionId()
                        findNavController().navigate(nextPage)
                    }
                } else {
                    dismissLoading()
                    equilPumpPlugin.showToast(rh.gs(R.string.equil_error))
                }
            }
        })
    }

    private fun setProfile() {
        val profile = profileFunction.getProfile()
        if (profile == null) {
            setTime()
            return
        }
        val basalSchedule = BasalSchedule.mapProfileToBasalSchedule(profile)
        if (basalSchedule.getEntries().size < 24) {
            setTime()
            return
        }
        showLoading()
        commandQueue.customCommand(CmdBasalSet(basalSchedule, profile, aapsLogger, sp, equilManager), object : Callback() {
            override fun run() {
                if (activity == null) return
                aapsLogger.debug(LTag.PUMPCOMM, "CmdTimeSet result====" + result.success)
                if (result.success) {
                    equilManager.setBasalSchedule(basalSchedule)
                    SystemClock.sleep(EquilConst.EQUIL_BLE_NEXT_CMD)
                    dismissLoading()
                    setTime()
                } else {
                    dismissLoading()
                    equilPumpPlugin.showToast(rh.gs(R.string.equil_error))
                }

            }
        })
    }
}
