package info.nightscout.androidaps.activities

import android.os.Bundle
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.defaultProfile.DefaultProfile
import info.nightscout.androidaps.dialogs.ProfileViewerDialog
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.ToastUtils
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.stats.TddCalculator
import kotlinx.android.synthetic.main.activity_profilehelper.*
import java.text.DecimalFormat
import javax.inject.Inject

class ProfileHelperActivity : NoSplashAppCompatActivity() {
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var tddCalculator: TddCalculator
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var defaultProfile: DefaultProfile

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profilehelper)

        profilehelper_age.setParams(15.0, 1.0, 80.0, 1.0, DecimalFormat("0"), false, null)
        profilehelper_weight.setParams(50.0, 5.0, 150.0, 1.0, DecimalFormat("0"), false, null)
        profilehelper_tdd.setParams(50.0, 3.0, 200.0, 1.0, DecimalFormat("0"), false, null)

        profilehelper_tdds.text = tddCalculator.stats()

        profilehelper_profile.setOnClickListener {
            val age = profilehelper_age.value
            val weight = profilehelper_weight.value
            val tdd = profilehelper_tdd.value
            if (age < 1 || age > 120) {
                ToastUtils.showToastInUiThread(this, R.string.invalidage)
                return@setOnClickListener
            }
            if ((weight < 5 || weight > 150) && tdd == 0.0) {
                ToastUtils.showToastInUiThread(this, R.string.invalidweight)
                return@setOnClickListener
            }
            if ((tdd < 5 || tdd > 150) && weight == 0.0) {
                ToastUtils.showToastInUiThread(this, R.string.invalidweight)
                return@setOnClickListener
            }
            profileFunction.getProfile()?.let { runningProfile ->
                val profile = defaultProfile.profile(age, tdd, weight, profileFunction.getUnits())
                ProfileViewerDialog().also { pvd ->
                    pvd.arguments = Bundle().also {
                        it.putLong("time", DateUtil.now())
                        it.putInt("mode", ProfileViewerDialog.Mode.PROFILE_COMPARE.ordinal)
                        it.putString("customProfile", runningProfile.data.toString())
                        it.putString("customProfile2", profile.data.toString())
                        it.putString("customProfileUnits", profile.units)
                        it.putString("customProfileName", "Age: $age TDD: $tdd Weight: $weight")
                    }
                }.show(supportFragmentManager, "ProfileViewDialog")
            }
        }

    }
}
