package app.aaps.di

import app.aaps.core.interfaces.plugin.PluginBase

/**
 * One source of plugins, so a problem can name where it came from.
 *
 * @param name shown in the error message, for example "Dagger @AllConfigs" or "Metro".
 * @param plugins the plugins that source contributes, keyed by their display order.
 */
data class PluginSource(val name: String, val plugins: Map<Int, PluginBase>)

/**
 * Merges the plugin maps from Dagger and Metro and reports anything wrong with the result.
 *
 * While both frameworks contribute plugins, two mistakes are possible and **both are silent**:
 *
 *  1. **Two sources use the same order key.** The later map wins and the earlier plugin simply
 *     disappears from the app. This is the more dangerous of the two, because it also hides the
 *     second problem: a plugin moved to Metro but left bound in Dagger keeps its old key, so the
 *     duplicate is masked by the overwrite and everything looks fine.
 *  2. **The same plugin class arrives twice under different keys.** The user then sees the plugin
 *     listed twice, and two instances of it run - which for a plugin that talks to the pump or
 *     uploads data is not a cosmetic problem.
 *
 * Neither framework can see the other's contributions, so neither can detect either case. Hence this.
 *
 * @return the merged plugins in display order, plus a list of problems. An empty problem list means
 *   the merge is healthy.
 */
fun mergePlugins(sources: List<PluginSource>): Pair<List<PluginBase>, List<String>> {
    val problems = mutableListOf<String>()
    val merged = LinkedHashMap<Int, PluginBase>()
    val keyOwner = mutableMapOf<Int, String>()

    for (source in sources) {
        for ((key, plugin) in source.plugins) {
            val previousOwner = keyOwner[key]
            if (previousOwner != null) {
                val previous = merged.getValue(key)
                problems += "order key $key used by both ${previousOwner} (${previous.javaClass.simpleName})" +
                    " and ${source.name} (${plugin.javaClass.simpleName}) - one of them is dropped"
            }
            merged[key] = plugin
            keyOwner[key] = source.name
        }
    }

    val ordered = merged.toList().sortedBy { it.first }.map { it.second }
    ordered.groupBy { it.javaClass }
        .filterValues { it.size > 1 }
        .forEach { (type, copies) ->
            problems += "plugin ${type.simpleName} is in the list ${copies.size} times" +
                " - it is probably contributed by Dagger and by Metro"
        }

    return ordered to problems
}
