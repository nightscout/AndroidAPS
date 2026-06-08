package app.aaps.plugins.main.general.nfcCommands

data class NfcUiCategory(
    val labelResId: Int,
    val commands: List<NfcCommandCode>,
)

object NfcCategories {
    fun build(): List<NfcUiCategory> {
        return NfcCommandCode.entries
            .groupBy { it.category }
            .map { (cat, cmds) ->
                NfcUiCategory(
                    labelResId = cat.labelResId,
                    commands = cmds
                )
            }
    }
}
