package info.nightscout.androidaps.activities

import android.os.Bundle
import android.widget.ArrayAdapter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.defaultProfile.DefaultProfile
import info.nightscout.androidaps.databinding.ActivitySurveyBinding
import info.nightscout.androidaps.dialogs.ProfileViewerDialog
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.utils.ActivityMonitor
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.InstanceId
import info.nightscout.androidaps.utils.SafeParse
import info.nightscout.androidaps.utils.ToastUtils
import info.nightscout.androidaps.utils.stats.TddCalculator
import info.nightscout.androidaps.utils.stats.TirCalculator
import javax.inject.Inject

class SurveyActivity : NoSplashAppCompatActivity() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var activePlugin: ActivePluginProvider
    @Inject lateinit var tddCalculator: TddCalculator
    @Inject lateinit var tirCalculator: TirCalculator
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var activityMonitor: ActivityMonitor
    @Inject lateinit var defaultProfile: DefaultProfile

    private lateinit var binding: ActivitySurveyBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySurveyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.id.text = InstanceId.instanceId()

        val profileStore = activePlugin.activeProfileInterface.profile
        val profileList = profileStore?.getProfileList() ?: return
        binding.spinner.adapter = ArrayAdapter(this, R.layout.spinner_centered, profileList)

        binding.tdds.text = tddCalculator.stats()
        binding.tir.text = tirCalculator.stats()
        binding.activity.text = activityMonitor.stats()

        binding.profile.setOnClickListener {
            val age = SafeParse.stringToDouble(binding.age.text.toString())
            val weight = SafeParse.stringToDouble(binding.weight.text.toString())
            val tdd = SafeParse.stringToDouble(binding.tdd.text.toString())
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
                defaultProfile.profile(age, tdd, weight, profileFunction.getUnits())?.let { profile ->
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

        binding.submit.setOnClickListener {
            val r = FirebaseRecord()
            r.id = InstanceId.instanceId()
            r.age = SafeParse.stringToInt(binding.age.text.toString())
            r.weight = SafeParse.stringToInt(binding.weight.text.toString())
            if (r.age < 1 || r.age > 120) {
                ToastUtils.showToastInUiThread(this, R.string.invalidage)
                return@setOnClickListener
            }
            if (r.weight < 5 || r.weight > 150) {
                ToastUtils.showToastInUiThread(this, R.string.invalidweight)
                return@setOnClickListener
            }

            if (binding.spinner.selectedItem == null)
                return@setOnClickListener
            val profileName = binding.spinner.selectedItem.toString()
            val specificProfile = profileStore.getSpecificProfile(profileName)

            r.profileJson = specificProfile.toString()

            val auth = FirebaseAuth.getInstance()
            auth.signInAnonymously()
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        aapsLogger.debug(LTag.CORE, "signInAnonymously:success")
                        //val user = auth.currentUser // TODO: do we need this, seems unused?

                        val database = FirebaseDatabase.getInstance().reference
                        database.child("survey").child(r.id).setValue(r)
                    } else {
                        aapsLogger.error("signInAnonymously:failure", task.exception!!)
                        ToastUtils.showToastInUiThread(this, "Authentication failed.")
                        //updateUI(null)
                    }

                    // ...
                }
            finish()
        }
    }

    inner class FirebaseRecord {

        var id = ""
        var age: Int = 0
        var weight: Int = 0
        var profileJson = "ghfg"
    }

}
