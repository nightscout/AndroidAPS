package info.nightscout.androidaps.plugins.pump.eopatch.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import dagger.android.support.DaggerAppCompatActivity
import info.nightscout.androidaps.plugins.pump.eopatch.R
import info.nightscout.androidaps.plugins.pump.eopatch.code.EventType
import info.nightscout.androidaps.plugins.pump.eopatch.code.PatchStep
import info.nightscout.androidaps.plugins.pump.eopatch.core.code.BolusType
import info.nightscout.androidaps.plugins.pump.eopatch.databinding.FragmentEopatchOverviewBinding
import info.nightscout.androidaps.plugins.pump.eopatch.extension.takeOne
import info.nightscout.androidaps.plugins.pump.eopatch.ui.viewmodel.EopatchOverviewViewModel
import app.aaps.core.ui.toast.ToastUtils
import io.reactivex.rxjava3.disposables.CompositeDisposable
import javax.inject.Inject

class EopatchOverviewFragment : EoBaseFragment<FragmentEopatchOverviewBinding>() {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var aapsLogger: AAPSLogger
    private lateinit var resultLauncherForResume: ActivityResultLauncher<Intent>
    private lateinit var resultLauncherForPause: ActivityResultLauncher<Intent>

    private var disposable: CompositeDisposable = CompositeDisposable()
    private var pauseDuration = 0.5f

    override fun getLayoutId(): Int = R.layout.fragment_eopatch_overview

    override fun onDestroy() {
        super.onDestroy()
        disposable.clear()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.apply {
            viewmodel = ViewModelProvider(this@EopatchOverviewFragment, viewModelFactory).get(EopatchOverviewViewModel::class.java)
            viewmodel?.apply {
                eventHandler.observe(viewLifecycleOwner) { evt ->
                    when (evt.peekContent()) {
                        EventType.ACTIVATION_CLICKED   -> requireContext().apply { startActivity(EopatchActivity.createIntentFromMenu(this, PatchStep.WAKE_UP)) }
                        EventType.DEACTIVATION_CLICKED -> requireContext().apply { startActivity(EopatchActivity.createIntentForChangePatch(this)) }
                        EventType.SUSPEND_CLICKED      -> suspend()
                        EventType.RESUME_CLICKED       -> resume()
                        EventType.INVALID_BASAL_RATE   -> ToastUtils.infoToast(requireContext(), R.string.invalid_basal_rate)
                        EventType.PROFILE_NOT_SET      -> ToastUtils.infoToast(requireContext(), R.string.no_profile_selected)
                        EventType.PAUSE_BASAL_SUCCESS  -> ToastUtils.infoToast(requireContext(), R.string.string_suspended_insulin_delivery_message)
                        EventType.PAUSE_BASAL_FAILED   -> ToastUtils.errorToast(requireContext(), R.string.string_pause_failed)
                        EventType.RESUME_BASAL_SUCCESS -> ToastUtils.infoToast(requireContext(), R.string.string_resumed_insulin_delivery_message)
                        EventType.RESUME_BASAL_FAILED  -> ToastUtils.errorToast(requireContext(), R.string.string_resume_failed)
                        else                           -> Unit
                    }
                }

                resultLauncherForResume = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                    when (it.resultCode) {
                        DaggerAppCompatActivity.RESULT_OK       -> resumeBasal()
                        DaggerAppCompatActivity.RESULT_CANCELED -> ToastUtils.errorToast(requireContext(), R.string.string_resume_failed)
                    }
                }

                resultLauncherForPause = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                    when (it.resultCode) {
                        DaggerAppCompatActivity.RESULT_OK       -> {
                            pauseBasal(pauseDuration)
                            pauseDuration = 0.5f
                        }

                        DaggerAppCompatActivity.RESULT_CANCELED -> ToastUtils.errorToast(requireContext(), R.string.string_pause_failed)
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        binding.viewmodel?.stopBasalRateUpdate()
    }

    override fun onResume() {
        super.onResume()
        binding.viewmodel?.startBasalRateUpdate()
    }

    private fun suspend() {
        binding.viewmodel?.apply {
            activity?.let {
                val builder = app.aaps.core.ui.dialogs.AlertDialogHelper.Builder(it)
                val msg = getSuspendDialogText()

                val dialog = builder.setTitle(R.string.string_suspend)
                    .setMessage(msg)
                    .setPositiveButton(R.string.confirm) { _, _ ->
                        openPauseTimePicker()
                    }
                    .setNegativeButton(R.string.cancel) { _, _ ->

                    }.create()
                dialog.show()
            }
        }
    }

    private fun resume() {
        binding.viewmodel?.apply {
            activity?.let {
                val builder = app.aaps.core.ui.dialogs.AlertDialogHelper.Builder(it)
                val dialog = builder.setTitle(R.string.string_resume_insulin_delivery_title)
                    .setMessage(R.string.string_resume_insulin_delivery_message)
                    .setPositiveButton(R.string.confirm) { _, _ ->
                        if (isPatchConnected) {
                            resumeBasal()
                        } else {
                            resultLauncherForResume.launch(EopatchActivity.createIntentForCheckConnection(requireContext(), true))
                        }
                    }
                    .setNegativeButton(R.string.cancel) { _, _ ->

                    }.create()
                dialog.show()
            }
        }
    }

    private fun openPauseTimePicker() {
        binding.viewmodel?.apply {
            activity?.let {
                val builder = app.aaps.core.ui.dialogs.AlertDialogHelper.Builder(it)
                val listArr = requireContext().resources.getStringArray(R.array.suspend_duration_array)
                var select = 0
                val dialog = builder.setTitle(R.string.string_suspend_time_insulin_delivery_title)
                    .setSingleChoiceItems(listArr, 0) { _, which ->
                        select = which
                    }
                    .setPositiveButton(R.string.confirm) { _, _ ->
                        if (isPatchConnected) {
                            pauseBasal((select + 1) * 0.5f)
                        } else {
                            pauseDuration = (select + 1) * 0.5f
                            resultLauncherForPause.launch(EopatchActivity.createIntentForCheckConnection(requireContext(), true))
                        }
                    }
                    .setNegativeButton(R.string.cancel) { _, _ ->

                    }.create()
                dialog.show()
            }
        }
    }

    private fun getSuspendDialogText(): String {
        binding.viewmodel?.apply {
            val isBolusActive = patchManager.patchState.isBolusActive
            val isTempBasalActive = patchManager.patchState.isTempBasalActive
            val tempRate = patchManager.preferenceManager.getTempBasalManager().startedBasal?.doseUnitText ?: ""
            val tempRemainTime = patchManager.preferenceManager.getTempBasalManager().startedBasal?.remainTimeText ?: ""
            var remainBolus = patchManager.patchState.isNowBolusActive.takeOne(patchManager.bolusCurrent.remain(BolusType.NOW), 0f)
            remainBolus += patchManager.patchState.isExtBolusActive.takeOne(patchManager.bolusCurrent.remain(BolusType.EXT), 0f)

            val sbMsg = StringBuilder()

            if (isBolusActive && isTempBasalActive) {
                sbMsg.append(getString(R.string.insulin_suspend_msg1, tempRate, tempRemainTime, remainBolus))
            } else if (isBolusActive) {
                sbMsg.append(getString(R.string.insulin_suspend_msg2, remainBolus))
            } else if (isTempBasalActive) {
                sbMsg.append(getString(R.string.insulin_suspend_msg3, tempRate, tempRemainTime))
            } else {
                sbMsg.append(getString(R.string.insulin_suspend_msg4))
            }
            return sbMsg.toString()
        }
        return ""
    }
}