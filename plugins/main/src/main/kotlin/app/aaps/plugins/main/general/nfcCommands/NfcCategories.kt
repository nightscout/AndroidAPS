package app.aaps.plugins.main.general.nfcCommands

data class NfcUiCategory(
    val labelResId: Int,
    val commands: List<NfcCommandCode>,
)

object NfcCategories {
    fun build(plugin: NfcCommandsPlugin): List<NfcUiCategory> {
        return NfcCommandCode.entries
            .filter { it.isSupported(plugin) }
            .groupBy { it.category }
            .map { (cat, cmds) ->
                NfcUiCategory(
                    labelResId = cat.labelResId,
                    commands = cmds
                )
            }
    }
}
