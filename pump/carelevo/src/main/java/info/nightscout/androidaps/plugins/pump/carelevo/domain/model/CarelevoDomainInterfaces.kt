package info.nightscout.androidaps.plugins.pump.carelevo.domain.model

interface RepositoryRequest
interface RepositoryResponse

sealed class ResponseResult<out T : Any> {
    data class Success<out T : Any>(val data : T?) : ResponseResult<T>()
    data class Failure(val message : String) : ResponseResult<Nothing>()
    data class Error(val e : Throwable) : ResponseResult<Nothing>()
}

sealed class RequestResult<out T : Any> {
    data class Pending<out T : Any>(val data : T) : RequestResult<T>()
    data class Success<out T : Any>(val data : T) : RequestResult<T>()
    data class Failure(val message : String) : RequestResult<Nothing>()
    data class Error(val e : Throwable) : RequestResult<Nothing>()
}