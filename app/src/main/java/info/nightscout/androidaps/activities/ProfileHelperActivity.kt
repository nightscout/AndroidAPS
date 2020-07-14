package info.nightscout.androidaps.activities

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.widget.PopupMenu
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.data.defaultProfile.DefaultProfile
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
    @Inject lateinit var localProfilePlugin: LocalProfilePlugin
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var activePlugin: ActivePluginProvider
    @Inject lateinit var databaseHelper: DatabaseHelperInterface

    enum class ProfileType {
        MOTOL_DEFAULT,
        CURRENT,
        AVAILABLE_PROFILE,
        PROFILE_SWITCH
    }

    var tabSelected = 0
    val typeSelected = arrayOf(ProfileType.MOTOL_DEFAULT, ProfileType.CURRENT)

    val ageUsed = arrayOf(15.0, 15.0)
    val weightUsed = arrayOf(50.0, 50.0)
    val tddUsed = arrayOf(50.0, 50.0)

    lateinit var profileList: ArrayList<CharSequence>
    val profileUsed = arrayOf(0, 0)

    lateinit var profileSwitch: List<ProfileSwitch>
    val profileSwitchUsed = arrayOf(0, 0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profilehelper)

        profilehelper_menu1.setOnClickListener {
            switchTab(0)
        }
        profilehelper_menu2.setOnClickListener {
            switchTab(1)
        }

        profilehelper_profiletype.setOnClickListener {
            PopupMenu(this, profilehelper_profiletype).apply {
                menuInflater.inflate(R.menu.menu_profilehelper, menu)
                setOnMenuItemClickListener { item ->
                    profilehelper_profiletype.setText(item.title)
                    when (item.itemId) {
                        R.id.menu_default       -> switchContent(ProfileType.MOTOL_DEFAULT)
                        R.id.menu_current       -> switchContent(ProfileType.CURRENT)
                        R.id.menu_available     -> switchContent(ProfileType.AVAILABLE_PROFILE)
                        R.id.menu_profileswitch -> switchContent(ProfileType.PROFILE_SWITCH)
                    }
                    true
                }
                show()
            }
        }

        // Active profile
        profileList = activePlugin.activeProfileInterface.profile?.getProfileList() ?: ArrayList()

        profilehelper_available_profile_list.setOnClickListener {
            PopupMenu(this, profilehelper_available_profile_list).apply {
                var order = 0
                for (name in profileList) menu.add(Menu.NONE, order, order++, name)
                setOnMenuItemClickListener { item ->
                    profilehelper_available_profile_list.setText(item.title)
                    profileUsed[tabSelected] = item.itemId
                    true
                }
                show()
            }
        }

        // Profile switch
        profileSwitch = databaseHelper.getProfileSwitchData(dateUtil._now() - T.months(2).msecs(), true)

        profilehelper_profileswitch_list.setOnClickListener {
            PopupMenu(this, profilehelper_profileswitch_list).apply {
                var order = 0
                for (name in profileSwitch) menu.add(Menu.NONE, order, order++, name.customizedName)
                setOnMenuItemClickListener { item ->
                    profilehelper_profileswitch_list.setText(item.title)
                    profileSwitchUsed[tabSelected] = item.itemId
                    true
                }
                show()
            }
        }

        // Default profile

        profilehelper_copytolocalprofile.setOnClickListener {
            val age = profilehelper_age.value
            val weight = profilehelper_weight.value
            val tdd = profilehelper_tdd.value
            defaultProfile.profile(age, tdd, weight, profileFunction.getUnits())?.let { profile ->
                OKDialog.showConfirmation(this, resourceHelper.gs(R.string.careportal_profileswitch), resourceHelper.gs(R.string.copytolocalprofile), Runnable {
                    localProfilePlugin.addProfile(LocalProfilePlugin.SingleProfile().copyFrom(localProfilePlugin.rawProfile, profile, "DefaultProfile" + dateUtil.dateAndTimeAndSecondsString(dateUtil._now())))
                    rxBus.send(EventLocalProfileChanged())
                })
            }
        }

        profilehelper_age.setParams(0.0, 1.0, 80.0, 1.0, DecimalFormat("0"), false, null)
        profilehelper_weight.setParams(0.0, 0.0, 150.0, 1.0, DecimalFormat("0"), false, null, object : TextWatcher {
            override fun afterTextChanged(s: Editable) {}
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                profilehelper_tdd_row.visibility = (profilehelper_weight.value == 0.0).toVisibility()
            }
        })
        profilehelper_tdd.setParams(0.0, 0.0, 200.0, 1.0, DecimalFormat("0"), false, null, object : TextWatcher {
            override fun afterTextChanged(s: Editable) {}
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                profilehelper_weight_row.visibility = (profilehelper_tdd.value == 0.0).toVisibility()
            }
        })

        profilehelper_tdds.text = tddCalculator.stats()

        // Current profile
        profilehelper_current_profile_text.text = profileFunction.getProfileName()

        // General
        profilehelper_compareprofile.setOnClickListener {
            val age = profilehelper_age.value
            val weight = profilehelper_weight.value
            val tdd = profilehelper_tdd.value
            if (typeSelected[0] == ProfileType.MOTOL_DEFAULT || typeSelected[1] == ProfileType.MOTOL_DEFAULT) {
                if (age < 1 || age > 17) {
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
            }

            getProfile(age, tdd, weight, 0)?.let { profile0 ->
                getProfile(age, tdd, weight, 1)?.let { profile1 ->
                    ProfileViewerDialog().also { pvd ->
                        pvd.arguments = Bundle().also {
                            it.putLong("time", DateUtil.now())
                            it.putInt("mode", ProfileViewerDialog.Mode.PROFILE_COMPARE.ordinal)
                            it.putString("customProfile", profile0.data.toString())
                            it.putString("customProfile2", profile1.data.toString())
                            it.putString("customProfileUnits", profileFunction.getUnits())
                            it.putString("customProfileName", getProfileName(age, tdd, weight, 0) + "\n" + getProfileName(age, tdd, weight, 1))
                        }
                    }.show(supportFragmentManager, "ProfileViewDialog")
                    return@setOnClickListener
                }
            }
            ToastUtils.showToastInUiThread(this, R.string.invalidinput)
        }

        switchTab(0)
    }

    private fun getProfile(age: Double, tdd: Double, weight: Double, tab: Int): Profile? =
        when (typeSelected[tab]) {
            ProfileType.MOTOL_DEFAULT     -> defaultProfile.profile(age, tdd, weight, profileFunction.getUnits())
            ProfileType.CURRENT           -> profileFunction.getProfile()?.convertToNonCustomizedProfile()
            ProfileType.AVAILABLE_PROFILE -> activePlugin.activeProfileInterface.profile?.getSpecificProfile(profileList[profileUsed[tab]].toString())
            ProfileType.PROFILE_SWITCH    -> profileSwitch[profileSwitchUsed[tab]].profileObject?.convertToNonCustomizedProfile()
        }

    private fun getProfileName(age: Double, tdd: Double, weight: Double, tab: Int): String =
        when (typeSelected[tab]) {
            ProfileType.MOTOL_DEFAULT     -> if (tdd > 0) resourceHelper.gs(R.string.formatwithtdd, age, tdd) else resourceHelper.gs(R.string.formatwithweight, age, weight)
            ProfileType.CURRENT           -> profileFunction.getProfileName()
            ProfileType.AVAILABLE_PROFILE -> profileList[profileUsed[tab]].toString()
            ProfileType.PROFILE_SWITCH    -> profileSwitch[profileSwitchUsed[tab]].customizedName
        }

    private fun switchTab(tab: Int) {
        setBackgroundColorOnSelected(tab)
        when (typeSelected[tab]) {
            ProfileType.MOTOL_DEFAULT     -> {
                ageUsed[tabSelected] = profilehelper_age.value
                weightUsed[tabSelected] = profilehelper_weight.value
                tddUsed[tabSelected] = profilehelper_tdd.value
                profilehelper_profiletype.setText(resourceHelper.gs(R.string.motoldefaultprofile))
            }

            ProfileType.CURRENT           -> {
                profilehelper_profiletype.setText(resourceHelper.gs(R.string.currentprofile))
            }

            ProfileType.AVAILABLE_PROFILE -> {
                profilehelper_profiletype.setText(resourceHelper.gs(R.string.availableprofile))
            }

            ProfileType.PROFILE_SWITCH    -> {
                profilehelper_profiletype.setText(resourceHelper.gs(R.string.careportal_profileswitch))
            }
        }
        tabSelected = tab
        switchContent(typeSelected[tabSelected])
    }

    private fun switchContent(newContent: ProfileType) {
        profilehelper_default_profile.visibility = (newContent == ProfileType.MOTOL_DEFAULT).toVisibility()
        profilehelper_current_profile.visibility = (newContent == ProfileType.CURRENT).toVisibility()
        profilehelper_available_profile.visibility = (newContent == ProfileType.AVAILABLE_PROFILE).toVisibility()
        profilehelper_profile_switch.visibility = (newContent == ProfileType.PROFILE_SWITCH).toVisibility()

        typeSelected[tabSelected] = newContent
        when (newContent) {
            ProfileType.MOTOL_DEFAULT     -> {
                profilehelper_age.value = ageUsed[tabSelected]
                profilehelper_weight.value = weightUsed[tabSelected]
                profilehelper_tdd.value = tddUsed[tabSelected]
            }

            ProfileType.CURRENT           -> {
            }

            ProfileType.AVAILABLE_PROFILE -> {
                if (profileList.size > 0)
                    profilehelper_available_profile_list.setText(profileList[profileUsed[tabSelected]].toString())
            }

            ProfileType.PROFILE_SWITCH    -> {
                if (profileSwitch.size > 0)
                    profilehelper_profileswitch_list.setText(profileSwitch[profileSwitchUsed[tabSelected]].customizedName)
            }
        }
    }

    private fun setBackgroundColorOnSelected(tab: Int) {
        profilehelper_menu1.setBackgroundColor(resourceHelper.gc(if (tab == 1) R.color.defaultbackground else R.color.tabBgColorSelected))
        profilehelper_menu2.setBackgroundColor(resourceHelper.gc(if (tab == 0) R.color.defaultbackground else R.color.tabBgColorSelected))
    }

}
