package app.aaps.ui.compose.scenes

import app.aaps.core.data.model.RM
import app.aaps.core.data.model.Scene
import app.aaps.core.data.model.SceneAction
import app.aaps.core.data.model.SceneEndAction
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TT
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Test

class SceneSerializerTest : TestBase() {

    @Test
    fun roundTrip_singleScene() {
        val scene = Scene(
            id = "test-1",
            name = "Exercise",
            icon = "exercise",
            defaultDurationMinutes = 60,
            actions = listOf(
                SceneAction.TempTarget(reason = TT.Reason.ACTIVITY, targetMgdl = 140.0),
                SceneAction.ProfileSwitch(profileName = "Sport", percentage = 80, timeShiftHours = 1),
                SceneAction.SmbToggle(enabled = false),
                SceneAction.LoopModeChange(mode = RM.Mode.CLOSED_LOOP_LGS),
                SceneAction.CarePortalEvent(type = TE.Type.EXERCISE, note = "Morning run")
            ),
            endAction = SceneEndAction.Notification,
            isDeletable = true,
            sortOrder = 1
        )

        val json = listOf(scene).toJson()
        val result = json.toScenes()

        assertThat(result).hasSize(1)
        val restored = result[0]
        assertThat(restored.id).isEqualTo("test-1")
        assertThat(restored.name).isEqualTo("Exercise")
        assertThat(restored.icon).isEqualTo("exercise")
        assertThat(restored.defaultDurationMinutes).isEqualTo(60)
        assertThat(restored.isDeletable).isTrue()
        assertThat(restored.sortOrder).isEqualTo(1)
        assertThat(restored.actions).hasSize(5)

        // Verify each action type round-tripped correctly
        val tt = restored.actions[0] as SceneAction.TempTarget
        assertThat(tt.reason).isEqualTo(TT.Reason.ACTIVITY)
        assertThat(tt.targetMgdl).isEqualTo(140.0)
        assertThat(tt.durationMinutes).isEqualTo(60)

        val ps = restored.actions[1] as SceneAction.ProfileSwitch
        assertThat(ps.profileName).isEqualTo("Sport")
        assertThat(ps.percentage).isEqualTo(80)
        assertThat(ps.timeShiftHours).isEqualTo(1)
        assertThat(ps.durationMinutes).isEqualTo(30)

        val smb = restored.actions[2] as SceneAction.SmbToggle
        assertThat(smb.enabled).isFalse()

        val loop = restored.actions[3] as SceneAction.LoopModeChange
        assertThat(loop.mode).isEqualTo(RM.Mode.CLOSED_LOOP_LGS)
        assertThat(loop.durationMinutes).isEqualTo(45)

        val cp = restored.actions[4] as SceneAction.CarePortalEvent
        assertThat(cp.type).isEqualTo(TE.Type.EXERCISE)
        assertThat(cp.note).isEqualTo("Morning run")
        assertThat(cp.durationMinutes).isEqualTo(60)
    }

    @Test
    fun roundTrip_multipleScenes() {
        val scenes = listOf(
            Scene(id = "s1", name = "Exercise", defaultDurationMinutes = 60),
            Scene(id = "s2", name = "Sleep", defaultDurationMinutes = 480, sortOrder = 1),
            Scene(id = "s3", name = "Sick Day", defaultDurationMinutes = 240, sortOrder = 2)
        )

        val result = scenes.toJson().toScenes()

        assertThat(result).hasSize(3)
        assertThat(result.map { it.id }).containsExactly("s1", "s2", "s3").inOrder()
        assertThat(result.map { it.name }).containsExactly("Exercise", "Sleep", "Sick Day").inOrder()
    }

    @Test
    fun emptyList_serializesToEmptyArray() {
        val json = emptyList<Scene>().toJson()
        assertThat(json).isEqualTo("[]")
    }

    @Test
    fun emptyString_deserializesToEmptyList() {
        assertThat("".toScenes()).isEmpty()
    }

    @Test
    fun invalidJson_returnsEmptyList() {
        assertThat("garbage".toScenes()).isEmpty()
        assertThat("{not an array}".toScenes()).isEmpty()
    }

    @Test
    fun unknownActionType_skipped() {
        val json = JSONArray().apply {
            put(JSONObject().apply {
                put("id", "test")
                put("name", "Test")
                put("actions", JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "unknown_future_type")
                        put("data", "whatever")
                    })
                    put(JSONObject().apply {
                        put("type", "smb_toggle")
                        put("enabled", true)
                    })
                })
            })
        }.toString()

        val result = json.toScenes()
        assertThat(result).hasSize(1)
        // Unknown action skipped, only SMB toggle remains
        assertThat(result[0].actions).hasSize(1)
        assertThat(result[0].actions[0]).isInstanceOf(SceneAction.SmbToggle::class.java)
    }

    @Test
    fun sceneEndAction_notification_roundTrip() {
        val scene = Scene(
            id = "n1",
            name = "Notify",
            endAction = SceneEndAction.Notification
        )

        val restored = listOf(scene).toJson().toScenes()[0]
        assertThat(restored.endAction).isEqualTo(SceneEndAction.Notification)
    }

    @Test
    fun sceneEndAction_suggestScene_roundTrip() {
        val scene = Scene(
            id = "s1",
            name = "Pre-Meal",
            endAction = SceneEndAction.SuggestScene("post-meal-id")
        )

        val restored = listOf(scene).toJson().toScenes()[0]
        assertThat(restored.endAction).isInstanceOf(SceneEndAction.SuggestScene::class.java)
        assertThat((restored.endAction as SceneEndAction.SuggestScene).sceneId).isEqualTo("post-meal-id")
    }

    @Test
    fun tempTarget_allReasons_roundTrip() {
        for (reason in TT.Reason.entries) {
            val scene = Scene(
                id = "tt-${reason.name}",
                name = reason.text,
                actions = listOf(
                    SceneAction.TempTarget(reason = reason, targetMgdl = 100.0)
                )
            )

            val restored = listOf(scene).toJson().toScenes()[0]
            val tt = restored.actions[0] as SceneAction.TempTarget
            assertThat(tt.reason).isEqualTo(reason)
        }
    }

    @Test
    fun loopMode_allModes_roundTrip() {
        for (mode in RM.Mode.entries) {
            val scene = Scene(
                id = "lm-${mode.name}",
                name = mode.name,
                actions = listOf(
                    SceneAction.LoopModeChange(mode = mode)
                )
            )

            val restored = listOf(scene).toJson().toScenes()[0]
            val lm = restored.actions[0] as SceneAction.LoopModeChange
            assertThat(lm.mode).isEqualTo(mode)
        }
    }
}
