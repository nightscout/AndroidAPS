package info.nightscout.androidaps.activities

import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.widget.PopupMenu
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.data.defaultProfile.DefaultProfile
import info.nightscout.androidaps.data.defaultProfile.DefaultProfileDPV
import info.nightscout.androidaps.databinding.ActivityProfilehelperBinding
import info.nightscout.androidaps.db.ProfileSwitch
import info.nightscout.androidaps.dialogs.ProfileViewerDialog
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.DatabaseHelperInterface
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.profile.local.LocalProfilePlugin
import info.nightscout.androidaps.plugins.profile.local.events.EventLocalProfileChanged
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.ToastUtils
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.extensions.toVisibility
import info.nightscout.androidaps.utils.stats.TddCalculator
import java.text.DecimalFormat
import javax.inject.Inject

class ProfileHelperActivity : NoSplashAppCompatActivity() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var tddCalculator: TddCalculator
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var defaultProfile: DefaultProfile
    @Inject lateinit var defaultProfileDPV: DefaultProfileDPV
    @Inject lateinit var localProfilePlugin: LocalProfilePlugin
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var activePlugin: ActivePluginProvider
    @Inject lateinit var databaseHelper: DatabaseHelperInterface

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

    private lateinit var profileSwitch: List<ProfileSwitch>
    private val profileSwitchUsed = arrayOf(0, 0)

    private lateinit var binding: ActivityProfilehelperBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityProfilehelperBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.menu1.setOnClickListener {
            switchTab(0, typeSelected[0])
        }
        binding.menu2.setOnClickListener {
            switchTab(1, typeSelected[1])
        }

        binding.profiletype.setOnClickListener {
            PopupMenu(this, binding.profiletype).apply {
                menuInflater.inflate(R.menu.menu_profilehelper, menu)
                setOnMenuItemClickListener { item ->
                    binding.profiletype.setText(item.title)
                    when (item.itemId) {
                        R.id.menu_default -> switchTab(tabSelected, ProfileType.MOTOL_DEFAULT)
                        R.id.menu_default_dpv -> switchTab(tabSelected, ProfileType.DPV_DEFAULT)
                        R.id.menu_current -> switchTab(tabSelected, ProfileType.CURRENT)
                        R.id.menu_available -> switchTab(tabSelected, ProfileType.AVAILABLE_PROFILE)
                        R.id.menu_profileswitch -> switchTab(tabSelected, ProfileType.PROFILE_SWITCH)
                    }
                    true
                }
                show()
            }
        }

        // Active profile
        profileList = activePlugin.activeProfileInterface.profile?.getProfileList() ?: ArrayList()

        binding.availableProfileList.setOnClickListener {
            PopupMenu(this, binding.availableProfileList).apply {
                var order = 0
                for (name in profileList) menu.add(Menu.NONE, order, order++, name)
                setOnMenuItemClickListener { item ->
                    binding.availableProfileList.setText(item.title)
                    profileUsed[tabSelected] = item.itemId
                    true
                }
                show()
            }
        }

        // Profile switch
        profileSwitch = databaseHelper.getProfileSwitchData(dateUtil._now() - T.months(2).msecs(), true)

        binding.profileswitchList.setOnClickListener {
            PopupMenu(this, binding.profileswitchList).apply {
                var order = 0
                for (name in profileSwitch) menu.add(Menu.NONE, order, order++, name.customizedName)
                setOnMenuItemClickListener { item ->
                    binding.profileswitchList.setText(item.title)
                    profileSwitchUsed[tabSelected] = item.itemId
                    true
                }
                show()
            }
        }

        // Default profile
        binding.copytolocalprofile.setOnClickListener {
            val age = ageUsed[tabSelected]
            val weight = weightUsed[tabSelected]
            val tdd = tddUsed[tabSelected]
            val pct = pctUsed[tabSelected]
            val profile = if (typeSelected[tabSelected] == ProfileType.MOTOL_DEFAULT) defaultProfile.profile(age, tdd, weight, profileFunction.getUnits())
            else defaultProfileDPV.profile(age, tdd, pct / 100.0, profileFunction.getUnits())
            profile?.let {
                OKDialog.showConfirmation(this, resourceHelper.gs(R.string.careportal_profileswitch), resourceHelper.gs(R.string.copytolocalprofile), Runnable {
                    localProfilePlugin.addProfile(localProfilePlugin.copyFrom(it, "DefaultProfile" + dateUtil.dateAndTimeAndSecondsString(dateUtil._now())))
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

        binding.basalpctfromtdd.setParams(32.0, 32.0, 37.0, 1.0, DecimalFormat("0"), false, null)

        binding.tdds.text = tddCalculator.stats()

        // Current profile
        binding.currentProfileText.text = profileFunction.getProfileName()

        // General
        binding.compareprofile.setOnClickListener {
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
                            it.putLong("time", DateUtil.now())
                            it.putInt("mode", ProfileViewerDialog.Mode.PROFILE_COMPARE.ordinal)
                            it.putString("customProfile", profile0.data.toString())
                            it.putString("customProfile2", profile1.data.toString())
                            it.putString("customProfileUnits", profileFunction.getUnits())
                            it.putString("customProfileName", getProfileName(ageUsed[0], tddUsed[0], weightUsed[0], pctUsed[0] / 100.0, 0) + "\n" + getProfileName(ageUsed[1], tddUsed[1], weightUsed[1], pctUsed[1] / 100.0, 1))
                        }
                    }.show(supportFragmentManager, "ProfileViewDialog")
                    return@setOnClickListener
                }
            }
            ToastUtils.showToastInUiThread(this, R.string.invalidinput)
        }

        switchTab(0, typeSelected[0], false)
    }

    private fun getProfile(age: Double, tdd: Double, weight: Double, basalPct: Double, tab: Int): Profile? =
        try { // profile must not exist
            when (typeSelected[tab]) {
                ProfileType.MOTOL_DEFAULT -> defaultProfile.profile(age, tdd, weight, profileFunction.getUnits())
                ProfileType.DPV_DEFAULT -> defaultProfileDPV.profile(age, tdd, basalPct, profileFunction.getUnits())
                ProfileType.CURRENT -> profileFunction.getProfile()?.convertToNonCustomizedProfile()
                ProfileType.AVAILABLE_PROFILE -> activePlugin.activeProfileInterface.profile?.getSpecificProfile(profileList[profileUsed[tab]].toString())
                ProfileType.PROFILE_SWITCH -> profileSwitch[profileSwitchUsed[tab]].profileObject?.convertToNonCustomizedProfile()
            }
        } catch (e: Exception) {
            null
        }

    private fun getProfileName(age: Double, tdd: Double, weight: Double, basalSumPct: Double, tab: Int): String =
        when (typeSelected[tab]) {
            ProfileType.MOTOL_DEFAULT -> if (tdd > 0) resourceHelper.gs(R.string.formatwithtdd, age, tdd) else resourceHelper.gs(R.string.formatwithweight, age, weight)
            ProfileType.DPV_DEFAULT -> resourceHelper.gs(R.string.formatwittddandpct, age, tdd, (basalSumPct * 100).toInt())
            ProfileType.CURRENT -> profileFunction.getProfileName()
            ProfileType.AVAILABLE_PROFILE -> profileList[profileUsed[tab]].toString()
            ProfileType.PROFILE_SWITCH -> profileSwitch[profileSwitchUsed[tab]].customizedName
        }

    private fun storeValues() {
        ageUsed[tabSelected] = binding.age.value
        weightUsed[tabSelected] = binding.weight.value
        tddUsed[tabSelected] = binding.tdd.value
        pctUsed[tabSelected] = binding.basalpctfromtdd.value
    }

    private fun switchTab(tab: Int, newContent: ProfileType, storeOld: Boolean = true) {
        setBackgroundColorOnSelected(tab)
        // Store values for selected tab. listBox values are stored on selection change
        if (storeOld) storeValues()

        tabSelected = tab
        typeSelected[tabSelected] = newContent
        binding.profiletypeTitle.defaultHintTextColor = ColorStateList.valueOf(resourceHelper.gc(if (tab == 0) R.color.tabBgColorSelected else R.color.examinedProfile))

        // show new content
        binding.profiletype.setText(
            when (typeSelected[tabSelected]) {
                ProfileType.MOTOL_DEFAULT     -> resourceHelper.gs(R.string.motoldefaultprofile)
                ProfileType.DPV_DEFAULT       -> resourceHelper.gs(R.string.dpvdefaultprofile)
                ProfileType.CURRENT           -> resourceHelper.gs(R.string.currentprofile)
                ProfileType.AVAILABLE_PROFILE -> resourceHelper.gs(R.string.availableprofile)
                ProfileType.PROFILE_SWITCH    -> resourceHelper.gs(R.string.careportal_profileswitch)
            })
        binding.defaultProfile.visibility = (newContent == ProfileType.MOTOL_DEFAULT || newContent == ProfileType.DPV_DEFAULT).toVisibility()
        binding.currentProfile.visibility = (newContent == ProfileType.CURRENT).toVisibility()
        binding.availableProfile.visibility = (newContent == ProfileType.AVAILABLE_PROFILE).toVisibility()
        binding.profileSwitch.visibility = (newContent == ProfileType.PROFILE_SWITCH).toVisibility()

        // restore selected values
        binding.age.value = ageUsed[tabSelected]
        binding.weight.value = weightUsed[tabSelected]
        binding.tdd.value = tddUsed[tabSelected]
        binding.basalpctfromtdd.value = pctUsed[tabSelected]

        binding.basalpctfromtddRow.visibility = (newContent == ProfileType.DPV_DEFAULT).toVisibility()
        if (profileList.isNotEmpty())
            binding.availableProfileList.setText(profileList[profileUsed[tabSelected]].toString())
        if (profileSwitch.isNotEmpty())
            binding.profileswitchList.setText(profileSwitch[profileSwitchUsed[tabSelected]].customizedName)
    }

    private fun setBackgroundColorOnSelected(tab: Int) {
        binding.menu1.setBackgroundColor(resourceHelper.gc(if (tab == 1) R.color.defaultbackground else R.color.tabBgColorSelected))
        binding.menu2.setBackgroundColor(resourceHelper.gc(if (tab == 0) R.color.defaultbackground else R.color.examinedProfile))
    }
}
