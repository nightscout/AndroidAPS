package info.nightscout.androidaps.activities

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.TextView
import com.google.android.material.tabs.TabLayout
import com.google.common.collect.Lists
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.ProfileSealed
import info.nightscout.androidaps.data.PureProfile
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.EffectiveProfileSwitch
import info.nightscout.androidaps.databinding.ActivityProfilehelperBinding
import info.nightscout.androidaps.dialogs.ProfileViewerDialog
import info.nightscout.androidaps.extensions.toVisibility
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.profile.local.LocalProfilePlugin
import info.nightscout.androidaps.plugins.profile.local.events.EventLocalProfileChanged
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.ToastUtils
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.defaultProfile.DefaultProfile
import info.nightscout.androidaps.utils.defaultProfile.DefaultProfileDPV
import info.nightscout.androidaps.utils.stats.TddCalculator
import java.text.DecimalFormat
import javax.inject.Inject

class ProfileHelperActivity : NoSplashAppCompatActivity() {

    @Inject lateinit var tddCalculator: TddCalculator
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var defaultProfile: DefaultProfile
    @Inject lateinit var defaultProfileDPV: DefaultProfileDPV
    @Inject lateinit var localProfilePlugin: LocalProfilePlugin
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var repository: AppRepository

    enum class ProfileType {
        MOTOL_DEFAULT,
        DPV_DEFAULT,
        CURRENT,
        AVAILABLE_PROFILE,
        PROFILE_SWITCH
    }

    private var tabSelected = 0
    private val typeSelected = arrayOf(ProfileType.MOTOL_DEFAULT, ProfileType.CURRENT)

    private val ageUsed = arrayOf(15.0, 15.0)
    private val weightUsed = arrayOf(0.0, 0.0)
    private val tddUsed = arrayOf(0.0, 0.0)
    private val pctUsed = arrayOf(32.0, 32.0)

    private lateinit var profileList: ArrayList<CharSequence>
    private val profileUsed = arrayOf(0, 0)

    private lateinit var profileSwitch: List<EffectiveProfileSwitch>
    private val profileSwitchUsed = arrayOf(0, 0)

    private lateinit var binding: ActivityProfilehelperBinding

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfilehelperBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                switchTab(tab.position, typeSelected[tab.position])
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        val profileTypeList = Lists.newArrayList(
            rh.gs(R.string.motoldefaultprofile),
            rh.gs(R.string.dpvdefaultprofile),
            rh.gs(R.string.currentprofile),
            rh.gs(R.string.availableprofile),
            rh.gs(R.string.careportal_profileswitch)
        )
        binding.profileType.setAdapter(ArrayAdapter(this, R.layout.spinner_centered, profileTypeList))

        binding.profileType.setOnItemClickListener { _, _, _, _ ->
            when (binding.profileType.text.toString()) {
                rh.gs(R.string.motoldefaultprofile)      -> switchTab(tabSelected, ProfileType.MOTOL_DEFAULT)
                rh.gs(R.string.dpvdefaultprofile)        -> switchTab(tabSelected, ProfileType.DPV_DEFAULT)
                rh.gs(R.string.currentprofile)           -> switchTab(tabSelected, ProfileType.CURRENT)
                rh.gs(R.string.availableprofile)         -> switchTab(tabSelected, ProfileType.AVAILABLE_PROFILE)
                rh.gs(R.string.careportal_profileswitch) -> switchTab(tabSelected, ProfileType.PROFILE_SWITCH)
            }
        }

        // Active profile
        profileList = activePlugin.activeProfileSource.profile?.getProfileList() ?: ArrayList()

        binding.availableProfileList.setAdapter(ArrayAdapter(this, R.layout.spinner_centered, profileList))
        binding.availableProfileList.setOnItemClickListener { _, _, index, _ ->
            profileUsed[tabSelected] = index
        }

        // Profile switch
        profileSwitch = repository.getEffectiveProfileSwitchDataFromTime(dateUtil.now() - T.months(2).msecs(), true).blockingGet()

        val profileswitchListNames = profileSwitch.map { it.originalCustomizedName }
        binding.profileswitchList.setAdapter(ArrayAdapter(this, R.layout.spinner_centered, profileswitchListNames))
        binding.profileswitchList.setOnItemClickListener { _, _, index, _ ->
            profileSwitchUsed[tabSelected] = index
        }

        // Default profile
        binding.copyToLocalProfile.setOnClickListener {
            storeValues()
            val age = ageUsed[tabSelected]
            val weight = weightUsed[tabSelected]
            val tdd = tddUsed[tabSelected]
            val pct = pctUsed[tabSelected]
            val profile = if (typeSelected[tabSelected] == ProfileType.MOTOL_DEFAULT) defaultProfile.profile(age, tdd, weight, profileFunction.getUnits())
            else defaultProfileDPV.profile(age, tdd, pct / 100.0, profileFunction.getUnits())
            profile?.let {
                OKDialog.showConfirmation(this, rh.gs(R.string.careportal_profileswitch), rh.gs(R.string.copytolocalprofile), Runnable {
                    localProfilePlugin.addProfile(
                        localProfilePlugin.copyFrom(
                            it, "DefaultProfile " +
                                dateUtil.dateAndTimeAndSecondsString(dateUtil.now())
                                    .replace(".", "/")
                        )
                    )
                    rxBus.send(EventLocalProfileChanged())
                })
            }
        }

        binding.age.setParams(0.0, 1.0, 18.0, 1.0, DecimalFormat("0"), false, null)
        binding.weight.setParams(0.0, 0.0, 150.0, 1.0, DecimalFormat("0"), false, null, object : TextWatcher {
            override fun afterTextChanged(s: Editable) {}
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                binding.tddRow.visibility = (binding.weight.value == 0.0).toVisibility()
            }
        })
        binding.tdd.setParams(0.0, 0.0, 200.0, 1.0, DecimalFormat("0"), false, null, object : TextWatcher {
            override fun afterTextChanged(s: Editable) {}
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                binding.weightRow.visibility = (binding.tdd.value == 0.0).toVisibility()
            }
        })

        binding.basalPctFromTdd.setParams(32.0, 32.0, 37.0, 1.0, DecimalFormat("0"), false, null)

        binding.tdds.addView(TextView(this).apply { text = rh.gs(R.string.tdd) + ": " + rh.gs(R.string.calculation_in_progress) })
        Thread {
            val tdds = tddCalculator.stats(this)
            runOnUiThread {
                binding.tdds.removeAllViews()
                binding.tdds.addView(tdds)
            }
        }.start()

        // Current profile
        binding.currentProfileText.text = profileFunction.getProfileName()

        // General
        binding.compareProfiles.setOnClickListener {
            storeValues()
            for (i in 0..1) {
                if (typeSelected[i] == ProfileType.MOTOL_DEFAULT) {
                    if (ageUsed[i] < 1 || ageUsed[i] > 18) {
                        ToastUtils.showToastInUiThread(this, R.string.invalidage)
                        return@setOnClickListener
                    }
                    if ((weightUsed[i] < 5 || weightUsed[i] > 150) && tddUsed[i] == 0.0) {
                        ToastUtils.showToastInUiThread(this, R.string.invalidweight)
                        return@setOnClickListener
                    }
                    if ((tddUsed[i] < 5 || tddUsed[i] > 150) && weightUsed[i] == 0.0) {
                        ToastUtils.showToastInUiThread(this, R.string.invalidweight)
                        return@setOnClickListener
                    }
                }
                if (typeSelected[i] == ProfileType.DPV_DEFAULT) {
                    if (ageUsed[i] < 1 || ageUsed[i] > 18) {
                        ToastUtils.showToastInUiThread(this, R.string.invalidage)
                        return@setOnClickListener
                    }
                    if (tddUsed[i] < 5 || tddUsed[i] > 150) {
                        ToastUtils.showToastInUiThread(this, R.string.invalidweight)
                        return@setOnClickListener
                    }
                    if ((pctUsed[i] < 32 || pctUsed[i] > 37)) {
                        ToastUtils.showToastInUiThread(this, R.string.invalidpct)
                        return@setOnClickListener
                    }
                }
            }

            getProfile(ageUsed[0], tddUsed[0], weightUsed[0], pctUsed[0] / 100.0, 0)?.let { profile0 ->
                getProfile(ageUsed[1], tddUsed[1], weightUsed[1], pctUsed[1] / 100.0, 1)?.let { profile1 ->
                    ProfileViewerDialog().also { pvd ->
                        pvd.arguments = Bundle().also {
                            it.putLong("time", dateUtil.now())
                            it.putInt("mode", ProfileViewerDialog.Mode.PROFILE_COMPARE.ordinal)
                            it.putString("customProfile", profile0.jsonObject.toString())
                            it.putString("customProfile2", profile1.jsonObject.toString())
                            it.putString(
                                "customProfileName",
                                getProfileName(ageUsed[0], tddUsed[0], weightUsed[0], pctUsed[0] / 100.0, 0) + "\n" + getProfileName(
                                    ageUsed[1],
                                    tddUsed[1],
                                    weightUsed[1],
                                    pctUsed[1] / 100.0,
                                    1
                                )
                            )
                        }
                    }.show(supportFragmentManager, "ProfileViewDialog")
                    return@setOnClickListener
                }
            }
            ToastUtils.showToastInUiThread(this, R.string.invalidinput)
        }
        binding.ageLabel.labelFor = binding.age.editTextId
        binding.tddLabel.labelFor = binding.tdd.editTextId
        binding.weightLabel.labelFor = binding.weight.editTextId
        binding.basalPctFromTddLabel.labelFor = binding.basalPctFromTdd.editTextId

        switchTab(0, typeSelected[0], false)
    }

    private fun getProfile(age: Double, tdd: Double, weight: Double, basalPct: Double, tab: Int): PureProfile? =
        try { // Profile must not exist
            when (typeSelected[tab]) {
                ProfileType.MOTOL_DEFAULT     -> defaultProfile.profile(age, tdd, weight, profileFunction.getUnits())
                ProfileType.DPV_DEFAULT       -> defaultProfileDPV.profile(age, tdd, basalPct, profileFunction.getUnits())
                ProfileType.CURRENT           -> profileFunction.getProfile()?.convertToNonCustomizedProfile(dateUtil)
                ProfileType.AVAILABLE_PROFILE -> activePlugin.activeProfileSource.profile?.getSpecificProfile(profileList[profileUsed[tab]].toString())
                ProfileType.PROFILE_SWITCH    -> ProfileSealed.EPS(profileSwitch[profileSwitchUsed[tab]]).convertToNonCustomizedProfile(dateUtil)
            }
        } catch (e: Exception) {
            null
        }

    private fun getProfileName(age: Double, tdd: Double, weight: Double, basalSumPct: Double, tab: Int): String =
        when (typeSelected[tab]) {
            ProfileType.MOTOL_DEFAULT     -> if (tdd > 0) rh.gs(R.string.formatwithtdd, age, tdd) else rh.gs(R.string.formatwithweight, age, weight)
            ProfileType.DPV_DEFAULT       -> rh.gs(R.string.formatwittddandpct, age, tdd, (basalSumPct * 100).toInt())
            ProfileType.CURRENT           -> profileFunction.getProfileName()
            ProfileType.AVAILABLE_PROFILE -> profileList[profileUsed[tab]].toString()
            ProfileType.PROFILE_SWITCH    -> profileSwitch[profileSwitchUsed[tab]].originalCustomizedName
        }

    private fun storeValues() {
        ageUsed[tabSelected] = binding.age.value
        weightUsed[tabSelected] = binding.weight.value
        tddUsed[tabSelected] = binding.tdd.value
        pctUsed[tabSelected] = binding.basalPctFromTdd.value
    }

    private fun switchTab(tab: Int, newContent: ProfileType, storeOld: Boolean = true) {
        // Store values for selected tab. listBox values are stored on selection change
        if (storeOld) storeValues()

        tabSelected = tab
        typeSelected[tabSelected] = newContent

        // Show new content
        binding.profileType.setText(
            when (typeSelected[tabSelected]) {
                ProfileType.MOTOL_DEFAULT     -> rh.gs(R.string.motoldefaultprofile)
                ProfileType.DPV_DEFAULT       -> rh.gs(R.string.dpvdefaultprofile)
                ProfileType.CURRENT           -> rh.gs(R.string.currentprofile)
                ProfileType.AVAILABLE_PROFILE -> rh.gs(R.string.availableprofile)
                ProfileType.PROFILE_SWITCH    -> rh.gs(R.string.careportal_profileswitch)
            },
            false
        )
        binding.defaultProfile.visibility = (newContent == ProfileType.MOTOL_DEFAULT || newContent == ProfileType.DPV_DEFAULT).toVisibility()
        binding.currentProfile.visibility = (newContent == ProfileType.CURRENT).toVisibility()
        binding.availableProfile.visibility = (newContent == ProfileType.AVAILABLE_PROFILE).toVisibility()
        binding.profileSwitch.visibility = (newContent == ProfileType.PROFILE_SWITCH).toVisibility()

        // Restore selected values
        binding.age.value = ageUsed[tabSelected]
        binding.weight.value = weightUsed[tabSelected]
        binding.tdd.value = tddUsed[tabSelected]
        binding.basalPctFromTdd.value = pctUsed[tabSelected]

        binding.basalPctFromTddRow.visibility = (newContent == ProfileType.DPV_DEFAULT).toVisibility()
        if (profileList.isNotEmpty()) {
            binding.availableProfileList.setText(profileList[profileUsed[tabSelected]].toString(), false)
        }
        if (profileSwitch.isNotEmpty()) {
            binding.profileswitchList.setText(profileSwitch[profileSwitchUsed[tabSelected]].originalCustomizedName, false)
        }
    }

}
