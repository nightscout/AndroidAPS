package app.aaps.implementation.alerts

import app.aaps.core.interfaces.alerts.AlertSeverity
import app.aaps.core.interfaces.alerts.BgAlarmRule
import app.aaps.core.interfaces.alerts.BgAlarmRules
import app.aaps.core.interfaces.alerts.BgDirection
import app.aaps.core.interfaces.alerts.BgRuleType
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * The user's BG level rules, stored as a JSON list in [StringNonKey.BgAlarmRules]. The key is synced from the master,
 * so a client sees the master's rules here too, and a change from the master comes through [rules]. Never stored: the
 * default rules ([BgAlarmRules.DEFAULT]); a list the user emptied stays empty.
 *
 * Read on first use, not when the object graph is built.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class BgAlarmRulesImpl(
    private val preferences: Preferences,
    private val aapsLogger: AAPSLogger,
    @ApplicationScope private val scope: CoroutineScope
) : BgAlarmRules {

    override val rules: StateFlow<List<BgAlarmRule>> by lazy {
        preferences.observe(StringNonKey.BgAlarmRules).map { parse(it) }
            .stateIn(scope, SharingStarted.Eagerly, parse(preferences.get(StringNonKey.BgAlarmRules)))
    }

    override fun save(rules: List<BgAlarmRule>) {
        // On a client the sync sends it to the master, which takes it from an Admin only and sends it to every phone
        preferences.put(StringNonKey.BgAlarmRules, toJson(rules))
    }

    internal fun parse(raw: String): List<BgAlarmRule> = if (raw.isEmpty()) BgAlarmRules.DEFAULT else try {
        Json.parseToJsonElement(raw).jsonArray.mapNotNull { element ->
            val o = element.jsonObject
            // One rule that cannot be read is dropped, not the whole list
            runCatching { o.toRule() }.getOrNull().also { if (it == null) aapsLogger.warn(LTag.BGSOURCE, "BG alarm rule unreadable, dropped: $o") }
        }
    } catch (e: Exception) {
        aapsLogger.error(LTag.BGSOURCE, "BG alarm rules unreadable, none used", e)
        emptyList()
    }

    private fun JsonObject.toRule(): BgAlarmRule {
        val direction = BgDirection.valueOf(getValue("direction").jsonPrimitive.content)
        // Stored before there were types: a level rule
        val type = get("type")?.jsonPrimitive?.content?.let { BgRuleType.valueOf(it) } ?: BgRuleType.LEVEL
        val duration = get("durationMinutes")?.jsonPrimitive?.int ?: 0
        // A level rule has no delay any more (decided 2026-11-27). A high stored with one meant "only if it stays
        // high", which is persistent high now; a low rings at once, the safe side.
        val delayedHigh = type == BgRuleType.LEVEL && direction == BgDirection.HIGH && duration > 0
        return BgAlarmRule(
            id = getValue("id").jsonPrimitive.content,
            direction = direction,
            thresholdMgdl = getValue("thresholdMgdl").jsonPrimitive.double,
            severity = AlertSeverity.valueOf(getValue("severity").jsonPrimitive.content),
            fromMinute = get("fromMinute")?.jsonPrimitive?.int,
            toMinute = get("toMinute")?.jsonPrimitive?.int,
            durationMinutes = when {
                delayedHigh              -> maxOf(duration, BgAlarmRule.PERSISTENT_HIGH_MIN_MINUTES)
                type == BgRuleType.LEVEL -> 0
                else                     -> duration
            },
            // "silenceMinutes" of an older rule is not read: the kind decides now (BgAlert.silenceMinutes)
            enabled = get("enabled")?.jsonPrimitive?.boolean ?: true,
            type = if (delayedHigh) BgRuleType.PERSISTENT_HIGH else type
        )
    }

    internal fun toJson(rules: List<BgAlarmRule>): String = buildJsonArray {
        rules.forEach { r ->
            add(buildJsonObject {
                put("id", r.id)
                put("direction", r.direction.name)
                put("thresholdMgdl", r.thresholdMgdl)
                put("severity", r.severity.name)
                r.fromMinute?.let { put("fromMinute", it) }
                r.toMinute?.let { put("toMinute", it) }
                put("durationMinutes", r.durationMinutes)
                put("enabled", r.enabled)
                put("type", r.type.name)
            })
        }
    }.toString()
}
