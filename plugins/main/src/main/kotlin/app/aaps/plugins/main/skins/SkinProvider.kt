package app.aaps.plugins.main.skins

import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.main.di.SkinsModule
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SkinProvider @Inject constructor(
    val preferences: Preferences,
    @SkinsModule.Skin val allSkins: Map<@JvmSuppressWildcards Int, @JvmSuppressWildcards SkinInterface>
) {

    fun activeSkin(): SkinInterface =
        list.firstOrNull { it.javaClass.name == preferences.get(StringKey.GeneralSkin) }
            ?: list.first()

    val list: List<SkinInterface>
        get() = allSkins.toImmutableMap().toList().sortedBy { it.first }.map { it.second }

    /** Returns an immutable copy of this. */
    private fun Map<Int, SkinInterface>.toImmutableMap(): Map<Int, SkinInterface> =
        if (isEmpty()) emptyMap()
        else Collections.unmodifiableMap(LinkedHashMap(this))
}
