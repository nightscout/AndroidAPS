package info.nightscout.androidaps.danars.activities

import android.os.Bundle
import android.util.Base64
import info.nightscout.androidaps.activities.NoSplashAppCompatActivity
import info.nightscout.androidaps.danars.DanaRSPlugin
import info.nightscout.androidaps.danars.R
import info.nightscout.androidaps.danars.databinding.DanarsEnterPinActivityBinding
import info.nightscout.androidaps.danars.services.BLEComm
import info.nightscout.androidaps.events.EventPumpStatusChanged
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.extensions.hexStringToByteArray
import info.nightscout.androidaps.utils.sharedPreferences.SP
import info.nightscout.androidaps.utils.textValidator.DefaultEditTextValidator
import info.nightscout.androidaps.utils.textValidator.EditTextValidator
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import javax.inject.Inject
import kotlin.experimental.xor

class EnterPinActivity : NoSplashAppCompatActivity() {

    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var danaRSPlugin: DanaRSPlugin
    @Inject lateinit var sp: SP
    @Inject lateinit var bleComm: BLEComm

    private val disposable = CompositeDisposable()

    private lateinit var binding: DanarsEnterPinActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DanarsEnterPinActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val p1 = DefaultEditTextValidator(binding.rsV3Pin1, this)
            .setTestErrorString(resourceHelper.gs(R.string.error_mustbe12hexadidits), this)
            .setCustomRegexp(resourceHelper.gs(R.string.twelvehexanumber), this)
            .setTestType(EditTextValidator.TEST_REGEXP, this)
        val p2 = DefaultEditTextValidator(binding.rsV3Pin2, this)
            .setTestErrorString(resourceHelper.gs(R.string.error_mustbe8hexadidits), this)
            .setCustomRegexp(resourceHelper.gs(R.string.eighthexanumber), this)
            .setTestType(EditTextValidator.TEST_REGEXP, this)

        binding.okcancel.ok.setOnClickListener {
            if (p1.testValidity(false) && p2.testValidity(false)) {
                val result = checkPairingCheckSum(
                    binding.rsV3Pin1.text.toString().hexStringToByteArray(),
                    binding.rsV3Pin2.text.toString().substring(0..5).hexStringToByteArray(),
                    binding.rsV3Pin2.text.toString().substring(6..7).hexStringToByteArray())
                if (result) {
                    bleComm.finishV3Pairing()
                    finish()
                }
                else OKDialog.show(this, resourceHelper.gs(R.string.error), resourceHelper.gs(R.string.invalidinput))
            }
        }
        binding.okcancel.cancel.setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        disposable.add(rxBus
            .toObservable(EventPumpStatusChanged::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ if (it.status == EventPumpStatusChanged.Status.DISCONNECTED) finish() }) { fabricPrivacy.logException(it) }
        )
    }

    override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    private fun checkPairingCheckSum(pairingKey: ByteArray, randomPairingKey: ByteArray, checksum: ByteArray): Boolean {

        // pairingKey ByteArray(6)
        // randomPairingKey ByteArray(3)
        // checksum ByteArray(1)

        var pairingKeyCheckSum: Byte = 0
        for (i in pairingKey.indices)
            pairingKeyCheckSum = pairingKeyCheckSum xor pairingKey[i]

        sp.putString(resourceHelper.gs(R.string.key_danars_v3_pairingkey) + danaRSPlugin.mDeviceName, Base64.encodeToString(pairingKey, Base64.DEFAULT))

        for (i in randomPairingKey.indices)
            pairingKeyCheckSum = pairingKeyCheckSum xor randomPairingKey[i]

        sp.putString(resourceHelper.gs(R.string.key_danars_v3_randompairingkey) + danaRSPlugin.mDeviceName, Base64.encodeToString(randomPairingKey, Base64.DEFAULT))

        return checksum[0] == pairingKeyCheckSum
    }

}
