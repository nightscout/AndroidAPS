package info.nightscout.androidaps.plugins.pump.eopatch.ui

import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import dagger.android.support.DaggerAppCompatActivity
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.pump.eopatch.R
import info.nightscout.androidaps.plugins.pump.eopatch.EoPatchRxBus
import info.nightscout.androidaps.plugins.pump.eopatch.core.code.BolusType
import info.nightscout.androidaps.plugins.pump.eopatch.code.PatchStep
import info.nightscout.androidaps.plugins.pump.eopatch.code.EventType
import info.nightscout.androidaps.plugins.pump.eopatch.databinding.FragmentEopatchOverviewBinding
import info.nightscout.androidaps.plugins.pump.eopatch.extension.fillExtras
import info.nightscout.androidaps.plugins.pump.eopatch.extension.observeOnMainThread
import info.nightscout.androidaps.plugins.pump.eopatch.extension.subscribeDefault
import info.nightscout.androidaps.plugins.pump.eopatch.extension.takeOne
import info.nightscout.androidaps.plugins.pump.eopatch.ui.viewmodel.EopatchOverviewViewModel
import info.nightscout.androidaps.plugins.pump.eopatch.vo.ActivityResultEvent
import io.reactivex.disposables.CompositeDisposable
import javax.inject.Inject

class EopatchOverviewFragment: EoBaseFragment<FragmentEopatchOverviewBinding>() {
    @Inject lateinit var rxBus: RxBus

    private var disposable: CompositeDisposable = CompositeDisposable()

    private var mProgressDialog: ProgressDialog? = null

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
                UIEventTypeHandler.observe(viewLifecycleOwner, Observer { evt ->
                    when(evt.peekContent()){
                        EventType.ACTIVTION_CLICKED   -> requireContext().let { startActivity(EopatchActivity.createIntentFromMenu(it, PatchStep.WAKE_UP)) }
                        EventType.DEACTIVTION_CLICKED -> requireContext().let { startActivity(EopatchActivity.createIntentForChangePatch(it)) }
                        EventType.SUSPEND_CLICKED     -> suspend()
                        EventType.RESUME_CLICKED      -> resume()
                        EventType.INVALID_BASAL_RATE  -> Toast.makeText(activity, R.string.unsupported_basal_rate, Toast.LENGTH_SHORT).show()
                        EventType.PROFILE_NOT_SET     -> Toast.makeText(activity, R.string.no_profile_selected, Toast.LENGTH_SHORT).show()
                        else                          -> Unit
                    }
                })
            }
        }


    }

    private fun suspend() {
        binding.viewmodel?.apply {
            activity?.let {
                val builder = AlertDialog.Builder(it)
                val msg = getSuspendDialogText()

                val dialog = builder.setTitle(R.string.string_suspend)
                    .setMessage(msg)
                    .setPositiveButton(R.string.confirm, DialogInterface.OnClickListener { dialog, which ->
                        openPauseTimePicker()
                    })
                    .setNegativeButton(R.string.cancel, DialogInterface.OnClickListener { dialog, which ->

                    }).create()
                dialog.show()
            }
        }
    }

    private fun resume() {
        binding.viewmodel?.apply {
            activity?.let {
                val builder = AlertDialog.Builder(it)
                val dialog = builder.setTitle(R.string.string_resume_insulin_delivery_title)
                    .setMessage(R.string.string_resume_insulin_delivery_message)
                    .setPositiveButton(R.string.confirm, DialogInterface.OnClickListener { dialog, which ->
                        if(isPatchConnected) {
                            resumeBasal()
                        }else{
                            checkCommunication({
                                resumeBasal()
                            })
                        }
                    })
                    .setNegativeButton(R.string.cancel, DialogInterface.OnClickListener { dialog, which ->

                    }).create()
                dialog.show()
            }
        }
    }

    private fun openPauseTimePicker() {
        binding.viewmodel?.apply {
            activity?.let{
                val builder = AlertDialog.Builder(it)
                val listArr = requireContext().resources.getStringArray(R.array.suspend_duration_array)
                var select = 0
                val dialog = builder.setTitle(R.string.string_suspend_time_insulin_delivery_title)
                    .setSingleChoiceItems(listArr, 0, DialogInterface.OnClickListener { dialog, which ->
                        select = which
                    })
                    .setPositiveButton(R.string.confirm, DialogInterface.OnClickListener { dialog, which ->
                        if (isPatchConnected) {
                            pauseBasal((select + 1) * 0.5f)
                        } else {
                            checkCommunication({
                                pauseBasal((select + 1) * 0.5f)
                            })
                        }
                    })
                    .setNegativeButton(R.string.cancel, DialogInterface.OnClickListener { dialog, which ->

                    }).create()
                dialog.show()
            }
        }
    }

    private fun getSuspendDialogText(): String{
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

    override fun startActivityForResult(action: Context.() -> Intent, requestCode: Int, vararg params: Pair<String, Any?>) {
        val intent = action(requireContext())
        if(params.isNotEmpty()) intent.fillExtras(params)
        startActivityForResult(intent, requestCode)
    }

    override fun checkCommunication(onSuccess: () -> Unit, onCancel: (() -> Unit)?, onDiscard: (() -> Unit)?, goHomeAfterDiscard: Boolean) {
        EoPatchRxBus.listen(ActivityResultEvent::class.java)
            .doOnSubscribe { startActivityForResult({ EopatchActivity.createIntentForCheckConnection(this, goHomeAfterDiscard) }, 10001) }
            .observeOnMainThread()
            .subscribeDefault {
                if (it.requestCode == 10001) {
                    when (it.resultCode) {
                        DaggerAppCompatActivity.RESULT_OK       -> onSuccess.invoke()
                        DaggerAppCompatActivity.RESULT_CANCELED -> onCancel?.invoke()
                        EopatchActivity.RESULT_DISCARDED        -> onDiscard?.invoke()
                    }
                }
            }.addTo()
    }

}