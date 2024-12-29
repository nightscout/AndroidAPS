package app.aaps.pump.eopatch.ble.task

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.pump.eopatch.ble.PreferenceManager
import app.aaps.pump.eopatch.core.Patch
import app.aaps.pump.eopatch.core.exception.NoActivatedPatchException
import app.aaps.pump.eopatch.core.exception.PatchDisconnectedException
import app.aaps.pump.eopatch.core.response.BaseResponse
import app.aaps.pump.eopatch.core.scan.BleConnectionState
import app.aaps.pump.eopatch.vo.NormalBasalManager
import app.aaps.pump.eopatch.vo.PatchConfig
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.functions.Consumer
import java.lang.Exception
import java.util.HashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class TaskBase @Inject constructor(val func: TaskFunc) {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var pm: PreferenceManager
    @Inject lateinit var normalBasalManager: NormalBasalManager
    @Inject lateinit var patchConfig: PatchConfig
    @Inject lateinit var taskQueue: TaskQueue

    /* enqueue 시 사용 */
    var disposable: Disposable? = null
    protected val lock: Any = Any()
    val patch = Patch.getInstance()

    init {
        maps.put(func, this)
    }

    /* Task 들의 작업 순서 및 조건 체크 */
    protected fun isReady(): Observable<TaskFunc> {
        return taskQueue.isReady(func).doOnNext(Consumer { preCondition() })
    }

    protected fun isReady2(): Observable<TaskFunc> {
        return taskQueue.isReady2(func).doOnNext(Consumer { preCondition() })
    }

    @Throws(Exception::class) protected fun checkResponse(response: BaseResponse) {
        if (!response.isSuccess) {
            throw Exception("Response failed! - " + response.resultCode.name)
        }
    }

    @Synchronized
    open fun enqueue() {
    }

    @Synchronized
    open fun enqueue(flag: Boolean) {
    }

    @Throws(Exception::class) protected open fun preCondition() {
    }

    @Throws(Exception::class) protected fun checkPatchConnected() {
        if (patch.connectionState == BleConnectionState.DISCONNECTED) {
            throw PatchDisconnectedException()
        }
    }

    @Throws(Exception::class) protected fun checkPatchActivated() {
        if (patchConfig.isDeactivated) {
            throw NoActivatedPatchException()
        }
    }

    companion object {

        var maps: HashMap<TaskFunc, TaskBase> = HashMap<TaskFunc, TaskBase>()

        protected const val TASK_ENQUEUE_TIME_OUT: Long = 60 // SECONDS

        fun enqueue(func: TaskFunc) {
            maps[func]?.enqueue()
        }

        fun enqueue(func: TaskFunc, flag: Boolean) {
            maps[func]?.enqueue(flag)
        }
    }
}
