package com.sparjapati.vendorClient.response

import okhttp3.Request
import retrofit2.Response

sealed class NetworkResponse<out T>(
    open val responseCode: Int,
    open val request: Request,
    open val rawResponse: Response<*>?,
) {
    data class Success<T>(
        val data: T,
        override val responseCode: Int,
        override val request: Request,
        override val rawResponse: Response<T>,
    ) : NetworkResponse<T>(responseCode, request, rawResponse)

    data class Error(
        val message: String?,
        val exceptionType: NetworkExceptionType,
        val errorBody: String?,
        override val responseCode: Int,
        override val request: Request,
        override val rawResponse: Response<*>?,
    ) : NetworkResponse<Nothing>(responseCode, request, rawResponse)

    fun success(): Boolean = this is Success
}

enum class NetworkExceptionType {
    HTTP, NETWORK, RATE_LIMITED, API_DISABLED, TEMP_API_DISABLED, CIRCUIT_OPEN, UNEXPECTED
}

@Suppress("UNCHECKED_CAST")
inline fun <T, R> NetworkResponse<T>.map(transform: (T) -> R): NetworkResponse<R> = when (this) {
    is NetworkResponse.Success -> NetworkResponse.Success(
        data = transform(data),
        responseCode = responseCode,
        request = request,
        // rawResponse carries the original HTTP envelope; the type param is erased at runtime,
        // so casting Response<T> → Response<R> is safe — no actual T/R values are read from it here.
        rawResponse = rawResponse as Response<R>,
    )
    is NetworkResponse.Error -> this
}

inline fun <T, R> NetworkResponse<T>.fold(
    onSuccess: (T) -> R,
    onError: (NetworkResponse.Error) -> R,
): R = when (this) {
    is NetworkResponse.Success -> onSuccess(data)
    is NetworkResponse.Error -> onError(this)
}

inline fun <T> NetworkResponse<T>.getOrElse(default: (NetworkResponse.Error) -> T): T = when (this) {
    is NetworkResponse.Success -> data
    is NetworkResponse.Error -> default(this)
}

inline fun <T> NetworkResponse<T>.onSuccess(action: (T) -> Unit): NetworkResponse<T> {
    if (this is NetworkResponse.Success) action(data)
    return this
}

inline fun <T> NetworkResponse<T>.onError(action: (NetworkResponse.Error) -> Unit): NetworkResponse<T> {
    if (this is NetworkResponse.Error) action(this)
    return this
}
