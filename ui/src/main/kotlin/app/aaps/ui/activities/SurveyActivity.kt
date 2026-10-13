package app.aaps.ui.activities

import android.os.Bundle
import android.widget.ArrayAdapter
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.SafeParse
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.core.utils.fabric.InstanceId
import app.aaps.ui.R
import app.aaps.ui.databinding.ActivitySurveyBinding
import app.aaps.ui.defaultProfile.DefaultProfile
import javax.inject.Inject

class SurveyActivity : TranslatedDaggerAppCompatActivity() {

    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var defaultProfile: DefaultProfile
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var uiInteraction: UiInteraction

    private lateinit var binding: ActivitySurveyBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySurveyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.id.text = InstanceId.instanceId

        val profileStore = activePlugin.activeProfileSource.profile
        val profileList = profileStore?.getProfileList() ?: return
        binding.spinner.adapter = ArrayAdapter(this, app.aaps.core.ui.R.layout.spinner_centered, profileList)

        binding.profile.setOnClickListener {
            val age = SafeParse.stringToInt(binding.age.text.toString())
            val weight = SafeParse.stringToDouble(binding.weight.text.toString())
            val tdd = SafeParse.stringToDouble(binding.tdd.text.toString())
            if (age < 1 || age > 120) {
                ToastUtils.warnToast(this, R.string.invalid_age)
                return@setOnClickListener
            }
            if ((weight < 5 || weight > 150) && tdd == 0.0) {
                ToastUtils.warnToast(this, R.string.invalid_weight)
                return@setOnClickListener
            }
            if ((tdd < 5 || tdd > 150) && weight == 0.0) {
                ToastUtils.warnToast(this, R.string.invalid_weight)
                return@setOnClickListener
            }
            profileFunction.getProfile()?.let { runningProfile ->
                defaultProfile.profile(age, tdd, weight, profileFunction.getUnits())?.let { profile ->
                    uiInteraction.runProfileViewerDialog(
                        fragmentManager = supportFragmentManager,
                        time = dateUtil.now(),
                        mode = UiInteraction.Mode.PROFILE_COMPARE,
                        customProfile = runningProfile.toPureNsJson(dateUtil).toString(),
                        customProfileName = "Age: $age TDD: $tdd Weight: $weight",
                        customProfile2 = profile.jsonObject.toString()
                    )
                }
            }
        }

        binding.submit.setOnClickListener {
            val r = FirebaseRecord()
            r.id = InstanceId.instanceId
            r.age = SafeParse.stringToInt(binding.age.text.toString())
            r.weight = SafeParse.stringToInt(binding.weight.text.toString())
            if (r.age < 1 || r.age > 120) {
                ToastUtils.warnToast(this, R.string.invalid_age)
                return@setOnClickListener
            }
            if (r.weight < 5 || r.weight > 150) {
                ToastUtils.warnToast(this, R.string.invalid_weight)
                return@setOnClickListener
            }

            if (binding.spinner.selectedItem == null)
                return@setOnClickListener
            val profileName = binding.spinner.selectedItem.toString()
            val specificProfile = profileStore.getSpecificProfile(profileName)

            r.profileJson = specificProfile.toString()
            /*
                        val auth = FirebaseAuth.getInstance()
                        auth.signInAnonymously()
                            .addOnCompleteListener(this) { task ->
                                if (task.isSuccessful) {
                                    aapsLogger.debug(LTag.CORE, "signInAnonymously:success")
                                    //val user = auth.currentUser // do we need this, seems unused?

                                    val database = FirebaseDatabase.getInstance().reference
                                    database.child("survey").child(r.id).setValue(r)
                                } else {
                                    aapsLogger.error("signInAnonymously:failure", task.exception!!)
                                    ToastUtils.warnToast(this, "Authentication failed.")
                                    //updateUI(null)
                                }

                                // ...
                            }
              */
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.spinner.adapter = null
        binding.profile.setOnClickListener(null)
        binding.submit.setOnClickListener(null)
    }

    inner class FirebaseRecord {

        var id = ""
        var age: Int = 0
        var weight: Int = 0
        var profileJson = "ghfg"
    }

}
