package info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard2.fragment.info

class PodReplacedInfoFragment : InfoFragmentBase() {
    override fun getText(): String = "the Pod has been replaced"

    override fun getNextPageActionId(): Int? = null

    override fun getTitle(): String = "Pod replaced"

    override fun getIndex(): Int = 8
}