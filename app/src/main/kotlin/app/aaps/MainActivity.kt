package app.aaps

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.text.InputType
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.style.ForegroundColorSpan
import android.text.util.Linkify
import android.util.TypedValue
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.core.view.MenuCompat
import androidx.core.view.MenuProvider
import app.aaps.activities.HistoryBrowseActivity
import app.aaps.activities.PreferencesActivity
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.maintenance.FileListProvider
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.protection.ExportPasswordDataStore
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.events.EventAppInitialized
import app.aaps.core.interfaces.rx.events.EventPreferenceChange
import app.aaps.core.interfaces.rx.events.EventRebuildTabs
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.core.interfaces.ui.IconsProvider
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.interfaces.versionChecker.VersionCheckerUtils
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringKey
import app.aaps.core.objects.crypto.CryptoUtil
import app.aaps.core.ui.UIRunnable
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.ui.locale.LocaleHelper
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.core.utils.isRunningRealPumpTest
import app.aaps.databinding.ActivityMainBinding
import app.aaps.plugins.configuration.activities.DaggerAppCompatActivityWithResult
import app.aaps.plugins.configuration.activities.SingleFragmentActivity
import app.aaps.plugins.configuration.maintenance.MaintenancePlugin
import app.aaps.plugins.configuration.setupwizard.SetupWizardActivity
import app.aaps.plugins.constraints.signatureVerifier.SignatureVerifierPlugin
import app.aaps.ui.activities.ProfileHelperActivity
import app.aaps.ui.activities.StatsActivity
import app.aaps.ui.activities.TreatmentsActivity
import app.aaps.ui.tabs.TabPageAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.joanzapata.iconify.Iconify
import com.joanzapata.iconify.fonts.FontAwesomeModule
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject

class MainActivity : DaggerAppCompatActivityWithResult() {

    // 自定义Base32编解码，完全原生实现，不用外部库
    private object Base32Coder {
        private val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

        fun encode(input: ByteArray): String {
            val output = StringBuilder()
            var buffer = 0
            var bitsLeft = 0
            for (b in input) {
                buffer = (buffer shl 8) or (b.toInt() and 0xFF)
                bitsLeft += 8
                while (bitsLeft >= 5) {
                    bitsLeft -= 5
                    output.append(alphabet[(buffer shr bitsLeft) and 0x1F])
                }
            }
            if (bitsLeft > 0) {
                buffer = buffer shl (5 - bitsLeft)
                output.append(alphabet[buffer and 0x1F])
            }
            return output.toString()
        }

        fun decode(input: String): ByteArray {
            val cleanInput = input.uppercase().trimEnd('=')
            val output = ByteArray(cleanInput.length * 5 / 8)
            var buffer = 0
            var bitsLeft = 0
            var index = 0
            for (c in cleanInput) {
                val value = alphabet.indexOf(c)
                if (value < 0) throw IllegalArgumentException("无效的Base32字符: $c")
                buffer = (buffer shl 5) or value
                bitsLeft += 5
                if (bitsLeft >= 8) {
                    output[index++] = (buffer shr (bitsLeft - 8)).toByte()
                    bitsLeft -= 8
                }
            }
            return output.copyOf(index)
        }
    }

    // TOTP核心实现，原生HMAC-SHA1，完全兼容谷歌验证器
    private fun verifyTotp(secret: ByteArray, code: String, tolerance: Int = 1): Boolean {
        if (code.length != 6) return false
        val codeNum = code.toIntOrNull() ?: return false

        val timeStepMs = 30L * 1000L
        val currentTime = System.currentTimeMillis()

        // 支持前后1个时间步的时间容错
        for (offset in -tolerance..tolerance) {
            val checkTime = currentTime + offset * timeStepMs
            val counter = checkTime / timeStepMs
            val generatedCode = generateTotp(secret, counter)
            if (generatedCode == codeNum) {
                return true
            }
        }
        return false
    }

    private fun generateTotp(secret: ByteArray, counter: Long): Int {
        // 把counter转成8字节数组
        val counterBytes = ByteArray(8)
        var temp = counter
        for (i in 7 downTo 0) {
            counterBytes[i] = (temp and 0xFF).toByte()
            temp = temp shr 8
        }

        // 原生HMAC-SHA1计算，Android自带API
        val mac = Mac.getInstance("HmacSHA1")
        val secretKey = SecretKeySpec(secret, "HmacSHA1")
        mac.init(secretKey)
        val hmacResult = mac.doFinal(counterBytes)

        // 动态截断
        val offset = hmacResult.last().toInt() and 0x1F
        val binary = ((hmacResult[offset].toInt() and 0x7F) shl 24) or
            ((hmacResult[offset + 1].toInt() and 0xFF) shl 16) or
            ((hmacResult[offset + 2].toInt() and 0xFF) shl 8) or
            (hmacResult[offset + 3].toInt() and 0xFF)

        // 生成6位密码
        return binary % 1000000
    }

    private val disposable = CompositeDisposable()

    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var versionCheckerUtils: VersionCheckerUtils
    @Inject lateinit var smsCommunicator: SmsCommunicator
    @Inject lateinit var loop: Loop
    @Inject lateinit var config: Config
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var iconsProvider: IconsProvider
    @Inject lateinit var constraintChecker: ConstraintsChecker
    @Inject lateinit var signatureVerifierPlugin: SignatureVerifierPlugin
    @Inject lateinit var maintenancePlugin: MaintenancePlugin
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var fileListProvider: FileListProvider
    @Inject lateinit var cryptoUtil: CryptoUtil
    @Inject lateinit var exportPasswordDataStore: ExportPasswordDataStore
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var configBuilder: ConfigBuilder

    private var actionBarDrawerToggle: ActionBarDrawerToggle? = null
    private var pluginPreferencesMenuItem: MenuItem? = null
    private var menu: Menu? = null
    private var menuOpen = false
    private var isProtectionCheckActive = false
    private lateinit var binding: ActivityMainBinding
    private var mainMenuProvider: MenuProvider? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Iconify.with(FontAwesomeModule())
        LocaleHelper.update(applicationContext)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)

        initAllComponents(savedInstanceState)

        // 处理原有验证状态的迁移
        val prefs = getSharedPreferences("AppLock", Context.MODE_PRIVATE)
        var verified = prefs.getBoolean("password_verified", false)
        val hasTotpSecret = prefs.getString("totp_secret", null) != null

        if (!hasTotpSecret && verified) {
            prefs.edit().putBoolean("password_verified", false).apply()
            verified = false
        }

        if (!verified) {
            Handler(Looper.getMainLooper()).postDelayed({
                                                            if (initTotpSecretIfNeeded()) {
                                                                showPasswordVerificationDialog()
                                                            }
                                                        }, 200)
        }
    }

    /**
     * 初始化TOTP密钥，首次使用引导用户设置
     */
    private fun initTotpSecretIfNeeded(): Boolean {
        val prefs = getSharedPreferences("AppLock", Context.MODE_PRIVATE)
        if (prefs.getString("totp_secret", null) != null) {
            return true
        }

        // 生成随机20字节密钥，符合RFC标准
        val secret = ByteArray(20)
        SecureRandom().nextBytes(secret)
        val secretBase32 = Base32Coder.encode(secret)

        // 保存密钥
        prefs.edit().putString("totp_secret", secretBase32).apply()

        // 构建设置对话框，去掉二维码，只显示密钥，避免外部依赖
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp2px(16), dp2px(16), dp2px(16), dp2px(16))

            addView(TextView(this@MainActivity).apply {
                text = "首次使用：请先设置动态密码\n1. 截图32位密钥\n2. 添加客服，发送密钥截图\n3. 获取6位动态密码"
                textSize = 14f
            })

            addView(TextView(this@MainActivity).apply {
                text = "密钥：$secretBase32"
                textSize = 18f
                setTextColor(Color.RED)
                setPadding(0, dp2px(16), 0, dp2px(16))
            })

            addView(EditText(this@MainActivity).apply {
                id = android.R.id.input
                inputType = InputType.TYPE_CLASS_NUMBER
                hint = "请输入验证器申请的6位动态密码"
                maxLines = 1
            })
        }
//首次使用：设置动态密码
//1. 打开谷歌/微软验证器
//2. 手动添加账户，输入密钥
//3. 输入当前6位动态密码
        MaterialAlertDialogBuilder(this)
            .setTitle("设置动态密码")
            .setView(dialogView)
            .setCancelable(false)
            .setPositiveButton("确认") { dialog, _ ->
                val inputCode = dialogView.findViewById<EditText>(android.R.id.input).text.toString()
                val secretBytes = Base32Coder.decode(secretBase32)

                if (verifyTotp(secretBytes, inputCode)) {
                    ToastUtils.okToast(this, "动态密码设置成功！")
                    showPasswordVerificationDialog()
                } else {
                    ToastUtils.errorToast(this, "密码错误，请重新设置")
                    prefs.edit().remove("totp_secret").apply()
                    initTotpSecretIfNeeded()
                }
            }
            .setNegativeButton("退出") { _, _ ->
                finish()
            }
            .show()

        return false
    }

    private fun initAllComponents(savedInstanceState: Bundle?) {
        actionBarDrawerToggle = ActionBarDrawerToggle(this, binding.mainDrawerLayout, R.string.open_navigation, R.string.close_navigation).also {
            binding.mainDrawerLayout.addDrawerListener(it)
            it.syncState()
        }

        processPreferenceChange(EventPreferenceChange(BooleanKey.OverviewKeepScreenOn.key))

        disposable += rxBus
            .toObservable(EventRebuildTabs::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           if (it.recreate) recreate()
                           else setupViews()
                           setWakeLock()
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ processPreferenceChange(it) }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventAppInitialized::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ start() }, fabricPrivacy::logException)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.mainDrawerLayout.isDrawerOpen(GravityCompat.START))
                    binding.mainDrawerLayout.closeDrawers()
                else if (menuOpen)
                    menu?.close()
                else if (binding.mainPager.currentItem != 0)
                    binding.mainPager.currentItem = 0
                else finish()
            }
        })

        mainMenuProvider = object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                MenuCompat.setGroupDividerEnabled(menu, true)
                this@MainActivity.menu = menu
                menuInflater.inflate(R.menu.menu_main, menu)
                pluginPreferencesMenuItem = menu.findItem(R.id.nav_plugin_preferences)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean =
                when (menuItem.itemId) {
                    R.id.nav_preferences        -> {
                        protectionCheck.queryProtection(this@MainActivity, ProtectionCheck.Protection.PREFERENCES, {
                            startActivity(Intent(this@MainActivity, PreferencesActivity::class.java).setAction("info.nightscout.androidaps.MainActivity"))
                        })
                        true
                    }
                    R.id.nav_historybrowser     -> {
                        startActivity(Intent(this@MainActivity, HistoryBrowseActivity::class.java).setAction("info.nightscout.androidaps.MainActivity"))
                        true
                    }
                    R.id.nav_treatments         -> {
                        startActivity(Intent(this@MainActivity, TreatmentsActivity::class.java).setAction("info.nightscout.androidaps.MainActivity"))
                        true
                    }
                    R.id.nav_setupwizard        -> {
                        protectionCheck.queryProtection(this@MainActivity, ProtectionCheck.Protection.PREFERENCES, {
                            startActivity(Intent(this@MainActivity, SetupWizardActivity::class.java).setAction("info.nightscout.androidaps.MainActivity"))
                        })
                        true
                    }
                    R.id.nav_about              -> {
                        var message = "Build: ${config.BUILD_VERSION}\n"
                        message += "Flavor: ${BuildConfig.FLAVOR}${BuildConfig.BUILD_TYPE}\n"
                        message += "${rh.gs(app.aaps.plugins.configuration.R.string.configbuilder_nightscoutversion_label)} ${activePlugin.activeNsClient?.detectedNsVersion() ?: rh.gs(app.aaps.plugins.main.R.string.not_available_full)}"
                        if (config.isEngineeringMode()) message += "\n${rh.gs(app.aaps.plugins.configuration.R.string.engineering_mode_enabled)}"
                        if (config.isUnfinishedMode()) message += "\nUnfinished mode enabled"
                        if (!fabricPrivacy.fabricEnabled()) message += "\n${rh.gs(app.aaps.core.ui.R.string.fabric_upload_disabled)}"
                        message += rh.gs(app.aaps.core.ui.R.string.about_link_urls)
                        val messageSpanned = SpannableString(message)
                        Linkify.addLinks(messageSpanned, Linkify.WEB_URLS)
                        MaterialAlertDialogBuilder(this@MainActivity)
                            .setTitle(rh.gs(R.string.app_name) + " " + config.VERSION)
                            .setIcon(iconsProvider.getIcon())
                            .setMessage(messageSpanned)
                            .setPositiveButton(rh.gs(app.aaps.core.ui.R.string.ok), null)
                            .setNeutralButton(rh.gs(app.aaps.core.ui.R.string.cta_dont_kill_my_app_info)) { _, _ ->
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://dontkillmyapp.com/" + Build.MANUFACTURER.lowercase().replace(" ", "-"))))
                            }
                            .create().apply {
                                show()
                                findViewById<TextView>(android.R.id.message)?.movementMethod = LinkMovementMethod.getInstance()
                            }
                        true
                    }
                    R.id.nav_exit               -> {
                        finish()
                        configBuilder.exitApp("Menu", Sources.Aaps, false)
                        true
                    }
                    R.id.nav_plugin_preferences -> {
                        val plugin = (binding.mainPager.adapter as TabPageAdapter).getPluginAt(binding.mainPager.currentItem)
                        protectionCheck.queryProtection(this@MainActivity, ProtectionCheck.Protection.PREFERENCES, {
                            startActivity(Intent(this@MainActivity, PreferencesActivity::class.java).setAction("info.nightscout.androidaps.MainActivity").putExtra(UiInteraction.PLUGIN_NAME, plugin.javaClass.simpleName))
                        })
                        true
                    }
                    R.id.nav_defaultprofile     -> {
                        startActivity(Intent(this@MainActivity, ProfileHelperActivity::class.java).setAction("info.nightscout.androidaps.MainActivity"))
                        true
                    }
                    R.id.nav_stats              -> {
                        startActivity(Intent(this@MainActivity, StatsActivity::class.java).setAction("info.nightscout.androidaps.MainActivity"))
                        true
                    }
                    else                        ->
                        actionBarDrawerToggle?.onOptionsItemSelected(menuItem)!!
                }
        }
        mainMenuProvider?.let { addMenuProvider(it) }
        if (config.appInitialized) setupViews()
    }

    private fun showPasswordVerificationDialog() {
        val maskView = View(this)
        maskView.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        maskView.setBackgroundColor(Color.parseColor("#CC000000"))
        maskView.isClickable = true
        val rootView = window.decorView.findViewById<FrameLayout>(android.R.id.content)
        rootView.addView(maskView)

        val passwordInput = EditText(this)
        passwordInput.inputType = InputType.TYPE_CLASS_NUMBER
        passwordInput.hint = "请输入验证器生成的6位动态密码"
        val padding = dp2px(16)
        passwordInput.setPadding(padding, padding, padding, padding)

        MaterialAlertDialogBuilder(this)
            .setTitle("APP动态密码验证")
            .setView(passwordInput)
            .setCancelable(false)
            .setPositiveButton("验证") { dialog, _ ->
                val inputPwd = passwordInput.text.toString()
                val prefs = getSharedPreferences("AppLock", Context.MODE_PRIVATE)
                val secretBase32 = prefs.getString("totp_secret", null)!!
                val secretBytes = Base32Coder.decode(secretBase32)

                if (verifyTotp(secretBytes, inputPwd)) {
                    getSharedPreferences("AppLock", Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean("password_verified", true)
                        .apply()

                    rootView.removeView(maskView)
                    dialog.dismiss()
                    ToastUtils.okToast(this, "验证成功")
                } else {
                    ToastUtils.errorToast(this, "动态密码错误")
                    dialog.dismiss()
                    Handler(Looper.getMainLooper()).post { showPasswordVerificationDialog() }
                }
            }
            .setNegativeButton("退出") { _, _ ->
                finish()
            }
            .show()
    }

    private fun dp2px(dp: Int): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics).toInt()
    }

    private fun start() {
        binding.splash.visibility = View.GONE
        setUserStats()
        setupViews()

        if (startWizard() && !isRunningRealPumpTest()) {
            protectionCheck.queryProtection(this, ProtectionCheck.Protection.PREFERENCES, {
                startActivity(Intent(this, SetupWizardActivity::class.java).setAction("info.nightscout.androidaps.MainActivity"))
            })
        }
        androidPermission.notifyForStoragePermission(this)
        androidPermission.notifyForBatteryOptimizationPermission(this)
        if (!config.AAPSCLIENT) androidPermission.notifyForLocationPermissions(this)
        if (config.PUMPDRIVERS) {
            if (smsCommunicator.isEnabled()) androidPermission.notifyForSMSPermissions(this)
            androidPermission.notifyForSystemWindowPermissions(this)
            androidPermission.notifyForBtConnectPermission(this)
        }
        passwordResetCheck(this)
        exportPasswordResetCheck(this)

        if (config.isDev() && preferences.get(StringKey.MaintenanceIdentification).isBlank())
            uiInteraction.addNotificationWithAction(
                id = Notification.IDENTIFICATION_NOT_SET,
                text = rh.gs(R.string.identification_not_set),
                level = Notification.INFO,
                buttonText = R.string.set,
                action = Runnable {
                    preferences.put(BooleanKey.GeneralSimpleMode, false)
                    startActivity(Intent(this@MainActivity, PreferencesActivity::class.java).setAction("info.nightscout.androidaps.MainActivity").putExtra(UiInteraction.PLUGIN_NAME, MaintenancePlugin::class.java.simpleName))
                },
                validityCheck = { config.isDev() && preferences.get(StringKey.MaintenanceIdentification).isBlank() }
            )

        if (preferences.get(StringKey.ProtectionMasterPassword) == "")
            uiInteraction.addNotificationWithAction(
                id = Notification.MASTER_PASSWORD_NOT_SET,
                text = rh.gs(app.aaps.core.ui.R.string.master_password_not_set),
                level = Notification.NORMAL,
                buttonText = R.string.set,
                action = { startActivity(Intent(this@MainActivity, PreferencesActivity::class.java).setAction("info.nightscout.androidaps.MainActivity").putExtra(UiInteraction.PREFERENCE, UiInteraction.Preferences.PROTECTION)) },
                validityCheck = { preferences.get(StringKey.ProtectionMasterPassword) == "" }
            )
        if (preferences.getIfExists(StringKey.AapsDirectoryUri).isNullOrEmpty())
            uiInteraction.addNotificationWithAction(
                id = Notification.AAPS_DIR_NOT_SELECTED,
                text = rh.gs(app.aaps.core.ui.R.string.aaps_directory_not_selected),
                level = Notification.IMPORTANCE_HIGH,
                buttonText = R.string.select,
                action = { maintenancePlugin.selectAapsDirectory(this) },
                validityCheck = { preferences.getIfExists(StringKey.AapsDirectoryUri).isNullOrEmpty() }
            )
    }

    private fun startWizard(): Boolean = !preferences.get(BooleanKey.GeneralSetupWizardProcessed)

    override fun onPostCreate(savedInstanceState: Bundle?, persistentState: PersistableBundle?) {
        super.onPostCreate(savedInstanceState, persistentState)
        actionBarDrawerToggle?.syncState()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.mainPager.adapter = null
        binding.mainDrawerLayout.removeDrawerListener(actionBarDrawerToggle!!)
        mainMenuProvider?.let { removeMenuProvider(it) }
        disposable.clear()
    }

    override fun onResume() {
        super.onResume()
        if (config.appInitialized) binding.splash.visibility = View.GONE
        if (!isProtectionCheckActive) {
            isProtectionCheckActive = true
            protectionCheck.queryProtection(this, ProtectionCheck.Protection.APPLICATION, UIRunnable { isProtectionCheckActive = false },
                                            UIRunnable { OKDialog.show(this, "", rh.gs(R.string.authorizationfailed), true) { isProtectionCheckActive = false; finish() } },
                                            UIRunnable { OKDialog.show(this, "", rh.gs(R.string.authorizationfailed), true) { isProtectionCheckActive = false; finish() } }
            )
        }
    }

    private fun setWakeLock() {
        val keepScreenOn = preferences.get(BooleanKey.OverviewKeepScreenOn)
        if (keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun processPreferenceChange(ev: EventPreferenceChange) {
        if (ev.isChanged(BooleanKey.OverviewKeepScreenOn.key)) setWakeLock()
        if (ev.isChanged(StringKey.GeneralSkin.key)) recreate()
    }

    private fun setupViews() {
        val pageAdapter = TabPageAdapter(this)
        binding.mainNavigationView.setNavigationItemSelectedListener { true }
        val menu = binding.mainNavigationView.menu.also { it.clear() }
        for (p in activePlugin.getPluginsList())
            if (p.isEnabled() && p.hasFragment() && p.showInList(p.getType())) {
                if ((preferences.simpleMode && p.pluginDescription.simpleModePosition == PluginDescription.Position.TAB) || (!preferences.simpleMode && p.isFragmentVisible()))
                    pageAdapter.registerNewFragment(p)
                if ((preferences.simpleMode && !p.pluginDescription.neverVisible && p.pluginDescription.simpleModePosition == PluginDescription.Position.MENU) || (!preferences.simpleMode && !p.pluginDescription.neverVisible && !p.isFragmentVisible())) {
                    val menuItem = menu.add(p.name)
                    menuItem.isCheckable = true
                    if (p.menuIcon != -1) menuItem.setIcon(p.menuIcon) else menuItem.setIcon(app.aaps.core.ui.R.drawable.ic_settings)
                    menuItem.setOnMenuItemClickListener {
                        startActivity(Intent(this, SingleFragmentActivity::class.java).setAction(this::class.simpleName).putExtra("plugin", activePlugin.getPluginsList().indexOf(p)))
                        binding.mainDrawerLayout.closeDrawers()
                        true
                    }
                }
            }
        binding.mainPager.adapter = pageAdapter
        binding.mainPager.offscreenPageLimit = 8

        if (preferences.get(BooleanKey.OverviewShortTabTitles)) {
            binding.tabsNormal.visibility = View.GONE
            binding.tabsCompact.visibility = View.VISIBLE
            binding.toolbar.layoutParams = LinearLayout.LayoutParams(Toolbar.LayoutParams.MATCH_PARENT, resources.getDimension(app.aaps.core.ui.R.dimen.compact_height).toInt())
            TabLayoutMediator(binding.tabsCompact, binding.mainPager) { tab, position ->
                tab.text = (binding.mainPager.adapter as TabPageAdapter).getPluginAt(position).nameShort
            }.attach()
        } else {
            binding.tabsNormal.visibility = View.VISIBLE
            binding.tabsCompact.visibility = View.GONE
            val typedValue = TypedValue()
            if (theme.resolveAttribute(android.R.attr.actionBarSize, typedValue, true)) {
                binding.toolbar.layoutParams = LinearLayout.LayoutParams(
                    Toolbar.LayoutParams.MATCH_PARENT,
                    TypedValue.complexToDimensionPixelSize(typedValue.data, resources.displayMetrics)
                )
            }
            TabLayoutMediator(binding.tabsNormal, binding.mainPager) { tab, position ->
                tab.text = (binding.mainPager.adapter as TabPageAdapter).getPluginAt(position).name
            }.attach()
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        return super.dispatchTouchEvent(event)
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        menuOpen = true
        if (binding.mainDrawerLayout.isDrawerOpen(GravityCompat.START)) binding.mainDrawerLayout.closeDrawers()
        val result = super.onMenuOpened(featureId, menu)
        menu.findItem(R.id.nav_treatments)?.isEnabled = profileFunction.getProfile() != null
        if (binding.mainPager.currentItem >= 0) {
            val plugin = (binding.mainPager.adapter as TabPageAdapter?)?.getPluginAt(binding.mainPager.currentItem) ?: return result
            this.menu?.findItem(R.id.nav_plugin_preferences)?.title = rh.gs(R.string.nav_preferences_plugin, plugin.name)
            pluginPreferencesMenuItem?.isEnabled = (binding.mainPager.adapter as TabPageAdapter).getPluginAt(binding.mainPager.currentItem).preferencesId != PluginDescription.PREFERENCE_NONE
        }
        if (pluginPreferencesMenuItem?.isEnabled == false) {
            val spanString = SpannableString(this.menu?.findItem(R.id.nav_plugin_preferences)?.title.toString())
            spanString.setSpan(ForegroundColorSpan(rh.gac(app.aaps.core.ui.R.attr.disabledTextColor)), 0, spanString.length, 0)
            this.menu?.findItem(R.id.nav_plugin_preferences)?.title = spanString
        }
        return result
    }

    override fun onPanelClosed(featureId: Int, menu: Menu) {
        menuOpen = false
        super.onPanelClosed(featureId, menu)
    }

    private fun setUserStats() {
        if (!fabricPrivacy.fabricEnabled()) return
        val closedLoopEnabled = if (constraintChecker.isClosedLoopAllowed().value()) "CLOSED_LOOP_ENABLED" else "CLOSED_LOOP_DISABLED"
        val remote = config.REMOTE.lowercase(Locale.getDefault()).replace("https://","").replace("http://","").replace(".git","").replace(".com/",":").replace(".org/",":").replace(".net/",":")
        fabricPrivacy.setUserProperty("Mode", config.APPLICATION_ID + "-" + closedLoopEnabled)
        fabricPrivacy.setUserProperty("Language", preferences.getIfExists(StringKey.GeneralLanguage) ?: Locale.getDefault().language)
        fabricPrivacy.setUserProperty("Version", config.VERSION_NAME)
        fabricPrivacy.setUserProperty("HEAD", BuildConfig.HEAD)
        fabricPrivacy.setUserProperty("Remote", remote)
        val hashes = signatureVerifierPlugin.shortHashes()
        if (hashes.isNotEmpty()) fabricPrivacy.setUserProperty("Hash", hashes[0])
        activePlugin.activePump.let { fabricPrivacy.setUserProperty("Pump", it::class.java.simpleName) }
        if (!config.AAPSCLIENT && !config.PUMPCONTROL) activePlugin.activeAPS.let { fabricPrivacy.setUserProperty("Aps", it::class.java.simpleName) }
        activePlugin.activeBgSource.let { fabricPrivacy.setUserProperty("BgSource", it::class.java.simpleName) }
        fabricPrivacy.setUserProperty("Profile", activePlugin.activeProfileSource.javaClass.simpleName)
        activePlugin.activeSensitivity.let { fabricPrivacy.setUserProperty("Sensitivity", it::class.java.simpleName) }
        activePlugin.activeInsulin.let { fabricPrivacy.setUserProperty("Insulin", it::class.java.simpleName) }

        FirebaseCrashlytics.getInstance().setCustomKey("HEAD", BuildConfig.HEAD)
        FirebaseCrashlytics.getInstance().setCustomKey("Version", config.VERSION_NAME)
        FirebaseCrashlytics.getInstance().setCustomKey("BuildType", config.BUILD_TYPE)
        FirebaseCrashlytics.getInstance().setCustomKey("BuildFlavor", config.FLAVOR)
        FirebaseCrashlytics.getInstance().setCustomKey("Remote", remote)
        FirebaseCrashlytics.getInstance().setCustomKey("Committed", config.COMMITTED)
        if (hashes.isNotEmpty()) FirebaseCrashlytics.getInstance().setCustomKey("Hash", hashes[0])
        FirebaseCrashlytics.getInstance().setCustomKey("Email", preferences.get(StringKey.MaintenanceIdentification))
    }

    private fun passwordResetCheck(context: Context) {
        val fh = fileListProvider.ensureExtraDirExists()?.findFile("PasswordReset")
        if (fh?.exists() == true) {
            Thread {
                while (activePlugin.activePump.serialNumber().isEmpty()) Thread.sleep(100)
                preferences.put(StringKey.ProtectionMasterPassword, cryptoUtil.hashPassword(activePlugin.activePump.serialNumber()))
                fh.delete()
                exportPasswordDataStore.clearPasswordDataStore(context)
                ToastUtils.okToast(context, context.getString(app.aaps.core.ui.R.string.password_set))
            }.start()
        }
    }

    private fun exportPasswordResetCheck(context: Context) {
        val fh = fileListProvider.ensureExtraDirExists()?.findFile("ExportPasswordReset")
        if (fh?.exists() == true) {
            exportPasswordDataStore.clearPasswordDataStore(context)
            fh.delete()
            ToastUtils.okToast(context, context.getString(app.aaps.core.ui.R.string.datastore_password_cleared))
        }
    }
}