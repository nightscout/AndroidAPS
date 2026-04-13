package app.aaps

import android.content.Context
import android.content.Intent
import android.graphics.Color
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
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
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

    // 安全 Base32（全异常捕获）
    private object Base32Coder {
        private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        fun encode(input: ByteArray): String {
            return try {
                val output = StringBuilder()
                var buffer = 0
                var bitsLeft = 0
                for (b in input) {
                    buffer = (buffer shl 8) or (b.toInt() and 0xFF)
                    bitsLeft += 8
                    while (bitsLeft >= 5) {
                        bitsLeft -= 5
                        output.append(ALPHABET[(buffer shr bitsLeft) and 0x1F])
                    }
                }
                if (bitsLeft > 0) {
                    buffer = buffer shl (5 - bitsLeft)
                    output.append(ALPHABET[buffer and 0x1F])
                }
                output.toString()
            } catch (e: Exception) {
                e.printStackTrace()
                ""
            }
        }

        fun decode(input: String): ByteArray? {
            return try {
                val clean = input.uppercase().trimEnd('=')
                val output = ByteArray(clean.length * 5 / 8)
                var buffer = 0
                var bitsLeft = 0
                var idx = 0
                for (c in clean) {
                    val v = ALPHABET.indexOf(c)
                    if (v < 0) return null
                    buffer = (buffer shl 5) or v
                    bitsLeft += 5
                    if (bitsLeft >= 8) {
                        output[idx++] = (buffer shr (bitsLeft - 8)).toByte()
                        bitsLeft -= 8
                    }
                }
                output.copyOf(idx)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    // 安全 TOTP 验证（全try-catch）
    private fun verifyTotpSafe(secret: ByteArray?, code: String, tolerance: Int = 1): Boolean {
        if (secret == null || code.length != 6) return false
        return try {
            val num = code.toIntOrNull() ?: return false
            val step = 30_000L
            val now = System.currentTimeMillis()
            for (offset in -tolerance..tolerance) {
                val counter = (now + offset * step) / step
                if (generateTotpCodeSafe(secret, counter) == num) return true
            }
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun generateTotpCodeSafe(secret: ByteArray, counter: Long): Int? {
        return try {
            val counterBytes = ByteArray(8)
            var tmp = counter
            for (i in 7 downTo 0) {
                counterBytes[i] = (tmp and 0xFF).toByte()
                tmp = tmp shr 8
            }
            val mac = Mac.getInstance("HmacSHA1")
            mac.init(SecretKeySpec(secret, "HmacSHA1"))
            val hmac = mac.doFinal(counterBytes)
            val offset = hmac.last().toInt() and 0x1F
            val binary = ((hmac[offset].toInt() and 0x7F) shl 24) or
                ((hmac[offset+1].toInt() and 0xFF) shl 16) or
                ((hmac[offset+2].toInt() and 0xFF) shl 8) or
                (hmac[offset+3].toInt() and 0xFF)
            binary % 1_000_000
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
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

    private var isDialogActive = false
    private var currentVerifyDialog: AlertDialog? = null

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

        // 超安全延迟启动
        Handler(Looper.getMainLooper()).postDelayed({
                                                        if (!isFinishing && !isDestroyed) {
                                                            safeCheckAuthFlow()
                                                        }
                                                    }, 500)
    }

    /**
     * 全安全认证流程
     */
    private fun safeCheckAuthFlow() {
        if (isDialogActive || isFinishing || isDestroyed) return
        val prefs = getSharedPreferences("AppLock", Context.MODE_PRIVATE)
        val secret = prefs.getString("totp_secret", null)
        val verified = prefs.getBoolean("password_verified", false)

        when {
            secret == null -> {
                isDialogActive = true
                showSafeFirstTimeSetup { success ->
                    isDialogActive = false
                    if (success) safeCheckAuthFlow() else finish()
                }
            }
            !verified -> {
                isDialogActive = true
                showSafeTotpVerification { success ->
                    isDialogActive = false
                    if (!success) safeCheckAuthFlow()
                }
            }
            else -> Unit
        }
    }

    /**
     * 安全首次设置（全try-catch）
     */
    private fun showSafeFirstTimeSetup(onResult: (Boolean) -> Unit) {
        if (isFinishing || isDestroyed) { onResult(false); return }

        val secretBytes = ByteArray(20).apply { SecureRandom().nextBytes(this) }
        val secretBase32 = Base32Coder.encode(secretBytes)

        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp2px(16)
            setPadding(pad, pad, pad, pad)
            addView(TextView(this@MainActivity).apply {
                text = getString(R.string.totp_setup_hint)
                textSize = 14f
            })
            addView(TextView(this@MainActivity).apply {
                text = getString(R.string.totp_secret_label, secretBase32)
                textSize = 18f
                setTextColor(Color.BLUE)
                setPadding(0, dp2px(12), 0, dp2px(12))
            })
            addView(EditText(this@MainActivity).apply {
                id = android.R.id.input
                inputType = InputType.TYPE_CLASS_NUMBER
                hint = getString(R.string.totp_input_hint)
            })
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.totp_setup_title)
            .setView(dialogView)
            .setCancelable(false)
            .setPositiveButton(R.string.ok) { dialog, _ ->
                try {
                    val code = dialogView.findViewById<EditText>(android.R.id.input).text.toString().trim()
                    val valid = verifyTotpSafe(secretBytes, code)

                    if (valid) {
                        // 安全保存
                        getSharedPreferences("AppLock", MODE_PRIVATE)
                            .edit()
                            .putString("totp_secret", secretBase32)
                            .apply()
                        // 安全Toast
                        runOnUiThreadSafely {
                            ToastUtils.okToast(this@MainActivity, getString(R.string.totp_setup_success))
                        }
                        dialog.dismiss()
                        onResult(true)
                    } else {
                        runOnUiThreadSafely {
                            ToastUtils.errorToast(this@MainActivity, getString(R.string.totp_error_wrong))
                        }
                        dialog.dismiss()
                        onResult(false)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    runOnUiThreadSafely {
                        ToastUtils.errorToast(this@MainActivity, getString(R.string.totp_error_exception, e.message ?: ""))
                    }
                    dialog.dismiss()
                    onResult(false)
                }
            }
            .setNegativeButton(R.string.exit) { _, _ ->
                onResult(false)
                finish()
            }
            .show()
    }

    /**
     * 安全验证（全try-catch、顺序严格）
     */
    private fun showSafeTotpVerification(onResult: (Boolean) -> Unit) {
        if (isFinishing || isDestroyed) { onResult(false); return }

        val maskView = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#CC000000"))
            isClickable = true
        }
        val rootContent = window.decorView.findViewById<FrameLayout>(android.R.id.content)
        runOnUiThreadSafely {
            rootContent.addView(maskView)
        }

        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.totp_verify_hint)
            setPadding(dp2px(16), dp2px(16), dp2px(16), dp2px(16))
        }

        currentVerifyDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.totp_verify_title)
            .setView(input)
            .setCancelable(false)
            .setPositiveButton(R.string.ok) { dialog, _ ->
                try {
                    val code = input.text.toString().trim()
                    val prefs = getSharedPreferences("AppLock", Context.MODE_PRIVATE)
                    val secretBase32 = prefs.getString("totp_secret", null)

                    if (secretBase32.isNullOrEmpty()) {
                        runOnUiThreadSafely {
                            ToastUtils.errorToast(this@MainActivity, getString(R.string.totp_error_secret_lost))
                        }
                        safeCleanup(maskView, rootContent)
                        onResult(false)
                        return@setPositiveButton
                    }

                    val secretBytes = Base32Coder.decode(secretBase32)
                    val valid = verifyTotpSafe(secretBytes, code)

                    if (valid) {
                        // 顺序：清mask → 存状态 → Toast → 关对话框
                        safeCleanup(maskView, rootContent)
                        prefs.edit().putBoolean("password_verified", true).apply()
                        runOnUiThreadSafely {
                            ToastUtils.okToast(this@MainActivity, getString(R.string.totp_verify_success))
                        }
                        dialog.dismiss()
                        onResult(true)
                    } else {
                        runOnUiThreadSafely {
                            ToastUtils.errorToast(this@MainActivity, getString(R.string.totp_error_wrong))
                        }
                        safeCleanup(maskView, rootContent)
                        dialog.dismiss()
                        onResult(false)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    runOnUiThreadSafely {
                        ToastUtils.errorToast(this@MainActivity, getString(R.string.totp_error_exception, e.message ?: ""))
                    }
                    safeCleanup(maskView, rootContent)
                    dialog.dismiss()
                    onResult(false)
                }
            }
            .setNegativeButton(R.string.exit) { _, _ ->
                safeCleanup(maskView, rootContent)
                onResult(false)
                finish()
            }
            .create()
        currentVerifyDialog?.show()
    }

    // 安全清理UI
    private fun safeCleanup(maskView: View, root: ViewGroup) {
        runOnUiThreadSafely {
            currentVerifyDialog?.dismiss()
            currentVerifyDialog = null
            root.removeView(maskView)
        }
    }

    // 安全运行UI（防Token失效）
    private fun runOnUiThreadSafely(block: () -> Unit) {
        if (!isFinishing && !isDestroyed) {
            Handler(Looper.getMainLooper()).post(block)
        }
    }

    // ------------------------------
    // 下面原有代码保持不变
    // ------------------------------
    private fun initAllComponents(savedInstanceState: Bundle?) {
        actionBarDrawerToggle = ActionBarDrawerToggle(this, binding.mainDrawerLayout,
                                                      R.string.open_navigation, R.string.close_navigation).also {
            binding.mainDrawerLayout.addDrawerListener(it)
            it.syncState()
        }

        processPreferenceChange(EventPreferenceChange(BooleanKey.OverviewKeepScreenOn.key))

        disposable += rxBus
            .toObservable(EventRebuildTabs::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           if (it.recreate) recreate() else setupViews()
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
                when {
                    binding.mainDrawerLayout.isDrawerOpen(GravityCompat.START) ->
                        binding.mainDrawerLayout.closeDrawers()
                    menuOpen -> menu?.close()
                    binding.mainPager.currentItem != 0 ->
                        binding.mainPager.currentItem = 0
                    else -> finish()
                }
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
                    R.id.nav_preferences -> {
                        protectionCheck.queryProtection(this@MainActivity, ProtectionCheck.Protection.PREFERENCES, {
                            startActivity(Intent(this@MainActivity, PreferencesActivity::class.java)
                                              .setAction("info.nightscout.androidaps.MainActivity"))
                        })
                        true
                    }
                    R.id.nav_historybrowser -> {
                        startActivity(Intent(this@MainActivity, HistoryBrowseActivity::class.java)
                                          .setAction("info.nightscout.androidaps.MainActivity"))
                        true
                    }
                    R.id.nav_treatments -> {
                        startActivity(Intent(this@MainActivity, TreatmentsActivity::class.java)
                                          .setAction("info.nightscout.androidaps.MainActivity"))
                        true
                    }
                    R.id.nav_setupwizard -> {
                        protectionCheck.queryProtection(this@MainActivity, ProtectionCheck.Protection.PREFERENCES, {
                            startActivity(Intent(this@MainActivity, SetupWizardActivity::class.java)
                                              .setAction("info.nightscout.androidaps.MainActivity"))
                        })
                        true
                    }
                    R.id.nav_about -> {
                        var message = "Build: ${config.BUILD_VERSION}\n"
                        message += "Flavor: ${BuildConfig.FLAVOR}${BuildConfig.BUILD_TYPE}\n"
                        message += "${rh.gs(app.aaps.plugins.configuration.R.string.configbuilder_nightscoutversion_label)} ${activePlugin.activeNsClient?.detectedNsVersion() ?: rh.gs(app.aaps.plugins.main.R.string.not_available_full)}"
                        if (config.isEngineeringMode()) message += "\n${rh.gs(app.aaps.plugins.configuration.R.string.engineering_mode_enabled)}"
                        if (config.isUnfinishedMode()) message += "\nUnfinished mode enabled"
                        if (!fabricPrivacy.fabricEnabled()) message += "\n${rh.gs(app.aaps.core.ui.R.string.fabric_upload_disabled)}"
                        message += rh.gs(app.aaps.core.ui.R.string.about_link_urls)
                        val spanned = SpannableString(message)
                        Linkify.addLinks(spanned, Linkify.WEB_URLS)
                        MaterialAlertDialogBuilder(this@MainActivity)
                            .setTitle(rh.gs(R.string.app_name) + " " + config.VERSION)
                            .setIcon(iconsProvider.getIcon())
                            .setMessage(spanned)
                            .setPositiveButton(rh.gs(app.aaps.core.ui.R.string.ok), null)
                            .setNeutralButton(rh.gs(app.aaps.core.ui.R.string.cta_dont_kill_my_app_info)) { _, _ ->
                                startActivity(Intent(Intent.ACTION_VIEW,
                                                     Uri.parse("https://dontkillmyapp.com/${Build.MANUFACTURER.lowercase().replace(" ", "-")}")))
                            }
                            .create().apply {
                                show()
                                findViewById<TextView>(android.R.id.message)?.movementMethod = LinkMovementMethod.getInstance()
                            }
                        true
                    }
                    R.id.nav_exit -> {
                        finish()
                        configBuilder.exitApp("Menu", Sources.Aaps, false)
                        true
                    }
                    R.id.nav_plugin_preferences -> {
                        val plugin = (binding.mainPager.adapter as TabPageAdapter)
                            .getPluginAt(binding.mainPager.currentItem)
                        protectionCheck.queryProtection(this@MainActivity, ProtectionCheck.Protection.PREFERENCES, {
                            startActivity(Intent(this@MainActivity, PreferencesActivity::class.java)
                                              .setAction("info.nightscout.androidaps.MainActivity")
                                              .putExtra(UiInteraction.PLUGIN_NAME, plugin.javaClass.simpleName))
                        })
                        true
                    }
                    R.id.nav_defaultprofile -> {
                        startActivity(Intent(this@MainActivity, ProfileHelperActivity::class.java)
                                          .setAction("info.nightscout.androidaps.MainActivity"))
                        true
                    }
                    R.id.nav_stats -> {
                        startActivity(Intent(this@MainActivity, StatsActivity::class.java)
                                          .setAction("info.nightscout.androidaps.MainActivity"))
                        true
                    }
                    else -> actionBarDrawerToggle?.onOptionsItemSelected(menuItem)!!
                }
        }
        mainMenuProvider?.let { addMenuProvider(it) }
        if (config.appInitialized) setupViews()
    }

    private fun dp2px(dp: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics).toInt()

    private fun start() {
        binding.splash.visibility = View.GONE
        setUserStats()
        setupViews()

        if (startWizard() && !isRunningRealPumpTest()) {
            protectionCheck.queryProtection(this, ProtectionCheck.Protection.PREFERENCES, {
                startActivity(Intent(this, SetupWizardActivity::class.java)
                                  .setAction("info.nightscout.androidaps.MainActivity"))
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
                    startActivity(Intent(this@MainActivity, PreferencesActivity::class.java)
                                      .setAction("info.nightscout.androidaps.MainActivity")
                                      .putExtra(UiInteraction.PLUGIN_NAME, MaintenancePlugin::class.java.simpleName))
                },
                validityCheck = { config.isDev() && preferences.get(StringKey.MaintenanceIdentification).isBlank() }
            )

        if (preferences.get(StringKey.ProtectionMasterPassword) == "")
            uiInteraction.addNotificationWithAction(
                id = Notification.MASTER_PASSWORD_NOT_SET,
                text = rh.gs(app.aaps.core.ui.R.string.master_password_not_set),
                level = Notification.NORMAL,
                buttonText = R.string.set,
                action = {
                    startActivity(Intent(this@MainActivity, PreferencesActivity::class.java)
                                      .setAction("info.nightscout.androidaps.MainActivity")
                                      .putExtra(UiInteraction.PREFERENCE, UiInteraction.Preferences.PROTECTION))
                },
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
            protectionCheck.queryProtection(
                this, ProtectionCheck.Protection.APPLICATION,
                UIRunnable { isProtectionCheckActive = false },
                UIRunnable {
                    OKDialog.show(this, "", rh.gs(R.string.authorizationfailed), true) {
                        isProtectionCheckActive = false; finish()
                    }
                },
                UIRunnable {
                    OKDialog.show(this, "", rh.gs(R.string.authorizationfailed), true) {
                        isProtectionCheckActive = false; finish()
                    }
                }
            )
        }
    }

    private fun setWakeLock() {
        val keepOn = preferences.get(BooleanKey.OverviewKeepScreenOn)
        if (keepOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun processPreferenceChange(ev: EventPreferenceChange) {
        if (ev.isChanged(BooleanKey.OverviewKeepScreenOn.key)) setWakeLock()
        if (ev.isChanged(StringKey.GeneralSkin.key)) recreate()
    }

    private fun setupViews() {
        val adapter = TabPageAdapter(this)
        binding.mainNavigationView.setNavigationItemSelectedListener { true }
        val menu = binding.mainNavigationView.menu.apply { clear() }

        for (p in activePlugin.getPluginsList()) {
            if (p.isEnabled() && p.hasFragment() && p.showInList(p.getType())) {
                val simple = preferences.simpleMode
                if ((simple && p.pluginDescription.simpleModePosition == PluginDescription.Position.TAB) ||
                    (!simple && p.isFragmentVisible())) {
                    adapter.registerNewFragment(p)
                }
                if ((simple && !p.pluginDescription.neverVisible && p.pluginDescription.simpleModePosition == PluginDescription.Position.MENU) ||
                    (!simple && !p.pluginDescription.neverVisible && !p.isFragmentVisible())) {
                    val item = menu.add(p.name)
                    item.isCheckable = true
                    item.setIcon(if (p.menuIcon != -1) p.menuIcon else app.aaps.core.ui.R.drawable.ic_settings)
                    item.setOnMenuItemClickListener {
                        startActivity(Intent(this, SingleFragmentActivity::class.java)
                                          .setAction(this::class.simpleName)
                                          .putExtra("plugin", activePlugin.getPluginsList().indexOf(p)))
                        binding.mainDrawerLayout.closeDrawers()
                        true
                    }
                }
            }
        }

        binding.mainPager.adapter = adapter
        binding.mainPager.offscreenPageLimit = 8

        if (preferences.get(BooleanKey.OverviewShortTabTitles)) {
            binding.tabsNormal.visibility = View.GONE
            binding.tabsCompact.visibility = View.VISIBLE
            binding.toolbar.layoutParams = LinearLayout.LayoutParams(
                Toolbar.LayoutParams.MATCH_PARENT,
                resources.getDimension(app.aaps.core.ui.R.dimen.compact_height).toInt()
            )
            TabLayoutMediator(binding.tabsCompact, binding.mainPager) { tab, pos ->
                tab.text = adapter.getPluginAt(pos).nameShort
            }.attach()
        } else {
            binding.tabsNormal.visibility = View.VISIBLE
            binding.tabsCompact.visibility = View.GONE
            val tv = TypedValue()
            if (theme.resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
                binding.toolbar.layoutParams = LinearLayout.LayoutParams(
                    Toolbar.LayoutParams.MATCH_PARENT,
                    TypedValue.complexToDimensionPixelSize(tv.data, resources.displayMetrics)
                )
            }
            TabLayoutMediator(binding.tabsNormal, binding.mainPager) { tab, pos ->
                tab.text = adapter.getPluginAt(pos).name
            }.attach()
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean = super.dispatchTouchEvent(ev)

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        menuOpen = true
        if (binding.mainDrawerLayout.isDrawerOpen(GravityCompat.START))
            binding.mainDrawerLayout.closeDrawers()
        val ret = super.onMenuOpened(featureId, menu)
        menu.findItem(R.id.nav_treatments)?.isEnabled = profileFunction.getProfile() != null

        if (binding.mainPager.currentItem >= 0) {
            val plugin = (binding.mainPager.adapter as TabPageAdapter?)
                ?.getPluginAt(binding.mainPager.currentItem) ?: return ret
            this.menu?.findItem(R.id.nav_plugin_preferences)?.title =
                rh.gs(R.string.nav_preferences_plugin, plugin.name)
            pluginPreferencesMenuItem?.isEnabled =
                plugin.preferencesId != PluginDescription.PREFERENCE_NONE
        }

        if (pluginPreferencesMenuItem?.isEnabled == false) {
            val span = SpannableString(this.menu?.findItem(R.id.nav_plugin_preferences)?.title.toString())
            span.setSpan(ForegroundColorSpan(rh.gac(app.aaps.core.ui.R.attr.disabledTextColor)),
                         0, span.length, 0)
            this.menu?.findItem(R.id.nav_plugin_preferences)?.title = span
        }
        return ret
    }

    override fun onPanelClosed(featureId: Int, menu: Menu) {
        menuOpen = false
        super.onPanelClosed(featureId, menu)
    }

    private fun setUserStats() {
        if (!fabricPrivacy.fabricEnabled()) return
        val loopMode = if (constraintChecker.isClosedLoopAllowed().value())
            "CLOSED_LOOP_ENABLED" else "CLOSED_LOOP_DISABLED"
        val remote = config.REMOTE.lowercase(Locale.getDefault())
            .replace("https://","").replace("http://","")
            .replace(".git","").replace(".com/",":")
            .replace(".org/",":").replace(".net/",":")

        fabricPrivacy.setUserProperty("Mode", "${config.APPLICATION_ID}-$loopMode")
        fabricPrivacy.setUserProperty("Language", preferences.getIfExists(StringKey.GeneralLanguage) ?: Locale.getDefault().language)
        fabricPrivacy.setUserProperty("Version", config.VERSION_NAME)
        fabricPrivacy.setUserProperty("HEAD", BuildConfig.HEAD)
        fabricPrivacy.setUserProperty("Remote", remote)
        signatureVerifierPlugin.shortHashes().firstOrNull()?.let {
            fabricPrivacy.setUserProperty("Hash", it)
        }
        activePlugin.activePump.let { fabricPrivacy.setUserProperty("Pump", it::class.java.simpleName) }
        if (!config.AAPSCLIENT && !config.PUMPCONTROL)
            activePlugin.activeAPS.let { fabricPrivacy.setUserProperty("Aps", it::class.java.simpleName) }
        activePlugin.activeBgSource.let { fabricPrivacy.setUserProperty("BgSource", it::class.java.simpleName) }
        fabricPrivacy.setUserProperty("Profile", activePlugin.activeProfileSource.javaClass.simpleName)
        activePlugin.activeSensitivity.let { fabricPrivacy.setUserProperty("Sensitivity", it::class.java.simpleName) }
        activePlugin.activeInsulin.let { fabricPrivacy.setUserProperty("Insulin", it::class.java.simpleName) }

        FirebaseCrashlytics.getInstance().apply {
            setCustomKey("HEAD", BuildConfig.HEAD)
            setCustomKey("Version", config.VERSION_NAME)
            setCustomKey("BuildType", config.BUILD_TYPE)
            setCustomKey("BuildFlavor", config.FLAVOR)
            setCustomKey("Remote", remote)
            setCustomKey("Committed", config.COMMITTED)
            signatureVerifierPlugin.shortHashes().firstOrNull()?.let {
                setCustomKey("Hash", it)
            }
            setCustomKey("Email", preferences.get(StringKey.MaintenanceIdentification))
        }
    }

    private fun passwordResetCheck(context: Context) {
        val fh = fileListProvider.ensureExtraDirExists()?.findFile("PasswordReset")
        if (fh?.exists() == true) {
            Thread {
                while (activePlugin.activePump.serialNumber().isEmpty()) Thread.sleep(100)
                preferences.put(StringKey.ProtectionMasterPassword,
                                cryptoUtil.hashPassword(activePlugin.activePump.serialNumber()))
                fh.delete()
                exportPasswordDataStore.clearPasswordDataStore(context)
                runOnUiThread {
                    ToastUtils.okToast(context, context.getString(app.aaps.core.ui.R.string.password_set))
                }
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