package info.nightscout.plugins.skins

import app.aaps.core.interfaces.sharedPreferences.SP
import info.nightscout.plugins.R
import info.nightscout.plugins.di.SkinsModule
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SkinProvider @Inject constructor(
    val sp: SP,
    @SkinsModule.Skin val allSkins: Map<@JvmSuppressWildcards Int, @JvmSuppressWildcards SkinInterface>
) {

    fun activeSkin(): SkinInterface =
        list.firstOrNull { it.javaClass.name == sp.getString(R.string.key_skin, "") }
            ?: list.first()

    val list: List<SkinInterface>
        get() = allSkins.toImmutableMap().toList().sortedBy { it.first }.map { it.second }

    /** Returns an immutable copy of this. */
    private fun Map<Int, SkinInterface>.toImmutableMap(): Map<Int, SkinInterface> =
        if (isEmpty()) emptyMap()
        else Collections.unmodifiableMap(LinkedHashMap(this))
}
