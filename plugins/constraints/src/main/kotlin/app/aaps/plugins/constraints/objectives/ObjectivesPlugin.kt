package app.aaps.plugins.constraints.objectives

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.Objectives
import app.aaps.core.interfaces.constraints.PluginConstraints
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.constraints.R
import app.aaps.plugins.constraints.objectives.objectives.Objective
import app.aaps.plugins.constraints.objectives.objectives.Objective0
import app.aaps.plugins.constraints.objectives.objectives.Objective1
import app.aaps.plugins.constraints.objectives.objectives.Objective10
import app.aaps.plugins.constraints.objectives.objectives.Objective2
import app.aaps.plugins.constraints.objectives.objectives.Objective3
import app.aaps.plugins.constraints.objectives.objectives.Objective4
import app.aaps.plugins.constraints.objectives.objectives.Objective5
import app.aaps.plugins.constraints.objectives.objectives.Objective6
import app.aaps.plugins.constraints.objectives.objectives.Objective7
import app.aaps.plugins.constraints.objectives.objectives.Objective9
import dagger.android.HasAndroidInjector
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObjectivesPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    preferences: Preferences,
    private val sp: SP, // 新增：共享偏好存储，用于重置逻辑
    private val injector: HasAndroidInjector // 新增：Android注入器，用于创建目标实例
) : PluginBaseWithPreferences(
    pluginDescription = PluginDescription()
        .mainType(PluginType.CONSTRAINTS)
        .fragmentClass(ObjectivesFragment::class.qualifiedName)
        .pluginIcon(app.aaps.core.ui.R.drawable.ic_graduation)
        .pluginName(app.aaps.core.ui.R.string.objectives)
        .shortName(R.string.objectives_shortname)
        .enableByDefault(true) // 强制默认启用，不再依赖APS版本判断
        .description(R.string.description_objectives),
    ownPreferences = listOf(), // 清空目标相关的自定义偏好，不再需要
    aapsLogger, rh, preferences
), PluginConstraints, Objectives {

    // 手动初始化目标列表，不再依赖Dagger自动注入
    var objectives: MutableList<Objective> = ArrayList()

    init {
        setupObjectives()
    }

    private fun setupObjectives() {
        objectives.clear()
        val currentTime = System.currentTimeMillis()
        val oneDayMs = 86400000L // 1天的毫秒数

        // 手动创建所有目标实例，强制标记为：1天前开始，当前时间已完成
        objectives.add(Objective0(injector).apply {
            startedOn = currentTime - oneDayMs
            accomplishedOn = currentTime
        })
        objectives.add(Objective1(injector).apply {
            startedOn = currentTime - oneDayMs
            accomplishedOn = currentTime
        })
        objectives.add(Objective2(injector).apply {
            startedOn = currentTime - oneDayMs
            accomplishedOn = currentTime
        })
        objectives.add(Objective3(injector).apply {
            startedOn = currentTime - oneDayMs
            accomplishedOn = currentTime
        })
        objectives.add(Objective4(injector).apply {
            startedOn = currentTime - oneDayMs
            accomplishedOn = currentTime
        })
        objectives.add(Objective5(injector).apply {
            startedOn = currentTime - oneDayMs
            accomplishedOn = currentTime
        })
        objectives.add(Objective6(injector).apply {
            startedOn = currentTime - oneDayMs
            accomplishedOn = currentTime
        })
        objectives.add(Objective7(injector).apply {
            startedOn = currentTime - oneDayMs
            accomplishedOn = currentTime
        })
        objectives.add(Objective9(injector).apply {
            startedOn = currentTime - oneDayMs
            accomplishedOn = currentTime
        })
        objectives.add(Objective10(injector).apply {
            startedOn = currentTime - oneDayMs
            accomplishedOn = currentTime
        })
    }

    fun reset() {
        for (objective in objectives) {
            objective.startedOn = 0
            objective.accomplishedOn = 0
        }
        // 适配SP的存储API，重置所有目标标记
        sp.putBoolean(app.aaps.core.utils.R.string.key_objectives_bg_is_available_in_ns, false)
        sp.putBoolean(app.aaps.core.utils.R.string.key_objectives_pump_status_is_available_in_ns, false)
        sp.putInt(app.aaps.core.utils.R.string.key_ObjectivesmanualEnacts, 0)
        sp.putBoolean(app.aaps.core.utils.R.string.key_objectiveuseprofileswitch, false)
        sp.putBoolean(app.aaps.core.utils.R.string.key_objectiveusedisconnect, false)
        sp.putBoolean(app.aaps.core.utils.R.string.key_objectiveusereconnect, false)
        sp.putBoolean(app.aaps.core.utils.R.string.key_objectiveusetemptarget, false)
        sp.putBoolean(app.aaps.core.utils.R.string.key_objectiveuseactions, false)
        sp.putBoolean(app.aaps.core.utils.R.string.key_objectiveuseloop, false)
        sp.putBoolean(app.aaps.core.utils.R.string.key_objectiveusescale, false)
    }

    fun allPriorAccomplished(position: Int): Boolean {
        // 所有目标默认已完成，前置校验直接通过
        return true
    }

    /**
     * 清空所有功能约束，直接返回原值，解锁全部功能
     */
    override fun isLoopInvocationAllowed(value: Constraint<Boolean>): Constraint<Boolean> {
        return value
    }

    override fun isLgsForced(value: Constraint<Boolean>): Constraint<Boolean> {
        return value
    }

    override fun isLgsAllowed(value: Constraint<Boolean>): Constraint<Boolean> {
        return value
    }

    override fun isClosedLoopAllowed(value: Constraint<Boolean>): Constraint<Boolean> {
        return value
    }

    override fun isAutosensModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        return value
    }

    override fun isSMBModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        return value
    }

    override fun applyMaxIOBConstraints(maxIob: Constraint<Double>): Constraint<Double> {
        return maxIob
    }

    override fun isAutomationEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        return value
    }

    /**
     * 强制所有目标标记为已完成，绕过状态校验
     */
    override fun isAccomplished(index: Int) = true

    /**
     * 强制所有目标标记为已开始，绕过状态校验
     */
    override fun isStarted(index: Int): Boolean = true
}