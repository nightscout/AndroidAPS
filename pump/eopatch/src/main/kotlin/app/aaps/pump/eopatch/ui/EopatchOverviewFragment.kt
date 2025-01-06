package app.aaps.pump.eopatch.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.pump.eopatch.R
import app.aaps.pump.eopatch.code.EventType
import app.aaps.pump.eopatch.code.PatchStep
import app.aaps.pump.eopatch.core.code.BolusType
import app.aaps.pump.eopatch.databinding.FragmentEopatchOverviewBinding
import app.aaps.pump.eopatch.extension.takeOne
import app.aaps.pump.eopatch.ui.viewmodel.EopatchOverviewViewModel
import app.aaps.pump.eopatch.vo.TempBasalManager
import dagger.android.support.DaggerAppCompatActivity
import io.reactivex.rxjava3.disposables.CompositeDisposable
import javax.inject.Inject

class EopatchOverviewFragment : EoBaseFragment<FragmentEopatchOverviewBinding>() {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var tempBasalManager: TempBasalManager
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
            viewmodel = ViewModelProvider(this@EopatchOverviewFragment, viewModelFactory)[EopatchOverviewViewModel::class.java]
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
            val isBolusActive = preferenceManager.patchState.isBolusActive
            val isTempBasalActive = preferenceManager.patchState.isTempBasalActive
            val tempRate = tempBasalManager.startedBasal?.doseUnitText ?: ""
            val tempRemainTime = tempBasalManager.startedBasal?.remainTimeText ?: ""
            var remainBolus = preferenceManager.patchState.isNowBolusActive.takeOne(preferenceManager.bolusCurrent.remain(BolusType.NOW), 0f)
            remainBolus += preferenceManager.patchState.isExtBolusActive.takeOne(preferenceManager.bolusCurrent.remain(BolusType.EXT), 0f)

            val sbMsg = StringBuilder()

            when {
                isBolusActive && isTempBasalActive -> sbMsg.append(getString(R.string.insulin_suspend_msg1, tempRate, tempRemainTime, remainBolus))
                isBolusActive                      -> sbMsg.append(getString(R.string.insulin_suspend_msg2, remainBolus))
                isTempBasalActive                  -> sbMsg.append(getString(R.string.insulin_suspend_msg3, tempRate, tempRemainTime))
                else                               -> sbMsg.append(getString(R.string.insulin_suspend_msg4))
            }
            return sbMsg.toString()
        }
        return ""
    }
}