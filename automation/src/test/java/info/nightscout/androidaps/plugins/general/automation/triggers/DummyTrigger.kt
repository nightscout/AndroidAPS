package info.nightscout.androidaps.plugins.general.automation.triggers

import com.google.common.base.Optional
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector

class DummyTrigger(var result: Boolean) : Trigger(HasAndroidInjector { AndroidInjector { } }) {

    override fun shouldRun(): Boolean {
        return result
    }

    override fun toJSON(): String {
        return ""
    }

    override fun fromJSON(data: String): Trigger {
        return DummyTrigger(result)
    }

    override fun friendlyName(): Int {
        return 0
    }

    override fun friendlyDescription(): String {
        return " "
    }

    override fun icon(): Optional<Int?> {
        return Optional.absent()
    }

    override fun duplicate(): Trigger {
        return DummyTrigger(result)
    }
}