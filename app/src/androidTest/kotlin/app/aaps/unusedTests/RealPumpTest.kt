package app.aaps.unusedTests

//@LargeTest
//@RunWith(AndroidJUnit4::class)
class RealPumpTest {
    /*
        companion object {
            const val R_PASSWORD = 1234
            const val R_SERIAL = "PBB00013LR_P"
        }

        private val validProfile = "{\"dia\":\"6\",\"carbratio\":[{\"time\":\"00:00\",\"value\":\"30\"}],\"carbs_hr\":\"20\",\"delay\":\"20\",\"sens\":[{\"time\":\"00:00\",\"value\":\"10\"},{\"time\":\"2:00\",\"value\":\"11\"}],\"timezone\":\"UTC\",\"basal\":[{\"time\":\"00:00\",\"value\":\"0.1\"}],\"target_low\":[{\"time\":\"00:00\",\"value\":\"4\"}],\"target_high\":[{\"time\":\"00:00\",\"value\":\"5\"}],\"startDate\":\"1970-01-01T00:00:00.000Z\",\"units\":\"mmol\"}"

        @Inject lateinit var pump : app.aaps.pump.danarv2.DanaRv2Plugin
        @Inject lateinit var randomBgPlugin :RandomBgPlugin
        @Inject lateinit var localProfilePlugin: LocalProfilePlugin
        @Inject lateinit var profileFunction: ProfileFunction
        @Inject lateinit var insulinOrefUltraRapidActingPlugin: InsulinOrefUltraRapidActingPlugin
        @Inject lateinit var sensitivityOref1Plugin: SensitivityOref1Plugin
        @Inject lateinit var openAPSSMBPlugin: OpenAPSSMBPlugin
        @Inject lateinit var loopPlugin: LoopPlugin
        @Inject lateinit var actionsPlugin: ActionsPlugin
        @Inject lateinit var configBuilderPlugin: ConfigBuilderPlugin
        @Inject lateinit var objectivesPlugin: ObjectivesPlugin
        @Inject lateinit var treatmentsPlugin: TreatmentsPlugin
        @Inject lateinit var preferences: Preferences

        @Rule
        @JvmField
        var mActivityTestRule = ActivityTestRule(MainActivity::class.java)

        @Rule
        @JvmField
        var mGrantPermissionRule: GrantPermissionRule =
            GrantPermissionRule.grant(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            )

        @Before
        fun clear() {
            sp.clear()
            sp.putBoolean(R.string.key_setupwizard_processed, true)
            sp.putString(R.string.key_aps_mode, "closed")
            MainApp.getDbHelper().resetDatabases()
            MainApp.devBranch = false
        }

        private fun preparePlugins() {
            // Source
            configBuilderPlugin.performPluginSwitch(randomBgPlugin,true, PluginType.BGSOURCE)
            // Profile
            configBuilderPlugin.performPluginSwitch(localProfilePlugin, true, PluginType.PROFILE)
            val profile = Profile(JSONObject(validProfile), GlucoseUnit.MGDL.asText)
            Assert.assertTrue(profile.isValid("Test"))
            localProfilePlugin.profiles.clear()
            localProfilePlugin.numOfProfiles = 0
            val singleProfile = LocalProfilePlugin.SingleProfile().copyFrom(localProfilePlugin.rawProfile, profile, "TestProfile")
            localProfilePlugin.addProfile(singleProfile)
            val profileSwitch = profileFunction.prepareProfileSwitch(localProfilePlugin.createProfileStore(), "TestProfile", 0, 100, 0, dateUtil._now())
            treatmentsPlugin.addToHistoryProfileSwitch(profileSwitch)
            // Insulin
            configBuilderPlugin.performPluginSwitch(insulinOrefUltraRapidActingPlugin, true, PluginType.INSULIN)
            // Pump
            sp.putInt(R.string.key_danar_password, R_PASSWORD)
            sp.putString(R.string.key_danar_bt_name, R_SERIAL)
            configBuilderPlugin.performPluginSwitch((pump as PluginBase), true, PluginType.PUMP)
            // Sensitivity
            configBuilderPlugin.performPluginSwitch(sensitivityOref1Plugin, true, PluginType.SENSITIVITY)
            // APS
            configBuilderPlugin.performPluginSwitch(openAPSSMBPlugin, true, PluginType.APS)
            configBuilderPlugin.performPluginSwitch(loopPlugin, true, PluginType.LOOP)

            // Enable common
            configBuilderPlugin.performPluginSwitch(actionsPlugin, true, )

            // Disable unneeded
            MainApp.getPluginsList().remove(objectivesPlugin)
        }

        @Test
        fun doTest() {
            Assert.assertTrue(isRunningTest())
            preparePlugins()

            while (!pump.isInitialized) {
                //log.debug("Waiting for initialization")
                SystemClock.sleep(1000)
            }

            while (true) {
                //log.debug("Tick")
                SystemClock.sleep(1000)
            }
        }
     */
}