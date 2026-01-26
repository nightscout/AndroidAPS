package app.aaps.pump.equil.ui.pair

import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.Button
import androidx.navigation.fragment.findNavController
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.ui.extensions.runOnUiThread
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.pump.equil.EquilConst
import app.aaps.pump.equil.R
import app.aaps.pump.equil.driver.definition.BasalSchedule
import app.aaps.pump.equil.keys.EquilIntPreferenceKey
import app.aaps.pump.equil.manager.command.CmdAlarmSet
import app.aaps.pump.equil.manager.command.CmdBasalSet
import app.aaps.pump.equil.manager.command.CmdDevicesGet
import app.aaps.pump.equil.manager.command.CmdStepSet
import app.aaps.pump.equil.manager.command.CmdTimeSet

class EquilPairAirFragment : EquilPairFragmentBase() {

    private var buttonAir: Button? = null
    private var buttonFinish: Button? = null

    override fun getLayoutId(): Int = R.layout.equil_pair_air_fragment

    override fun getNextPageActionId(): Int = R.id.action_startEquilActivationFragment_to_startEquilPairConfirmFragment

    override fun getIndex(): Int = if ((activity as? EquilPairActivity)?.pair == false) 4 else 5

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        buttonAir = view.findViewById(R.id.button_air)
        buttonFinish = view.findViewById(R.id.button_finish)
        view.findViewById<Button>(R.id.button_next)?.let { buttonNext ->
            buttonNext.alpha = 0.3f
            buttonNext.isClickable = false
        }
        buttonAir?.setOnClickListener {
            context?.let {
                showLoading()
                setStep()
            }
        }
        buttonFinish?.setOnClickListener {
            context?.let {
                showLoading()
                if ((activity as? EquilPairActivity)?.pair == true) setAlarmMode()
                else if (equilManager.equilState?.basalSchedule == null) setProfile()
                else setTime()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        buttonAir?.setOnClickListener(null)
        buttonFinish?.setOnClickListener(null)
        buttonAir = null
        buttonFinish = null
    }

    private fun setStep() {
        commandQueue.customCommand(CmdStepSet(false, EquilConst.EQUIL_STEP_AIR, aapsLogger, preferences, equilManager), object : Callback() {
            override fun run() {
                if (activity == null) return
                aapsLogger.debug(LTag.PUMPCOMM, "result====" + result.success)
                dismissLoading()
                if (!result.success)
                    ToastUtils.errorToast(requireContext(), rh.gs(R.string.equil_error))
            }
        })
    }

    private fun setTime() {
        showLoading()
        commandQueue.customCommand(CmdTimeSet(aapsLogger, preferences, equilManager), object : Callback() {
            override fun run() {
                if (activity == null) return
                dismissLoading()
                if (result.success) {
                    SystemClock.sleep(EquilConst.EQUIL_BLE_NEXT_CMD)
                    readFM()
                } else ToastUtils.errorToast(requireContext(), rh.gs(R.string.equil_error))
            }
        })
    }

    private fun setAlarmMode() {
        showLoading()
        val mode = preferences.get(EquilIntPreferenceKey.EquilTone)
        commandQueue.customCommand(CmdAlarmSet(mode, aapsLogger, preferences, equilManager), object : Callback() {
            override fun run() {
                if (activity == null) return
                dismissLoading()
                if (result.success) {
                    SystemClock.sleep(EquilConst.EQUIL_BLE_NEXT_CMD)
                    setProfile()
                } else ToastUtils.errorToast(requireContext(), rh.gs(R.string.equil_error))
            }
        })
    }

    private fun readFM() {
        commandQueue.customCommand(CmdDevicesGet(aapsLogger, preferences, equilManager), object : Callback() {
            override fun run() {
                if (activity == null) return
                aapsLogger.debug(LTag.PUMPCOMM, "CmdGetDevices result====" + result.success)
                dismissLoading()
                if (result.success) {
                    runOnUiThread {
                        val nextPage = getNextPageActionId()
                        findNavController().navigate(nextPage)
                    }
                } else ToastUtils.errorToast(requireContext(), rh.gs(R.string.equil_error))
            }
        })
    }

    private fun setProfile() {
        val profile = pumpSync.expectedPumpState().profile
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
        commandQueue.customCommand(CmdBasalSet(basalSchedule, profile, aapsLogger, preferences, equilManager), object : Callback() {
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
                    ToastUtils.errorToast(requireContext(), rh.gs(R.string.equil_error))
                }

            }
        })
    }
}
