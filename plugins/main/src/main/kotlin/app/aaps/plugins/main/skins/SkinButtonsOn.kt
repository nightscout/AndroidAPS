package app.aaps.plugins.main.skins

import app.aaps.core.interfaces.configuration.Config
import app.aaps.plugins.main.R
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SkinButtonsOn @Inject constructor(private val config: Config) : SkinInterface {

    override val description: Int get() = R.string.buttonson_description
    override val mainGraphHeight: Int get() = 200
    override val secondaryGraphHeight: Int get() = 100
}
